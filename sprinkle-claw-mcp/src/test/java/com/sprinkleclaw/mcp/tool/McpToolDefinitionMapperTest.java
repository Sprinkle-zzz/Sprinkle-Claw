package com.sprinkleclaw.mcp.tool;

import com.sprinkleclaw.mcp.client.McpClient.McpToolInfo;
import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolDefinitionMapperTest {

    @Test
    void toToolDefinition_mapsFields() {
        var schema = Map.<String, Object>of("type", "object",
                "properties", Map.of("path", Map.of("type", "string")));
        var info = new McpToolInfo("read_file", "Read a file", schema);

        var def = McpToolDefinitionMapper.toToolDefinition(info);
        assertThat(def.name()).isEqualTo("read_file");
        assertThat(def.description()).isEqualTo("Read a file");
        assertThat(def.inputSchema()).containsKey("type");
    }

    @Test
    void toMcpToolSchema_mapsAgentTool() {
        var tool = new AgentTool() {
            @Override
            public ToolDefinition definition() {
                return ToolDefinition.of("echo", "Echo input",
                        Map.of("type", "object"));
            }

            @Override
            public ToolResult execute(Map<String, Object> input, ToolContext context) {
                return ToolResult.success("echo", input.toString());
            }
        };

        var schema = McpToolDefinitionMapper.toMcpToolSchema(tool);
        assertThat(schema.get("name")).isEqualTo("echo");
        assertThat(schema.get("description")).isEqualTo("Echo input");
        assertThat(schema).containsKey("inputSchema");
    }
}
