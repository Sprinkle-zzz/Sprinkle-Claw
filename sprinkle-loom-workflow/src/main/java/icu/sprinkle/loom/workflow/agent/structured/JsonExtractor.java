package icu.sprinkle.loom.workflow.agent.structured;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 LLM 输出文本中提取 JSON 字符串。
 *
 * <p>提取顺序：</p>
 * <ol>
 *   <li>fenced code block（{@code ```json ... ```} 或 {@code ``` ... ```}）</li>
 *   <li>裸 JSON（整段文本本身就是 JSON）</li>
 *   <li>嵌入文本中的 JSON：栈式扫描第一个完整的 {@code {...}} 或 {@code [...]} 对象，
 *       正确处理字符串内大括号、转义字符（如 {@code "use \"quoted\" {var}"}）</li>
 * </ol>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class JsonExtractor {

    private static final Pattern FENCED_JSON = Pattern.compile(
            "```(?:json)?\\s*\\n(.+?)\\n\\s*```", Pattern.DOTALL);

    private JsonExtractor() {
    }

    /**
     * 从 LLM 输出中提取 JSON 字符串。
     *
     * @return 提取的 JSON 字符串，未找到返回 null
     */
    public static String extract(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return null;
        }

        // 1. 优先匹配 fenced code block
        Matcher fenced = FENCED_JSON.matcher(llmOutput);
        if (fenced.find()) {
            return fenced.group(1).strip();
        }

        // 2. 整段就是 JSON
        String trimmed = llmOutput.strip();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return trimmed;
        }

        // 3. 嵌入文本中：栈式扫描第一个完整的 JSON 对象 / 数组
        return scanFirstCompleteJson(llmOutput);
    }

    /**
     * 栈式扫描：找到第一个 {@code {} 或 [} 后，向前匹配到对应的 {@code }} 或 {@code ]}。
     * 正确处理字符串字面量内的括号和反斜杠转义。
     */
    private static String scanFirstCompleteJson(String text) {
        int len = text.length();
        for (int start = 0; start < len; start++) {
            char c = text.charAt(start);
            if (c == '{' || c == '[') {
                int end = matchBalanced(text, start);
                if (end > start) {
                    return text.substring(start, end + 1);
                }
            }
        }
        return null;
    }

    /**
     * 从指定位置开始向前扫描配对的闭括号。
     *
     * @return 闭括号位置；未找到（不平衡）返回 -1
     */
    private static int matchBalanced(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        int len = text.length();

        for (int i = start; i < len; i++) {
            char c = text.charAt(i);

            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }

            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
                if (depth < 0) {
                    return -1; // 不平衡
                }
            }
        }
        return -1;
    }
}
