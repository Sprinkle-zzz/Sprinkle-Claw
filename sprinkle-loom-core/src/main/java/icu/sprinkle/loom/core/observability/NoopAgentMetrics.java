package icu.sprinkle.loom.core.observability;

import java.time.Duration;

/**
 * 空操作指标收集器，所有方法均为空实现。
 * <p>作为默认实现，避免用户未配置时产生 NPE。</p>
 *
 * @author sprinkle
 * @since 2026/3/19
 */
public final class NoopAgentMetrics implements AgentMetrics {

    /**
     * 单例实例。
     */
    public static final NoopAgentMetrics INSTANCE = new NoopAgentMetrics();

    private NoopAgentMetrics() {
    }

    @Override
    public void recordLlmCall() {
    }

    @Override
    public void recordLlmError() {
    }

    @Override
    public void recordLlmLatency(Duration duration) {
    }

    @Override
    public void recordToolCalls(int count) {
    }

    @Override
    public void recordTokenUsage(int inputTokens, int outputTokens) {
    }
}
