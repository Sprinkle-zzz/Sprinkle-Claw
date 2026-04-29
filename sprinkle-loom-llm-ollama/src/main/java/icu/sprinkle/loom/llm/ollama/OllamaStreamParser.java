package icu.sprinkle.loom.llm.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.sprinkle.loom.llm.LlmCapabilities;
import icu.sprinkle.loom.llm.LlmException;
import icu.sprinkle.loom.llm.LlmException.ErrorKind;
import icu.sprinkle.loom.llm.StreamCallback;
import icu.sprinkle.loom.protocol.llm.ChatResponse;
import icu.sprinkle.loom.protocol.llm.StopReason;
import icu.sprinkle.loom.protocol.llm.Usage;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ollama NDJSON 流式响应解析器。
 * <p>Ollama 使用换行分隔 JSON（NDJSON）而非 SSE，每行是一个完整 JSON 对象。</p>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
final class OllamaStreamParser {

    private static final Logger log = LoggerFactory.getLogger(OllamaStreamParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StreamCallback callback;
    private final LlmCapabilities capabilities;
    private final StringBuilder accumText = new StringBuilder();
    private final List<ContentBlock> accumBlocks = new ArrayList<>();
    private int promptTokens;
    private int completionTokens;
    private String modelId = "";

    OllamaStreamParser(StreamCallback callback, LlmCapabilities capabilities) {
        this.callback = callback;
        this.capabilities = capabilities;
    }

    /**
     * 消费 NDJSON 流，逐行解析并回调，返回最终组装的 ChatResponse。
     */
    @SuppressWarnings("unchecked")
    ChatResponse consume(InputStream stream) throws LlmException {
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode node = MAPPER.readTree(line);

                // error check
                if (node.has("error")) {
                    String error = node.get("error").asText();
                    throw new LlmException(ErrorKind.SERVER_ERROR, "Ollama stream error: " + error);
                }

                onChunk(node);
                if (node.path("done").asBoolean(false)) break;
            }
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException(ErrorKind.NETWORK_ERROR, "Stream parse error: " + e.getMessage(), e);
        }
        return assemble();
    }

    @SuppressWarnings("unchecked")
    private void onChunk(JsonNode node) {
        JsonNode messageNode = node.path("message");

        // text delta
        String delta = messageNode.path("content").asText("");
        if (!delta.isEmpty()) {
            callback.onToken(delta);
            accumText.append(delta);
        }

        // native tool_calls (some Ollama models support streaming tool_calls)
        JsonNode toolCalls = messageNode.get("tool_calls");
        if (toolCalls != null && toolCalls.isArray()) {
            for (JsonNode tc : toolCalls) {
                JsonNode funcNode = tc.get("function");
                if (funcNode != null) {
                    String name = funcNode.path("name").asText();
                    Map<String, Object> args = MAPPER.convertValue(
                            funcNode.get("arguments"), Map.class);
                    String id = "ollama_" + name + "_" + System.nanoTime();
                    accumBlocks.add(new ContentBlock.ToolUseBlock(id, name, args != null ? args : Map.of()));
                }
            }
        }

        // done=true → extract usage
        if (node.path("done").asBoolean(false)) {
            promptTokens = node.path("prompt_eval_count").asInt(0);
            completionTokens = node.path("eval_count").asInt(0);
            modelId = node.path("model").asText(modelId);
        } else {
            modelId = node.path("model").asText(modelId);
        }
    }

    private ChatResponse assemble() {
        List<ContentBlock> blocks = new ArrayList<>(accumBlocks);
        String text = accumText.toString();

        if (!text.isEmpty()) {
            // 如果模型不支持原生工具调用，尝试从累积文本中提取工具调用
            if (!capabilities.supportsToolUse()) {
                List<ContentBlock> toolBlocks = OllamaToolBridge.parseToolCallsFromText(text);
                if (!toolBlocks.isEmpty()) {
                    blocks.addAll(toolBlocks);
                } else {
                    blocks.addFirst(new ContentBlock.TextBlock(text));
                }
            } else {
                blocks.addFirst(new ContentBlock.TextBlock(text));
            }
        }

        boolean hasToolCalls = blocks.stream().anyMatch(b -> b instanceof ContentBlock.ToolUseBlock);
        StopReason stopReason = hasToolCalls ? StopReason.TOOL_USE : StopReason.END_TURN;

        return new ChatResponse(blocks, stopReason, new Usage(promptTokens, completionTokens), modelId);
    }
}
