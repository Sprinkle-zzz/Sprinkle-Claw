package icu.sprinkle.loom.core.observability;

import icu.sprinkle.loom.core.context.AgentContext;
import icu.sprinkle.loom.protocol.llm.ChatResponse;

/**
 * 空操作追踪器，所有方法均为空实现。
 * <p>作为默认实现，避免用户未配置时产生 NPE。</p>
 *
 * @author sprinkle
 * @since 2026/3/19
 */
public final class NoopAgentTracer implements AgentTracer {

    /**
     * 单例实例。
     */
    public static final NoopAgentTracer INSTANCE = new NoopAgentTracer();

    private NoopAgentTracer() {
    }

    @Override
    public void onLoopStart(AgentContext context) {
    }

    @Override
    public void onLlmResponse(AgentContext context, ChatResponse response, int iteration) {
    }

    @Override
    public void onLoopEnd(AgentContext context, int totalIterations) {
    }
}
