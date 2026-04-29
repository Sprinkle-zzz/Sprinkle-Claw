package icu.sprinkle.loom.mcp.bridge;

import icu.sprinkle.loom.tool.AgentTool;
import icu.sprinkle.loom.tool.ToolContext;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpToolProviderTest {

    @Mock
    McpSyncClient client;

    private static McpSchema.Tool tool(String name) {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object", Map.of(), List.of(), null, null, null);
        return new McpSchema.Tool(name, null, "d", schema, null, null, null);
    }

    @Test
    void should_list_and_cache_tools() {
        when(client.listTools()).thenReturn(
                new ListToolsResult(List.of(tool("a"), tool("b")), null));

        McpToolProvider provider = new McpToolProvider(client);
        List<AgentTool> first = provider.provideTools(new ToolContext(Path.of(".")));
        List<AgentTool> second = provider.provideTools(new ToolContext(Path.of(".")));

        assertThat(first).hasSize(2);
        assertThat(first.get(0).definition().name()).isEqualTo("a");
        assertThat(first.get(1).definition().name()).isEqualTo("b");
        assertThat(second).isSameAs(first);
        verify(client, times(1)).listTools();
    }

    @Test
    void refreshTools_should_force_reload() {
        when(client.listTools())
                .thenReturn(new ListToolsResult(List.of(tool("a")), null))
                .thenReturn(new ListToolsResult(List.of(tool("a"), tool("b")), null));

        McpToolProvider provider = new McpToolProvider(client);
        provider.provideTools(new ToolContext(Path.of(".")));
        List<AgentTool> reloaded = provider.refreshTools();

        assertThat(reloaded).hasSize(2);
        verify(client, times(2)).listTools();
    }

    @Test
    void should_return_empty_when_listTools_throws() {
        when(client.listTools()).thenThrow(new RuntimeException("network down"));

        List<AgentTool> tools = new McpToolProvider(client)
                .provideTools(new ToolContext(Path.of(".")));

        assertThat(tools).isEmpty();
    }
}
