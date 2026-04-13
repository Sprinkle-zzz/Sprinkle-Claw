package com.sprinkleclaw.mcp.tool;

import com.sprinkleclaw.mcp.client.McpClient;
import com.sprinkleclaw.mcp.client.McpClient.McpToolInfo;
import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * 单个 MCP 远程工具的 AgentTool 适配器。
 * <p>将 MCP 工具包装为 SDK 原生 AgentTool，使其可被 AgentLoop 直接调用。</p>
 *
 * @author sprinkle
 * @since 2026/4/13
 */
public final class McpToolAdapter implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(McpToolAdapter.class);

    private final McpClient client;
    private final ToolDefinition definition;

    public McpToolAdapter(McpClient client, McpToolInfo toolInfo) {
        this.client = Objects.requireNonNull(client);
        this.definition = McpToolDefinitionMapper.toToolDefinition(toolInfo);
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        try {
            var result = client.callTool(definition.name(), input).join();
            var text = result.textContent();
            if (result.isError()) {
                return ToolResult.error(definition.name(), text);
            }
            return ToolResult.success(definition.name(), text);
        } catch (Exception e) {
            log.warn("[McpTool] 远程工具 {} 调用失败: {}", definition.name(), e.getMessage());
            return ToolResult.error(definition.name(), "MCP tool call failed: " + e.getMessage());
        }
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean isConcurrencySafe() {
        return true;
    }
}
