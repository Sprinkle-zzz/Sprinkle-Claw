package icu.sprinkle.loom.protocol.llm;

/**
 * LLM 推理/思考模式配置。
 *
 * <p>支持两种推理控制方式：</p>
 * <ul>
 *   <li>Anthropic：通过 {@code budgetTokens} 控制思考阶段的 token 预算</li>
 *   <li>OpenAI：通过 {@code reasoningEffort} 控制推理努力程度（LOW/MEDIUM/HIGH）</li>
 * </ul>
 *
 * @param enabled         是否启用思考/推理模式
 * @param budgetTokens    思考阶段的最大 token 预算（Anthropic 使用）
 * @param reasoningEffort 推理努力程度（OpenAI 使用，为 null 表示不指定）
 *
 * @author sprinkle
 * @since 2026/3/17
 */
public record ThinkingConfig(boolean enabled, int budgetTokens, ReasoningEffort reasoningEffort) {

    /**
     * 兼容旧的双参数构造（reasoningEffort 默认为 null）。
     */
    public ThinkingConfig(boolean enabled, int budgetTokens) {
        this(enabled, budgetTokens, null);
    }

    /**
     * 创建启用思考模式的配置（Anthropic 风格，指定 token 预算）。
     *
     * @param budget 思考 token 预算
     * @return 已启用的思考配置
     */
    public static ThinkingConfig enabled(int budget) {
        return new ThinkingConfig(true, budget, null);
    }

    /**
     * 创建启用推理模式的配置（OpenAI 风格，指定推理努力程度）。
     *
     * @param effort 推理努力程度
     * @return 已启用的推理配置
     */
    public static ThinkingConfig withEffort(ReasoningEffort effort) {
        return new ThinkingConfig(true, 0, effort);
    }

    /**
     * 禁用思考模式（默认）。
     */
    public static ThinkingConfig disabled() {
        return new ThinkingConfig(false, 0, null);
    }
}
