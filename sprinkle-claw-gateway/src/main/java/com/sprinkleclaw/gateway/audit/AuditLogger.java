package com.sprinkleclaw.gateway.audit;

/**
 * 审计日志 SPI。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public interface AuditLogger {

    /**
     * 记录审计事件。
     */
    void log(AuditEvent event);

    /**
     * 刷出缓冲。
     */
    default void flush() {}

    /**
     * 关闭资源。
     */
    default void close() {}
}
