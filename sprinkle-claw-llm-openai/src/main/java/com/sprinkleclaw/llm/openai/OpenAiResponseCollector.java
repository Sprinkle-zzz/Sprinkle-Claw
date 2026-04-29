package com.sprinkleclaw.llm.openai;

import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.llm.StopReason;
import com.sprinkleclaw.protocol.llm.Usage;
import com.sprinkleclaw.protocol.message.ContentBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI SSE 流式响应累积器。
 * <p>将 {@code choices[0].delta} 增量事件逐步累积为最终的 {@link ChatResponse}。</p>
 *
 * @author sprinkle
 * @since 0.5.0 (MVP4)
 */
public class OpenAiResponseCollector {

    private final StringBuilder textContent = new StringBuilder();
    private final StringBuilder thinkingContent = new StringBuilder();
    private String finishReason;
    private String modelId;
    private int inputTokens;
    private int outputTokens;
    private int reasoningTokens;
    private int cachedTokens;

    // tool_calls 按 index 累积（OpenAI 支持并行工具调用）
    private final Map<Integer, ToolCallAccumulator> toolCalls = new HashMap<>();

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public void appendContent(String content) {
        if (content != null) {
            textContent.append(content);
        }
    }

    /**
     * 追加推理内容增量（deepseek-v4-pro 的 {@code reasoning_content} 字段）。
     */
    public void appendThinking(String content) {
        if (content != null) {
            thinkingContent.append(content);
        }
    }

    public void setFinishReason(String reason) {
        this.finishReason = reason;
    }

    /**
     * 开始或追加工具调用 delta。
     */
    public void appendToolCall(int index, String id, String name, String argumentsDelta) {
        var acc = toolCalls.computeIfAbsent(index, i -> new ToolCallAccumulator());
        if (id != null) {
            acc.id = id;
        }
        if (name != null) {
            acc.name = name;
        }
        if (argumentsDelta != null) {
            acc.arguments.append(argumentsDelta);
        }
    }

    /**
     * 获取指定 index 的工具调用 ID。
     */
    public String getToolCallId(int index) {
        var acc = toolCalls.get(index);
        return acc != null ? acc.id : null;
    }

    /**
     * 获取指定 index 的工具名称。
     */
    public String getToolCallName(int index) {
        var acc = toolCalls.get(index);
        return acc != null ? acc.name : null;
    }

    public void setUsage(int input, int output, int reasoning) {
        this.inputTokens = input;
        this.outputTokens = output;
        this.reasoningTokens = reasoning;
    }

    /**
     * 设置缓存命中 token 数（OpenAI {@code prompt_tokens_details.cached_tokens} 或
     * DeepSeek {@code prompt_cache_hit_tokens}，谁有取谁）。
     */
    public void setCachedTokens(int cachedTokens) {
        this.cachedTokens = cachedTokens;
    }

    /**
     * 构建最终响应。
     */
    public ChatResponse build() {
        List<ContentBlock> blocks = new ArrayList<>();

        // 推理内容在前（与 Anthropic Thinking 顺序一致）
        String thinking = thinkingContent.toString();
        if (!thinking.isEmpty()) {
            blocks.add(new ContentBlock.ThinkingBlock(thinking));
        }

        String text = textContent.toString();
        if (!text.isEmpty()) {
            blocks.add(new ContentBlock.TextBlock(text));
        }

        // 按 index 顺序添加工具调用
        toolCalls.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    var acc = entry.getValue();
                    Map<String, Object> input = parseArguments(acc.arguments.toString());
                    blocks.add(new ContentBlock.ToolUseBlock(
                            acc.id != null ? acc.id : "", acc.name != null ? acc.name : "", input));
                });

        StopReason stopReason = mapFinishReason(finishReason);
        Usage usage = new Usage(inputTokens, outputTokens, reasoningTokens, 0, cachedTokens);
        return new ChatResponse(blocks, stopReason, usage, modelId != null ? modelId : "");
    }

    private StopReason mapFinishReason(String reason) {
        if (reason == null) {
            return StopReason.END_TURN;
        }
        return switch (reason) {
            case "stop" -> StopReason.END_TURN;
            case "tool_calls" -> StopReason.TOOL_USE;
            case "length" -> StopReason.MAX_TOKENS;
            case "content_filter" -> StopReason.CONTENT_FILTER;
            default -> StopReason.END_TURN;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("_raw", json);
        }
    }

    private static class ToolCallAccumulator {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }
}
