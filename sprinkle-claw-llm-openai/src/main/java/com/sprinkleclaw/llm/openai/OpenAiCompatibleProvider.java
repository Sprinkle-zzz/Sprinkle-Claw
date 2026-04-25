package com.sprinkleclaw.llm.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sprinkleclaw.llm.*;
import com.sprinkleclaw.llm.LlmException.ErrorKind;
import com.sprinkleclaw.protocol.llm.*;
import com.sprinkleclaw.protocol.message.ContentBlock;
import com.sprinkleclaw.protocol.message.Message;
import com.sprinkleclaw.protocol.tool.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Stream;

/**
 * OpenAI Chat Completions API 兼容的 {@link LlmProvider} 实现。
 *
 * <p>支持所有兼容 OpenAI API 格式的厂商，包括但不限于：</p>
 * <ul>
 *   <li>OpenAI — {@code https://api.openai.com/v1}</li>
 *   <li>DeepSeek — {@code https://api.deepseek.com/v1}</li>
 *   <li>通义千问 (Qwen) — {@code https://dashscope.aliyuncs.com/compatible-mode/v1}</li>
 *   <li>智谱 GLM — {@code https://open.bigmodel.cn/api/paas/v4}</li>
 *   <li>豆包 (字节跳动) — {@code https://ark.cn-beijing.volces.com/api/v3}</li>
 * </ul>
 *
 * <p>用户只需切换 {@link LlmConfig#baseUrl()} 和 {@link LlmConfig#apiKey()} 即可对接不同厂商。</p>
 *
 * @author sprinkle
 * @since 2026/3/18
 */
public final class OpenAiCompatibleProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private final HttpClient httpClient;
    private final LlmConfig config;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    /**
     * @param config LLM 配置（需包含 apiKey、model，baseUrl 为空时默认 OpenAI 官方地址）
     */
    public OpenAiCompatibleProvider(LlmConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = SharedHttpClient.get();
        this.baseUrl = config.baseUrl().isEmpty() ? DEFAULT_BASE_URL : config.baseUrl();
    }

    @Override
    public String providerId() {
        return "openai";
    }

    @Override
    public LlmCapabilities capabilities() {
        return LlmCapabilities.builder()
                .supportsReasoning(true)
                .supportsStructuredOutput(true)
                .contextWindowTokens(128_000)
                .maxOutputTokens(16_384)
                .supportsToolChoice(true)
                .supportsStreaming(true)
                .supportsToolUse(true)
                .supportsPromptCache(true)
                .supportsVision(true)
                .supportsPdfInput(false)
                .supportsAudioInput(false)
                .build();
    }

    /**
     * 发送聊天请求到 OpenAI 兼容 API。
     * <p>请求格式遵循 OpenAI Chat Completions API 规范。</p>
     */
    @Override
    public ChatResponse chat(ChatRequest request) throws LlmException {
        try {
            String body = buildRequestBody(request);
            log.debug("发送请求到 OpenAI 兼容 API: {}", baseUrl);

            HttpRequest.Builder httpBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(config.timeout());

            for (Map.Entry<String, String> h : config.headers().entrySet()) {
                httpBuilder.header(h.getKey(), h.getValue());
            }

            HttpResponse<String> httpResponse =
                    httpClient.send(httpBuilder.build(), HttpResponse.BodyHandlers.ofString());

            handleHttpError(httpResponse);

            return parseResponse(httpResponse.body());

        } catch (LlmException e) {
            throw e;
        } catch (IOException e) {
            throw new LlmException(ErrorKind.NETWORK_ERROR, "Network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(ErrorKind.TIMEOUT, "Request interrupted", e);
        } catch (Exception e) {
            throw new LlmException(ErrorKind.UNKNOWN, "Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * 流式调用 OpenAI 兼容 API，逐 token 回调。
     */
    @Override
    public ChatResponse streamChat(ChatRequest request, StreamCallback callback) throws LlmException {
        try {
            String body = buildRequestBody(request, true);
            log.debug("发送流式请求到 OpenAI 兼容 API: {}", baseUrl);

            HttpRequest.Builder httpBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(config.timeout());

            for (Map.Entry<String, String> h : config.headers().entrySet()) {
                httpBuilder.header(h.getKey(), h.getValue());
            }

            HttpResponse<Stream<String>> httpResponse =
                    httpClient.send(httpBuilder.build(), HttpResponse.BodyHandlers.ofLines());

            if (httpResponse.statusCode() >= 400) {
                String errorBody = String.join("\n", httpResponse.body().toList());
                handleHttpErrorWithStatus(httpResponse.statusCode(), errorBody);
            }

            var collector = new OpenAiResponseCollector();
            var sseParser = new SseLineParser();

            httpResponse.body().forEach(line -> {
                SseLineParser.SseEvent sseEvent = sseParser.feed(line);
                if (sseEvent == null || sseEvent.isDone()) {
                    return;
                }

                try {
                    dispatchSseEvent(sseEvent, collector, callback);
                } catch (Exception e) {
                    log.warn("处理 SSE 事件异常: {}", e.getMessage());
                }
            });

            return collector.build();

        } catch (LlmException e) {
            throw e;
        } catch (IOException e) {
            throw new LlmException(ErrorKind.NETWORK_ERROR, "Stream error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(ErrorKind.TIMEOUT, "Stream interrupted", e);
        } catch (Exception e) {
            throw new LlmException(ErrorKind.UNKNOWN, "Stream error: " + e.getMessage(), e);
        }
    }

    /**
     * 分发 OpenAI SSE 事件。
     */
    private void dispatchSseEvent(SseLineParser.SseEvent sseEvent,
                                  OpenAiResponseCollector collector,
                                  StreamCallback callback) throws Exception {
        JsonNode data = objectMapper.readTree(sseEvent.data());

        // 提取 model
        if (data.has("model")) {
            collector.setModelId(data.get("model").asText());
        }

        // 提取 usage（OpenAI 在最后一个 chunk 中返回 usage）
        JsonNode usageNode = data.get("usage");
        if (usageNode != null && !usageNode.isNull()) {
            collector.setUsage(
                    usageNode.path("prompt_tokens").asInt(0),
                    usageNode.path("completion_tokens").asInt(0),
                    usageNode.path("completion_tokens_details").path("reasoning_tokens").asInt(0));
        }

        // 提取 choices[0]
        JsonNode choices = data.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return;
        }
        JsonNode choice = choices.get(0);

        // finish_reason
        JsonNode finishReason = choice.get("finish_reason");
        if (finishReason != null && !finishReason.isNull()) {
            collector.setFinishReason(finishReason.asText());
        }

        // delta
        JsonNode delta = choice.get("delta");
        if (delta == null) {
            return;
        }

        // 文本内容
        JsonNode contentNode = delta.get("content");
        if (contentNode != null && !contentNode.isNull()) {
            String content = contentNode.asText();
            callback.onToken(content);
            collector.appendContent(content);
        }

        // tool_calls delta
        JsonNode toolCallsNode = delta.get("tool_calls");
        if (toolCallsNode != null && toolCallsNode.isArray()) {
            for (JsonNode tc : toolCallsNode) {
                int index = tc.get("index").asInt();
                String id = tc.has("id") ? tc.get("id").asText() : null;
                JsonNode funcNode = tc.get("function");
                String name = null;
                String argsDelta = null;
                if (funcNode != null) {
                    name = funcNode.has("name") ? funcNode.get("name").asText() : null;
                    argsDelta = funcNode.has("arguments") ? funcNode.get("arguments").asText() : null;
                }
                collector.appendToolCall(index, id, name, argsDelta);
                if (argsDelta != null) {
                    callback.onToolUseInput(
                            collector.getToolCallId(index),
                            collector.getToolCallName(index),
                            argsDelta);
                }
            }
        }
    }

    /**
     * 将 Protocol ChatRequest 转换为 OpenAI Chat Completions 请求体。
     */
    private String buildRequestBody(ChatRequest request) {
        return buildRequestBody(request, false);
    }

    /**
     * 将 Protocol ChatRequest 转换为 OpenAI Chat Completions 请求体。
     */
    private String buildRequestBody(ChatRequest request, boolean stream) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.model());
        root.put("max_tokens", request.maxTokens());
        root.put("temperature", request.temperature());

        if (stream) {
            root.put("stream", true);
            // 请求流式 usage（OpenAI 需要显式设置）
            root.putObject("stream_options").put("include_usage", true);
        }

        ArrayNode messagesArray = root.putArray("messages");

        if (!request.systemPrompt().isEmpty()) {
            ObjectNode systemMsg = messagesArray.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", request.systemPrompt());
        }

        for (Message msg : request.messages()) {
            convertMessage(msg, messagesArray);
        }

        // 推理模式支持
        request.thinkingConfig().ifPresent(tc -> {
            if (tc.enabled() && tc.reasoningEffort() != null) {
                root.put("reasoning_effort", tc.reasoningEffort().value());
            }
        });

        if (!request.tools().isEmpty()) {
            ArrayNode toolsArray = root.putArray("tools");
            for (ToolDefinition tool : request.tools()) {
                ObjectNode toolWrapper = toolsArray.addObject();
                toolWrapper.put("type", "function");
                ObjectNode funcNode = toolWrapper.putObject("function");
                funcNode.put("name", tool.name());
                funcNode.put("description", tool.description());
                funcNode.set("parameters", objectMapper.valueToTree(tool.inputSchema()));
            }

            // toolChoice 序列化：AUTO → "auto"；NONE → "none"；REQUIRED → "required"；
            // Forced(name) → {type: "function", function: {name: name}}
            serializeToolChoice(request.toolChoice(), root);
        }

        return root.toString();
    }

    /**
     * 将 Protocol Message 转换为 OpenAI 消息格式。
     * <p>OpenAI 格式特点：tool_calls 嵌在 assistant 消息中，tool 结果用 role=tool。</p>
     */
    private void convertMessage(Message msg, ArrayNode messagesArray) {
        switch (msg) {
            case Message.UserMessage u -> {
                ObjectNode msgNode = messagesArray.addObject();
                msgNode.put("role", "user");
                boolean hasMultimodal = u.content().stream().anyMatch(
                        b -> b instanceof ContentBlock.ImageBlock
                                || b instanceof ContentBlock.DocumentBlock
                                || b instanceof ContentBlock.AudioBlock);
                if (hasMultimodal) {
                    ArrayNode contentArray = msgNode.putArray("content");
                    for (ContentBlock block : u.content()) {
                        serializeOpenAiUserBlock(block, contentArray);
                    }
                } else {
                    StringBuilder text = new StringBuilder();
                    for (ContentBlock block : u.content()) {
                        if (block instanceof ContentBlock.TextBlock t) {
                            text.append(t.text());
                        }
                    }
                    msgNode.put("content", text.toString());
                }
            }
            case Message.AssistantMessage a -> {
                ObjectNode msgNode = messagesArray.addObject();
                msgNode.put("role", "assistant");

                String textContent = a.content().stream()
                        .filter(ContentBlock.TextBlock.class::isInstance)
                        .map(b -> ((ContentBlock.TextBlock) b).text())
                        .reduce("", String::concat);
                if (!textContent.isEmpty()) {
                    msgNode.put("content", textContent);
                } else {
                    msgNode.putNull("content");
                }

                List<ContentBlock.ToolUseBlock> toolCalls = a.content().stream()
                        .filter(ContentBlock.ToolUseBlock.class::isInstance)
                        .map(ContentBlock.ToolUseBlock.class::cast)
                        .toList();
                if (!toolCalls.isEmpty()) {
                    ArrayNode toolCallsArray = msgNode.putArray("tool_calls");
                    for (ContentBlock.ToolUseBlock tc : toolCalls) {
                        ObjectNode tcNode = toolCallsArray.addObject();
                        tcNode.put("id", tc.id());
                        tcNode.put("type", "function");
                        ObjectNode funcNode = tcNode.putObject("function");
                        funcNode.put("name", tc.name());
                        try {
                            funcNode.put("arguments", objectMapper.writeValueAsString(tc.input()));
                        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                            funcNode.put("arguments", tc.input().toString());
                        }
                    }
                }
            }
            case Message.ToolResultMessage tr -> {
                ObjectNode msgNode = messagesArray.addObject();
                msgNode.put("role", "tool");
                msgNode.put("tool_call_id", tr.toolCallId());
                msgNode.put("content", tr.content());
            }
        }
    }

    /**
     * 将 OpenAI Chat Completions 响应解析为 Protocol ChatResponse。
     * <p>核心映射：choices[0].message → ContentBlock 列表。</p>
     */
    @SuppressWarnings("unchecked")
    private ChatResponse parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                return ChatResponse.empty(StopReason.END_TURN);
            }

            JsonNode firstChoice = choices.get(0);
            JsonNode messageNode = firstChoice.get("message");
            String finishReason = firstChoice.has("finish_reason") && !firstChoice.get("finish_reason").isNull()
                    ? firstChoice.get("finish_reason").asText() : "stop";

            List<ContentBlock> blocks = new ArrayList<>();

            if (messageNode.has("content") && !messageNode.get("content").isNull()) {
                String content = messageNode.get("content").asText();
                if (!content.isEmpty()) {
                    blocks.add(new ContentBlock.TextBlock(content));
                }
            }

            if (messageNode.has("tool_calls") && messageNode.get("tool_calls").isArray()) {
                for (JsonNode tc : messageNode.get("tool_calls")) {
                    JsonNode funcNode = tc.get("function");
                    String id = tc.get("id").asText();
                    String name = funcNode.get("name").asText();
                    String argsStr = funcNode.get("arguments").asText();
                    Map<String, Object> args = objectMapper.readValue(argsStr, Map.class);
                    blocks.add(new ContentBlock.ToolUseBlock(id, name, args));
                }
            }

            StopReason stopReason = mapFinishReason(finishReason);

            int inputTokens = root.at("/usage/prompt_tokens").asInt(0);
            int outputTokens = root.at("/usage/completion_tokens").asInt(0);
            int reasoningTokens = root.at("/usage/completion_tokens_details/reasoning_tokens").asInt(0);
            int cachedTokens = root.at("/usage/prompt_tokens_details/cached_tokens").asInt(0);
            Usage usage = new Usage(inputTokens, outputTokens, reasoningTokens, 0, cachedTokens);

            String modelId = root.has("model") ? root.get("model").asText() : "";

            return new ChatResponse(blocks, stopReason, usage, modelId);

        } catch (Exception e) {
            throw new LlmException(ErrorKind.UNKNOWN, "Failed to parse response: " + e.getMessage(), e);
        }
    }

    /**
     * 将 Protocol ToolChoice 序列化为 OpenAI tool_choice JSON 字段。
     * 序列化 UserMessage 中的多模态 ContentBlock 到 OpenAI 格式。
     */
    private void serializeOpenAiUserBlock(ContentBlock block, ArrayNode contentArray) {
        switch (block) {
            case ContentBlock.TextBlock t -> contentArray.addObject()
                    .put("type", "text")
                    .put("text", t.text());
            case ContentBlock.ImageBlock i -> {
                ObjectNode node = contentArray.addObject();
                node.put("type", "image_url");
                ObjectNode imgUrl = node.putObject("image_url");
                if (i.base64Data() != null) {
                    imgUrl.put("url", "data:" + i.mediaType() + ";base64," + i.base64Data());
                } else {
                    imgUrl.put("url", i.url());
                }
                imgUrl.put("detail", "auto");
            }
            case ContentBlock.DocumentBlock ignored -> contentArray.addObject()
                    .put("type", "text")
                    .put("text", "[Unsupported: Document content cannot be processed by this model]");
            case ContentBlock.AudioBlock ignored -> contentArray.addObject()
                    .put("type", "text")
                    .put("text", "[Unsupported: Audio content cannot be processed by this model]");
            case ContentBlock.ToolUseBlock ignored -> {
            }
            case ContentBlock.ThinkingBlock ignored -> {
            }
        }
    }

    /**
     * <p>映射规则：AUTO → "auto"；NONE → "none"；REQUIRED → "required"；
     * Forced(name) → {type: "function", function: {name: name}}。</p>
     */
    private void serializeToolChoice(ChatRequest.ToolChoice toolChoice, ObjectNode root) {
        switch (toolChoice) {
            case ChatRequest.ToolChoice.Auto ignored -> root.put("tool_choice", "auto");
            case ChatRequest.ToolChoice.None ignored -> root.put("tool_choice", "none");
            case ChatRequest.ToolChoice.Required ignored -> root.put("tool_choice", "required");
            case ChatRequest.ToolChoice.Forced f -> {
                ObjectNode tc = root.putObject("tool_choice");
                tc.put("type", "function");
                tc.putObject("function").put("name", f.toolName());
            }
        }
    }

    /**
     * OpenAI finish_reason → Protocol StopReason 映射。
     */
    private StopReason mapFinishReason(String finishReason) {
        return switch (finishReason) {
            case "stop" -> StopReason.END_TURN;
            case "tool_calls" -> StopReason.TOOL_USE;
            case "length" -> StopReason.MAX_TOKENS;
            case "content_filter" -> StopReason.CONTENT_FILTER;
            default -> StopReason.END_TURN;
        };
    }

    /**
     * 处理 HTTP 错误响应。
     */
    private void handleHttpErrorWithStatus(int status, String body) {
        String message = "HTTP " + status + ": " + extractErrorMessage(body);
        ErrorKind kind = switch (status) {
            case 401 -> ErrorKind.AUTH_ERROR;
            case 429 -> ErrorKind.RATE_LIMIT;
            case 400 -> ErrorKind.INVALID_REQUEST;
            case 500, 502, 503 -> ErrorKind.SERVER_ERROR;
            default -> ErrorKind.UNKNOWN;
        };
        throw new LlmException(kind, message);
    }

    private void handleHttpError(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }

        String body = response.body();
        String message = "HTTP " + status + ": " + extractErrorMessage(body);

        ErrorKind kind = switch (status) {
            case 401 -> ErrorKind.AUTH_ERROR;
            case 429 -> ErrorKind.RATE_LIMIT;
            case 400 -> ErrorKind.INVALID_REQUEST;
            case 500, 502, 503 -> ErrorKind.SERVER_ERROR;
            default -> ErrorKind.UNKNOWN;
        };

        throw new LlmException(kind, message);
    }

    /**
     * 从 OpenAI 格式错误响应中提取错误消息。
     */
    private String extractErrorMessage(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode errorNode = node.get("error");
            if (errorNode != null && errorNode.has("message")) {
                return errorNode.get("message").asText();
            }
        } catch (Exception ignored) {
        }
        return body;
    }
}
