package icu.sprinkle.loom.protocol.llm;

/**
 * Token 用量统计。
 *
 * @param inputTokens              输入 token 数
 * @param outputTokens             输出 token 数
 * @param reasoningTokens          推理 token 数（用于推理模型，0 表示无推理或不支持）
 * @param cacheCreationInputTokens 缓存创建写入 token 数（Anthropic 专用，0 表示无缓存写入）
 * @param cacheReadInputTokens     缓存读取命中 token 数（Anthropic / OpenAI，0 表示未命中）
 *
 * @author sprinkle
 * @since 2026/3/17
 */
public record Usage(int inputTokens, int outputTokens, int reasoningTokens,
                    int cacheCreationInputTokens, int cacheReadInputTokens) {

    /**
     * 兼容旧的双参数构造（reasoningTokens 和缓存字段默认为 0）。
     */
    public Usage(int inputTokens, int outputTokens) {
        this(inputTokens, outputTokens, 0, 0, 0);
    }

    /**
     * 兼容旧的三参数构造（缓存字段默认为 0）。
     */
    public Usage(int inputTokens, int outputTokens, int reasoningTokens) {
        this(inputTokens, outputTokens, reasoningTokens, 0, 0);
    }

    /**
     * 计算总 token 用量（包含推理 token）。
     */
    public int totalTokens() {
        return inputTokens + outputTokens + reasoningTokens;
    }

    /**
     * 缓存命中率（万分比），0~10000。
     * <p>例如返回 8500 表示 85.00% 命中率。</p>
     */
    public int cacheHitRateBp() {
        int base = inputTokens + cacheReadInputTokens;
        return base == 0 ? 0 : (int) ((long) cacheReadInputTokens * 10_000 / base);
    }
}
