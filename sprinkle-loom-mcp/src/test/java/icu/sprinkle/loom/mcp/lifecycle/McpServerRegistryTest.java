package icu.sprinkle.loom.mcp.lifecycle;

import icu.sprinkle.loom.mcp.health.McpHealthState;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class McpServerRegistryTest {

    @Test
    @SuppressWarnings("unchecked")
    void pingAll_should_record_failure_and_set_state_DOWN_after_threshold() throws Exception {
        McpServerRegistry registry = new McpServerRegistry(0L);
        try {
            McpSyncClient client = mock(McpSyncClient.class);
            doThrow(new RuntimeException("network down")).when(client).ping();

            McpProcessManager manager = spy(new McpProcessManager(
                    icu.sprinkle.loom.mcp.config.McpServerConfig.builder("svc")
                            .transport(icu.sprinkle.loom.mcp.config.McpServerConfig.Transport.STDIO)
                            .command("echo").build()));
            Field clientField = McpProcessManager.class.getDeclaredField("client");
            clientField.setAccessible(true);
            clientField.set(manager, client);

            Field managersField = McpServerRegistry.class.getDeclaredField("managers");
            managersField.setAccessible(true);
            ((Map<String, McpProcessManager>) managersField.get(registry)).put("svc", manager);

            Field healthField = McpServerRegistry.class.getDeclaredField("health");
            healthField.setAccessible(true);
            ((Map<String, McpHealthState>) healthField.get(registry)).put("svc", new McpHealthState());

            registry.pingAll();
            assertThat(registry.healthOf("svc").status()).isEqualTo(McpHealthState.Status.DEGRADED);
            registry.pingAll();
            registry.pingAll();
            assertThat(registry.healthOf("svc").status()).isEqualTo(McpHealthState.Status.DOWN);
            assertThat(registry.healthOf("svc").consecutiveFailures()).isEqualTo(3);
        } finally {
            registry.close();
        }
    }
}
