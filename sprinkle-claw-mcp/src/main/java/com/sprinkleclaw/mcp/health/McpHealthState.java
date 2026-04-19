package com.sprinkleclaw.mcp.health;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单个 MCP 服务器的健康状态：UP / DEGRADED / DOWN，由连续失败次数驱动。
 *
 * <p>语义：
 * <ul>
 *   <li>0 次失败 → UP</li>
 *   <li>1~2 次失败 → DEGRADED</li>
 *   <li>≥3 次失败 → DOWN</li>
 * </ul>
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public final class McpHealthState {

    public enum Status { UP, DEGRADED, DOWN }

    public static final int FAILURE_THRESHOLD = 3;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile Status status = Status.UP;
    private volatile String lastError;

    public Status status() {
        return status;
    }

    public int consecutiveFailures() {
        return consecutiveFailures.get();
    }

    public String lastError() {
        return lastError;
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        lastError = null;
        status = Status.UP;
    }

    public void recordFailure(String error) {
        int n = consecutiveFailures.incrementAndGet();
        lastError = error;
        status = n >= FAILURE_THRESHOLD ? Status.DOWN : Status.DEGRADED;
    }
}
