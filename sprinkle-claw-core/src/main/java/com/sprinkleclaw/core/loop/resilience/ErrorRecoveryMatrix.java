package com.sprinkleclaw.core.loop.resilience;

import java.time.Duration;

/**
 * 错误恢复策略矩阵。
 * <p>根据 {@link ErrorType} 和 {@link RecoveryContext} 确定恢复策略。
 * 策略不可变，可通过子类或 SPI 替换整个矩阵。</p>
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public class ErrorRecoveryMatrix {

    private final ResilienceConfig config;

    public ErrorRecoveryMatrix(ResilienceConfig config) {
        this.config = config != null ? config : ResilienceConfig.DEFAULT;
    }

    public ErrorRecoveryMatrix() {
        this(ResilienceConfig.DEFAULT);
    }

    /**
     * 根据错误类型确定恢复策略。
     *
     * @param type 错误类型
     * @param ctx  恢复上下文
     * @return 恢复策略
     */
    public ErrorRecovery resolve(ErrorType type, RecoveryContext ctx) {
        return switch (type) {
            case RATE_LIMITED -> handleRateLimit(ctx);
            case SERVICE_OVERLOADED -> handleOverload(ctx);
            case AUTH_EXPIRED -> ErrorRecovery.abort("Authentication expired, cannot retry");
            case PROMPT_TOO_LONG -> handlePromptTooLong(ctx);
            case OUTPUT_TRUNCATED -> ErrorRecovery.escalateOutput();
            case INVALID_REQUEST -> ErrorRecovery.abort("Invalid request: " + ctx.message());
            case NETWORK_ERROR -> handleNetworkError(ctx);
            case TOOL_TIMEOUT -> ErrorRecovery.ignore("Tool timed out, reporting to LLM");
            case TOOL_SUSPENDED -> ErrorRecovery.suspend();
            case DOOM_LOOP -> ErrorRecovery.abort("Repetitive tool pattern detected");
            case MAX_ITERATIONS -> ErrorRecovery.abort("Max iterations reached");
            case STREAM_IDLE_TIMEOUT -> handleStreamIdle(ctx);
            case STRUCTURED_OUTPUT_INVALID -> handleStructuredOutputError(ctx);
        };
    }

    private ErrorRecovery handleRateLimit(RecoveryContext ctx) {
        if (ctx.attempt() >= config.maxRetries()) {
            return ctx.hasFallbackModel()
                    ? ErrorRecovery.fallback("Rate limit exceeded after " + config.maxRetries() + " retries")
                    : ErrorRecovery.abort("Rate limit exceeded after " + config.maxRetries() + " retries");
        }
        // 优先使用 API 返回的 retry-after，否则指数退避
        Duration backoff = ctx.retryAfter()
                .orElse(exponentialBackoff(ctx.attempt()));
        // 不超过最大延迟
        if (backoff.compareTo(config.maxRetryDelay()) > 0) {
            backoff = config.maxRetryDelay();
        }
        return ErrorRecovery.retry(backoff, "Rate limited, retrying after " + backoff.toMillis() + "ms");
    }

    private ErrorRecovery handleOverload(RecoveryContext ctx) {
        // 后台任务更激进地放弃
        if (ctx.isBackgroundTask()) {
            return ErrorRecovery.abort("Service overloaded, background task aborted");
        }
        if (ctx.attempt() >= 3) {
            return ctx.hasFallbackModel()
                    ? ErrorRecovery.fallback("Service overloaded after 3 retries")
                    : ErrorRecovery.abort("Service overloaded after 3 retries");
        }
        return ErrorRecovery.retry(
                Duration.ofSeconds(5L * (ctx.attempt() + 1)),
                "Service overloaded, retrying");
    }

    private ErrorRecovery handlePromptTooLong(RecoveryContext ctx) {
        // 先尝试压缩上下文
        if (!ctx.hasCompacted()) {
            return ErrorRecovery.compactAndRetry();
        }
        // 压缩后仍溢出 → 截断最旧消息
        if (ctx.attempt() < 2) {
            return ErrorRecovery.truncateAndRetry();
        }
        return ErrorRecovery.abort("Prompt too long even after compaction");
    }

    private ErrorRecovery handleNetworkError(RecoveryContext ctx) {
        if (ctx.attempt() >= 3) {
            return ctx.hasFallbackModel()
                    ? ErrorRecovery.fallback("Network unreachable after 3 retries")
                    : ErrorRecovery.abort("Network unreachable after 3 retries");
        }
        return ErrorRecovery.retry(Duration.ofSeconds(2), "Network error, retrying");
    }

    private ErrorRecovery handleStreamIdle(RecoveryContext ctx) {
        if (ctx.attempt() >= 2) {
            return ctx.hasFallbackModel()
                    ? ErrorRecovery.fallback("Stream idle timeout after 2 retries")
                    : ErrorRecovery.abort("Stream idle timeout after 2 retries");
        }
        return ErrorRecovery.retry(Duration.ofSeconds(1), "Stream idle, retrying");
    }

    private ErrorRecovery handleStructuredOutputError(RecoveryContext ctx) {
        if (ctx.attempt() >= 2) {
            return ErrorRecovery.abort("Structured output validation failed after 2 retries");
        }
        return ErrorRecovery.retry(Duration.ZERO,
                "Structured output invalid, retrying with hint");
    }

    /**
     * 指数退避：1s, 2s, 4s, 8s, 16s...
     */
    private Duration exponentialBackoff(int attempt) {
        long seconds = (long) Math.pow(2, attempt);
        return Duration.ofSeconds(Math.min(seconds, config.maxRetryDelay().toSeconds()));
    }
}
