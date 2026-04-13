package com.sprinkleclaw.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprinkleclaw.mcp.protocol.JsonRpc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * MCP 客户端门面接口。
 *
 * @author sprinkle
 * @since 2026/4/13
 */
public interface McpClient extends AutoCloseable {

    /**
     * 与服务端执行初始化握手。
     *
     * @return 服务端能力信息
     */
    CompletableFuture<JsonNode> initialize();

    /**
     * 列出服务端可用工具。
     *
     * @return 工具定义列表
     */
    CompletableFuture<List<McpToolInfo>> listTools();

    /**
     * 调用远程工具。
     *
     * @param toolName  工具名称
     * @param arguments 参数
     * @return 工具执行结果
     */
    CompletableFuture<McpCallResult> callTool(String toolName, Map<String, Object> arguments);

    /**
     * 发送 Ping。
     *
     * @return 响应
     */
    CompletableFuture<JsonRpc.Response> ping();

    /**
     * 客户端是否已连接。
     */
    boolean isConnected();

    /**
     * MCP 工具信息。
     *
     * @param name        工具名称
     * @param description 工具描述
     * @param inputSchema 输入 JSON Schema
     */
    record McpToolInfo(String name, String description, Map<String, Object> inputSchema) {}

    /**
     * MCP 工具调用结果。
     *
     * @param content 结果内容列表
     * @param isError 是否为错误
     */
    record McpCallResult(List<ContentItem> content, boolean isError) {

        public String textContent() {
            return content.stream()
                    .filter(c -> "text".equals(c.type()))
                    .map(ContentItem::text)
                    .findFirst()
                    .orElse("");
        }

        public record ContentItem(String type, String text) {}
    }
}
