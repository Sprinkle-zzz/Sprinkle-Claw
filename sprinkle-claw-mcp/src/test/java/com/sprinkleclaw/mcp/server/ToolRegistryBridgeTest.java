package com.sprinkleclaw.mcp.server;

import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolContext;
import com.sprinkleclaw.tool.ToolRegistry;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryBridgeTest {

    private static AgentTool echoTool() {
        return new AgentTool() {
            @Override public ToolDefinition definition() {
                return ToolDefinition.of("echo", "Echo input", Map.of(
                        "type", "object",
                        "properties", Map.of("msg", Map.of("type", "string")),
                        "required", List.of("msg")));
            }
            @Override public ToolResult execute(Map<String, Object> input, ToolContext context) {
                return ToolResult.success("echo", String.valueOf(input.get("msg")));
            }
        };
    }

    @Test
    void should_convert_registry_to_sync_tool_spec_list() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(echoTool());

        List<SyncToolSpecification> specs = ToolRegistryBridge.toServerTools(registry);

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).tool().name()).isEqualTo("echo");
        assertThat(specs.get(0).tool().description()).isEqualTo("Echo input");
    }

    @Test
    void callHandler_should_invoke_underlying_AgentTool() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(echoTool());

        SyncToolSpecification spec = ToolRegistryBridge.toServerTools(registry).get(0);
        CallToolResult result = spec.callHandler().apply(null,
                new CallToolRequest("echo", Map.of("msg", "hi")));

        assertThat(result.content()).hasSize(1);
        assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("hi");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void toJsonSchema_should_handle_full_map() {
        Map<String, Object> raw = Map.of(
                "type", "object",
                "properties", Map.of("a", Map.of("type", "string")),
                "required", List.of("a"),
                "additionalProperties", Boolean.FALSE);
        JsonSchema schema = ToolRegistryBridge.toJsonSchema(raw);
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties()).containsKey("a");
        assertThat(schema.required()).containsExactly("a");
        assertThat(schema.additionalProperties()).isFalse();
    }

    @Test
    void toJsonSchema_should_default_when_null_or_empty() {
        JsonSchema s1 = ToolRegistryBridge.toJsonSchema(null);
        JsonSchema s2 = ToolRegistryBridge.toJsonSchema(Map.of());
        assertThat(s1.type()).isEqualTo("object");
        assertThat(s2.type()).isEqualTo("object");
    }
}
