package icu.sprinkle.loom.ext.subagent;

import icu.sprinkle.loom.protocol.tool.ToolDefinition;
import icu.sprinkle.loom.protocol.tool.ToolResult;
import icu.sprinkle.loom.tool.AgentTool;
import icu.sprinkle.loom.tool.ToolContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * sub_agent 工具：派生子 Agent 独立执行指定任务，返回文本摘要。
 *
 * <p>子 Agent 拥有隔离的上下文（不继承当前对话历史），执行完成后仅将
 * 文本摘要返回给主 Agent，子 Agent 上下文被丢弃。</p>
 *
 * <p>适用场景：</p>
 * <ul>
 *   <li>任务独立，不需要当前对话上下文</li>
 *   <li>探索性分析或聚焦性调研</li>
 *   <li>需要隔离执行避免干扰主流程</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public final class SubAgentTool implements AgentTool {

    private final SubAgentSpawner spawner;

    public SubAgentTool(SubAgentSpawner spawner) {
        this.spawner = spawner;
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("task", Map.of(
                "type", "string",
                "description", "A detailed description of the task for the sub-agent to complete. "
                        + "Include all necessary context since the sub-agent doesn't have access "
                        + "to the current conversation."
        ));
        props.put("tools", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "Optional list of tool names the sub-agent is allowed to use. "
                        + "If empty, the sub-agent inherits the parent's tool set (minus sub_agent itself)."
        ));
        props.put("max_iterations", Map.of(
                "type", "integer",
                "description", "Maximum number of loop iterations for the sub-agent. Default: 30."
        ));
        props.put("model", Map.of(
                "type", "string",
                "description", "Optional model override for the sub-agent "
                        + "(e.g., a faster/cheaper model for simple tasks). "
                        + "If empty, inherits the parent's model."
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("task"));

        return ToolDefinition.of(
                "sub_agent",
                "Spawn a sub-agent to handle a specific task independently. "
                        + "The sub-agent runs with its own isolated context and returns a summary when done. "
                        + "Use this for tasks that can be handled independently without needing "
                        + "the current conversation context.",
                schema
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        String task = (String) input.get("task");
        if (task == null || task.isBlank()) {
            return ToolResult.error(name(), "Parameter 'task' is required");
        }

        // 提取可选工具白名单
        List<String> tools = new ArrayList<>();
        Object toolsRaw = input.get("tools");
        if (toolsRaw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    tools.add(s);
                }
            }
        }

        // 提取最大迭代次数
        int maxIterations = SubAgentConfig.DEFAULT.maxIterations();
        Object maxIterRaw = input.get("max_iterations");
        if (maxIterRaw instanceof Number n) {
            maxIterations = Math.max(1, n.intValue());
        }

        SubAgentConfig config = new SubAgentConfig(
                maxIterations,
                SubAgentConfig.DEFAULT.timeout(),
                SubAgentConfig.DEFAULT.excludedTools(),
                tools,
                SubAgentConfig.DEFAULT.systemPromptSuffix(),
                true,
                input.get("model") instanceof String m ? m : ""
        );

        SubAgentResult result = spawner.spawn(task, config);

        String output = result.summary() != null ? result.summary() : "";
        if (!result.completedNormally()) {
            output += "\n\n[Sub-agent terminated: reached iteration limit or timed out]";
        }
        output += "\n[Sub-agent stats: " + result.iterationsUsed() + " iterations, ~"
                + result.estimatedTokensUsed() + " tokens]";

        return ToolResult.success(name(), output);
    }

    @Override
    public String name() {
        return "sub_agent";
    }
}
