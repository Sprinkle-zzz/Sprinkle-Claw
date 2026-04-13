package com.sprinkleclaw.mcp.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprinkleclaw.mcp.protocol.JsonRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 基于子进程 stdin/stdout 的 MCP 传输层实现。
 * <p>使用虚拟线程读取子进程 stdout，通过请求 ID 关联响应。</p>
 *
 * @author sprinkle
 * @since 2026/4/13
 */
public final class StdioTransport implements McpTransport {

    private static final Logger log = LoggerFactory.getLogger(StdioTransport.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProcessBuilder processBuilder;
    private Process process;
    private BufferedWriter writer;
    private Thread readerThread;

    private final AtomicLong idCounter = new AtomicLong(1);
    private final Map<Object, CompletableFuture<JsonRpc.Response>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicBoolean active = new AtomicBoolean(false);

    private volatile Consumer<JsonRpc.Request> requestHandler;
    private volatile Consumer<JsonRpc.Request> notificationHandler;

    /**
     * 创建 Stdio 传输层。
     *
     * @param command MCP 服务端启动命令
     */
    public StdioTransport(String... command) {
        this.processBuilder = new ProcessBuilder(command)
                .redirectErrorStream(false);
    }

    /**
     * 创建 Stdio 传输层（指定环境变量）。
     *
     * @param env     环境变量
     * @param command MCP 服务端启动命令
     */
    public StdioTransport(Map<String, String> env, String... command) {
        this.processBuilder = new ProcessBuilder(command)
                .redirectErrorStream(false);
        this.processBuilder.environment().putAll(env);
    }

    @Override
    public void start() {
        if (active.getAndSet(true)) {
            return;
        }
        try {
            process = processBuilder.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            readerThread = Thread.ofVirtual().name("mcp-stdio-reader").start(this::readLoop);
            log.info("[McpStdio] 子进程已启动: {}", processBuilder.command());
        } catch (IOException e) {
            active.set(false);
            throw new UncheckedIOException("Failed to start MCP server process", e);
        }
    }

    @Override
    public CompletableFuture<JsonRpc.Response> send(JsonRpc.Request request) {
        var id = request.id() != null ? request.id() : idCounter.getAndIncrement();
        var req = JsonRpc.Request.create(id, request.method(), request.params());
        var future = new CompletableFuture<JsonRpc.Response>();
        pendingRequests.put(id, future);
        try {
            writeLine(MAPPER.writeValueAsString(req));
        } catch (Exception e) {
            pendingRequests.remove(id);
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public void sendNotification(JsonRpc.Request notification) {
        try {
            writeLine(MAPPER.writeValueAsString(notification));
        } catch (Exception e) {
            log.warn("[McpStdio] 发送通知失败: {}", e.getMessage());
        }
    }

    @Override
    public void onRequest(Consumer<JsonRpc.Request> handler) {
        this.requestHandler = handler;
    }

    @Override
    public void onNotification(Consumer<JsonRpc.Request> handler) {
        this.notificationHandler = handler;
    }

    @Override
    public boolean isActive() {
        return active.get() && process != null && process.isAlive();
    }

    @Override
    public void close() {
        if (!active.getAndSet(false)) {
            return;
        }
        pendingRequests.values().forEach(f ->
                f.completeExceptionally(new IOException("Transport closed")));
        pendingRequests.clear();

        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        if (readerThread != null) {
            readerThread.interrupt();
        }
        log.info("[McpStdio] 传输层已关闭");
    }

    private synchronized void writeLine(String json) throws IOException {
        writer.write(json);
        writer.newLine();
        writer.flush();
    }

    private void readLoop() {
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while (active.get() && (line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                handleIncoming(line);
            }
        } catch (IOException e) {
            if (active.get()) {
                log.warn("[McpStdio] 读取异常: {}", e.getMessage());
            }
        } finally {
            active.set(false);
        }
    }

    private void handleIncoming(String line) {
        try {
            var node = MAPPER.readTree(line);

            // 判断消息类型
            if (node.has("result") || node.has("error")) {
                // 响应
                var response = MAPPER.treeToValue(node, JsonRpc.Response.class);
                var future = pendingRequests.remove(response.id());
                if (future != null) {
                    future.complete(response);
                } else {
                    log.warn("[McpStdio] 收到未关联的响应 id={}", response.id());
                }
            } else if (node.has("method")) {
                // 请求或通知
                var request = MAPPER.treeToValue(node, JsonRpc.Request.class);
                if (request.id() != null) {
                    if (requestHandler != null) {
                        requestHandler.accept(request);
                    }
                } else {
                    if (notificationHandler != null) {
                        notificationHandler.accept(request);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[McpStdio] 解析消息失败: {}", e.getMessage());
        }
    }

    /**
     * 发送 JSON-RPC 响应（服务端模式使用）。
     *
     * @param response 响应消息
     */
    public void sendResponse(JsonRpc.Response response) {
        try {
            writeLine(MAPPER.writeValueAsString(response));
        } catch (Exception e) {
            log.warn("[McpStdio] 发送响应失败: {}", e.getMessage());
        }
    }

    /**
     * 获取下一个请求 ID。
     */
    public long nextId() {
        return idCounter.getAndIncrement();
    }
}
