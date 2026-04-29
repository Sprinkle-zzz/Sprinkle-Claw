package icu.sprinkle.loom.gateway.filter;

import icu.sprinkle.loom.gateway.GatewayRequest;
import icu.sprinkle.loom.gateway.GatewayResponse;

/**
 * 网关过滤器 SPI。
 * <p>实现者通过 {@link #order()} 指定执行顺序，数值越小越先执行。
 * preFilter 在 Agent 执行前调用，postFilter 在执行后调用。</p>
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public interface GatewayFilter {

    /**
     * 过滤器执行顺序，数值越小越先执行。
     */
    int order();

    /**
     * 前置过滤。返回 {@link FilterResult.Reject} 立即短路，不执行 Agent。
     */
    FilterResult preFilter(GatewayRequest request);

    /**
     * 后置过滤（默认放行）。在 Agent 执行后调用。
     */
    default FilterResult postFilter(GatewayRequest request, GatewayResponse response) {
        return FilterResult.pass();
    }
}
