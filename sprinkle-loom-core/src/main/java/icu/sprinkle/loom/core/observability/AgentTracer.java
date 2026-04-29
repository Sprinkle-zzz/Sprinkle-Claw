package icu.sprinkle.loom.core.observability;

import icu.sprinkle.loom.core.context.AgentContext;
import icu.sprinkle.loom.protocol.llm.ChatResponse;

/**
 * Agent 执行追踪 SPI，用于记录循环的开始、LLM 响应和结束事件。
 * <p>默认使用 {@link NoopAgentTracer} 空实现，可替换为 OpenTelemetry 等具体实现。</p>
 *
 * @author sprinkle
 * @since 2026/3/19
 */
public interface AgentTracer {

    /**
     * 循环开始时触发。
     *
     * @param context 当前上下文
     */
    void onLoopStart(AgentContext context);

    /**
     * 收到 LLM 响应时触发。
     *
     * @param context   当前上下文
     * @param response  LLM 响应
     * @param iteration 当前迭代轮次
     */
    void onLlmResponse(AgentContext context, ChatResponse response, int iteration);

    /**
     * 循环结束时触发。
     *
     * @param context         当前上下文
     * @param totalIterations 总迭代次数
     */
    void onLoopEnd(AgentContext context, int totalIterations);
}
