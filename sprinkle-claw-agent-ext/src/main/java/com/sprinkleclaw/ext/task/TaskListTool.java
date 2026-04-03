package com.sprinkleclaw.ext.task;

import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * task_list 工具：列出任务板上的所有任务（支持状态过滤）。
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public final class TaskListTool implements AgentTool {

    private final TaskManager taskManager;

    public TaskListTool(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("status", Map.of(
                "type", "string",
                "enum", List.of("pending", "in_progress", "completed", "cancelled", "all"),
                "description", "Filter tasks by status. Default: 'all'."
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);

        return ToolDefinition.of(
                "task_list",
                "List all tasks on the persistent task board, optionally filtered by status.",
                schema
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        String statusFilter = (String) input.getOrDefault("status", "all");

        List<Task> tasks = taskManager.listAll();
        if (!"all".equals(statusFilter)) {
            tasks = tasks.stream()
                    .filter(t -> statusFilter.equals(t.status()))
                    .toList();
        }

        if (tasks.isEmpty()) {
            String msg = "all".equals(statusFilter)
                    ? "No tasks."
                    : "No tasks with status '" + statusFilter + "'.";
            return ToolResult.success(name(), msg);
        }

        var sb = new StringBuilder();
        for (Task task : tasks) {
            sb.append(task.formatAsText()).append("\n");
        }
        return ToolResult.success(name(), sb.toString().trim());
    }

    @Override
    public String name() {
        return "task_list";
    }
}
