package com.sprinkleclaw.ext.task;

import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * task_update 工具：更新任务状态或描述。
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public final class TaskUpdateTool implements AgentTool {

    private final TaskManager taskManager;

    public TaskUpdateTool(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("task_id", Map.of(
                "type", "integer",
                "description", "The ID of the task to update."
        ));
        props.put("status", Map.of(
                "type", "string",
                "enum", List.of("pending", "in_progress", "completed", "cancelled"),
                "description", "New task status."
        ));
        props.put("description", Map.of(
                "type", "string",
                "description", "Updated task description."
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("task_id"));

        return ToolDefinition.of(
                "task_update",
                "Update the status or description of a task. Completing a task automatically "
                        + "removes it from dependent tasks' blocked_by list.",
                schema
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        Object rawId = input.get("task_id");
        if (rawId == null) {
            return ToolResult.error(name(), "Parameter 'task_id' is required");
        }
        int taskId = ((Number) rawId).intValue();

        String status = (String) input.get("status");
        String description = (String) input.get("description");

        if (status == null && description == null) {
            return ToolResult.error(name(), "At least one of 'status' or 'description' must be provided");
        }

        try {
            Task updated = null;
            if (status != null) {
                updated = taskManager.updateStatus(taskId, status);
            }
            if (description != null) {
                updated = taskManager.updateDescription(taskId, description);
            }
            return ToolResult.success(name(), updated != null ? updated.formatAsText() : "Updated.");
        } catch (IllegalArgumentException e) {
            return ToolResult.error(name(), e.getMessage());
        }
    }

    @Override
    public String name() {
        return "task_update";
    }
}
