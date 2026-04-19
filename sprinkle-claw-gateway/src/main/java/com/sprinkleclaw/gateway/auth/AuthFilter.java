package com.sprinkleclaw.gateway.auth;

import com.sprinkleclaw.gateway.ErrorCode;
import com.sprinkleclaw.gateway.GatewayRequest;
import com.sprinkleclaw.gateway.GatewayResponse;
import com.sprinkleclaw.gateway.filter.FilterOrder;
import com.sprinkleclaw.gateway.filter.FilterResult;
import com.sprinkleclaw.gateway.filter.GatewayFilter;

import java.util.List;

/**
 * 认证过滤器。遍历所有 {@link AuthProvider}，任一成功即放行；全部失败返回 401。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class AuthFilter implements GatewayFilter {

    private final List<AuthProvider> providers;

    public AuthFilter(AuthProvider... providers) {
        this.providers = List.of(providers);
    }

    public AuthFilter(List<AuthProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    @Override
    public int order() {
        return FilterOrder.AUTH;
    }

    @Override
    public FilterResult preFilter(GatewayRequest request) {
        String authHeader = request.authHeader();

        if (authHeader == null || authHeader.isBlank()) {
            return FilterResult.reject(ErrorCode.AUTH_001, "Authentication required");
        }

        for (AuthProvider provider : providers) {
            var authContext = provider.authenticate(authHeader);
            if (authContext.isPresent()) {
                request.attributes().put(GatewayRequest.AUTH_CONTEXT_KEY, authContext.get());
                return FilterResult.pass();
            }
        }

        return FilterResult.reject(ErrorCode.AUTH_002, "Invalid credentials");
    }
}
