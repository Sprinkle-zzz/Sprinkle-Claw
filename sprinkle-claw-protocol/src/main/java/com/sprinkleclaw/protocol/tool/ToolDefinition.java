package com.sprinkleclaw.protocol.tool;

import java.util.Map;

/**
 * 工具定义，描述一个可供 LLM 调用的工具的名称、用途和输入参数格式。
 *
 * @param name        工具名称（唯一标识）
 * @param description 工具描述（展示给 LLM，帮助其决策何时使用）
 * @param inputSchema 输入参数的 JSON Schema 定义
 *
 * @author sprinkle
 * @since 2026/3/17
 */
public record ToolDefinition(String name, String description, Map<String, Object> inputSchema) {
    /**
     * 快捷创建工具定义。
     *
     * @param name        工具名称
     * @param description 工具描述
     * @param schema      输入参数 JSON Schema
     * @return 工具定义实例
     */
    public static ToolDefinition of(String name, String description, Map<String, Object> schema) {
        return new ToolDefinition(name, description, schema);
    }
}
