package com.sprinkleclaw.protocol.llm;

/**
 * Token 用量统计。
 *
 * @param inputTokens     输入 token 数
 * @param outputTokens    输出 token 数
 * @param reasoningTokens 推理 token 数（用于推理模型，0 表示无推理或不支持）
 *
 * @author sprinkle
 * @since 2026/3/17
 */
public record Usage(int inputTokens, int outputTokens, int reasoningTokens) {

    /**
     * 兼容旧的双参数构造（reasoningTokens 默认为 0）。
     */
    public Usage(int inputTokens, int outputTokens) {
        this(inputTokens, outputTokens, 0);
    }

    /**
     * 计算总 token 用量（包含推理 token）。
     */
    public int totalTokens() {
        return inputTokens + outputTokens + reasoningTokens;
    }
}
