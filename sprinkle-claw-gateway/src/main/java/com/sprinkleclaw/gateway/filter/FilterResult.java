package com.sprinkleclaw.gateway.filter;

import com.sprinkleclaw.gateway.ErrorCode;

import java.util.Map;

/**
 * 过滤器执行结果。sealed interface 保证穷举。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public sealed interface FilterResult {

    record Pass() implements FilterResult {}

    record Reject(
            ErrorCode errorCode,
            String detail,
            Map<String, String> headers
    ) implements FilterResult {}

    static FilterResult pass() {
        return new Pass();
    }

    static FilterResult reject(ErrorCode code, String detail) {
        return new Reject(code, detail, Map.of());
    }

    static FilterResult reject(ErrorCode code, String detail, Map<String, String> headers) {
        return new Reject(code, detail, headers);
    }
}
