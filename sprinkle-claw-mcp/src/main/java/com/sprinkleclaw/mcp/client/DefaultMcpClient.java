package com.sprinkleclaw.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sprinkleclaw.mcp.protocol.JsonRpc;
import com.sprinkleclaw.mcp.protocol.McpMethod;
import com.sprinkleclaw.mcp.transport.McpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * MCP 客户端默认实现。
 *
 * @author sprinkle
 * @since 2026/4/13
 */
public final class DefaultMcpClient implements McpClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultMcpClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpTransport transport;
    private final McpClientConfig config;
    private volatile boolean initialized;

    public DefaultMcpClient(McpTransport transport, McpClientConfig config) {
        this.transport = Objects.requireNonNull(transport);
        this.config = Objects.requireNonNull(config);
    }

    public DefaultMcpClient(McpTransport transport) {
        this(transport, McpClientConfig.defaults());
    }

    @Override
    public CompletableFuture<JsonNode> initialize() {
        var params = MAPPER.createObjectNode();
        var clientInfo = MAPPER.createObjectNode();
        clientInfo.put("name", config.clientName());
        clientInfo.put("version", config.clientVersion());
        params.set("clientInfo", clientInfo);
        params.put("protocolVersion", "2024-11-05");

        var capabilities = MAPPER.createObjectNode();
        params.set("capabilities", capabilities);

        var request = JsonRpc.Request.create(null, McpMethod.INITIALIZE, params);
        return transport.send(request).thenApply(response -> {
            if (!response.isSuccess()) {
                throw new McpException("Initialize failed: " + response.error().message());
            }
            // 发送 initialized 通知
            transport.sendNotification(JsonRpc.Request.notification(McpMethod.INITIALIZED, null));
            initialized = true;
            log.info("[McpClient] 初始化成功");
            return response.result();
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<McpToolInfo>> listTools() {
        var request = JsonRpc.Request.create(null, McpMethod.TOOLS_LIST, MAPPER.createObjectNode());
        return transport.send(request).thenApply(response -> {
            if (!response.isSuccess()) {
                throw new McpException("tools/list failed: " + response.error().message());
            }
            var result = response.result();
            var tools = new ArrayList<McpToolInfo>();
            if (result.has("tools") && result.get("tools").isArray()) {
                for (var toolNode : result.get("tools")) {
                    var name = toolNode.get("name").asText();
                    var desc = toolNode.has("description") ? toolNode.get("description").asText() : "";
                    var schema = toolNode.has("inputSchema")
                            ? MAPPER.convertValue(toolNode.get("inputSchema"), Map.class)
                            : Map.<String, Object>of();
                    tools.add(new McpToolInfo(name, desc, schema));
                }
            }
            return List.copyOf(tools);
        });
    }

    @Override
    public CompletableFuture<McpCallResult> callTool(String toolName, Map<String, Object> arguments) {
        var params = MAPPER.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", MAPPER.valueToTree(arguments));

        var request = JsonRpc.Request.create(null, McpMethod.TOOLS_CALL, params);
        return transport.send(request).thenApply(response -> {
            if (!response.isSuccess()) {
                throw new McpException("tools/call failed: " + response.error().message());
            }
            return parseCallResult(response.result());
        });
    }

    @Override
    public CompletableFuture<JsonRpc.Response> ping() {
        var request = JsonRpc.Request.create(null, McpMethod.PING, null);
        return transport.send(request);
    }

    @Override
    public boolean isConnected() {
        return initialized && transport.isActive();
    }

    @Override
    public void close() throws Exception {
        transport.close();
        initialized = false;
    }

    private McpCallResult parseCallResult(JsonNode result) {
        var content = new ArrayList<McpCallResult.ContentItem>();
        boolean isError = result.has("isError") && result.get("isError").asBoolean();

        if (result.has("content") && result.get("content").isArray()) {
            for (var item : result.get("content")) {
                var type = item.has("type") ? item.get("type").asText() : "text";
                var text = item.has("text") ? item.get("text").asText() : "";
                content.add(new McpCallResult.ContentItem(type, text));
            }
        }
        return new McpCallResult(List.copyOf(content), isError);
    }

    /**
     * MCP 客户端异常。
     */
    public static final class McpException extends RuntimeException {
        public McpException(String message) {
            super(message);
        }

        public McpException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
