package com.sprinkleclaw.llm.anthropic;

import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.llm.StopReason;
import com.sprinkleclaw.protocol.llm.Usage;
import com.sprinkleclaw.protocol.message.ContentBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic SSE 流式响应累积器。
 * <p>将流式增量事件逐步累积为最终的 {@link ChatResponse}。</p>
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public class AnthropicResponseCollector {

    private String messageId;
    private int inputTokens;
    private int outputTokens;
    private String stopReasonStr;
    private String modelId;

    // 内容块累积
    private final List<ContentBlock> contentBlocks = new ArrayList<>();
    private final Map<Integer, BlockAccumulator> activeBlocks = new HashMap<>();

    /**
     * 记录 message_start 的 input_tokens。
     */
    public void setInputTokens(int tokens) {
        this.inputTokens = tokens;
    }

    /**
     * 记录 message ID。
     */
    public void setMessageId(String id) {
        this.messageId = id;
    }

    /**
     * 记录模型 ID。
     */
    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    /**
     * 记录 message_delta 的 output_tokens。
     */
    public void setOutputTokens(int tokens) {
        this.outputTokens = tokens;
    }

    /**
     * 记录 stop_reason。
     */
    public void setStopReason(String reason) {
        this.stopReasonStr = reason;
    }

    /**
     * 开始一个 tool_use 内容块。
     */
    public void startToolUse(int index, String id, String name) {
        activeBlocks.put(index, new BlockAccumulator("tool_use", id, name));
    }

    /**
     * 开始一个 text/thinking 内容块。
     */
    public void startContentBlock(int index, String type) {
        activeBlocks.put(index, new BlockAccumulator(type, null, null));
    }

    /**
     * 追加文本 delta。
     */
    public void appendText(String text) {
        // 找到最后一个 text 类型的活跃块
        for (var entry : activeBlocks.entrySet()) {
            if ("text".equals(entry.getValue().type)) {
                entry.getValue().content.append(text);
                return;
            }
        }
    }

    /**
     * 追加 thinking delta。
     */
    public void appendThinking(String thinking) {
        for (var entry : activeBlocks.entrySet()) {
            if ("thinking".equals(entry.getValue().type)) {
                entry.getValue().content.append(thinking);
                return;
            }
        }
    }

    /**
     * 追加 tool_use input delta。
     */
    public void appendToolInput(int index, String partial) {
        var block = activeBlocks.get(index);
        if (block != null) {
            block.content.append(partial);
        }
    }

    /**
     * 获取指定索引的 tool_use ID。
     */
    public String currentToolUseId(int index) {
        var block = activeBlocks.get(index);
        return block != null ? block.toolUseId : null;
    }

    /**
     * 获取指定索引的 tool 名称。
     */
    public String currentToolName(int index) {
        var block = activeBlocks.get(index);
        return block != null ? block.toolName : null;
    }

    /**
     * 完成一个内容块。
     */
    public void finalizeContentBlock(int index) {
        var block = activeBlocks.remove(index);
        if (block == null) return;

        switch (block.type) {
            case "text" -> contentBlocks.add(new ContentBlock.TextBlock(block.content.toString()));
            case "thinking" -> contentBlocks.add(new ContentBlock.ThinkingBlock(block.content.toString()));
            case "tool_use" -> contentBlocks.add(new ContentBlock.ToolUseBlock(
                    block.toolUseId, block.toolName, parseToolInput(block.content.toString())));
        }
    }

    /**
     * 心跳事件（ping）。
     */
    public void markHeartbeat() {
        // StreamIdleWatchdog 由外部处理
    }

    /**
     * 构建最终响应。
     */
    public ChatResponse build() {
        // 清理未完成的块
        for (var entry : new ArrayList<>(activeBlocks.entrySet())) {
            finalizeContentBlock(entry.getKey());
        }

        StopReason stopReason = mapStopReason(stopReasonStr);
        Usage usage = new Usage(inputTokens, outputTokens);
        return new ChatResponse(List.copyOf(contentBlocks), stopReason, usage,
                modelId != null ? modelId : "");
    }

    private StopReason mapStopReason(String reason) {
        if (reason == null) return StopReason.END_TURN;
        return switch (reason) {
            case "end_turn" -> StopReason.END_TURN;
            case "tool_use" -> StopReason.TOOL_USE;
            case "max_tokens" -> StopReason.MAX_TOKENS;
            case "stop_sequence" -> StopReason.STOP_SEQUENCE;
            default -> StopReason.END_TURN;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseToolInput(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("_raw", json);
        }
    }

    private static class BlockAccumulator {
        final String type;
        final String toolUseId;
        final String toolName;
        final StringBuilder content = new StringBuilder();

        BlockAccumulator(String type, String toolUseId, String toolName) {
            this.type = type;
            this.toolUseId = toolUseId;
            this.toolName = toolName;
        }
    }
}
