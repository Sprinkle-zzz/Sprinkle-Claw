package icu.sprinkle.loom.spring.autoconfigure;

import icu.sprinkle.loom.bootstrap.Loom;
import icu.sprinkle.loom.core.loop.event.AgentEvent;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * 面向 Spring WebFlux 应用的 Reactor 适配器。
 *
 * <p>starter 只提供 Agent 层 Flux 适配；低层 LLM 事件流可由应用使用
 * {@link JdkFlowAdapter} 直接适配 JDK {@link java.util.concurrent.Flow.Publisher}。</p>
 *
 * @author sprinkle
 * @since 2026/5/10
 */
public final class LoomFluxAdapters {

    private LoomFluxAdapters() {
    }

    /**
     * 将一次性 Agent 执行适配为 Flux。
     *
     * @param loom        Loom 实例
     * @param userMessage 用户消息
     * @return Agent 事件流
     */
    public static Flux<AgentEvent> runFlux(Loom loom, String userMessage) {
        Objects.requireNonNull(loom, "loom");
        return JdkFlowAdapter.flowPublisherToFlux(loom.runStreaming(userMessage));
    }

    /**
     * 将多轮会话执行适配为 Flux。
     *
     * @param loom        Loom 实例
     * @param userMessage 用户消息
     * @return Agent 事件流
     */
    public static Flux<AgentEvent> chatFlux(Loom loom, String userMessage) {
        Objects.requireNonNull(loom, "loom");
        return JdkFlowAdapter.flowPublisherToFlux(loom.chatStreaming(userMessage));
    }

    /**
     * 将恢复会话执行适配为 Flux。
     *
     * @param loom        Loom 实例
     * @param sessionId   会话 ID
     * @param userMessage 用户消息
     * @return Agent 事件流
     */
    public static Flux<AgentEvent> resumeFlux(Loom loom, String sessionId, String userMessage) {
        Objects.requireNonNull(loom, "loom");
        return JdkFlowAdapter.flowPublisherToFlux(loom.resumeStreaming(sessionId, userMessage));
    }
}
