package com.sprinkleclaw.gateway.tenant;

/**
 * 租户计划层级。定义每层级的请求频率和配额。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public enum TenantPlan {

    FREE(10, 1_000, 10_000),
    BASIC(60, 10_000, 100_000),
    PRO(300, 100_000, 1_000_000);

    private final int requestsPerMinute;
    private final int maxTokensPerRequest;
    private final long dailyTokenQuota;

    TenantPlan(int requestsPerMinute, int maxTokensPerRequest, long dailyTokenQuota) {
        this.requestsPerMinute = requestsPerMinute;
        this.maxTokensPerRequest = maxTokensPerRequest;
        this.dailyTokenQuota = dailyTokenQuota;
    }

    public int requestsPerMinute() {
        return requestsPerMinute;
    }

    public int maxTokensPerRequest() {
        return maxTokensPerRequest;
    }

    public long dailyTokenQuota() {
        return dailyTokenQuota;
    }
}
