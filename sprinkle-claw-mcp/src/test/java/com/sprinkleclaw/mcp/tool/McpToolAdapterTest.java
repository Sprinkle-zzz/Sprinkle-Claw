package com.sprinkleclaw.mcp.tool;

import com.sprinkleclaw.mcp.client.McpClient;
import com.sprinkleclaw.mcp.client.McpClient.McpCallResult;
import com.sprinkleclaw.mcp.client.McpClient.McpToolInfo;
import com.sprinkleclaw.tool.ToolContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpToolAdapterTest {

    @Mock
    McpClient client;

    @Test
    void execute_success_returnsToolResult() {
        var info = new McpToolInfo("echo", "Echo", Map.of("type", "object"));
        var adapter = new McpToolAdapter(client, info);

        var callResult = new McpCallResult(
                List.of(new McpCallResult.ContentItem("text", "hello")), false);
        when(client.callTool(eq("echo"), any())).thenReturn(CompletableFuture.completedFuture(callResult));

        var result = adapter.execute(Map.of("input", "hello"), new ToolContext(Path.of(".")));
        assertThat(result.isError()).isFalse();
        assertThat(result.output()).isEqualTo("hello");
    }

    @Test
    void execute_error_returnsErrorResult() {
        var info = new McpToolInfo("fail", "Fail", Map.of());
        var adapter = new McpToolAdapter(client, info);

        var callResult = new McpCallResult(
                List.of(new McpCallResult.ContentItem("text", "error msg")), true);
        when(client.callTool(eq("fail"), any())).thenReturn(CompletableFuture.completedFuture(callResult));

        var result = adapter.execute(Map.of(), new ToolContext(Path.of(".")));
        assertThat(result.isError()).isTrue();
        assertThat(result.output()).isEqualTo("error msg");
    }

    @Test
    void execute_exception_returnsErrorResult() {
        var info = new McpToolInfo("boom", "Boom", Map.of());
        var adapter = new McpToolAdapter(client, info);

        when(client.callTool(eq("boom"), any())).thenReturn(
                CompletableFuture.failedFuture(new RuntimeException("connection lost")));

        var result = adapter.execute(Map.of(), new ToolContext(Path.of(".")));
        assertThat(result.isError()).isTrue();
        assertThat(result.output()).contains("connection lost");
    }

    @Test
    void definition_matchesToolInfo() {
        var info = new McpToolInfo("test_tool", "A test tool",
                Map.of("type", "object", "properties", Map.of()));
        var adapter = new McpToolAdapter(client, info);

        assertThat(adapter.definition().name()).isEqualTo("test_tool");
        assertThat(adapter.definition().description()).isEqualTo("A test tool");
        assertThat(adapter.isConcurrencySafe()).isTrue();
    }
}
