package com.sprinkleclaw.llm;

/**
 * LLM 提供者的能力声明。
 *
 * <p>用于在运行时查询 Provider 支持的特性，以便框架做出适配决策
 * （如是否启用推理模式、结构化输出、上下文窗口大小等）。</p>
 *
 * @author sprinkle
 * @since 2026/4/4
 */
public record LlmCapabilities(
        boolean supportsReasoning,
        boolean supportsStructuredOutput,
        int contextWindowTokens,
        int maxOutputTokens
) {

    /**
     * 默认能力声明（保守值：不支持推理/结构化，128K 窗口，4K 输出）。
     */
    public static final LlmCapabilities DEFAULT = new LlmCapabilities(
            false, false, 128_000, 4096
    );

    /**
     * 创建构建器实例。
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean supportsReasoning = false;
        private boolean supportsStructuredOutput = false;
        private int contextWindowTokens = 128_000;
        private int maxOutputTokens = 4096;

        private Builder() {
        }

        public Builder supportsReasoning(boolean supportsReasoning) {
            this.supportsReasoning = supportsReasoning;
            return this;
        }

        public Builder supportsStructuredOutput(boolean supportsStructuredOutput) {
            this.supportsStructuredOutput = supportsStructuredOutput;
            return this;
        }

        public Builder contextWindowTokens(int contextWindowTokens) {
            this.contextWindowTokens = contextWindowTokens;
            return this;
        }

        public Builder maxOutputTokens(int maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public LlmCapabilities build() {
            return new LlmCapabilities(supportsReasoning, supportsStructuredOutput,
                    contextWindowTokens, maxOutputTokens);
        }
    }
}
