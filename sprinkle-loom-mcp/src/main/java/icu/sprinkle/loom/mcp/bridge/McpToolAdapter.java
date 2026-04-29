package icu.sprinkle.loom.mcp.bridge;

import icu.sprinkle.loom.mcp.error.McpErrorMapper;
import icu.sprinkle.loom.protocol.tool.ToolDefinition;
import icu.sprinkle.loom.protocol.tool.ToolResult;
import icu.sprinkle.loom.tool.AgentTool;
import icu.sprinkle.loom.tool.ToolContext;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 将远端 MCP 工具桥接为本地 {@link AgentTool}，调用底层 {@link McpSyncClient}。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public final class McpToolAdapter implements AgentTool {

    private final McpSyncClient client;
    private final ToolDefinition definition;

    public McpToolAdapter(McpSyncClient client, McpSchema.Tool tool) {
        this.client = Objects.requireNonNull(client, "client");
        this.definition = McpToolDefinitionMapper.toToolDefinition(Objects.requireNonNull(tool, "tool"));
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        try {
            CallToolResult result = client.callTool(new CallToolRequest(definition.name(), input));
            String text = extractText(result);
            return Boolean.TRUE.equals(result.isError())
                    ? ToolResult.error(definition.name(), text)
                    : ToolResult.success(definition.name(), text);
        } catch (Exception e) {
            return McpErrorMapper.toToolResult(definition.name(), e);
        }
    }

    private static String extractText(CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return "";
        }
        return result.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .collect(Collectors.joining("\n"));
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean isConcurrencySafe() {
        return true;
    }
}
