package icu.sprinkle.loom.gateway.ratelimit;

import icu.sprinkle.loom.gateway.ErrorCode;
import icu.sprinkle.loom.gateway.GatewayRequest;
import icu.sprinkle.loom.gateway.filter.FilterOrder;
import icu.sprinkle.loom.gateway.filter.FilterResult;
import icu.sprinkle.loom.gateway.filter.GatewayFilter;
import icu.sprinkle.loom.gateway.tenant.TenantContext;
import icu.sprinkle.loom.gateway.tenant.TenantPlan;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 限流过滤器。从租户上下文获取计划层级，调用 {@link RateLimiter} 判定。
 * <p>超限返回 Reject + X-RateLimit-* 响应头。</p>
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class RateLimitFilter implements GatewayFilter {

    private final RateLimiter rateLimiter;

    public RateLimitFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public int order() {
        return FilterOrder.RATE_LIMIT;
    }

    @Override
    public FilterResult preFilter(GatewayRequest request) {
        TenantContext tenant = request.tenantContext().orElse(null);
        String key = tenant != null ? tenant.tenantId() : "anonymous";
        TenantPlan plan = tenant != null ? tenant.plan() : TenantPlan.FREE;

        RateLimitResult result = rateLimiter.tryConsume(key, plan);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-RateLimit-Limit", String.valueOf(result.limit()));
        headers.put("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        headers.put("X-RateLimit-Reset", String.valueOf(result.resetEpoch()));

        if (!result.allowed()) {
            headers.put("Retry-After", String.valueOf(result.retryAfter()));
            return FilterResult.reject(ErrorCode.RATE_001, "Rate limit exceeded", headers);
        }

        // 将限流头存入 attributes，供用户在响应中使用
        request.attributes().put("gateway.ratelimit.headers", headers);
        return FilterResult.pass();
    }
}
