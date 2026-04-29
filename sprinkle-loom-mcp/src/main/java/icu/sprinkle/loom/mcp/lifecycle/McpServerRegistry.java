package icu.sprinkle.loom.mcp.lifecycle;

import icu.sprinkle.loom.mcp.bridge.McpToolProvider;
import icu.sprinkle.loom.mcp.config.McpServerConfig;
import icu.sprinkle.loom.mcp.health.McpHealthState;
import icu.sprinkle.loom.tool.AgentTool;
import icu.sprinkle.loom.tool.ToolContext;
import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 多 MCP 服务器注册表：聚合多个服务器暴露的工具，并对每个服务器做周期性健康探活。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public final class McpServerRegistry implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpServerRegistry.class);
    private static final long DEFAULT_PING_INTERVAL_SECONDS = 30L;

    private final Map<String, McpProcessManager> managers = new ConcurrentHashMap<>();
    private final Map<String, McpToolProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, McpHealthState> health = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final long pingIntervalSeconds;

    public McpServerRegistry() {
        this(DEFAULT_PING_INTERVAL_SECONDS);
    }

    public McpServerRegistry(long pingIntervalSeconds) {
        this.pingIntervalSeconds = pingIntervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-health-scheduler");
            t.setDaemon(true);
            return t;
        });
        if (pingIntervalSeconds > 0) {
            scheduler.scheduleAtFixedRate(this::pingAll,
                    pingIntervalSeconds, pingIntervalSeconds, TimeUnit.SECONDS);
        }
    }

    public List<AgentTool> register(McpServerConfig config) {
        Objects.requireNonNull(config, "config");
        String id = config.id();
        McpProcessManager manager = new McpProcessManager(config);
        McpSyncClient client = manager.start();
        managers.put(id, manager);
        health.put(id, new McpHealthState());

        McpToolProvider provider = new McpToolProvider(client);
        List<AgentTool> tools = provider.provideTools(new ToolContext(Path.of(".")));
        providers.put(id, provider);

        log.info("[McpRegistry] 注册 MCP 服务器 id={} 工具数={}", id, tools.size());
        return tools;
    }

    public List<AgentTool> allTools() {
        List<AgentTool> all = new ArrayList<>();
        ToolContext ctx = new ToolContext(Path.of("."));
        for (McpToolProvider provider : providers.values()) {
            all.addAll(provider.provideTools(ctx));
        }
        return List.copyOf(all);
    }

    public McpSyncClient client(String id) {
        McpProcessManager manager = managers.get(id);
        return manager != null ? manager.client() : null;
    }

    public McpHealthState healthOf(String id) {
        return health.get(id);
    }

    public void unregister(String id) {
        providers.remove(id);
        health.remove(id);
        McpProcessManager manager = managers.remove(id);
        if (manager != null) {
            try {
                manager.close();
            } catch (Exception e) {
                log.warn("[McpRegistry] 关闭服务器 {} 失败: {}", id, e.getMessage());
            }
        }
    }

    public int size() {
        return managers.size();
    }

    void pingAll() {
        managers.forEach((id, manager) -> {
            McpSyncClient c = manager.client();
            if (c == null) return;
            McpHealthState state = health.get(id);
            try {
                c.ping();
                if (state != null) state.recordSuccess();
            } catch (Exception e) {
                if (state != null) state.recordFailure(e.getMessage());
                log.warn("[McpRegistry] ping 失败 id={} status={} err={}",
                        id, state != null ? state.status() : "?", e.getMessage());
            }
        });
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        for (String id : List.copyOf(managers.keySet())) {
            unregister(id);
        }
    }
}
