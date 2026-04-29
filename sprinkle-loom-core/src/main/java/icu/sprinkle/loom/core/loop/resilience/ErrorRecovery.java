package icu.sprinkle.loom.core.loop.resilience;

import java.time.Duration;

/**
 * 错误恢复策略 sealed interface。
 * <p>每种策略对应一种恢复行为，由 {@link ErrorRecoveryMatrix} 产生，AgentLoop 执行。</p>
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public sealed interface ErrorRecovery {

    /**
     * 延迟后重试（指数退避等）。
     */
    record Retry(Duration delay, String reason) implements ErrorRecovery {
    }

    /**
     * 中止执行。
     */
    record Abort(String reason) implements ErrorRecovery {
    }

    /**
     * 忽略错误，向 LLM 报告错误信息后继续。
     */
    record Ignore(String reason) implements ErrorRecovery {
    }

    /**
     * 切换到 Fallback 模型。
     */
    record Fallback(String reason) implements ErrorRecovery {
    }

    /**
     * 压缩上下文后重试（用于 PROMPT_TOO_LONG）。
     */
    record CompactAndRetry(String reason) implements ErrorRecovery {
    }

    /**
     * 截断最旧消息后重试（压缩仍不够时）。
     */
    record TruncateAndRetry(String reason) implements ErrorRecovery {
    }

    /**
     * 升级 max_output_tokens 后重试（用于 OUTPUT_TRUNCATED）。
     */
    record EscalateOutput(String reason) implements ErrorRecovery {
    }

    /**
     * 挂起等待外部输入（HITL 审批）。
     */
    record Suspend(String reason) implements ErrorRecovery {
    }

    // ===== 工厂方法 =====

    static Retry retry(Duration delay) {
        return new Retry(delay, "Retrying after " + delay.toMillis() + "ms");
    }

    static Retry retry(Duration delay, String reason) {
        return new Retry(delay, reason);
    }

    static Abort abort(String reason) {
        return new Abort(reason);
    }

    static Ignore ignore(String reason) {
        return new Ignore(reason);
    }

    static Fallback fallback(String reason) {
        return new Fallback(reason);
    }

    static CompactAndRetry compactAndRetry() {
        return new CompactAndRetry("Compacting context and retrying");
    }

    static TruncateAndRetry truncateAndRetry() {
        return new TruncateAndRetry("Truncating oldest messages and retrying");
    }

    static EscalateOutput escalateOutput() {
        return new EscalateOutput("Escalating max_output_tokens");
    }

    static Suspend suspend() {
        return new Suspend("Waiting for external approval");
    }
}
