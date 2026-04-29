package icu.sprinkle.loom.core.observability;

import java.time.Duration;

/**
 * Agent 指标收集 SPI，用于记录 LLM 调用次数、延迟、工具调用次数、token 用量等。
 * <p>默认使用 {@link NoopAgentMetrics} 空实现，用户可替换为 Micrometer 等具体实现。</p>
 *
 * @author sprinkle
 * @since 2026/3/19
 */
public interface AgentMetrics {

    /**
     * 记录一次 LLM 调用。
     */
    void recordLlmCall();

    /**
     * 记录一次 LLM 调用错误。
     */
    void recordLlmError();

    /**
     * 记录 LLM 调用延迟。
     *
     * @param duration 调用耗时
     */
    void recordLlmLatency(Duration duration);

    /**
     * 记录工具调用次数。
     *
     * @param count 本轮工具调用数量
     */
    void recordToolCalls(int count);

    /**
     * 记录 token 用量。
     *
     * @param inputTokens  输入 token 数
     * @param outputTokens 输出 token 数
     */
    void recordTokenUsage(int inputTokens, int outputTokens);

    /**
     * 记录缓存 token 数据。
     *
     * @param cacheCreation 缓存创建写入 token 数
     * @param cacheRead     缓存读取命中 token 数
     */
    default void recordCacheTokens(int cacheCreation, int cacheRead) {}
}
