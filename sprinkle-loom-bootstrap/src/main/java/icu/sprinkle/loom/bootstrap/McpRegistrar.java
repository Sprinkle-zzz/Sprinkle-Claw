package icu.sprinkle.loom.bootstrap;

import icu.sprinkle.loom.mcp.config.McpServerConfig;
import icu.sprinkle.loom.mcp.lifecycle.McpServerRegistry;
import icu.sprinkle.loom.tool.AgentTool;
import icu.sprinkle.loom.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 在 build() 期把 sprinkle-loom-mcp 注入主链路：启动每个 MCP 服务器、注册其工具到 ToolRegistry，
 * 并把 {@link McpServerRegistry} 作为 AutoCloseable 资源交给 Loom 管理。
 *
 * <p>sprinkle-loom-mcp 是 optional 依赖；缺失时 {@link #isAvailable()} 返回 false，
 * 调用方应在 {@code enableMcp(...)} 中给出明确错误。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
final class McpRegistrar {

    private static final Logger log = LoggerFactory.getLogger(McpRegistrar.class);

    private static final boolean MCP_AVAILABLE;

    static {
        boolean available;
        try {
            Class.forName("icu.sprinkle.loom.mcp.lifecycle.McpServerRegistry");
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
        MCP_AVAILABLE = available;
    }

    private McpRegistrar() {}

    static boolean isAvailable() {
        return MCP_AVAILABLE;
    }

    /**
     * 启动所有 MCP 服务器并注册其工具。
     *
     * @return 已启动的 {@link McpServerRegistry}（由调用方挂到 Loom 的 close 链上）
     */
    static McpServerRegistry register(List<McpServerConfig> servers, ToolRegistry registry) {
        McpServerRegistry mcpRegistry = new McpServerRegistry();
        for (McpServerConfig server : servers) {
            try {
                List<AgentTool> tools = mcpRegistry.register(server);
                registry.registerAll(tools);
            } catch (Exception e) {
                log.warn("[McpRegistrar] 启动 MCP 服务器 '{}' 失败: {}", server.id(), e.getMessage());
            }
        }
        log.info("[McpRegistrar] 已接入 {} 个 MCP 服务器", mcpRegistry.size());
        return mcpRegistry;
    }
}
