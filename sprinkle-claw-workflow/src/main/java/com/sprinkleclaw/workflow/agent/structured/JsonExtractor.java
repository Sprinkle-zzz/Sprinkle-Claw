package com.sprinkleclaw.workflow.agent.structured;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 LLM 输出文本中提取 JSON 字符串。
 * <p>支持 fenced code block（{@code ```json ... ```}）和裸 JSON 两种格式。</p>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class JsonExtractor {

    private static final Pattern FENCED_JSON = Pattern.compile(
            "```(?:json)?\\s*\\n(.+?)\\n\\s*```", Pattern.DOTALL);

    private static final Pattern BARE_JSON = Pattern.compile(
            "(\\{[\\s\\S]*\\})", Pattern.DOTALL);

    /**
     * 从 LLM 输出中提取 JSON 字符串。
     *
     * @return 提取的 JSON 字符串，未找到返回 null
     */
    public static String extract(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) return null;

        // 优先匹配 fenced code block
        Matcher fenced = FENCED_JSON.matcher(llmOutput);
        if (fenced.find()) {
            return fenced.group(1).strip();
        }

        // 尝试裸 JSON
        String trimmed = llmOutput.strip();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed;
        }

        // 尝试在文本中找到 JSON 对象
        Matcher bare = BARE_JSON.matcher(llmOutput);
        if (bare.find()) {
            return bare.group(1).strip();
        }

        return null;
    }
}
