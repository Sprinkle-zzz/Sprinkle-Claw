package icu.sprinkle.loom.ext.subagent;

import java.time.Duration;
import java.util.List;

/**
 * 子 Agent 执行配置。
 *
 * @param maxIterations      子 Agent 最大循环轮次
 * @param timeout            子 Agent 总超时时间
 * @param excludedTools      强制排除的工具名（默认排除 sub_agent 防递归）
 * @param allowedTools       白名单工具列表（为空表示继承父工具集排除后的剩余）
 * @param systemPromptSuffix 追加到子 Agent system prompt 的后缀
 * @param inheritConfig      是否继承父 AgentConfig 的其他设置（工作目录、封禁命令等）
 * @param modelOverride      子 Agent 使用的模型（空则继承父模型）
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public record SubAgentConfig(
        int maxIterations,
        Duration timeout,
        List<String> excludedTools,
        List<String> allowedTools,
        String systemPromptSuffix,
        boolean inheritConfig,
        String modelOverride
) {
    /**
     * 默认子 Agent 配置。
     */
    public static final SubAgentConfig DEFAULT = new SubAgentConfig(
            30,
            Duration.ofMinutes(10),
            List.of("sub_agent"),
            List.of(),
            "\nYou are a subagent. Focus on the given task and summarize your findings when done.",
            true,
            ""
    );

    /**
     * 探索型子 Agent：只读工具，适合代码分析和调研。
     * <p>限制 15 轮，5 分钟超时，排除所有写入工具。</p>
     */
    public static SubAgentConfig explore() {
        return new SubAgentConfig(
                15, Duration.ofMinutes(5),
                List.of("write_file", "edit_file", "bash", "sub_agent"),
                List.of(),
                "\nYou are an exploration subagent. Read and analyze code but do not modify anything. "
                        + "Summarize your findings concisely when done.",
                true, ""
        );
    }

    /**
     * 执行型子 Agent：完整工具集，适合独立子任务执行。
     * <p>30 轮，10 分钟超时，仅排除 sub_agent 防递归。</p>
     */
    public static SubAgentConfig execute() {
        return new SubAgentConfig(
                30, Duration.ofMinutes(10),
                List.of("sub_agent"),
                List.of(),
                "\nYou are an execution subagent. Complete the assigned task independently "
                        + "and report what you accomplished.",
                true, ""
        );
    }

    /**
     * 规划型子 Agent：只读+分析，适合设计方案和架构规划。
     * <p>限制 10 轮，5 分钟超时，排除所有执行工具。</p>
     */
    public static SubAgentConfig plan() {
        return new SubAgentConfig(
                10, Duration.ofMinutes(5),
                List.of("bash", "write_file", "edit_file", "sub_agent"),
                List.of(),
                "\nYou are a planning subagent. Analyze the codebase and create a detailed "
                        + "implementation plan. Do not execute any changes.",
                true, ""
        );
    }
}
