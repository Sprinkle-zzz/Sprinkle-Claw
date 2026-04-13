package com.sprinkleclaw.mcp.lifecycle;

import com.sprinkleclaw.mcp.client.McpClient;
import com.sprinkleclaw.mcp.tool.McpToolProvider;
import com.sprinkleclaw.tool.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 服务器注册表，管理多个 MCP 服务进程的连接。
 * <p>支持同时连接多个 MCP 服务器，聚合所有远程工具。</p>
 *
 * @author sprinkle
 * @since 2026/4/13
 */
public final class McpServerRegistry implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpServerRegistry.class);

    private final Map<String, McpProcessManager> managers = new ConcurrentHashMap<>();
    private final Map<String, McpToolProvider> providers = new ConcurrentHashMap<>();

    /**
     * 注册并启动一个 MCP 服务器。
     *
     * @param name    服务器名称（唯一标识）
     * @param manager 进程管理器
     * @return 该服务器提供的工具列表
     */
    public List<AgentTool> register(String name, McpProcessManager manager) {
        var client = manager.start();
        managers.put(name, manager);

        var provider = new McpToolProvider(client);
        var tools = provider.refreshTools();
        providers.put(name, provider);

        log.info("[McpRegistry] 已注册 MCP 服务器 '{}', 提供 {} 个工具", name, tools.size());
        return tools;
    }

    /**
     * 获取所有已注册服务器的工具。
     */
    public List<AgentTool> allTools() {
        var all = new ArrayList<AgentTool>();
        for (var provider : providers.values()) {
            all.addAll(provider.provideTools(null));
        }
        return List.copyOf(all);
    }

    /**
     * 获取指定服务器的客户端。
     */
    public McpClient client(String name) {
        var manager = managers.get(name);
        return manager != null ? manager.client() : null;
    }

    /**
     * 注销指定 MCP 服务器。
     */
    public void unregister(String name) {
        providers.remove(name);
        var manager = managers.remove(name);
        if (manager != null) {
            try {
                manager.close();
            } catch (Exception e) {
                log.warn("[McpRegistry] 关闭 MCP 服务器 '{}' 失败: {}", name, e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        for (var name : List.copyOf(managers.keySet())) {
            unregister(name);
        }
    }

    public int size() {
        return managers.size();
    }
}
