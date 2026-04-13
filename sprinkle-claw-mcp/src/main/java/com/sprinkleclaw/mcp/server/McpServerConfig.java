package com.sprinkleclaw.mcp.server;

/**
 * MCP 服务端配置。
 *
 * @param serverName    服务端名称
 * @param serverVersion 服务端版本
 *
 * @author sprinkle
 * @since 2026/4/13
 */
public record McpServerConfig(
        String serverName,
        String serverVersion
) {
    public McpServerConfig {
        if (serverName == null || serverName.isBlank()) serverName = "sprinkle-claw-mcp-server";
        if (serverVersion == null || serverVersion.isBlank()) serverVersion = "0.6.0";
    }

    public static McpServerConfig defaults() {
        return new McpServerConfig("sprinkle-claw-mcp-server", "0.6.0");
    }
}
