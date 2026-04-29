package icu.sprinkle.loom.llm.openai;

/**
 * OpenAI SSE 行解析器。
 * <p>与 Anthropic 版本类似，但额外处理 OpenAI 的 {@code [DONE]} 哨兵事件。</p>
 *
 * @author sprinkle
 * @since 0.5.0 (MVP4)
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
        if (line == null || line.isEmpty()) {
            if (hasData) {
                String data = currentData.toString();
                String event = currentEvent != null ? currentEvent : "message";
                reset();

                // OpenAI 以 [DONE] 标记流结束
                if ("[DONE]".equals(data.trim())) {
                    return new SseEvent("done", data);
                }
                return new SseEvent(event, data);
            }
            return null;
        }

        if (line.startsWith(":")) {
            return null;
        }

        int colonIndex = line.indexOf(':');
        if (colonIndex < 0) {
            return null;
        }

        String field = line.substring(0, colonIndex);
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
        }

        return null;
    }

    public void reset() {
        currentEvent = null;
        currentData.setLength(0);
        hasData = false;
    }

    /**
     * SSE 事件。
     */
    public record SseEvent(String event, String data) {
        public boolean isDone() {
            return "done".equals(event);
        }
    }
}
