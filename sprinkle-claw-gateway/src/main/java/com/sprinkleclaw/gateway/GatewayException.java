package com.sprinkleclaw.gateway;

import java.util.Map;

/**
 * 网关异常，携带 {@link ErrorCode} 和可选的响应头（如 X-RateLimit-*）。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class GatewayException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;
    private final Map<String, String> headers;

    public GatewayException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, Map.of());
    }

    public GatewayException(ErrorCode errorCode, String detail, Map<String, String> headers) {
        super(detail);
        this.errorCode = errorCode;
        this.detail = detail;
        this.headers = headers;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String detail() {
        return detail;
    }

    public Map<String, String> headers() {
        return headers;
    }
}
