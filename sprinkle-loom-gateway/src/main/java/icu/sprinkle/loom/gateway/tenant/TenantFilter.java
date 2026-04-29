package icu.sprinkle.loom.gateway.tenant;

import icu.sprinkle.loom.gateway.GatewayRequest;
import icu.sprinkle.loom.gateway.auth.AuthContext;
import icu.sprinkle.loom.gateway.filter.FilterOrder;
import icu.sprinkle.loom.gateway.filter.FilterResult;
import icu.sprinkle.loom.gateway.filter.GatewayFilter;

/**
 * 租户过滤器。从 {@link AuthContext} 提取租户信息，写入 TenantContext。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class TenantFilter implements GatewayFilter {

    @Override
    public int order() {
        return FilterOrder.TENANT;
    }

    @Override
    public FilterResult preFilter(GatewayRequest request) {
        AuthContext auth = request.authContext().orElse(null);
        if (auth == null) {
            // 无认证上下文时使用匿名租户
            request.attributes().put(GatewayRequest.TENANT_CONTEXT_KEY,
                    new TenantContext("anonymous", "anonymous", TenantPlan.FREE));
            return FilterResult.pass();
        }

        TenantPlan plan;
        try {
            plan = TenantPlan.valueOf(auth.plan());
        } catch (IllegalArgumentException e) {
            plan = TenantPlan.FREE;
        }

        request.attributes().put(GatewayRequest.TENANT_CONTEXT_KEY,
                new TenantContext(auth.tenantId(), auth.userId(), plan));
        return FilterResult.pass();
    }
}
