package com.sprinkleclaw.gateway.tenant;

/**
 * 租户上下文，由 TenantFilter 写入 GatewayRequest.attributes。
 *
 * @param tenantId 租户标识
 * @param userId   用户标识
 * @param plan     租户计划层级
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public record TenantContext(
        String tenantId,
        String userId,
        TenantPlan plan
) {
}
