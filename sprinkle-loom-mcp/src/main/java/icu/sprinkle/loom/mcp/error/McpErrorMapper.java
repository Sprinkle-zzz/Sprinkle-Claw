package icu.sprinkle.loom.mcp.error;

import icu.sprinkle.loom.protocol.tool.ToolResult;

/**
 * 将官方 SDK / 网络层异常统一转换为 {@link ToolResult#error}，
 * 隔离上层（AgentLoop / ToolExecutor）对 SDK 异常类型的依赖。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public final class McpErrorMapper {

    private McpErrorMapper() {}

    public static ToolResult toToolResult(String toolName, Throwable e) {
        String msg = e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : " - " + e.getMessage());
        return ToolResult.error(toolName, "MCP error: " + msg);
    }
}
