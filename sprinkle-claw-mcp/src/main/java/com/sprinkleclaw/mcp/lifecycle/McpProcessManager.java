package com.sprinkleclaw.mcp.lifecycle;

import com.sprinkleclaw.mcp.client.DefaultMcpClient;
import com.sprinkleclaw.mcp.client.McpClient;
import com.sprinkleclaw.mcp.client.McpClientConfig;
import com.sprinkleclaw.mcp.transport.McpTransport;
import com.sprinkleclaw.mcp.transport.StdioTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * MCP 子进程生命周期管理器。
 * <p>封装 Stdio 传输层 + 客户端创建 + 初始化握手的完整流程。</p>
 *
 * @author sprinkle
 * @since 2026/4/13
 */
public final class McpProcessManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpProcessManager.class);

    private final String[] command;
    private final Map<String, String> env;
    private final McpClientConfig clientConfig;

    private McpTransport transport;
    private McpClient client;

    public McpProcessManager(String[] command, Map<String, String> env, McpClientConfig clientConfig) {
        this.command = Objects.requireNonNull(command);
        this.env = env != null ? Map.copyOf(env) : Map.of();
        this.clientConfig = clientConfig != null ? clientConfig : McpClientConfig.defaults();
    }

    public McpProcessManager(String... command) {
        this(command, null, null);
    }

    /**
     * 启动子进程并完成初始化握手。
     *
     * @return 已初始化的 MCP 客户端
     */
    public McpClient start() {
        if (client != null && client.isConnected()) {
            return client;
        }
        transport = env.isEmpty()
                ? new StdioTransport(command)
                : new StdioTransport(env, command);
        transport.start();

        client = new DefaultMcpClient(transport, clientConfig);
        client.initialize().join();
        log.info("[McpProcessManager] MCP 服务已启动: {}", String.join(" ", command));
        return client;
    }

    /**
     * 获取当前客户端实例。
     */
    public McpClient client() {
        return client;
    }

    @Override
    public void close() throws Exception {
        if (client != null) {
            client.close();
            client = null;
        }
        if (transport != null) {
            transport.close();
            transport = null;
        }
    }
}
