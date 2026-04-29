package icu.sprinkle.loom.mcp.server;

import icu.sprinkle.loom.protocol.tool.ToolResult;
import icu.sprinkle.loom.tool.AgentTool;
import icu.sprinkle.loom.tool.ToolContext;
import icu.sprinkle.loom.tool.ToolRegistry;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 SC 的 {@link ToolRegistry} 转成官方 SDK 的 {@link SyncToolSpecification} 列表，
 * 供 {@link io.modelcontextprotocol.server.McpServer} 暴露给外部 MCP 客户端。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public final class ToolRegistryBridge {

    private ToolRegistryBridge() {}

    public static List<SyncToolSpecification> toServerTools(ToolRegistry registry) {
        List<SyncToolSpecification> result = new ArrayList<>(registry.size());
        for (AgentTool tool : registry.all()) {
            result.add(toSpec(tool));
        }
        return result;
    }

    private static SyncToolSpecification toSpec(AgentTool tool) {
        McpSchema.Tool schemaTool = new McpSchema.Tool(
                tool.name(),
                null,
                tool.definition().description(),
                toJsonSchema(tool.definition().inputSchema()),
                null,
                null,
                null);
        return SyncToolSpecification.builder()
                .tool(schemaTool)
                .callHandler((exchange, request) -> {
                    Map<String, Object> args = request.arguments() != null
                            ? request.arguments()
                            : Map.of();
                    ToolResult r = tool.execute(args, new ToolContext(Path.of(".")));
                    return new McpSchema.CallToolResult(
                            List.<McpSchema.Content>of(new McpSchema.TextContent(r.output())),
                            r.isError(), null, null);
                })
                .build();
    }

    @SuppressWarnings("unchecked")
    static McpSchema.JsonSchema toJsonSchema(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return new McpSchema.JsonSchema("object", Map.of(), List.of(), null, null, null);
        }
        String type = raw.get("type") instanceof String s ? s : "object";
        Map<String, Object> properties = raw.get("properties") instanceof Map<?, ?> p
                ? new LinkedHashMap<>((Map<String, Object>) p) : Map.of();
        List<String> required = raw.get("required") instanceof List<?> l
                ? new ArrayList<>((List<String>) l) : List.of();
        Boolean addProps = raw.get("additionalProperties") instanceof Boolean b ? b : null;
        Map<String, Object> defs = raw.get("$defs") instanceof Map<?, ?> d
                ? new LinkedHashMap<>((Map<String, Object>) d) : null;
        Map<String, Object> definitions = raw.get("definitions") instanceof Map<?, ?> d
                ? new LinkedHashMap<>((Map<String, Object>) d) : null;
        return new McpSchema.JsonSchema(type, properties, required, addProps, defs, definitions);
    }
}
