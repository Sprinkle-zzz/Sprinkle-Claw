package com.sprinkleclaw.tool;

import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;

import java.util.Map;

/**
 * Agent 工具 SPI 接口。
 * <p>实现类必须无状态且线程安全，因为 {@code ToolExecutor} 会通过虚拟线程并发执行多个工具。</p>
 *
 * @author sprinkle
 * @since 2026/3/18
 */
public interface AgentTool {

    /**
     * 获取工具定义（名称、描述、输入 JSON Schema）。
     *
     * @return 工具定义
     */
    ToolDefinition definition();

    /**
     * 执行工具调用。
     *
     * @param input   LLM 提供的输入参数
     * @param context 工具执行上下文（包含工作目录等信息）
     * @return 工具执行结果
     */
    ToolResult execute(Map<String, Object> input, ToolContext context);

    /**
     * 获取工具名称（默认从 {@link #definition()} 中获取）。
     *
     * @return 工具名称
     */
    default String name() {
        return definition().name();
    }
}
