package com.sprinkleclaw.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprinkleclaw.mcp.protocol.JsonRpc;
import com.sprinkleclaw.mcp.transport.StdioTransport;
import com.sprinkleclaw.tool.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MCP 服务端门面，以 Stdio 模式运行，将 SDK AgentTool 暴露为 MCP 工具。
 * <p>用于反向场景：让外部 MCP 客户端（如 Claude Desktop）调用本 SDK 的工具。</p>
 *
 * @author sprinkle
 * @since 2026/4/13
 */
public final class McpServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpServer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpServerConfig config;
    private final McpRequestHandler handler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public McpServer(McpServerConfig config, List<AgentTool> tools) {
        this.config = Objects.requireNonNull(config);
        this.handler = new McpRequestHandler(config, tools);
    }

    public McpServer(List<AgentTool> tools) {
        this(McpServerConfig.defaults(), tools);
    }

    /**
     * 以 Stdio 模式启动服务端，阻塞当前线程。
     * <p>从 stdin 读取 JSON-RPC 请求，处理后写入 stdout。</p>
     */
    public void runStdio() {
        runStdio(System.in, System.out);
    }

    /**
     * 以 Stdio 模式启动服务端（可指定输入输出流，便于测试）。
     */
    public void runStdio(InputStream in, OutputStream out) {
        if (running.getAndSet(true)) {
            throw new IllegalStateException("Server is already running");
        }

        log.info("[McpServer] Stdio 模式启动: {}", config.serverName());

        try (var reader = new BufferedReader(new InputStreamReader(in));
             var writer = new BufferedWriter(new OutputStreamWriter(out))) {

            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    var request = MAPPER.readValue(line, JsonRpc.Request.class);

                    // 通知消息不需要响应
                    if (request.id() == null) {
                        log.debug("[McpServer] 收到通知: {}", request.method());
                        continue;
                    }

                    var response = handler.handle(request);
                    var json = MAPPER.writeValueAsString(response);
                    synchronized (this) {
                        writer.write(json);
                        writer.newLine();
                        writer.flush();
                    }
                } catch (Exception e) {
                    log.warn("[McpServer] 处理消息失败: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                log.error("[McpServer] IO 异常: {}", e.getMessage());
            }
        } finally {
            running.set(false);
            log.info("[McpServer] 已停止");
        }
    }

    @Override
    public void close() {
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }
}
