package icu.sprinkle.loom.gateway.metrics;

/**
 * Token 用量上报 SPI。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public interface UsageReporter {

    /**
     * 上报用量事件。
     */
    void report(UsageEvent event);

    /**
     * 刷出缓冲。
     */
    default void flush() {}
}
