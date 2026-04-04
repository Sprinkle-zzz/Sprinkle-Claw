package com.sprinkleclaw.llm.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sprinkleclaw.llm.LlmCapabilities;
import com.sprinkleclaw.llm.LlmConfig;
import com.sprinkleclaw.llm.LlmException;
import com.sprinkleclaw.llm.LlmException.ErrorKind;
import com.sprinkleclaw.llm.LlmProvider;
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

/**
 * Anthropic Claude API 的 {@link LlmProvider} 实现。
 * <p>使用 JDK {@link HttpClient} 调用 Anthropic Messages API，
 * 负责 Protocol 模型与 Anthropic JSON 格式之间的双向转换。</p>
 *
 * @author sprinkle
 * @since 2026/3/18
 */
public final class AnthropicProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String API_VERSION = "2023-06-01";

    private final HttpClient httpClient;
    private final LlmConfig config;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    /**
     * @param config LLM 配置（需包含 apiKey 和 model）
     */
    public AnthropicProvider(LlmConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .build();
        this.baseUrl = config.baseUrl().isEmpty() ? DEFAULT_BASE_URL : config.baseUrl();
    }

    @Override
    public String providerId() {
        return "anthropic";
    }

    @Override
    public LlmCapabilities capabilities() {
        return LlmCapabilities.builder()
                .supportsReasoning(true)
                .supportsStructuredOutput(false)
                .contextWindowTokens(200_000)
                .maxOutputTokens(8192)
                .build();
    }

    /**
     * 发送聊天请求到 Anthropic API 并解析响应。
     * <p>HTTP 错误码映射：401 → AUTH_ERROR, 429 → RATE_LIMIT, 400 → INVALID_REQUEST, 5xx → SERVER_ERROR。</p>
     */
    @Override
    public ChatResponse chat(ChatRequest request) throws LlmException {
        try {
            String body = buildRequestBody(request);
            log.debug("发送请求到 Anthropic API");

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/messages"))
                    .header("x-api-key", config.apiKey())
                    .header("anthropic-version", API_VERSION)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(config.timeout())
                    .build();

            HttpResponse<String> httpResponse =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

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
     * 将 Protocol ChatRequest 转换为 Anthropic 请求 JSON 字符串。
     */
    private String buildRequestBody(ChatRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.model());
        root.put("max_tokens", request.maxTokens());

        if (!request.systemPrompt().isEmpty()) {
            root.put("system", request.systemPrompt());
        }

        ArrayNode messagesArray = root.putArray("messages");
        for (Message msg : request.messages()) {
            convertMessage(msg, messagesArray);
        }

        if (!request.tools().isEmpty()) {
            ArrayNode toolsArray = root.putArray("tools");
            for (ToolDefinition tool : request.tools()) {
                ObjectNode toolNode = toolsArray.addObject();
                toolNode.put("name", tool.name());
                toolNode.put("description", tool.description());
                toolNode.set("input_schema", objectMapper.valueToTree(tool.inputSchema()));
            }
        }

        return root.toString();
    }

    /**
     * 将 Protocol Message 转换为 Anthropic 消息 JSON 节点。
     * <p>ToolResultMessage 映射为 Anthropic 的 user 角色 + tool_result content block。</p>
     */
    private void convertMessage(Message msg, ArrayNode messagesArray) {
        switch (msg) {
            case Message.UserMessage u -> {
                ObjectNode msgNode = messagesArray.addObject();
                msgNode.put("role", "user");
                ArrayNode contentArray = msgNode.putArray("content");
                for (ContentBlock block : u.content()) {
                    if (block instanceof ContentBlock.TextBlock t) {
                        contentArray.addObject()
                                .put("type", "text")
                                .put("text", t.text());
                    }
                }
            }
            case Message.AssistantMessage a -> {
                ObjectNode msgNode = messagesArray.addObject();
                msgNode.put("role", "assistant");
                ArrayNode contentArray = msgNode.putArray("content");
                for (ContentBlock block : a.content()) {
                    switch (block) {
                        case ContentBlock.TextBlock t -> contentArray.addObject()
                                .put("type", "text")
                                .put("text", t.text());
                        case ContentBlock.ToolUseBlock t -> {
                            ObjectNode toolNode = contentArray.addObject();
                            toolNode.put("type", "tool_use");
                            toolNode.put("id", t.id());
                            toolNode.put("name", t.name());
                            toolNode.set("input", objectMapper.valueToTree(t.input()));
                        }
                        case ContentBlock.ThinkingBlock t -> contentArray.addObject()
                                .put("type", "thinking")
                                .put("thinking", t.thinking());
                    }
                }
            }
            case Message.ToolResultMessage tr -> {
                ObjectNode msgNode = messagesArray.addObject();
                msgNode.put("role", "user");
                ArrayNode contentArray = msgNode.putArray("content");
                ObjectNode resultNode = contentArray.addObject();
                resultNode.put("type", "tool_result");
                resultNode.put("tool_use_id", tr.toolCallId());
                resultNode.put("content", tr.content());
                if (tr.isError()) {
                    resultNode.put("is_error", true);
                }
            }
        }
    }

    /**
     * 将 Anthropic 响应 JSON 解析为 Protocol ChatResponse。
     * <p>核心映射：content[] 中的 text/tool_use/thinking → 对应的 ContentBlock。</p>
     */
    @SuppressWarnings("unchecked")
    private ChatResponse parseResponse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);

            List<ContentBlock> blocks = new ArrayList<>();
            JsonNode contentArray = node.get("content");
            if (contentArray != null && contentArray.isArray()) {
                for (JsonNode contentNode : contentArray) {
                    String type = contentNode.get("type").asText();
                    switch (type) {
                        case "text" -> blocks.add(new ContentBlock.TextBlock(contentNode.get("text").asText()));
                        case "tool_use" -> blocks.add(new ContentBlock.ToolUseBlock(
                                contentNode.get("id").asText(),
                                contentNode.get("name").asText(),
                                objectMapper.convertValue(contentNode.get("input"), Map.class)));
                        case "thinking" -> {
                            JsonNode thinkingNode = contentNode.get("thinking");
                            if (thinkingNode != null) {
                                blocks.add(new ContentBlock.ThinkingBlock(thinkingNode.asText()));
                            }
                        }
                    }
                }
            }

            StopReason stopReason = mapStopReason(
                    node.has("stop_reason") ? node.get("stop_reason").asText() : "end_turn");

            Usage usage = new Usage(
                    node.at("/usage/input_tokens").asInt(0),
                    node.at("/usage/output_tokens").asInt(0));

            String modelId = node.has("model") ? node.get("model").asText() : "";

            return new ChatResponse(blocks, stopReason, usage, modelId);

        } catch (Exception e) {
            throw new LlmException(ErrorKind.UNKNOWN, "Failed to parse response: " + e.getMessage(), e);
        }
    }

    /**
     * Anthropic stop_reason → Protocol StopReason 映射。
     */
    private StopReason mapStopReason(String anthropicReason) {
        return switch (anthropicReason) {
            case "end_turn" -> StopReason.END_TURN;
            case "tool_use" -> StopReason.TOOL_USE;
            case "max_tokens" -> StopReason.MAX_TOKENS;
            case "stop_sequence" -> StopReason.STOP_SEQUENCE;
            default -> StopReason.END_TURN;
        };
    }

    /**
     * 处理 HTTP 错误响应，将 HTTP 状态码映射为对应的 {@link ErrorKind}。
     */
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
     * 从 Anthropic 错误响应 JSON 中提取错误消息。
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
