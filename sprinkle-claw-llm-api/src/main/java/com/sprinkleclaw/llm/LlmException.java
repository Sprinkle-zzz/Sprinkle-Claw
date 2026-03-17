package com.sprinkleclaw.llm;

/**
 * LLM 请求异常，携带错误类型 {@link ErrorKind} 以支持上层决策（如是否重试）。
 *
 * @author sprinkle
 * @since 2026/3/17
 */
public class LlmException extends RuntimeException {
    private final ErrorKind kind;

    public LlmException(ErrorKind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public LlmException(ErrorKind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    /**
     * 获取错误类型。
     */
    public ErrorKind kind() {
        return kind;
    }

    /**
     * 判断此错误是否可重试（限流、服务端错误、超时）。
     *
     * @return 可重试返回 true
     */
    public boolean isRetryable() {
        return kind == ErrorKind.RATE_LIMIT || kind == ErrorKind.SERVER_ERROR || kind == ErrorKind.TIMEOUT;
    }

    /**
     * LLM 错误类型枚举。
     */
    public enum ErrorKind {
        /**
         * 认证失败（401）
         */
        AUTH_ERROR,
        /**
         * 请求限流（429）
         */
        RATE_LIMIT,
        /**
         * 无效请求（400）
         */
        INVALID_REQUEST,
        /**
         * 服务端错误（5xx）
         */
        SERVER_ERROR,
        /**
         * 请求超时
         */
        TIMEOUT,
        /**
         * 网络连接错误
         */
        NETWORK_ERROR,
        /**
         * 内容被过滤
         */
        CONTENT_FILTER,
        /**
         * 未知错误
         */
        UNKNOWN
    }
}
