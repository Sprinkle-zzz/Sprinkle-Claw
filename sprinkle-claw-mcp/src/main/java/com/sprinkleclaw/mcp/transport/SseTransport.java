package com.sprinkleclaw.mcp.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprinkleclaw.mcp.protocol.JsonRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 基于 HTTP + SSE 的 MCP 传输层实现。
 * <p>请求通过 HTTP POST 发送，响应和通知通过 SSE 流接收。</p>
 *
 * @author sprinkle
 * @since 2026/4/13
 */
public final class SseTransport implements McpTransport {

    private static final Logger log = LoggerFactory.getLogger(SseTransport.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final URI serverUri;
    private final HttpClient httpClient;
    private final Duration timeout;

    private final AtomicLong idCounter = new AtomicLong(1);
    private final Map<Object, CompletableFuture<JsonRpc.Response>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicBoolean active = new AtomicBoolean(false);

    private volatile Consumer<JsonRpc.Request> requestHandler;
    private volatile Consumer<JsonRpc.Request> notificationHandler;
    private volatile URI messageEndpoint;
    private Thread sseThread;

    /**
     * 创建 SSE 传输层。
     *
     * @param serverUri MCP 服务端 SSE 端点 URL
     * @param timeout   请求超时时间
     */
    public SseTransport(URI serverUri, Duration timeout) {
        this.serverUri = serverUri;
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    public SseTransport(URI serverUri) {
        this(serverUri, Duration.ofSeconds(30));
    }

    @Override
    public void start() {
        if (active.getAndSet(true)) {
            return;
        }
        sseThread = Thread.ofVirtual().name("mcp-sse-reader").start(this::connectSse);
        log.info("[McpSse] 正在连接: {}", serverUri);
    }

    @Override
    public CompletableFuture<JsonRpc.Response> send(JsonRpc.Request request) {
        var id = request.id() != null ? request.id() : idCounter.getAndIncrement();
        var req = JsonRpc.Request.create(id, request.method(), request.params());
        var future = new CompletableFuture<JsonRpc.Response>();
        pendingRequests.put(id, future);

        CompletableFuture.runAsync(() -> {
            try {
                var target = messageEndpoint != null ? messageEndpoint : serverUri;
                var httpReq = HttpRequest.newBuilder()
                        .uri(target)
                        .header("Content-Type", "application/json")
                        .timeout(timeout)
                        .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(req)))
                        .build();
                httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                pendingRequests.remove(id);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    @Override
    public void sendNotification(JsonRpc.Request notification) {
        CompletableFuture.runAsync(() -> {
            try {
                var target = messageEndpoint != null ? messageEndpoint : serverUri;
                var httpReq = HttpRequest.newBuilder()
                        .uri(target)
                        .header("Content-Type", "application/json")
                        .timeout(timeout)
                        .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(notification)))
                        .build();
                httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                log.warn("[McpSse] 发送通知失败: {}", e.getMessage());
            }
        });
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
        return active.get();
    }

    @Override
    public void close() {
        if (!active.getAndSet(false)) {
            return;
        }
        pendingRequests.values().forEach(f ->
                f.completeExceptionally(new IOException("Transport closed")));
        pendingRequests.clear();
        if (sseThread != null) {
            sseThread.interrupt();
        }
        log.info("[McpSse] 传输层已关闭");
    }

    private void connectSse() {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(serverUri)
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            try (var reader = new BufferedReader(new InputStreamReader(response.body()))) {
                String eventType = null;
                StringBuilder dataBuffer = new StringBuilder();

                String line;
                while (active.get() && (line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        // 事件分隔符 —— 处理缓冲的事件
                        if (dataBuffer.length() > 0) {
                            handleSseEvent(eventType, dataBuffer.toString());
                            eventType = null;
                            dataBuffer.setLength(0);
                        }
                        continue;
                    }
                    if (line.startsWith("event:")) {
                        eventType = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        if (dataBuffer.length() > 0) dataBuffer.append('\n');
                        dataBuffer.append(line.substring(5).trim());
                    }
                }
            }
        } catch (Exception e) {
            if (active.get()) {
                log.warn("[McpSse] SSE 连接异常: {}", e.getMessage());
            }
        } finally {
            active.set(false);
        }
    }

    private void handleSseEvent(String eventType, String data) {
        try {
            if ("endpoint".equals(eventType)) {
                // 服务端告知消息端点
                messageEndpoint = serverUri.resolve(data);
                log.debug("[McpSse] 消息端点: {}", messageEndpoint);
                return;
            }

            var node = MAPPER.readTree(data);
            if (node.has("result") || node.has("error")) {
                var response = MAPPER.treeToValue(node, JsonRpc.Response.class);
                var future = pendingRequests.remove(response.id());
                if (future != null) {
                    future.complete(response);
                }
            } else if (node.has("method")) {
                var request = MAPPER.treeToValue(node, JsonRpc.Request.class);
                if (request.id() != null) {
                    if (requestHandler != null) requestHandler.accept(request);
                } else {
                    if (notificationHandler != null) notificationHandler.accept(request);
                }
            }
        } catch (Exception e) {
            log.warn("[McpSse] 处理 SSE 事件失败: {}", e.getMessage());
        }
    }
}
