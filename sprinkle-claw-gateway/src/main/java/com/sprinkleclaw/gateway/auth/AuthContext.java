package com.sprinkleclaw.gateway.auth;

import java.util.Set;

/**
 * 认证上下文，由 AuthFilter 写入 GatewayRequest.attributes。
 *
 * @param userId      用户标识
 * @param tenantId    租户标识
 * @param plan        计划层级名称（如 FREE, BASIC, PRO）
 * @param permissions 权限集合
 * @param authMethod  认证方式（api-key / jwt）
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public record AuthContext(
        String userId,
        String tenantId,
        String plan,
        Set<String> permissions,
        String authMethod
) {
}
