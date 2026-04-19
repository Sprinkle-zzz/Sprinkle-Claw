package com.sprinkleclaw.gateway.filter;

import com.sprinkleclaw.gateway.GatewayException;
import com.sprinkleclaw.gateway.GatewayRequest;
import com.sprinkleclaw.gateway.GatewayResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * 过滤器责任链。按 order 升序执行 preFilter，按 order 降序执行 postFilter。
 * <p>核心方法 {@link #execute} 将 pre → Agent 执行 → post 编排为一个完整流程：
 * 任一 preFilter 返回 Reject 短路，不执行 Agent。</p>
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public final class GatewayFilterChain {

    private final List<GatewayFilter> filters;

    public GatewayFilterChain(List<GatewayFilter> filters) {
        this.filters = filters.stream()
                .sorted(Comparator.comparingInt(GatewayFilter::order))
                .toList();
    }

    /**
     * 完整执行：pre-filters → Agent 执行 → post-filters。
     *
     * @param request     管控请求上下文
     * @param agentAction Agent 执行逻辑（如 {@code () -> claw.run(message)}）
     * @return 管控响应（含 Agent 输出 + 过滤器附加的响应头）
     * @throws GatewayException 过滤器拒绝时抛出
     */
    public GatewayResponse execute(GatewayRequest request,
                                   Supplier<GatewayResponse> agentAction) {
        // 1. Pre-filters（升序）
        FilterResult preResult = executePreFilters(request);
        if (preResult instanceof FilterResult.Reject r) {
            throw new GatewayException(r.errorCode(), r.detail(), r.headers());
        }

        // 2. Agent 执行
        GatewayResponse response = agentAction.get();

        // 3. Post-filters（降序）
        FilterResult postResult = executePostFilters(request, response);
        if (postResult instanceof FilterResult.Reject r) {
            throw new GatewayException(r.errorCode(), r.detail(), r.headers());
        }

        return response;
    }

    /**
     * 仅执行前置过滤链。遇到 Reject 立即短路返回。
     */
    public FilterResult executePreFilters(GatewayRequest request) {
        for (GatewayFilter filter : filters) {
            FilterResult result = filter.preFilter(request);
            if (result instanceof FilterResult.Reject) {
                return result;
            }
        }
        return FilterResult.pass();
    }

    /**
     * 仅执行后置过滤链（反序）。
     */
    public FilterResult executePostFilters(GatewayRequest request, GatewayResponse response) {
        for (int i = filters.size() - 1; i >= 0; i--) {
            FilterResult result = filters.get(i).postFilter(request, response);
            if (result instanceof FilterResult.Reject) {
                return result;
            }
        }
        return FilterResult.pass();
    }

    /**
     * 获取过滤器列表（只读）。
     */
    public List<GatewayFilter> filters() {
        return filters;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<GatewayFilter> filters = new ArrayList<>();

        public Builder add(GatewayFilter filter) {
            this.filters.add(filter);
            return this;
        }

        public Builder addAll(List<? extends GatewayFilter> filters) {
            this.filters.addAll(filters);
            return this;
        }

        public GatewayFilterChain build() {
            return new GatewayFilterChain(filters);
        }
    }
}
