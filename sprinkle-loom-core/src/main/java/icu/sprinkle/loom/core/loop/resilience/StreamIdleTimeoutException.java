package icu.sprinkle.loom.core.loop.resilience;

import java.time.Duration;
import java.time.Instant;

/**
 * 流式空闲超时异常。
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public class StreamIdleTimeoutException extends RuntimeException {

    private final Duration idleTimeout;
    private final Instant lastEventTime;

    public StreamIdleTimeoutException(Duration idleTimeout, Instant lastEventTime) {
        super("Stream idle timeout: no events for " + idleTimeout
                + " (last event at " + lastEventTime + ")");
        this.idleTimeout = idleTimeout;
        this.lastEventTime = lastEventTime;
    }

    public Duration idleTimeout() {
        return idleTimeout;
    }

    public Instant lastEventTime() {
        return lastEventTime;
    }
}
