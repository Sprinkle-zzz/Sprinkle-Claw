package com.sprinkleclaw.gateway.metrics;

import com.sprinkleclaw.gateway.ErrorCode;
import com.sprinkleclaw.gateway.GatewayRequest;
import com.sprinkleclaw.gateway.GatewayResponse;
import com.sprinkleclaw.gateway.filter.FilterOrder;
import com.sprinkleclaw.gateway.filter.FilterResult;
import com.sprinkleclaw.gateway.filter.GatewayFilter;
import com.sprinkleclaw.gateway.tenant.TenantContext;
import com.sprinkleclaw.gateway.tenant.TenantQuota;

import java.time.Instant;

/**
 * Token 计量过滤器（postFilter）。从 GatewayResponse.usage() 提取 token 用量，
 * 上报到 {@link UsageReporter}，并检查日配额。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class TokenMeteringFilter implements GatewayFilter {

    private final UsageReporter reporter;
    private final TenantQuota tenantQuota;

    public TokenMeteringFilter(UsageReporter reporter, TenantQuota tenantQuota) {
        this.reporter = reporter;
        this.tenantQuota = tenantQuota;
    }

    public TokenMeteringFilter(UsageReporter reporter) {
        this(reporter, null);
    }

    @Override
    public int order() {
        return FilterOrder.TOKEN_METERING;
    }

    @Override
    public FilterResult preFilter(GatewayRequest request) {
        return FilterResult.pass();
    }

    @Override
    public FilterResult postFilter(GatewayRequest request, GatewayResponse response) {
        if (response.usage() == null) {
            return FilterResult.pass();
        }

        TenantContext tenant = request.tenantContext().orElse(null);
        String tenantId = tenant != null ? tenant.tenantId() : "anonymous";
        String userId = tenant != null ? tenant.userId() : "anonymous";

        int inputTokens = response.usage().inputTokens();
        int outputTokens = response.usage().outputTokens();
        int totalTokens = inputTokens + outputTokens;

        // 上报用量
        reporter.report(new UsageEvent(tenantId, userId, inputTokens, outputTokens, Instant.now()));

        // 检查日配额
        if (tenantQuota != null && tenant != null) {
            if (!tenantQuota.tryConsume(tenantId, tenant.plan(), totalTokens)) {
                return FilterResult.reject(ErrorCode.RATE_002, "Daily token quota exceeded");
            }
        }

        return FilterResult.pass();
    }
}
