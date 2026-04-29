package icu.sprinkle.loom.mcp.bridge;

import icu.sprinkle.loom.tool.AgentTool;
import icu.sprinkle.loom.tool.ToolContext;
import icu.sprinkle.loom.tool.ToolProvider;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 把单个 MCP 服务器暴露的工具批量包装为 {@link AgentTool}，纳入 {@code ToolRegistry}。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public final class McpToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(McpToolProvider.class);

    private final McpSyncClient client;
    private volatile List<AgentTool> cached;

    public McpToolProvider(McpSyncClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public List<AgentTool> provideTools(ToolContext context) {
        List<AgentTool> snapshot = cached;
        return snapshot != null ? snapshot : refreshTools();
    }

    public List<AgentTool> refreshTools() {
        try {
            ListToolsResult listed = client.listTools();
            List<AgentTool> tools = new ArrayList<>(listed.tools().size());
            for (Tool tool : listed.tools()) {
                tools.add(new McpToolAdapter(client, tool));
            }
            List<AgentTool> snapshot = List.copyOf(tools);
            cached = snapshot;
            log.info("[McpToolProvider] 已加载 {} 个 MCP 工具", snapshot.size());
            return snapshot;
        } catch (Exception e) {
            log.warn("[McpToolProvider] 加载 MCP 工具失败: {}", e.getMessage());
            return List.of();
        }
    }
}
