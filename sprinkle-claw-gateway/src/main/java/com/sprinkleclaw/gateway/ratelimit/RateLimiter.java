package com.sprinkleclaw.gateway.ratelimit;

import com.sprinkleclaw.gateway.tenant.TenantPlan;

/**
 * 限流器 SPI。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public interface RateLimiter {

    /**
     * 尝试消费一个请求配额。
     *
     * @param key  限流键（通常是 userId 或 tenantId）
     * @param plan 租户计划（决定限流参数）
     * @return 限流判定结果
     */
    RateLimitResult tryConsume(String key, TenantPlan plan);
}
