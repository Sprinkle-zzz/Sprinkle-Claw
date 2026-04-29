package icu.sprinkle.loom.core.loop;

import icu.sprinkle.loom.core.context.AgentContext;

/**
 * Agent 循环错误处理 SPI，决定异常发生时的恢复策略。
 *
 * @author sprinkle
 * @since 2026/3/19
 */
public interface AgentErrorHandler {

    /**
     * 处理循环中的异常并返回决策。
     *
     * @param context   当前上下文
     * @param error     发生的异常
     * @param iteration 当前迭代轮次
     * @return 错误决策
     */
    ErrorDecision handle(AgentContext context, Throwable error, int iteration);

    /**
     * 错误决策（密封接口）。
     */
    sealed interface ErrorDecision {
        /**
         * 重试：向对话中注入一条提示消息后继续循环。
         *
         * @param injectedMessage 注入的提示消息
         */
        record Retry(String injectedMessage) implements ErrorDecision {
        }

        /**
         * 终止：立即结束 Agent 循环。
         *
         * @param reason 终止原因
         */
        record Abort(String reason) implements ErrorDecision {
        }

        /**
         * 忽略：跳过此错误继续循环。
         */
        record Ignore() implements ErrorDecision {
        }
    }
}
