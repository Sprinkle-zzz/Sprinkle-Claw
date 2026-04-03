package com.sprinkleclaw.ext.task;

import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * task_create 工具：创建持久化任务（支持依赖图）。
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public final class TaskCreateTool implements AgentTool {

    private final TaskManager taskManager;

    public TaskCreateTool(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("subject", Map.of(
                "type", "string",
                "description", "Task title / short summary."
        ));
        props.put("description", Map.of(
                "type", "string",
                "description", "Detailed task description (optional)."
        ));
        props.put("blocked_by", Map.of(
                "type", "array",
                "items", Map.of("type", "integer"),
                "description", "List of task IDs that must be completed before this task can start."
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("subject"));

        return ToolDefinition.of(
                "task_create",
                "Create a persistent task on the task board. Supports dependency tracking via blocked_by.",
                schema
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        String subject = (String) input.get("subject");
        if (subject == null || subject.isBlank()) {
            return ToolResult.error(name(), "Parameter 'subject' is required");
        }
        String description = (String) input.getOrDefault("description", "");

        List<Integer> blockedBy = List.of();
        Object rawBlockedBy = input.get("blocked_by");
        if (rawBlockedBy instanceof List<?> list) {
            blockedBy = list.stream()
                    .filter(o -> o instanceof Number)
                    .map(o -> ((Number) o).intValue())
                    .toList();
        }

        try {
            Task task = taskManager.create(subject, description, blockedBy);
            return ToolResult.success(name(),
                    "Created task #" + task.id() + ": " + task.subject());
        } catch (IllegalArgumentException e) {
            return ToolResult.error(name(), e.getMessage());
        }
    }

    @Override
    public String name() {
        return "task_create";
    }
}
