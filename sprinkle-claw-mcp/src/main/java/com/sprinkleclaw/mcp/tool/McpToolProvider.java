package com.sprinkleclaw.mcp.tool;

import com.sprinkleclaw.mcp.client.McpClient;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolContext;
import com.sprinkleclaw.tool.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MCP 工具提供者，将远端 MCP 服务器的工具暴露为 SDK 原生 AgentTool。
 * <p>实现 {@link ToolProvider} SPI，可通过 ServiceLoader 或 ClawBuilder 手动注册。</p>
 *
 * @author sprinkle
 * @since 2026/4/13
 */
public final class McpToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(McpToolProvider.class);

    private final McpClient client;
    private volatile List<AgentTool> cachedTools;

    public McpToolProvider(McpClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public List<AgentTool> provideTools(ToolContext context) {
        if (cachedTools != null) {
            return cachedTools;
        }
        return refreshTools();
    }

    /**
     * 从 MCP 服务端刷新工具列表。
     *
     * @return 工具列表
     */
    public List<AgentTool> refreshTools() {
        try {
            var toolInfos = client.listTools().join();
            var tools = new ArrayList<AgentTool>();
            for (var info : toolInfos) {
                tools.add(new McpToolAdapter(client, info));
            }
            cachedTools = List.copyOf(tools);
            log.info("[McpToolProvider] 已加载 {} 个 MCP 工具", cachedTools.size());
            return cachedTools;
        } catch (Exception e) {
            log.warn("[McpToolProvider] 加载 MCP 工具失败: {}", e.getMessage());
            return List.of();
        }
    }
}
