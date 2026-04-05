package com.sprinkleclaw.ext.background;

import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * background_run 工具：在后台执行命令，立即返回任务 ID（非阻塞）。
 *
 * <p>适用于长耗时命令（如 mvn test、npm run build），Agent 可在命令运行期间继续其他工作。
 * 命令完成后结果通过通知队列自动注入上下文。</p>
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public final class BackgroundRunTool implements AgentTool {

    private final BackgroundManager bgManager;

    public BackgroundRunTool(BackgroundManager bgManager) {
        this.bgManager = bgManager;
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("command", Map.of(
                "type", "string",
                "description", "The shell command to execute in the background."
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("command"));

        return ToolDefinition.of(
                "background_run",
                "Run a command in the background without blocking. Returns immediately with a task ID. "
                        + "Use check_background to check status, or the result will be automatically "
                        + "reported when done. Use this for long-running commands like builds, tests, "
                        + "or data processing.",
                schema
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        String command = (String) input.get("command");
        if (command == null || command.isBlank()) {
            return ToolResult.error(name(), "Parameter 'command' is required");
        }

        String taskId = bgManager.run(command);
        return ToolResult.success(name(),
                "Background task " + taskId + " started.\n"
                        + "Command: " + command + "\n"
                        + "Use check_background(task_id=\"" + taskId + "\") to check status, "
                        + "or the result will be automatically reported when done.");
    }

    @Override
    public String name() {
        return "background_run";
    }
}
