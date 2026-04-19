package com.sprinkleclaw.gateway.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sprinkleclaw.gateway.tenant.TenantPlan;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

import java.time.Duration;

/**
 * 基于 Bucket4j + Caffeine 的令牌桶限流器。
 * <p>每个限流键分配独立的 Bucket，空闲 30 分钟自动清理。</p>
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class Bucket4jRateLimiter implements RateLimiter {

    private final Cache<String, Bucket> buckets;

    public Bucket4jRateLimiter() {
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(30))
                .maximumSize(10_000)
                .build();
    }

    @Override
    public RateLimitResult tryConsume(String key, TenantPlan plan) {
        Bucket bucket = buckets.get(key, k -> createBucket(plan));

        long availableTokens = bucket.getAvailableTokens();
        long limit = plan.requestsPerMinute();
        long resetEpoch = System.currentTimeMillis() / 1000 + 60;

        if (bucket.tryConsume(1)) {
            return RateLimitResult.allowed(limit, availableTokens - 1, resetEpoch);
        } else {
            long retryAfter = 60 - (System.currentTimeMillis() / 1000) % 60;
            return RateLimitResult.rejected(limit, 0, resetEpoch, retryAfter);
        }
    }

    private Bucket createBucket(TenantPlan plan) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(plan.requestsPerMinute())
                .refillGreedy(plan.requestsPerMinute(), Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(bandwidth).build();
    }
}
