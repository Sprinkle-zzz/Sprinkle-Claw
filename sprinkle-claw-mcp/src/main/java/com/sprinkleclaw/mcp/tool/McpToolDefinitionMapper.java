package com.sprinkleclaw.mcp.tool;

import com.sprinkleclaw.mcp.client.McpClient.McpToolInfo;
import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.tool.AgentTool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 工具定义与 SDK 工具定义之间的映射。
 *
 * @author sprinkle
 * @since 2026/4/13
 */
public final class McpToolDefinitionMapper {

    private McpToolDefinitionMapper() {}

    /**
     * MCP 工具信息 → SDK ToolDefinition。
     */
    public static ToolDefinition toToolDefinition(McpToolInfo info) {
        return ToolDefinition.of(info.name(), info.description(), info.inputSchema());
    }

    /**
     * SDK AgentTool → MCP 工具定义（用于服务端暴露）。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMcpToolSchema(AgentTool tool) {
        var def = tool.definition();
        var result = new LinkedHashMap<String, Object>();
        result.put("name", def.name());
        result.put("description", def.description());
        result.put("inputSchema", def.inputSchema());
        return result;
    }
}
