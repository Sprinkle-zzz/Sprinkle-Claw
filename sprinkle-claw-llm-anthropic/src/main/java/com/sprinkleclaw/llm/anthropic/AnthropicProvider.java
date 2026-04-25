package com.sprinkleclaw.llm.anthropic;

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
        this.httpClient = SharedHttpClient.get();
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
                .supportsToolChoice(true)
                .supportsStreaming(true)
                .supportsToolUse(true)
                .supportsPromptCache(true)
                .supportsVision(true)
                .supportsPdfInput(true)
                .supportsAudioInput(false)
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
     * 流式调用 Anthropic API，逐 token 回调。
     */
    @Override
    public ChatResponse streamChat(ChatRequest request, StreamCallback callback) throws LlmException {
        try {
            String body = buildRequestBody(request, true);
            log.debug("发送流式请求到 Anthropic API");

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/messages"))
                    .header("x-api-key", config.apiKey())
                    .header("anthropic-version", API_VERSION)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(config.timeout())
                    .build();

            HttpResponse<Stream<String>> httpResponse =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());

            if (httpResponse.statusCode() >= 400) {
                // 读取所有行拼接为错误 body
                String errorBody = String.join("\n",
                        httpResponse.body().toList());
                handleHttpErrorWithStatus(httpResponse.statusCode(), errorBody);
            }

            var collector = new AnthropicResponseCollector();
            var sseParser = new SseLineParser();

            httpResponse.body().forEach(line -> {
                SseLineParser.SseEvent sseEvent = sseParser.feed(line);
                if (sseEvent == null) {
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
     * 分发 SSE 事件到 collector 和 callback。
     */
    private void dispatchSseEvent(SseLineParser.SseEvent sseEvent,
                                  AnthropicResponseCollector collector,
                                  StreamCallback callback) throws Exception {
        String eventType = sseEvent.event();
        JsonNode data = objectMapper.readTree(sseEvent.data());

        switch (eventType) {
            case "message_start" -> {
                JsonNode usage = data.at("/message/usage");
                collector.setInputTokens(usage.path("input_tokens").asInt(0));
                collector.setMessageId(data.at("/message/id").asText(""));
                collector.setModelId(data.at("/message/model").asText(""));
            }

            case "content_block_start" -> {
                int index = data.get("index").asInt();
                JsonNode block = data.get("content_block");
                String type = block.get("type").asText();
                callback.onContentBlockStart(index, type);

                if ("tool_use".equals(type)) {
                    collector.startToolUse(index,
                            block.get("id").asText(),
                            block.get("name").asText());
                } else {
                    collector.startContentBlock(index, type);
                }
            }

            case "content_block_delta" -> {
                int index = data.get("index").asInt();
                JsonNode delta = data.get("delta");
                String deltaType = delta.get("type").asText();

                switch (deltaType) {
                    case "text_delta" -> {
                        String text = delta.get("text").asText();
                        callback.onToken(text);
                        collector.appendText(text);
                    }
                    case "thinking_delta" -> {
                        String thinking = delta.get("thinking").asText();
                        callback.onThinkingToken(thinking);
                        collector.appendThinking(thinking);
                    }
                    case "input_json_delta" -> {
                        String partial = delta.get("partial_json").asText();
                        callback.onToolUseInput(
                                collector.currentToolUseId(index),
                                collector.currentToolName(index),
                                partial);
                        collector.appendToolInput(index, partial);
                    }
                }
            }

            case "content_block_stop" -> {
                int index = data.get("index").asInt();
                callback.onContentBlockStop(index);
                collector.finalizeContentBlock(index);
            }

            case "message_delta" -> {
                JsonNode delta = data.get("delta");
                if (delta != null && delta.has("stop_reason")) {
                    collector.setStopReason(delta.get("stop_reason").asText());
                }
                JsonNode usage = data.get("usage");
                if (usage != null && usage.has("output_tokens")) {
                    collector.setOutputTokens(usage.get("output_tokens").asInt());
                }
            }

            case "error" -> {
                String errorType = data.at("/error/type").asText("unknown");
                String errorMsg = data.at("/error/message").asText("Unknown error");
                throw new LlmException(ErrorKind.SERVER_ERROR,
                        "Stream error [" + errorType + "]: " + errorMsg);
            }

            case "ping", "message_stop" -> {
                // 心跳和消息结束，不需要处理
                collector.markHeartbeat();
            }
        }
    }

    /**
     * 将 Protocol ChatRequest 转换为 Anthropic 请求 JSON 字符串。
     */
    private String buildRequestBody(ChatRequest request) {
        return buildRequestBody(request, false);
    }

    /**
     * 将 Protocol ChatRequest 转换为 Anthropic 请求 JSON 字符串。
     */
    private String buildRequestBody(ChatRequest request, boolean stream) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.model());
        root.put("max_tokens", request.maxTokens());

        if (stream) {
            root.put("stream", true);
        }

        if (!request.systemPrompt().isEmpty()) {
            if (request.systemCacheControl() instanceof com.sprinkleclaw.protocol.message.CacheControl.Ephemeral) {
                ArrayNode systemArray = root.putArray("system");
                ObjectNode textNode = systemArray.addObject();
                textNode.put("type", "text");
                textNode.put("text", request.systemPrompt());
                addCacheControl(textNode, request.systemCacheControl());
            } else {
                root.put("system", request.systemPrompt());
            }
        }

        ArrayNode messagesArray = root.putArray("messages");
        for (Message msg : request.messages()) {
            convertMessage(msg, messagesArray);
        }

        if (!request.tools().isEmpty()) {
            ArrayNode toolsArray = root.putArray("tools");
            List<ToolDefinition> toolList = request.tools();
            for (int i = 0; i < toolList.size(); i++) {
                ToolDefinition tool = toolList.get(i);
                ObjectNode toolNode = toolsArray.addObject();
                toolNode.put("name", tool.name());
                toolNode.put("description", tool.description());
                toolNode.set("input_schema", objectMapper.valueToTree(tool.inputSchema()));
                if (i == toolList.size() - 1) {
                    addCacheControl(toolNode, request.toolsCacheControl());
                }
            }

            // toolChoice 序列化：AUTO → 省略；REQUIRED → any；NONE → none；Forced → {type: "tool", name}
            serializeToolChoice(request.toolChoice(), root);
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
                    serializeUserContentBlock(block, contentArray);
                }
            }
            case Message.AssistantMessage a -> {
                ObjectNode msgNode = messagesArray.addObject();
                msgNode.put("role", "assistant");
                ArrayNode contentArray = msgNode.putArray("content");
                for (ContentBlock block : a.content()) {
                    switch (block) {
                        case ContentBlock.TextBlock t -> {
                            ObjectNode node = contentArray.addObject()
                                    .put("type", "text")
                                    .put("text", t.text());
                            addCacheControl(node, t.cacheControl());
                        }
                        case ContentBlock.ToolUseBlock t -> {
                            ObjectNode toolNode = contentArray.addObject();
                            toolNode.put("type", "tool_use");
                            toolNode.put("id", t.id());
                            toolNode.put("name", t.name());
                            toolNode.set("input", objectMapper.valueToTree(t.input()));
                            addCacheControl(toolNode, t.cacheControl());
                        }
                        case ContentBlock.ThinkingBlock t -> contentArray.addObject()
                                .put("type", "thinking")
                                .put("thinking", t.thinking());
                        case ContentBlock.ImageBlock i -> serializeImageBlock(i, contentArray);
                        case ContentBlock.DocumentBlock d -> serializeDocumentBlock(d, contentArray);
                        case ContentBlock.AudioBlock ignored -> contentArray.addObject()
                                .put("type", "text")
                                .put("text", "[Unsupported: Audio content cannot be processed by this model]");
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

            JsonNode usageNode = node.get("usage");
            int inputTokens = usageNode != null ? usageNode.path("input_tokens").asInt(0) : 0;
            int outputTokens = usageNode != null ? usageNode.path("output_tokens").asInt(0) : 0;
            int reasoningTokens = 0;
            int cacheCreation = usageNode != null ? usageNode.path("cache_creation_input_tokens").asInt(0) : 0;
            int cacheRead = usageNode != null ? usageNode.path("cache_read_input_tokens").asInt(0) : 0;
            Usage usage = new Usage(inputTokens, outputTokens, reasoningTokens, cacheCreation, cacheRead);

            String modelId = node.has("model") ? node.get("model").asText() : "";

            return new ChatResponse(blocks, stopReason, usage, modelId);

        } catch (Exception e) {
            throw new LlmException(ErrorKind.UNKNOWN, "Failed to parse response: " + e.getMessage(), e);
        }
    }

    /**
     * 序列化 UserMessage 中的 ContentBlock 到 Anthropic JSON。
     */
    private void serializeUserContentBlock(ContentBlock block, ArrayNode contentArray) {
        switch (block) {
            case ContentBlock.TextBlock t -> {
                ObjectNode node = contentArray.addObject()
                        .put("type", "text")
                        .put("text", t.text());
                addCacheControl(node, t.cacheControl());
            }
            case ContentBlock.ImageBlock i -> serializeImageBlock(i, contentArray);
            case ContentBlock.DocumentBlock d -> serializeDocumentBlock(d, contentArray);
            case ContentBlock.AudioBlock ignored -> contentArray.addObject()
                    .put("type", "text")
                    .put("text", "[Unsupported: Audio content cannot be processed by this model]");
            case ContentBlock.ToolUseBlock t -> {
                ObjectNode toolNode = contentArray.addObject();
                toolNode.put("type", "tool_use");
                toolNode.put("id", t.id());
                toolNode.put("name", t.name());
                toolNode.set("input", objectMapper.valueToTree(t.input()));
                addCacheControl(toolNode, t.cacheControl());
            }
            case ContentBlock.ThinkingBlock t -> contentArray.addObject()
                    .put("type", "thinking")
                    .put("thinking", t.thinking());
        }
    }

    private void serializeImageBlock(ContentBlock.ImageBlock block, ArrayNode contentArray) {
        ObjectNode node = contentArray.addObject();
        node.put("type", "image");
        ObjectNode source = node.putObject("source");
        if (block.base64Data() != null) {
            source.put("type", "base64");
            source.put("media_type", block.mediaType());
            source.put("data", block.base64Data());
        } else {
            source.put("type", "url");
            source.put("url", block.url());
        }
        addCacheControl(node, block.cacheControl());
    }

    private void serializeDocumentBlock(ContentBlock.DocumentBlock block, ArrayNode contentArray) {
        ObjectNode node = contentArray.addObject();
        node.put("type", "document");
        ObjectNode source = node.putObject("source");
        if (block.base64Data() != null) {
            source.put("type", "base64");
            source.put("media_type", block.mediaType());
            source.put("data", block.base64Data());
        } else {
            source.put("type", "url");
            source.put("url", block.url());
        }
        addCacheControl(node, block.cacheControl());
    }

    private void addCacheControl(ObjectNode node, com.sprinkleclaw.protocol.message.CacheControl cc) {
        if (cc instanceof com.sprinkleclaw.protocol.message.CacheControl.Ephemeral) {
            node.putObject("cache_control").put("type", "ephemeral");
        }
    }

    /**
     * 将 Protocol ToolChoice 序列化为 Anthropic tool_choice JSON 字段。
     * <p>映射规则：AUTO → 省略；REQUIRED → {type: "any"}；NONE → {type: "none"}；
     * Forced(name) → {type: "tool", name: name}。</p>
     */
    private void serializeToolChoice(ChatRequest.ToolChoice toolChoice, ObjectNode root) {
        switch (toolChoice) {
            case ChatRequest.ToolChoice.Auto ignored -> {
                // AUTO 时省略 tool_choice 字段，Anthropic 默认行为即 auto
            }
            case ChatRequest.ToolChoice.None ignored -> {
                ObjectNode tc = root.putObject("tool_choice");
                tc.put("type", "none");
            }
            case ChatRequest.ToolChoice.Required ignored -> {
                ObjectNode tc = root.putObject("tool_choice");
                tc.put("type", "any");
            }
            case ChatRequest.ToolChoice.Forced f -> {
                ObjectNode tc = root.putObject("tool_choice");
                tc.put("type", "tool");
                tc.put("name", f.toolName());
            }
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
     * 处理流式请求的 HTTP 错误。
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
