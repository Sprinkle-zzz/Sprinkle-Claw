package icu.sprinkle.loom.mcp.bridge;

import icu.sprinkle.loom.protocol.tool.ToolResult;
import icu.sprinkle.loom.tool.ToolContext;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpToolAdapterTest {

    @Mock
    McpSyncClient client;

    private static McpSchema.Tool tool(String name) {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object", Map.of(), List.of(), null, null, null);
        return new McpSchema.Tool(name, null, "desc", schema, null, null, null);
    }

    @Test
    void should_invoke_callTool_and_return_text_content() {
        CallToolResult result = new CallToolResult(
                List.<McpSchema.Content>of(new TextContent("hello")), Boolean.FALSE, null, null);
        when(client.callTool(any(CallToolRequest.class))).thenReturn(result);

        McpToolAdapter adapter = new McpToolAdapter(client, tool("echo"));
        ToolResult r = adapter.execute(Map.of("msg", "hi"), new ToolContext(Path.of(".")));

        ArgumentCaptor<CallToolRequest> captor = ArgumentCaptor.forClass(CallToolRequest.class);
        org.mockito.Mockito.verify(client).callTool(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("echo");
        assertThat(captor.getValue().arguments()).containsEntry("msg", "hi");

        assertThat(r.isError()).isFalse();
        assertThat(r.output()).isEqualTo("hello");
    }

    @Test
    void should_join_multiple_text_contents_with_newline() {
        CallToolResult result = new CallToolResult(
                List.<McpSchema.Content>of(new TextContent("line1"), new TextContent("line2")),
                Boolean.FALSE, null, null);
        when(client.callTool(any(CallToolRequest.class))).thenReturn(result);

        ToolResult r = new McpToolAdapter(client, tool("multi"))
                .execute(Map.of(), new ToolContext(Path.of(".")));

        assertThat(r.output()).isEqualTo("line1\nline2");
    }

    @Test
    void should_return_error_when_isError_true() {
        CallToolResult result = new CallToolResult(
                List.<McpSchema.Content>of(new TextContent("boom")), Boolean.TRUE, null, null);
        when(client.callTool(any(CallToolRequest.class))).thenReturn(result);

        ToolResult r = new McpToolAdapter(client, tool("fail"))
                .execute(Map.of(), new ToolContext(Path.of(".")));

        assertThat(r.isError()).isTrue();
        assertThat(r.output()).isEqualTo("boom");
    }

    @Test
    void should_map_exception_to_error_via_McpErrorMapper() {
        when(client.callTool(any(CallToolRequest.class)))
                .thenThrow(new RuntimeException("connection lost"));

        ToolResult r = new McpToolAdapter(client, tool("boom"))
                .execute(Map.of(), new ToolContext(Path.of(".")));

        assertThat(r.isError()).isTrue();
        assertThat(r.output()).contains("MCP error").contains("connection lost");
    }

    @Test
    void definition_should_carry_tool_name_and_description() {
        McpToolAdapter adapter = new McpToolAdapter(client, tool("ping"));
        assertThat(adapter.definition().name()).isEqualTo("ping");
        assertThat(adapter.definition().description()).isEqualTo("desc");
        assertThat(adapter.isConcurrencySafe()).isTrue();
    }
}
