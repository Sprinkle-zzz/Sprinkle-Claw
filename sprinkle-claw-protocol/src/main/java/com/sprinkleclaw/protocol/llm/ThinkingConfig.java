package com.sprinkleclaw.protocol.llm;

/**
 * LLM 思考模式配置（仅 Anthropic Claude 支持）。
 * <p>启用后 LLM 会先进行推理再输出，需配合 {@code ThinkingBlock} 处理。</p>
 *
 * @param enabled      是否启用思考模式
 * @param budgetTokens 思考阶段的最大 token 预算
 *
 * @author sprinkle
 * @since 2026/3/17
 */
public record ThinkingConfig(boolean enabled, int budgetTokens) {

    /**
     * 创建启用思考模式的配置。
     *
     * @param budget 思考 token 预算
     * @return 已启用的思考配置
     */
    public static ThinkingConfig enabled(int budget) {
        return new ThinkingConfig(true, budget);
    }

    /**
     * 禁用思考模式（默认）。
     */
    public static ThinkingConfig disabled() {
        return new ThinkingConfig(false, 0);
    }
}
