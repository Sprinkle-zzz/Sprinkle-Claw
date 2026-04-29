package icu.sprinkle.loom.mcp.bridge;

import icu.sprinkle.loom.protocol.tool.ToolDefinition;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在官方 SDK 的 {@link McpSchema.Tool} 与 SC 的 {@link ToolDefinition} 之间做字段映射。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public final class McpToolDefinitionMapper {

    private McpToolDefinitionMapper() {}

    public static ToolDefinition toToolDefinition(McpSchema.Tool tool) {
        return ToolDefinition.of(tool.name(),
                tool.description() == null ? "" : tool.description(),
                toSchemaMap(tool.inputSchema()));
    }

    static Map<String, Object> toSchemaMap(McpSchema.JsonSchema schema) {
        if (schema == null) {
            return Map.of("type", "object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", schema.type() == null ? "object" : schema.type());
        if (schema.properties() != null && !schema.properties().isEmpty()) {
            result.put("properties", schema.properties());
        }
        if (schema.required() != null && !schema.required().isEmpty()) {
            result.put("required", schema.required());
        }
        if (schema.additionalProperties() != null) {
            result.put("additionalProperties", schema.additionalProperties());
        }
        if (schema.defs() != null && !schema.defs().isEmpty()) {
            result.put("$defs", schema.defs());
        }
        if (schema.definitions() != null && !schema.definitions().isEmpty()) {
            result.put("definitions", schema.definitions());
        }
        return result;
    }
}
