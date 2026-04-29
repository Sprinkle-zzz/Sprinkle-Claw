package icu.sprinkle.loom.mcp.health;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class McpAvailabilityTest {

    @Test
    void sdk_should_be_available_in_test_classpath() {
        assertThat(McpAvailability.isAvailable()).isTrue();
    }

    @Test
    void requireAvailable_should_not_throw_when_sdk_present() {
        assertThatCode(McpAvailability::requireAvailable).doesNotThrowAnyException();
    }
}
