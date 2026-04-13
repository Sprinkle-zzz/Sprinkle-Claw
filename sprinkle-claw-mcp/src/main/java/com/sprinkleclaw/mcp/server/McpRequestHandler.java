package com.sprinkleclaw.mcp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sprinkleclaw.mcp.protocol.JsonRpc;
import com.sprinkleclaw.mcp.protocol.McpError;
import com.sprinkleclaw.mcp.protocol.McpMethod;
import com.sprinkleclaw.mcp.tool.McpToolDefinitionMapper;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 服务端 JSON-RPC 请求分发处理器。
 *
 * @author sprinkle
 * @since 2026/4/13
 */
public final class McpRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(McpRequestHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpServerConfig config;
    private final List<AgentTool> tools;
    private final Map<String, AgentTool> toolMap;

    public McpRequestHandler(McpServerConfig config, List<AgentTool> tools) {
        this.config = config;
        this.tools = List.copyOf(tools);
        this.toolMap = this.tools.stream().collect(Collectors.toMap(AgentTool::name, t -> t));
    }

    /**
     * 处理入站 JSON-RPC 请求。
     *
     * @param request 入站请求
     * @return 响应
     */
    public JsonRpc.Response handle(JsonRpc.Request request) {
        return switch (request.method()) {
            case McpMethod.INITIALIZE -> handleInitialize(request);
            case McpMethod.PING -> handlePing(request);
            case McpMethod.TOOLS_LIST -> handleToolsList(request);
            case McpMethod.TOOLS_CALL -> handleToolsCall(request);
            default -> JsonRpc.Response.error(request.id(),
                    JsonRpc.Error.of(McpError.METHOD_NOT_FOUND, "Unknown method: " + request.method()));
        };
    }

    private JsonRpc.Response handleInitialize(JsonRpc.Request request) {
        var result = MAPPER.createObjectNode();
        result.put("protocolVersion", "2024-11-05");

        var serverInfo = MAPPER.createObjectNode();
        serverInfo.put("name", config.serverName());
        serverInfo.put("version", config.serverVersion());
        result.set("serverInfo", serverInfo);

        var capabilities = MAPPER.createObjectNode();
        var toolsCap = MAPPER.createObjectNode();
        capabilities.set("tools", toolsCap);
        result.set("capabilities", capabilities);

        return JsonRpc.Response.success(request.id(), result);
    }

    private JsonRpc.Response handlePing(JsonRpc.Request request) {
        return JsonRpc.Response.success(request.id(), MAPPER.createObjectNode());
    }

    private JsonRpc.Response handleToolsList(JsonRpc.Request request) {
        var result = MAPPER.createObjectNode();
        ArrayNode toolsArray = MAPPER.createArrayNode();

        for (var tool : tools) {
            toolsArray.add(MAPPER.valueToTree(McpToolDefinitionMapper.toMcpToolSchema(tool)));
        }
        result.set("tools", toolsArray);

        return JsonRpc.Response.success(request.id(), result);
    }

    @SuppressWarnings("unchecked")
    private JsonRpc.Response handleToolsCall(JsonRpc.Request request) {
        var params = request.params();
        if (params == null || !params.has("name")) {
            return JsonRpc.Response.error(request.id(),
                    JsonRpc.Error.of(McpError.INVALID_PARAMS, "Missing 'name' in params"));
        }

        var toolName = params.get("name").asText();
        var tool = toolMap.get(toolName);
        if (tool == null) {
            return JsonRpc.Response.error(request.id(),
                    JsonRpc.Error.of(McpError.INVALID_PARAMS, "Unknown tool: " + toolName));
        }

        try {
            var arguments = params.has("arguments")
                    ? MAPPER.convertValue(params.get("arguments"), Map.class)
                    : Map.<String, Object>of();
            var context = new ToolContext(Path.of("."));
            ToolResult toolResult = tool.execute(arguments, context);

            return JsonRpc.Response.success(request.id(), buildCallResult(toolResult));
        } catch (Exception e) {
            log.warn("[McpServer] 工具 {} 执行失败: {}", toolName, e.getMessage());
            return JsonRpc.Response.success(request.id(), buildErrorResult(e.getMessage()));
        }
    }

    private JsonNode buildCallResult(ToolResult toolResult) {
        ObjectNode result = MAPPER.createObjectNode();
        ArrayNode content = MAPPER.createArrayNode();

        ObjectNode item = MAPPER.createObjectNode();
        item.put("type", "text");
        item.put("text", toolResult.output());
        content.add(item);

        result.set("content", content);
        result.put("isError", toolResult.isError());
        return result;
    }

    private JsonNode buildErrorResult(String message) {
        ObjectNode result = MAPPER.createObjectNode();
        ArrayNode content = MAPPER.createArrayNode();

        ObjectNode item = MAPPER.createObjectNode();
        item.put("type", "text");
        item.put("text", message);
        content.add(item);

        result.set("content", content);
        result.put("isError", true);
        return result;
    }
}
