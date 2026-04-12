package com.sprinkleclaw.workflow.agent.structured;

/**
 * 结构化输出解析失败异常。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public class StructuredOutputException extends RuntimeException {

    public StructuredOutputException(String message) {
        super(message);
    }

    public StructuredOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
