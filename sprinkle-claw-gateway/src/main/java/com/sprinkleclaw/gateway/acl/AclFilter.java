package com.sprinkleclaw.gateway.acl;

import com.sprinkleclaw.gateway.ErrorCode;
import com.sprinkleclaw.gateway.GatewayRequest;
import com.sprinkleclaw.gateway.filter.FilterOrder;
import com.sprinkleclaw.gateway.filter.FilterResult;
import com.sprinkleclaw.gateway.filter.GatewayFilter;

/**
 * IP 黑白名单过滤器。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class AclFilter implements GatewayFilter {

    private final AccessControlList acl;

    public AclFilter(AccessControlList acl) {
        this.acl = acl;
    }

    @Override
    public int order() {
        return FilterOrder.ACL;
    }

    @Override
    public FilterResult preFilter(GatewayRequest request) {
        if (!acl.isAllowed(request.remoteAddress())) {
            return FilterResult.reject(ErrorCode.ACL_001,
                    "Access denied for address: " + request.remoteAddress());
        }
        return FilterResult.pass();
    }
}
