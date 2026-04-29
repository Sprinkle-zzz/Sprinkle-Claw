package icu.sprinkle.loom.ext.task;

import icu.sprinkle.loom.protocol.tool.ToolDefinition;
import icu.sprinkle.loom.protocol.tool.ToolResult;
import icu.sprinkle.loom.tool.AgentTool;
import icu.sprinkle.loom.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * task_get 工具：获取单个任务的详细信息。
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public final class TaskGetTool implements AgentTool {

    private final TaskManager taskManager;

    public TaskGetTool(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("task_id", Map.of(
                "type", "integer",
                "description", "The ID of the task to retrieve."
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("task_id"));

        return ToolDefinition.of(
                "task_get",
                "Get the details of a specific task by its ID.",
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

        return taskManager.get(taskId)
                .map(task -> ToolResult.success(name(), task.formatAsText()))
                .orElse(ToolResult.error(name(), "Task #" + taskId + " not found."));
    }

    @Override
    public String name() {
        return "task_get";
    }
}
