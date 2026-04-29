package icu.sprinkle.loom.mcp.health;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpHealthStateTest {

    @Test
    void initial_status_should_be_UP() {
        McpHealthState s = new McpHealthState();
        assertThat(s.status()).isEqualTo(McpHealthState.Status.UP);
        assertThat(s.consecutiveFailures()).isZero();
        assertThat(s.lastError()).isNull();
    }

    @Test
    void single_failure_should_be_DEGRADED() {
        McpHealthState s = new McpHealthState();
        s.recordFailure("transient");
        assertThat(s.status()).isEqualTo(McpHealthState.Status.DEGRADED);
        assertThat(s.consecutiveFailures()).isEqualTo(1);
        assertThat(s.lastError()).isEqualTo("transient");
    }

    @Test
    void three_failures_should_be_DOWN() {
        McpHealthState s = new McpHealthState();
        s.recordFailure("a");
        s.recordFailure("b");
        s.recordFailure("c");
        assertThat(s.status()).isEqualTo(McpHealthState.Status.DOWN);
        assertThat(s.consecutiveFailures()).isEqualTo(3);
        assertThat(s.lastError()).isEqualTo("c");
    }

    @Test
    void success_should_reset_to_UP() {
        McpHealthState s = new McpHealthState();
        s.recordFailure("a");
        s.recordFailure("b");
        s.recordSuccess();
        assertThat(s.status()).isEqualTo(McpHealthState.Status.UP);
        assertThat(s.consecutiveFailures()).isZero();
        assertThat(s.lastError()).isNull();
    }
}
