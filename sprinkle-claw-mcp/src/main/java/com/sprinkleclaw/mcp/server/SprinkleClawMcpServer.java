package com.sprinkleclaw.mcp.server;

import com.sprinkleclaw.mcp.health.McpAvailability;
import com.sprinkleclaw.tool.ToolRegistry;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Objects;

/**
 * 把 SC 的 {@link ToolRegistry} 暴露为标准 MCP 服务端，便于外部客户端
 * （如 Claude Desktop、MCP Inspector）调用本地工具。基于官方 SDK 的
 * {@link McpSyncServer} 构建，默认走 STDIO 传输。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public final class SprinkleClawMcpServer implements AutoCloseable {

    private static final String DEFAULT_NAME = "sprinkle-claw";
    private static final String DEFAULT_VERSION = "0.7.0";

    private final McpSyncServer server;

    public SprinkleClawMcpServer(ToolRegistry registry) {
        this(registry, DEFAULT_NAME, DEFAULT_VERSION);
    }

    public SprinkleClawMcpServer(ToolRegistry registry, String name, String version) {
        Objects.requireNonNull(registry, "registry");
        McpAvailability.requireAvailable();
        StdioServerTransportProvider transport =
                new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        this.server = McpServer.sync(transport)
                .serverInfo(new McpSchema.Implementation(name, version))
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(ToolRegistryBridge.toServerTools(registry))
                .build();
    }

    public McpSyncServer underlying() {
        return server;
    }

    @Override
    public void close() {
        server.closeGracefully();
    }
}
