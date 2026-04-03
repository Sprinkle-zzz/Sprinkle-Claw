package com.sprinkleclaw.ext.subagent;

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
}
