package icu.sprinkle.loom.core.loop.resilience;

import java.time.Duration;
import java.util.Optional;

/**
 * 恢复策略的决策上下文。
 * <p>由 AgentLoop 在错误发生时构建，传递给 {@link ErrorRecoveryMatrix} 以确定恢复策略。</p>
 *
 * @param errorType        错误类型
 * @param attempt          当前重试次数（从 0 开始）
 * @param message          错误消息
 * @param isBackgroundTask 是否为后台任务（后台任务失败时策略更激进）
 * @param isStreaming      是否在流式模式
 * @param hasCompacted     是否已执行过上下文压缩
 * @param hasFallbackModel 是否配置了 Fallback 模型
 * @param retryAfter       API 返回的 Retry-After 建议值
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public record RecoveryContext(
        ErrorType errorType,
        int attempt,
        String message,
        boolean isBackgroundTask,
        boolean isStreaming,
        boolean hasCompacted,
        boolean hasFallbackModel,
        Optional<Duration> retryAfter
) {

    /**
     * 创建基础恢复上下文。
     */
    public static RecoveryContext of(ErrorType errorType, int attempt, String message) {
        return new RecoveryContext(errorType, attempt, message,
                false, false, false, false, Optional.empty());
    }

    /**
     * 设置是否为后台任务。
     */
    public RecoveryContext withBackgroundTask(boolean isBackground) {
        return new RecoveryContext(errorType, attempt, message,
                isBackground, isStreaming, hasCompacted, hasFallbackModel, retryAfter);
    }

    /**
     * 设置是否在流式模式。
     */
    public RecoveryContext withStreaming(boolean streaming) {
        return new RecoveryContext(errorType, attempt, message,
                isBackgroundTask, streaming, hasCompacted, hasFallbackModel, retryAfter);
    }

    /**
     * 设置是否已压缩。
     */
    public RecoveryContext withCompacted(boolean compacted) {
        return new RecoveryContext(errorType, attempt, message,
                isBackgroundTask, isStreaming, compacted, hasFallbackModel, retryAfter);
    }

    /**
     * 设置是否有 Fallback 模型。
     */
    public RecoveryContext withFallbackModel(boolean hasFallback) {
        return new RecoveryContext(errorType, attempt, message,
                isBackgroundTask, isStreaming, hasCompacted, hasFallback, retryAfter);
    }

    /**
     * 设置 Retry-After 值。
     */
    public RecoveryContext withRetryAfter(Duration retryAfter) {
        return new RecoveryContext(errorType, attempt, message,
                isBackgroundTask, isStreaming, hasCompacted, hasFallbackModel,
                Optional.ofNullable(retryAfter));
    }
}
