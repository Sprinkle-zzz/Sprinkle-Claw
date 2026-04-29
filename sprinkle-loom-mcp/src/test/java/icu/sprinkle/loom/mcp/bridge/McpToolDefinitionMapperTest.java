package icu.sprinkle.loom.mcp.bridge;

import icu.sprinkle.loom.protocol.tool.ToolDefinition;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolDefinitionMapperTest {

    @Test
    void should_map_tool_with_full_schema() {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object",
                Map.of("path", Map.of("type", "string")),
                List.of("path"),
                Boolean.FALSE,
                null,
                null);
        McpSchema.Tool tool = new McpSchema.Tool(
                "read_file", null, "Read a file", schema, null, null, null);

        ToolDefinition def = McpToolDefinitionMapper.toToolDefinition(tool);

        assertThat(def.name()).isEqualTo("read_file");
        assertThat(def.description()).isEqualTo("Read a file");
        assertThat(def.inputSchema()).containsEntry("type", "object");
        assertThat(def.inputSchema()).containsKey("properties");
        assertThat(def.inputSchema().get("required")).isEqualTo(List.of("path"));
        assertThat(def.inputSchema()).containsEntry("additionalProperties", Boolean.FALSE);
    }

    @Test
    void should_default_description_when_null() {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object", Map.of(), List.of(), null, null, null);
        McpSchema.Tool tool = new McpSchema.Tool(
                "ping", null, null, schema, null, null, null);

        ToolDefinition def = McpToolDefinitionMapper.toToolDefinition(tool);

        assertThat(def.description()).isEqualTo("");
    }

    @Test
    void should_default_to_object_when_schema_null() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "noop", null, "n", null, null, null, null);

        ToolDefinition def = McpToolDefinitionMapper.toToolDefinition(tool);

        assertThat(def.inputSchema()).containsEntry("type", "object");
    }
}
