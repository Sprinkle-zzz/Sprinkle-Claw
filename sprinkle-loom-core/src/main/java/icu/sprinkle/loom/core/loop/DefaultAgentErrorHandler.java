package icu.sprinkle.loom.core.loop;

import icu.sprinkle.loom.core.context.AgentContext;
import icu.sprinkle.loom.llm.LlmException;

/**
 * 默认 Agent 错误处理器。
 * <ul>
 *   <li>可重试的 LLM 异常（限流、服务端错误）→ 重试</li>
 *   <li>循环耗尽异常 → 终止</li>
 *   <li>其他异常 → 注入错误提示后重试</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/3/19
 */
public final class DefaultAgentErrorHandler implements AgentErrorHandler {

    @Override
    public ErrorDecision handle(AgentContext context, Throwable error, int iteration) {
        if (error instanceof LlmException llmEx && llmEx.isRetryable()) {
            return new ErrorDecision.Retry("Previous request failed (" + llmEx.kind() + "), retrying...");
        }
        if (error instanceof LoopGuard.LoopExhaustedException) {
            return new ErrorDecision.Abort(error.getMessage());
        }
        return new ErrorDecision.Retry("An error occurred: " + error.getMessage() + ". Please try a different approach.");
    }
}
