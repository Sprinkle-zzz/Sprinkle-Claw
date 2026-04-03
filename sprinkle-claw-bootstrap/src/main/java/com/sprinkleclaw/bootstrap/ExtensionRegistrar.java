package com.sprinkleclaw.bootstrap;

import com.sprinkleclaw.core.AgentConfig;
import com.sprinkleclaw.core.loop.LoopHook;
import com.sprinkleclaw.llm.LlmProvider;
import com.sprinkleclaw.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 扩展注册器：根据 AgentConfig 配置，自动注册 agent-ext 模块的工具和 Hook。
 *
 * <p>agent-ext 是可选依赖。如果 classpath 中不存在，所有注册方法安全跳过。
 * 通过直接引用 agent-ext 类实现，编译时有依赖（optional），运行时若缺失
 * 会在 {@link #isAvailable()} 检查时返回 false。</p>
 *
 * @author sprinkle
 * @since 2026/4/4
 */
final class ExtensionRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ExtensionRegistrar.class);

    private static final boolean AGENT_EXT_AVAILABLE;

    static {
        boolean available;
        try {
            Class.forName("com.sprinkleclaw.ext.subagent.SubAgentTool");
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
        AGENT_EXT_AVAILABLE = available;
    }

    /**
     * 检查 agent-ext 模块是否在 classpath 中。
     */
    static boolean isAvailable() {
        return AGENT_EXT_AVAILABLE;
    }

    /**
     * 根据 AgentConfig 注册扩展工具。
     *
     * @param registry    工具注册表
     * @param config      Agent 配置
     * @param llmProvider LLM 提供者（SubAgent 需要）
     * @param systemPrompt 系统提示词（SubAgent 需要）
     * @param hooks       Hook 列表（SubAgent 需要用于过滤传递给子 Agent）
     * @return 需要注册的 Hook 列表
     */
    static List<LoopHook> registerExtensions(ToolRegistry registry, AgentConfig config,
                                              LlmProvider llmProvider, String systemPrompt,
                                              List<LoopHook> hooks) {
        if (!AGENT_EXT_AVAILABLE) {
            log.debug("agent-ext 不在 classpath 中，跳过扩展注册");
            return List.of();
        }

        List<LoopHook> newHooks = new ArrayList<>();

        // SubAgent
        if (config.enableSubAgent()) {
            var spawner = new com.sprinkleclaw.ext.subagent.SubAgentSpawner(
                    llmProvider, registry, config, systemPrompt, hooks);
            registry.register(new com.sprinkleclaw.ext.subagent.SubAgentTool(spawner));
            log.info("已启用 SubAgent 工具（maxIterations: {}）", config.subAgentMaxIterations());
        }

        // Skill
        if (config.enableSkill()) {
            var skillLoader = new com.sprinkleclaw.ext.skill.SkillLoader();
            skillLoader.scanDirectory(config.skillsDirectory());
            registry.register(new com.sprinkleclaw.ext.skill.LoadSkillTool(skillLoader));
            log.info("已启用 Skill 加载（目录: {}，发现 {} 个 Skill）",
                    config.skillsDirectory(), skillLoader.registry().size());
        }

        // TaskBoard
        if (config.enableTaskBoard()) {
            var taskStore = new com.sprinkleclaw.ext.task.FileTaskStore(config.tasksDirectory());
            var taskManager = new com.sprinkleclaw.ext.task.TaskManager(taskStore);
            registry.register(new com.sprinkleclaw.ext.task.TaskCreateTool(taskManager));
            registry.register(new com.sprinkleclaw.ext.task.TaskUpdateTool(taskManager));
            registry.register(new com.sprinkleclaw.ext.task.TaskListTool(taskManager));
            registry.register(new com.sprinkleclaw.ext.task.TaskGetTool(taskManager));
            log.info("已启用持久化任务板（目录: {}）", config.tasksDirectory());
        }

        // Background Tasks
        if (config.enableBackgroundTasks()) {
            var backgroundManager = new com.sprinkleclaw.ext.background.BackgroundManager(
                    config.workingDirectory(), config.backgroundTaskTimeout());
            registry.register(new com.sprinkleclaw.ext.background.BackgroundRunTool(backgroundManager));
            registry.register(new com.sprinkleclaw.ext.background.CheckBackgroundTool(backgroundManager));
            newHooks.add(new com.sprinkleclaw.ext.background.BackgroundNotificationHook(backgroundManager));
            log.info("已启用后台任务管理（超时: {}）", config.backgroundTaskTimeout());
        }

        // Identity Reinject
        if (config.identityPrompt() != null && !config.identityPrompt().isBlank()) {
            newHooks.add(new com.sprinkleclaw.ext.identity.IdentityReinjectHook(config.identityPrompt()));
            log.info("已启用身份重注入 Hook");
        }

        return newHooks;
    }
}
