package com.sprinkleclaw.protocol.llm;

/**
 * Token 用量统计。
 *
 * @param inputTokens  输入 token 数
 * @param outputTokens 输出 token 数
 *
 * @author sprinkle
 * @since 2026/3/17
 */
public record Usage(int inputTokens, int outputTokens) {
    /**
     * 计算总 token 用量。
     */
    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}
