package com.sprinkleclaw.tool.builtin;

import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.RiskLevel;
import com.sprinkleclaw.tool.ToolContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 待办事项管理工具，让 Agent 具备结构化任务规划能力。
 *
 * <p>在执行复杂多步骤任务时，Agent 可以通过此工具创建和维护待办事项列表，
 * 保持对任务进度的跟踪。压缩后 TodoState 通过 {@code afterCompaction} 钩子
 * 注入到新上下文中。</p>
 *
 * <p>TodoState 存储在 {@code ToolContext.attributes} 中，
 * 键为 {@link TodoState#CONTEXT_KEY}。</p>
 *
 * @author sprinkle
 * @since 2026/3/26
 */
public final class TodoWriteTool implements AgentTool {

    @Override
    public ToolDefinition definition() {
        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("id", Map.of("type", "string", "description", "唯一标识符"));
        itemProps.put("content", Map.of("type", "string", "description", "任务内容描述"));
        itemProps.put("status", Map.of("type", "string",
                "enum", List.of("pending", "in_progress", "completed", "cancelled"),
                "description", "任务状态"));

        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("properties", itemProps);
        itemSchema.put("required", List.of("id", "content", "status"));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("todos", Map.of(
                "type", "array",
                "description", "待办事项列表",
                "items", itemSchema));
        props.put("merge", Map.of(
                "type", "boolean",
                "description", "是否与现有 todo 合并（true=合并更新，false=全量替换）"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("todos"));

        return ToolDefinition.of("todo_write",
                ToolDescriptions.load("todo_write",
                        "Create and manage a structured task list for tracking work progress."),
                schema);
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        Object todosRaw = input.get("todos");
        if (todosRaw == null) {
            return ToolResult.error(name(), "No todos provided");
        }

        List<TodoItem> todos = parseTodos(todosRaw);
        if (todos.isEmpty()) {
            return ToolResult.error(name(), "Empty todos list");
        }

        boolean merge = Boolean.TRUE.equals(input.get("merge"));

        TodoState current = context.getAttribute(TodoState.CONTEXT_KEY, TodoState.class);
        if (current == null) {
            current = TodoState.empty();
        }

        TodoState updated = merge ? current.merge(todos) : current.replace(todos);
        context.setAttribute(TodoState.CONTEXT_KEY, updated);

        return ToolResult.success(name(),
                "Updated " + updated.items().size() + " todo items.\n\n" + updated.formatAsText());
    }

    /**
     * 从 LLM 输入的原始 JSON 数据中解析 TodoItem 列表。
     */
    @SuppressWarnings("unchecked")
    private List<TodoItem> parseTodos(Object todosRaw) {
        if (!(todosRaw instanceof List<?> list)) {
            return List.of();
        }

        List<TodoItem> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String id = (String) map.get("id");
                String content = (String) map.get("content");
                String status = (String) map.get("status");
                if (id != null && content != null && status != null) {
                    result.add(new TodoItem(id, content, status));
                }
            }
        }
        return result;
    }

    @Override
    public boolean isConcurrencySafe() { return true; }

    @Override
    public RiskLevel riskLevel() { return RiskLevel.LOW; }
}
