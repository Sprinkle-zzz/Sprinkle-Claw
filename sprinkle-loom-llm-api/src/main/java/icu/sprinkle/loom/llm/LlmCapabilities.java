package icu.sprinkle.loom.llm;

/**
 * LLM 提供者的能力声明。
 *
 * <p>用于在运行时查询 Provider 支持的特性，以便框架做出适配决策
 * （如是否启用推理模式、结构化输出、上下文窗口大小等）。</p>
 *
 * @param supportsReasoning        是否支持推理/思考模式
 * @param supportsStructuredOutput 是否支持结构化输出（JSON Schema 模式）
 * @param contextWindowTokens      上下文窗口大小（token 数）
 * @param maxOutputTokens          最大输出 token 数
 * @param supportsToolChoice       是否支持 ToolChoice.REQUIRED / Forced 策略
 * @param supportsStreaming        是否支持真正的流式输出（非 fallback）
 * @param supportsToolUse          是否支持原生工具调用
 * @param supportsPromptCache      是否支持 Prompt Caching
 * @param supportsVision           是否支持图片输入
 * @param supportsPdfInput         是否支持原生 PDF 文档输入
 * @param supportsAudioInput       是否支持音频输入
 *
 * @author sprinkle
 * @since 2026/4/4
 */
public record LlmCapabilities(
        boolean supportsReasoning,
        boolean supportsStructuredOutput,
        int contextWindowTokens,
        int maxOutputTokens,
        boolean supportsToolChoice,
        boolean supportsStreaming,
        boolean supportsToolUse,
        boolean supportsPromptCache,
        boolean supportsVision,
        boolean supportsPdfInput,
        boolean supportsAudioInput
) {

    /**
     * 兼容旧代码的 4 参数构造器：新字段默认 false。
     */
    public LlmCapabilities(boolean supportsReasoning, boolean supportsStructuredOutput,
                           int contextWindowTokens, int maxOutputTokens) {
        this(supportsReasoning, supportsStructuredOutput,
                contextWindowTokens, maxOutputTokens,
                false, false, false, false, false, false, false);
    }

    /**
     * 兼容旧代码的 7 参数构造器：MVP7 新增字段默认 false。
     */
    public LlmCapabilities(boolean supportsReasoning, boolean supportsStructuredOutput,
                           int contextWindowTokens, int maxOutputTokens,
                           boolean supportsToolChoice, boolean supportsStreaming,
                           boolean supportsToolUse) {
        this(supportsReasoning, supportsStructuredOutput,
                contextWindowTokens, maxOutputTokens,
                supportsToolChoice, supportsStreaming, supportsToolUse,
                false, false, false, false);
    }

    /**
     * 默认能力声明（保守值：不支持推理/结构化/工具选择/流式/工具调用，128K 窗口，4K 输出）。
     */
    public static final LlmCapabilities DEFAULT = new LlmCapabilities(
            false, false, 128_000, 4096, false, false, false,
            false, false, false, false
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
        private boolean supportsToolChoice = false;
        private boolean supportsStreaming = false;
        private boolean supportsToolUse = false;
        private boolean supportsPromptCache = false;
        private boolean supportsVision = false;
        private boolean supportsPdfInput = false;
        private boolean supportsAudioInput = false;

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

        public Builder supportsToolChoice(boolean supportsToolChoice) {
            this.supportsToolChoice = supportsToolChoice;
            return this;
        }

        public Builder supportsStreaming(boolean supportsStreaming) {
            this.supportsStreaming = supportsStreaming;
            return this;
        }

        public Builder supportsToolUse(boolean supportsToolUse) {
            this.supportsToolUse = supportsToolUse;
            return this;
        }

        public Builder supportsPromptCache(boolean supportsPromptCache) {
            this.supportsPromptCache = supportsPromptCache;
            return this;
        }

        public Builder supportsVision(boolean supportsVision) {
            this.supportsVision = supportsVision;
            return this;
        }

        public Builder supportsPdfInput(boolean supportsPdfInput) {
            this.supportsPdfInput = supportsPdfInput;
            return this;
        }

        public Builder supportsAudioInput(boolean supportsAudioInput) {
            this.supportsAudioInput = supportsAudioInput;
            return this;
        }

        public LlmCapabilities build() {
            return new LlmCapabilities(supportsReasoning, supportsStructuredOutput,
                    contextWindowTokens, maxOutputTokens,
                    supportsToolChoice, supportsStreaming, supportsToolUse,
                    supportsPromptCache, supportsVision, supportsPdfInput, supportsAudioInput);
        }
    }
}
