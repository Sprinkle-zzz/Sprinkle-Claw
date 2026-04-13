package com.sprinkleclaw.mcp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sprinkleclaw.mcp.protocol.JsonRpc;
import com.sprinkleclaw.mcp.protocol.McpMethod;
import com.sprinkleclaw.mcp.transport.McpTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultMcpClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    McpTransport transport;

    DefaultMcpClient client;

    @BeforeEach
    void setUp() {
        client = new DefaultMcpClient(transport, McpClientConfig.defaults());
    }

    @Test
    void initialize_success_sendsInitializedNotification() {
        var result = MAPPER.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        var response = JsonRpc.Response.success(1, result);

        when(transport.send(any())).thenReturn(CompletableFuture.completedFuture(response));

        var initResult = client.initialize().join();
        assertThat(initResult.get("protocolVersion").asText()).isEqualTo("2024-11-05");
        verify(transport).sendNotification(any());
    }

    @Test
    void initialize_failure_throwsMcpException() {
        var error = JsonRpc.Error.of(-32600, "Bad request");
        var response = JsonRpc.Response.error(1, error);

        when(transport.send(any())).thenReturn(CompletableFuture.completedFuture(response));

        assertThatThrownBy(() -> client.initialize().join())
                .hasCauseInstanceOf(DefaultMcpClient.McpException.class);
    }

    @Test
    void listTools_parsesToolArray() {
        ObjectNode result = MAPPER.createObjectNode();
        ArrayNode tools = MAPPER.createArrayNode();

        ObjectNode tool = MAPPER.createObjectNode();
        tool.put("name", "read_file");
        tool.put("description", "Read a file");
        tool.set("inputSchema", MAPPER.createObjectNode().put("type", "object"));
        tools.add(tool);

        result.set("tools", tools);

        when(transport.send(any())).thenReturn(
                CompletableFuture.completedFuture(JsonRpc.Response.success(1, result)));

        var toolList = client.listTools().join();
        assertThat(toolList).hasSize(1);
        assertThat(toolList.getFirst().name()).isEqualTo("read_file");
    }

    @Test
    void callTool_parsesResult() {
        ObjectNode result = MAPPER.createObjectNode();
        ArrayNode content = MAPPER.createArrayNode();
        ObjectNode item = MAPPER.createObjectNode();
        item.put("type", "text");
        item.put("text", "file content");
        content.add(item);
        result.set("content", content);
        result.put("isError", false);

        when(transport.send(any())).thenReturn(
                CompletableFuture.completedFuture(JsonRpc.Response.success(1, result)));

        var callResult = client.callTool("read_file", Map.of("path", "/test")).join();
        assertThat(callResult.isError()).isFalse();
        assertThat(callResult.textContent()).isEqualTo("file content");
    }

    @Test
    void ping_returnsResponse() {
        var response = JsonRpc.Response.success(1, MAPPER.createObjectNode());
        when(transport.send(any())).thenReturn(CompletableFuture.completedFuture(response));

        var resp = client.ping().join();
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void isConnected_falseBeforeInitialize() {
        assertThat(client.isConnected()).isFalse();
    }
}
