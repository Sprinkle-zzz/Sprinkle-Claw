package icu.sprinkle.loom.ext.background;

import icu.sprinkle.loom.protocol.tool.ToolDefinition;
import icu.sprinkle.loom.protocol.tool.ToolResult;
import icu.sprinkle.loom.tool.AgentTool;
import icu.sprinkle.loom.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * check_background 工具：查询后台任务状态或取消运行中的任务。
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public final class CheckBackgroundTool implements AgentTool {

    private final BackgroundManager bgManager;

    public CheckBackgroundTool(BackgroundManager bgManager) {
        this.bgManager = bgManager;
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("task_id", Map.of(
                "type", "string",
                "description", "The ID of a specific background task to check. If omitted, lists all tasks."
        ));
        props.put("cancel", Map.of(
                "type", "boolean",
                "description", "If true and task_id is provided, cancel the running task. Default: false."
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);

        return ToolDefinition.of(
                "check_background",
                "Check the status of background tasks, or cancel a running task. "
                        + "Check a specific task by ID, cancel it, or list all tasks.",
                schema
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        String taskId = (String) input.get("task_id");
        boolean cancel = Boolean.TRUE.equals(input.get("cancel"));

        // 取消任务
        if (cancel && taskId != null) {
            boolean cancelled = bgManager.cancel(taskId);
            return cancelled
                    ? ToolResult.success(name(), "Background task " + taskId + " cancelled.")
                    : ToolResult.error(name(),
                    "Cannot cancel task " + taskId + " (not found or not running).");
        }

        // 查询指定任务
        if (taskId != null) {
            return bgManager.get(taskId)
                    .map(task -> ToolResult.success(name(), task.formatSummary(2000)))
                    .orElse(ToolResult.error(name(), "Unknown background task: " + taskId));
        }

        // 列出所有任务
        List<BackgroundTask> allTasks = bgManager.listAll();
        if (allTasks.isEmpty()) {
            return ToolResult.success(name(), "No background tasks.");
        }

        var sb = new StringBuilder();
        sb.append("Background tasks (").append(allTasks.size()).append("):\n\n");
        for (BackgroundTask task : allTasks) {
            sb.append(task.formatSummary(200)).append("\n\n");
        }
        return ToolResult.success(name(), sb.toString().trim());
    }

    @Override
    public String name() {
        return "check_background";
    }
}
