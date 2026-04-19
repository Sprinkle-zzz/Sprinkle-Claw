package com.sprinkleclaw.gateway.ratelimit;

/**
 * 限流判定结果。
 *
 * @param allowed    是否放行
 * @param limit      当前窗口限额
 * @param remaining  剩余配额
 * @param resetEpoch 窗口重置时间（epoch 秒）
 * @param retryAfter 建议重试等待秒数（仅限流时有意义）
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public record RateLimitResult(
        boolean allowed,
        long limit,
        long remaining,
        long resetEpoch,
        long retryAfter
) {

    public static RateLimitResult allowed(long limit, long remaining, long resetEpoch) {
        return new RateLimitResult(true, limit, remaining, resetEpoch, 0);
    }

    public static RateLimitResult rejected(long limit, long remaining, long resetEpoch, long retryAfter) {
        return new RateLimitResult(false, limit, remaining, resetEpoch, retryAfter);
    }
}
