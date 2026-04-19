package com.sprinkleclaw.gateway.filter;

import com.sprinkleclaw.gateway.ErrorCode;
import com.sprinkleclaw.gateway.GatewayException;
import com.sprinkleclaw.gateway.GatewayRequest;
import com.sprinkleclaw.gateway.GatewayResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayFilterChainTest {

    private static GatewayFilter filter(int order, List<String> trace, String name) {
        return new GatewayFilter() {
            @Override
            public int order() { return order; }
            @Override
            public FilterResult preFilter(GatewayRequest request) {
                trace.add("pre:" + name);
                return FilterResult.pass();
            }
            @Override
            public FilterResult postFilter(GatewayRequest request, GatewayResponse response) {
                trace.add("post:" + name);
                return FilterResult.pass();
            }
        };
    }

    @Test
    void preFiltersExecuteInAscendingOrderAndPostInDescending() {
        List<String> trace = new ArrayList<>();
        GatewayFilterChain chain = new GatewayFilterChain(List.of(
                filter(300, trace, "c"),
                filter(100, trace, "a"),
                filter(200, trace, "b")
        ));

        GatewayResponse response = GatewayResponse.of("ok", null, 1L);
        GatewayResponse out = chain.execute(
                GatewayRequest.of("key", "1.1.1.1", "hi"),
                () -> response
        );

        assertSame(response, out);
        assertEquals(List.of("pre:a", "pre:b", "pre:c", "post:c", "post:b", "post:a"), trace);
    }

    @Test
    void preFilterRejectShortCircuitsAgentAndPostChain() {
        List<String> trace = new ArrayList<>();
        GatewayFilter blocker = new GatewayFilter() {
            @Override public int order() { return 150; }
            @Override public FilterResult preFilter(GatewayRequest r) {
                return FilterResult.reject(ErrorCode.AUTH_001, "blocked");
            }
        };

        GatewayFilterChain chain = new GatewayFilterChain(List.of(
                filter(100, trace, "a"), blocker, filter(200, trace, "b")
        ));

        GatewayException ex = assertThrows(GatewayException.class,
                () -> chain.execute(GatewayRequest.of("k", "ip", "msg"),
                        () -> { trace.add("agent"); return GatewayResponse.of("x", null, 0); }));

        assertEquals(ErrorCode.AUTH_001, ex.errorCode());
        assertTrue(trace.contains("pre:a"));
        assertTrue(!trace.contains("agent"));
        assertTrue(!trace.contains("pre:b"));
    }

    @Test
    void executePreFiltersReturnsRejectWhenAnyFilterRejects() {
        GatewayFilter reject = new GatewayFilter() {
            @Override public int order() { return 100; }
            @Override public FilterResult preFilter(GatewayRequest r) {
                return FilterResult.reject(ErrorCode.ACL_001, "denied");
            }
        };
        GatewayFilterChain chain = new GatewayFilterChain(List.of(reject));

        FilterResult result = chain.executePreFilters(GatewayRequest.of("k", "ip", "msg"));
        assertInstanceOf(FilterResult.Reject.class, result);
        assertEquals(ErrorCode.ACL_001, ((FilterResult.Reject) result).errorCode());
    }
}
