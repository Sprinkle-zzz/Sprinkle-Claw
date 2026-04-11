package com.sprinkleclaw.llm.anthropic;

/**
 * SSE（Server-Sent Events）行解析器。
 * <p>逐行喂入 SSE 文本，当检测到空行（事件边界）时返回完整的 {@link SseEvent}。
 * 支持多行 data 拼接（多个 "data:" 行拼接为一个 data 字段）。</p>
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public class SseLineParser {

    private String currentEvent = null;
    private final StringBuilder currentData = new StringBuilder();
    private boolean hasData = false;

    /**
     * 喂入一行 SSE 文本。
     *
     * @param line 一行文本（不含换行符）
     * @return 当检测到事件边界时返回完整事件，否则返回 null
     */
    public SseEvent feed(String line) {
        // 空行 = 事件边界
        if (line == null || line.isEmpty()) {
            if (hasData) {
                SseEvent event = new SseEvent(
                        currentEvent != null ? currentEvent : "message",
                        currentData.toString()
                );
                reset();
                return event;
            }
            return null;
        }

        // 注释行（以 : 开头）
        if (line.startsWith(":")) {
            return null;
        }

        // 解析字段
        int colonIndex = line.indexOf(':');
        if (colonIndex < 0) {
            // 仅字段名，无值
            return null;
        }

        String field = line.substring(0, colonIndex);
        // 值从冒号后开始，如果紧跟空格则跳过一个空格
        String value = colonIndex + 1 < line.length()
                ? (line.charAt(colonIndex + 1) == ' '
                ? line.substring(colonIndex + 2)
                : line.substring(colonIndex + 1))
                : "";

        switch (field) {
            case "event" -> currentEvent = value;
            case "data" -> {
                if (hasData) {
                    currentData.append('\n');
                }
                currentData.append(value);
                hasData = true;
            }
            // id, retry 等字段在此场景不需要
        }

        return null;
    }

    /**
     * 重置解析器状态。
     */
    public void reset() {
        currentEvent = null;
        currentData.setLength(0);
        hasData = false;
    }

    /**
     * SSE 事件。
     *
     * @param event 事件类型（如 "message_start", "content_block_delta"）
     * @param data  事件数据（JSON 字符串）
     */
    public record SseEvent(String event, String data) {
    }
}
