package icu.sprinkle.loom.gateway.audit;

import icu.sprinkle.loom.gateway.GatewayRequest;
import icu.sprinkle.loom.gateway.GatewayResponse;
import icu.sprinkle.loom.gateway.filter.FilterOrder;
import icu.sprinkle.loom.gateway.filter.FilterResult;
import icu.sprinkle.loom.gateway.filter.GatewayFilter;
import icu.sprinkle.loom.gateway.tenant.TenantContext;

import java.time.Instant;

/**
 * 审计过滤器。pre 阶段记录请求元数据，post 阶段记录响应和 token 用量。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class AuditFilter implements GatewayFilter {

    private final AuditLogger auditLogger;

    public AuditFilter(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @Override
    public int order() {
        return FilterOrder.AUDIT_PRE;
    }

    @Override
    public FilterResult preFilter(GatewayRequest request) {
        TenantContext tenant = request.tenantContext().orElse(null);
        auditLogger.log(new AuditEvent(
                request.requestId(),
                tenant != null ? tenant.tenantId() : "anonymous",
                tenant != null ? tenant.userId() : "anonymous",
                "REQUEST",
                request.message(),
                0, 0, 0,
                Instant.now()
        ));
        return FilterResult.pass();
    }

    @Override
    public FilterResult postFilter(GatewayRequest request, GatewayResponse response) {
        TenantContext tenant = request.tenantContext().orElse(null);
        auditLogger.log(new AuditEvent(
                request.requestId(),
                tenant != null ? tenant.tenantId() : "anonymous",
                tenant != null ? tenant.userId() : "anonymous",
                "RESPONSE",
                response.output(),
                response.usage() != null ? response.usage().inputTokens() : 0,
                response.usage() != null ? response.usage().outputTokens() : 0,
                response.elapsedMs(),
                Instant.now()
        ));
        return FilterResult.pass();
    }
}
