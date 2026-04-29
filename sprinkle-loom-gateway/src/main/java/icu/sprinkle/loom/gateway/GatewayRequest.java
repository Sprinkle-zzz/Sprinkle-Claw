package icu.sprinkle.loom.gateway;

import icu.sprinkle.loom.gateway.auth.AuthContext;
import icu.sprinkle.loom.gateway.tenant.TenantContext;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 管控请求上下文。SDK 语义，不绑定 HTTP。
 * <p>{@code attributes} 用于过滤器间传递上下文（如 AuthContext、TenantContext）。
 * 使用 Map 而非 ThreadLocal，兼容虚拟线程。</p>
 *
 * @param requestId     请求 ID
 * @param authHeader    认证凭据（API Key 或 JWT token）
 * @param remoteAddress 调用方地址（用于 ACL）
 * @param message       用户消息（用于注入检测）
 * @param timestamp     请求时间戳
 * @param attributes    过滤器间共享的可变上下文
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public record GatewayRequest(
        String requestId,
        String authHeader,
        String remoteAddress,
        String message,
        Instant timestamp,
        Map<String, Object> attributes
) {

    public static final String AUTH_CONTEXT_KEY = "gateway.auth.context";
    public static final String TENANT_CONTEXT_KEY = "gateway.tenant.context";

    public GatewayRequest {
        if (attributes == null) {
            attributes = new LinkedHashMap<>();
        }
    }

    /**
     * 快捷构造。
     */
    public static GatewayRequest of(String authHeader, String remoteAddress, String message) {
        return new GatewayRequest(
                UUID.randomUUID().toString(),
                authHeader,
                remoteAddress,
                message,
                Instant.now(),
                new LinkedHashMap<>()
        );
    }

    /**
     * 获取认证上下文（由 AuthFilter 写入）。
     */
    public Optional<AuthContext> authContext() {
        return Optional.ofNullable((AuthContext) attributes.get(AUTH_CONTEXT_KEY));
    }

    /**
     * 获取租户上下文（由 TenantFilter 写入）。
     */
    public Optional<TenantContext> tenantContext() {
        return Optional.ofNullable((TenantContext) attributes.get(TENANT_CONTEXT_KEY));
    }
}
