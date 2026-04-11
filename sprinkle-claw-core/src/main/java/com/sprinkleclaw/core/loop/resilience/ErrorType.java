package com.sprinkleclaw.core.loop.resilience;

/**
 * Agent 运行过程中的错误类型枚举。
 * <p>覆盖 LLM API 错误、工具执行错误、循环异常和流式错误四大类。</p>
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public enum ErrorType {

    // ===== LLM API 错误 =====

    /**
     * 429 Too Many Requests
     */
    RATE_LIMITED,
    /**
     * 529 / 503 Service Unavailable
     */
    SERVICE_OVERLOADED,
    /**
     * 401 / 403 认证失效
     */
    AUTH_EXPIRED,
    /**
     * 400 context_length_exceeded
     */
    PROMPT_TOO_LONG,
    /**
     * stop_reason = max_tokens
     */
    OUTPUT_TRUNCATED,
    /**
     * 400 其他无效请求
     */
    INVALID_REQUEST,
    /**
     * 连接超时、DNS 失败等
     */
    NETWORK_ERROR,

    // ===== 工具执行错误 =====

    /**
     * 工具执行超时
     */
    TOOL_TIMEOUT,
    /**
     * ToolSuspendException（HITL 审批等待）
     */
    TOOL_SUSPENDED,

    // ===== 循环异常 =====

    /**
     * 重复工具调用检测（Doom Loop）
     */
    DOOM_LOOP,
    /**
     * 超过最大迭代轮次
     */
    MAX_ITERATIONS,

    // ===== 流式错误 =====

    /**
     * SSE 事件间隔超时
     */
    STREAM_IDLE_TIMEOUT,

    // ===== 结构化输出 =====

    /**
     * JSON Schema 校验失败
     */
    STRUCTURED_OUTPUT_INVALID
}
