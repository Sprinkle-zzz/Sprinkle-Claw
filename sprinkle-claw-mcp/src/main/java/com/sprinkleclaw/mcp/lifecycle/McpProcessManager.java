package com.sprinkleclaw.mcp.lifecycle;

import com.sprinkleclaw.mcp.config.McpServerConfig;
import com.sprinkleclaw.mcp.config.McpTransportFactory;
import com.sprinkleclaw.mcp.health.McpAvailability;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 单个 MCP 服务器连接的生命周期管理器：
 * 根据 {@link McpServerConfig} 构造传输层并完成握手，对外暴露 {@link McpSyncClient}。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public final class McpProcessManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpProcessManager.class);
    private static final String CLIENT_NAME = "sprinkle-claw";
    private static final String CLIENT_VERSION = "0.7.0";

    private final McpServerConfig config;

    private McpClientTransport transport;
    private McpSyncClient client;

    public McpProcessManager(McpServerConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public synchronized McpSyncClient start() {
        if (client != null) {
            return client;
        }
        McpAvailability.requireAvailable();
        transport = McpTransportFactory.create(config);
        client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation(CLIENT_NAME, CLIENT_VERSION))
                .requestTimeout(config.requestTimeout())
                .build();
        client.initialize();
        log.info("[McpProcessManager] MCP 服务已启动: id={} transport={}",
                config.id(), config.transport());
        return client;
    }

    public McpSyncClient client() {
        return client;
    }

    public McpServerConfig config() {
        return config;
    }

    @Override
    public synchronized void close() {
        if (client != null) {
            try {
                client.closeGracefully();
            } catch (Exception e) {
                log.warn("[McpProcessManager] 关闭 MCP 客户端失败 id={}: {}", config.id(), e.getMessage());
            } finally {
                client = null;
            }
        }
        transport = null;
    }
}
