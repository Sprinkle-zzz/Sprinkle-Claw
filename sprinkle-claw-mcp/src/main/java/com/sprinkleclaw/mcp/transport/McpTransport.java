package com.sprinkleclaw.mcp.transport;

import com.sprinkleclaw.mcp.protocol.JsonRpc;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * MCP 传输层 SPI，负责 JSON-RPC 消息的发送与接收。
 *
 * @author sprinkle
 * @since 2026/4/13
 */
public interface McpTransport extends AutoCloseable {

    /**
     * 发送 JSON-RPC 请求并异步等待响应。
     *
     * @param request JSON-RPC 请求
     * @return 响应 Future
     */
    CompletableFuture<JsonRpc.Response> send(JsonRpc.Request request);

    /**
     * 发送通知（无需响应）。
     *
     * @param notification JSON-RPC 通知
     */
    void sendNotification(JsonRpc.Request notification);

    /**
     * 注册入站请求处理器（服务端模式使用）。
     *
     * @param handler 请求处理器
     */
    void onRequest(Consumer<JsonRpc.Request> handler);

    /**
     * 注册入站通知处理器。
     *
     * @param handler 通知处理器
     */
    void onNotification(Consumer<JsonRpc.Request> handler);

    /**
     * 启动传输层。
     */
    void start();

    /**
     * 传输层是否处于活跃状态。
     */
    boolean isActive();
}
