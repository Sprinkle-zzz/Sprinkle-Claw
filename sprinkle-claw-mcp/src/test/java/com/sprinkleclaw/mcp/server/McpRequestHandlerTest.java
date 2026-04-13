package com.sprinkleclaw.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprinkleclaw.mcp.protocol.JsonRpc;
import com.sprinkleclaw.mcp.protocol.McpMethod;
import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpRequestHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private McpRequestHandler handler;

    private final AgentTool echoTool = new AgentTool() {
        @Override
        public ToolDefinition definition() {
            return ToolDefinition.of("echo", "Echo input",
                    Map.of("type", "object", "properties",
                            Map.of("text", Map.of("type", "string"))));
        }

        @Override
        public ToolResult execute(Map<String, Object> input, ToolContext context) {
            return ToolResult.success("echo", String.valueOf(input.get("text")));
        }
    };

    @BeforeEach
    void setUp() {
        handler = new McpRequestHandler(McpServerConfig.defaults(), List.of(echoTool));
    }

    @Test
    void handle_initialize_returnsServerInfo() {
        var req = JsonRpc.Request.create(1, McpMethod.INITIALIZE, MAPPER.createObjectNode());
        var resp = handler.handle(req);

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.result().has("serverInfo")).isTrue();
        assertThat(resp.result().get("protocolVersion").asText()).isEqualTo("2024-11-05");
    }

    @Test
    void handle_ping_returnsEmptyResult() {
        var req = JsonRpc.Request.create(2, McpMethod.PING, null);
        var resp = handler.handle(req);

        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void handle_toolsList_returnsTools() {
        var req = JsonRpc.Request.create(3, McpMethod.TOOLS_LIST, MAPPER.createObjectNode());
        var resp = handler.handle(req);

        assertThat(resp.isSuccess()).isTrue();
        var tools = resp.result().get("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).get("name").asText()).isEqualTo("echo");
    }

    @Test
    void handle_toolsCall_executesTool() {
        var params = MAPPER.createObjectNode();
        params.put("name", "echo");
        params.set("arguments", MAPPER.valueToTree(Map.of("text", "hello")));

        var req = JsonRpc.Request.create(4, McpMethod.TOOLS_CALL, params);
        var resp = handler.handle(req);

        assertThat(resp.isSuccess()).isTrue();
        var content = resp.result().get("content");
        assertThat(content.get(0).get("text").asText()).isEqualTo("hello");
        assertThat(resp.result().get("isError").asBoolean()).isFalse();
    }

    @Test
    void handle_toolsCall_unknownTool_returnsError() {
        var params = MAPPER.createObjectNode();
        params.put("name", "nonexistent");

        var req = JsonRpc.Request.create(5, McpMethod.TOOLS_CALL, params);
        var resp = handler.handle(req);

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.error().message()).contains("Unknown tool");
    }

    @Test
    void handle_unknownMethod_returnsMethodNotFound() {
        var req = JsonRpc.Request.create(6, "unknown/method", null);
        var resp = handler.handle(req);

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.error().code()).isEqualTo(-32601);
    }

    @Test
    void handle_toolsCall_toolThrows_returnsErrorContent() {
        var failTool = new AgentTool() {
            @Override
            public ToolDefinition definition() {
                return ToolDefinition.of("boom", "Explodes", Map.of("type", "object"));
            }

            @Override
            public ToolResult execute(Map<String, Object> input, ToolContext context) {
                throw new RuntimeException("kaboom");
            }
        };

        var h = new McpRequestHandler(McpServerConfig.defaults(), List.of(failTool));
        var params = MAPPER.createObjectNode();
        params.put("name", "boom");

        var req = JsonRpc.Request.create(7, McpMethod.TOOLS_CALL, params);
        var resp = h.handle(req);

        assertThat(resp.isSuccess()).isTrue(); // MCP 规范：工具失败通过 isError 标记
        assertThat(resp.result().get("isError").asBoolean()).isTrue();
    }
}
