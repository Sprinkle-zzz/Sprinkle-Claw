package icu.sprinkle.loom.llm.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import icu.sprinkle.loom.llm.LlmCapabilities;
import icu.sprinkle.loom.llm.LlmException;
import icu.sprinkle.loom.llm.LlmException.ErrorKind;
import icu.sprinkle.loom.protocol.llm.*;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import icu.sprinkle.loom.protocol.message.Message;
import icu.sprinkle.loom.protocol.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ChatRequest ↔ Ollama /api/chat JSON 双向转换器。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
final class OllamaProtocolMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 将 Protocol ChatRequest 转换为 Ollama /api/chat 请求 JSON。
     */
    String toOllamaRequest(ChatRequest request, OllamaConfig config,
                           LlmCapabilities capabilities, boolean stream) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", config.model());
        root.put("stream", stream);

        // messages
        ArrayNode messagesArray = root.putArray("messages");
        if (!request.systemPrompt().isEmpty()) {
            String systemContent = request.systemPrompt();
            // 如果模型不支持原生工具调用，把工具定义注入 system prompt
            if (!capabilities.supportsToolUse() && !request.tools().isEmpty()) {
                systemContent += "\n\n" + OllamaToolBridge.toPromptTools(request.tools(), request.toolChoice());
            }
            ObjectNode systemMsg = messagesArray.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemContent);
        }
        for (Message msg : request.messages()) {
            convertMessage(msg, messagesArray);
        }

        // tools（仅当模型原生支持时透传）
        if (capabilities.supportsToolUse() && !request.tools().isEmpty()) {
            ArrayNode toolsArray = root.putArray("tools");
            for (ToolDefinition tool : request.tools()) {
                ObjectNode toolWrapper = toolsArray.addObject();
                toolWrapper.put("type", "function");
                ObjectNode funcNode = toolWrapper.putObject("function");
                funcNode.put("name", tool.name());
                funcNode.put("description", tool.description());
                funcNode.set("parameters", MAPPER.valueToTree(tool.inputSchema()));
            }
        }

        // options
        ObjectNode options = root.putObject("options");
        if (request.maxTokens() > 0) {
            options.put("num_predict", request.maxTokens());
        }
        if (request.temperature() >= 0) {
            options.put("temperature", request.temperature());
        }
        if (!request.stopSequences().isEmpty()) {
            ArrayNode stopArray = options.putArray("stop");
            request.stopSequences().forEach(stopArray::add);
        }
        if (config.numCtx() > 0) {
            options.put("num_ctx", config.numCtx());
        }
        // 移除空 options
        if (options.isEmpty()) {
            root.remove("options");
        }

        // keep_alive
        if (config.keepAlive() != null) {
            root.put("keep_alive", config.keepAlive().toSeconds() + "s");
        }

        return root.toString();
    }

    /**
     * 将 Ollama /api/chat 非流式响应 JSON 解析为 Protocol ChatResponse。
     */
    @SuppressWarnings("unchecked")
    ChatResponse toChatResponse(String json, LlmCapabilities capabilities) throws LlmException {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode messageNode = root.path("message");
            String role = messageNode.path("role").asText("assistant");

            List<ContentBlock> blocks = new ArrayList<>();

            // text content
            String content = messageNode.path("content").asText("");
            if (!content.isEmpty()) {
                // 如果模型不支持原生工具调用，尝试从文本中提取工具调用
                if (!capabilities.supportsToolUse()) {
                    List<ContentBlock> toolBlocks = OllamaToolBridge.parseToolCallsFromText(content);
                    if (!toolBlocks.isEmpty()) {
                        blocks.addAll(toolBlocks);
                    } else {
                        blocks.add(new ContentBlock.TextBlock(content));
                    }
                } else {
                    blocks.add(new ContentBlock.TextBlock(content));
                }
            }

            // native tool_calls
            JsonNode toolCalls = messageNode.get("tool_calls");
            if (toolCalls != null && toolCalls.isArray()) {
                for (JsonNode tc : toolCalls) {
                    JsonNode funcNode = tc.get("function");
                    if (funcNode != null) {
                        String name = funcNode.path("name").asText();
                        Map<String, Object> args = MAPPER.convertValue(
                                funcNode.get("arguments"), Map.class);
                        String id = "ollama_" + name + "_" + System.nanoTime();
                        blocks.add(new ContentBlock.ToolUseBlock(id, name, args != null ? args : Map.of()));
                    }
                }
            }

            // stop reason
            boolean done = root.path("done").asBoolean(true);
            boolean hasToolCalls = blocks.stream().anyMatch(b -> b instanceof ContentBlock.ToolUseBlock);
            StopReason stopReason = hasToolCalls ? StopReason.TOOL_USE
                    : done ? StopReason.END_TURN : StopReason.END_TURN;

            // usage
            int promptTokens = root.path("prompt_eval_count").asInt(0);
            int completionTokens = root.path("eval_count").asInt(0);
            Usage usage = new Usage(promptTokens, completionTokens);

            String modelId = root.path("model").asText("");

            return new ChatResponse(blocks, stopReason, usage, modelId);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException(ErrorKind.UNKNOWN, "Failed to parse Ollama response: " + e.getMessage(), e);
        }
    }

    private void convertMessage(Message msg, ArrayNode messagesArray) {
        switch (msg) {
            case Message.UserMessage u -> {
                ObjectNode msgNode = messagesArray.addObject();
                msgNode.put("role", "user");
                StringBuilder text = new StringBuilder();
                List<String> base64Images = new ArrayList<>();
                for (ContentBlock block : u.content()) {
                    if (block instanceof ContentBlock.TextBlock t) {
                        text.append(t.text());
                    } else if (block instanceof ContentBlock.ImageBlock i) {
                        if (i.base64Data() != null) {
                            base64Images.add(i.base64Data());
                        }
                    } else if (block instanceof ContentBlock.DocumentBlock) {
                        text.append("[Unsupported: Document content cannot be processed by this model]");
                    } else if (block instanceof ContentBlock.AudioBlock) {
                        text.append("[Unsupported: Audio content cannot be processed by this model]");
                    }
                }
                msgNode.put("content", text.toString());
                if (!base64Images.isEmpty()) {
                    ArrayNode imagesArray = msgNode.putArray("images");
                    base64Images.forEach(imagesArray::add);
                }
            }
            case Message.AssistantMessage a -> {
                ObjectNode msgNode = messagesArray.addObject();
                msgNode.put("role", "assistant");
                String textContent = a.content().stream()
                        .filter(ContentBlock.TextBlock.class::isInstance)
                        .map(b -> ((ContentBlock.TextBlock) b).text())
                        .reduce("", String::concat);
                msgNode.put("content", textContent);

                // tool_calls in assistant message
                List<ContentBlock.ToolUseBlock> toolCalls = a.content().stream()
                        .filter(ContentBlock.ToolUseBlock.class::isInstance)
                        .map(ContentBlock.ToolUseBlock.class::cast)
                        .toList();
                if (!toolCalls.isEmpty()) {
                    ArrayNode toolCallsArray = msgNode.putArray("tool_calls");
                    for (ContentBlock.ToolUseBlock tc : toolCalls) {
                        ObjectNode tcNode = toolCallsArray.addObject();
                        ObjectNode funcNode = tcNode.putObject("function");
                        funcNode.put("name", tc.name());
                        funcNode.set("arguments", MAPPER.valueToTree(tc.input()));
                    }
                }
            }
            case Message.ToolResultMessage tr -> {
                ObjectNode msgNode = messagesArray.addObject();
                msgNode.put("role", "tool");
                msgNode.put("content", tr.content());
            }
        }
    }
}
