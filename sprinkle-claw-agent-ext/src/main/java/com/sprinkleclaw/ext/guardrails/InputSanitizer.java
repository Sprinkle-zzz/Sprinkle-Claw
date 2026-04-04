package com.sprinkleclaw.ext.guardrails;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 输入安全检查器：长度限制 + 常见注入模式检测。
 *
 * <p>检测常见的 prompt injection 模式（如 "ignore all previous instructions"），
 * 以及超长输入可能导致的 token 浪费。返回 {@link SanitizeResult} 包含警告列表，
 * 调用方可选择是否拒绝。</p>
 *
 * @author sprinkle
 * @since 2026/4/4
 */
public final class InputSanitizer {

    /**
     * 默认输入最大长度（字符数）
     */
    private static final int DEFAULT_MAX_INPUT_LENGTH = 100_000;

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+instructions"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(?:a|an)\\s+"),
            Pattern.compile("(?i)system\\s*:\\s*"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?(above|prior|previous)"),
            Pattern.compile("(?i)forget\\s+(everything|all)\\s+(above|you)")
    );

    private final int maxInputLength;

    /**
     * 创建输入检查器（默认最大长度 100K 字符）。
     */
    public InputSanitizer() {
        this(DEFAULT_MAX_INPUT_LENGTH);
    }

    /**
     * 创建输入检查器。
     *
     * @param maxInputLength 最大输入长度（字符数）
     */
    public InputSanitizer(int maxInputLength) {
        this.maxInputLength = Math.max(1000, maxInputLength);
    }

    /**
     * 检查输入文本。
     *
     * @param input 用户输入
     * @return 检查结果
     */
    public SanitizeResult sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return SanitizeResult.ok();
        }

        var warnings = new java.util.ArrayList<String>();

        // 长度检查
        if (input.length() > maxInputLength) {
            warnings.add("Input exceeds maximum length (" + input.length()
                    + " > " + maxInputLength + " chars)");
        }

        // 注入模式检测
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                warnings.add("Potential prompt injection detected: " + pattern.pattern());
            }
        }

        return warnings.isEmpty() ? SanitizeResult.ok() : new SanitizeResult(false, warnings);
    }

    /**
     * 检查结果。
     *
     * @param passed   是否通过检查
     * @param warnings 警告信息列表
     */
    public record SanitizeResult(boolean passed, List<String> warnings) {
        static SanitizeResult ok() {
            return new SanitizeResult(true, List.of());
        }
    }
}
