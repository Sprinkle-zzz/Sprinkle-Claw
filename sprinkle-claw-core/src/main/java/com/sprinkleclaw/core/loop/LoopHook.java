package com.sprinkleclaw.core.loop;

import com.sprinkleclaw.core.context.AgentContext;
import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.message.ContentBlock.ToolUseBlock;

/**
 * Agent 循环生命周期钩子接口。
 * <p>所有方法提供默认空实现，使用者按需覆盖。</p>
 *
 * <h3>调用时序</h3>
 * <pre>
 * preLlmCall → LLM 调用 → postLlmCall → beforeToolExecution（每个工具）
 *   → 工具执行 → postToolExecution → ... → onLoopEnd
 * </pre>
 *
 * @author sprinkle
 * @since 2026/3/19
 */
public interface LoopHook {

    /**
     * 在发送消息给 LLM 之前调用。
     *
     * @param context   当前上下文
     * @param iteration 当前迭代轮次
     */
    default void preLlmCall(AgentContext context, int iteration) {
    }

    /**
     * 在收到 LLM 响应后、工具执行前调用。
     *
     * @param context   当前上下文
     * @param response  LLM 响应
     * @param iteration 当前迭代轮次
     */
    default void postLlmCall(AgentContext context, ChatResponse response, int iteration) {
    }

    /**
     * 在单个工具调用执行前调用，返回拦截决策以控制该工具是否执行。
     * <p>多个 Hook 按注册顺序依次调用，若某个 Hook 返回 {@link ToolInterception.Skip}
     * 则后续 Hook 不再调用。</p>
     *
     * @param context  当前上下文
     * @param toolCall 即将执行的工具调用
     * @return 拦截决策（默认 {@link ToolInterception#CONTINUE}）
     */
    default ToolInterception beforeToolExecution(AgentContext context, ToolUseBlock toolCall) {
        return ToolInterception.CONTINUE;
    }

    /**
     * 在本轮所有工具执行完成后调用。
     *
     * @param context   当前上下文
     * @param iteration 当前迭代轮次
     */
    default void postToolExecution(AgentContext context, int iteration) {
    }

    /**
     * 在循环结束时调用（正常结束或异常终止）。
     *
     * @param context         当前上下文
     * @param totalIterations 总迭代次数
     */
    default void onLoopEnd(AgentContext context, int totalIterations) {
    }
}
