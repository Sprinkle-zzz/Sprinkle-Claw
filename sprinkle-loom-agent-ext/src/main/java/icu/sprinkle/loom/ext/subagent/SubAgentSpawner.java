package icu.sprinkle.loom.ext.subagent;

import icu.sprinkle.loom.core.AgentConfig;
import icu.sprinkle.loom.core.AgentResult;
import icu.sprinkle.loom.core.context.AgentContext;
import icu.sprinkle.loom.core.context.TokenEstimator;
import icu.sprinkle.loom.core.loop.AgentLoop;
import icu.sprinkle.loom.core.loop.LoopHook;
import icu.sprinkle.loom.core.loop.TodoReminderHook;
import icu.sprinkle.loom.core.loop.ToolExecutor;
import icu.sprinkle.loom.ext.background.BackgroundNotificationHook;
import icu.sprinkle.loom.llm.LlmProvider;
import icu.sprinkle.loom.protocol.llm.StopReason;
import icu.sprinkle.loom.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 子 Agent 派生器，负责构建并运行隔离的子 Agent。
 *
 * <p>子 Agent 拥有独立的 {@link AgentLoop}、{@link AgentContext} 和 LoopGuard，
 * 执行完成后仅将文本摘要返回给主 Agent，子上下文被丢弃。</p>
 *
 * <h3>安全机制</h3>
 * <ul>
 *   <li>强制排除 {@code sub_agent} 工具，防止无限递归</li>
 *   <li>独立 LoopGuard 控制轮次和超时上限</li>
 *   <li>上下文完全隔离，子 Agent 不继承父 Agent 消息历史</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public final class SubAgentSpawner {

    private static final Logger log = LoggerFactory.getLogger(SubAgentSpawner.class);

    private static final TokenEstimator TOKEN_ESTIMATOR = new TokenEstimator();

    private final LlmProvider llm;
    private final ToolRegistry parentToolRegistry;
    private final AgentConfig parentConfig;
    private final String parentSystemPrompt;
    private final List<LoopHook> parentHooks;

    /**
     * 创建子 Agent 派生器。
     *
     * @param llm                父 Agent 的 LLM 提供者（共享复用）
     * @param parentToolRegistry 父 Agent 的工具注册表（用于过滤后传给子 Agent）
     * @param parentConfig       父 Agent 配置（继承部分设置）
     * @param parentSystemPrompt 父 Agent 系统提示词（子 Agent 以此为基础追加后缀）
     * @param parentHooks        父 Agent 生命周期 Hook 列表（子 Agent 兼容的 Hook 会被继承）
     */
    public SubAgentSpawner(LlmProvider llm,
                           ToolRegistry parentToolRegistry,
                           AgentConfig parentConfig,
                           String parentSystemPrompt,
                           List<LoopHook> parentHooks) {
        this.llm = llm;
        this.parentToolRegistry = parentToolRegistry;
        this.parentConfig = parentConfig;
        this.parentSystemPrompt = parentSystemPrompt != null ? parentSystemPrompt : "";
        this.parentHooks = parentHooks != null ? parentHooks : List.of();
    }

    /**
     * 派生子 Agent 执行指定任务。
     *
     * @param prompt 子任务描述（子 Agent 从此开始对话）
     * @param config 子 Agent 配置（null 时使用 {@link SubAgentConfig#DEFAULT}）
     * @return 子 Agent 执行结果摘要
     */
    public SubAgentResult spawn(String prompt, SubAgentConfig config) {
        if (config == null) {
            config = SubAgentConfig.DEFAULT;
        }

        log.info("Spawning sub-agent: maxIterations={}, allowedTools={}, excludedTools={}",
                config.maxIterations(), config.allowedTools(), config.excludedTools());

        try {
            // 1. 构建子 Agent 工具集
            ToolRegistry childRegistry = buildChildToolRegistry(config);

            // 2. 构建子 Agent 配置
            AgentConfig childConfig = buildChildConfig(config);

            // 3. 构建子 Agent 上下文（完全隔离，从空消息列表开始）
            String childSystemPrompt = parentSystemPrompt + config.systemPromptSuffix();
            AgentContext childContext = new AgentContext(
                    childSystemPrompt, childConfig, childRegistry.definitions());
            childContext.appendUserMessage(prompt);

            // 4. 过滤 Hook：仅保留与子 Agent 兼容的 Hook
            List<LoopHook> childHooks = filterHooksForChild(parentHooks);

            // 5. 构建子 Agent 执行器
            ToolExecutor childToolExecutor = new ToolExecutor(childRegistry, null);

            // 6. 构建并运行子 AgentLoop（不启用会话管理和上下文压缩）
            AgentLoop childLoop = new AgentLoop(
                    llm, childToolExecutor, childContext, childHooks,
                    null, null, null, null, null);

            AgentResult result = childLoop.run();

            // 7. 估算消耗的 token 数
            int estimatedTokens = TOKEN_ESTIMATOR.estimate(childContext.messages());

            boolean completedNormally = result.stopReason() == StopReason.END_TURN;
            log.info("Sub-agent completed: iterations={}, completedNormally={}",
                    result.totalIterations(), completedNormally);

            return new SubAgentResult(
                    result.output(),
                    result.totalIterations(),
                    estimatedTokens,
                    completedNormally
            );

        } catch (Exception e) {
            log.error("Sub-agent failed with exception", e);
            return new SubAgentResult(
                    "[Sub-agent failed: " + e.getMessage() + "]",
                    0, 0, false
            );
        }
    }

    /**
     * 根据配置构建子 Agent 工具注册表。
     *
     * <p>始终排除 {@code sub_agent} 工具防止递归，支持白名单和黑名单两种过滤模式。</p>
     */
    private ToolRegistry buildChildToolRegistry(SubAgentConfig config) {
        Set<String> excluded = new HashSet<>(config.excludedTools());
        excluded.add("sub_agent"); // 强制排除，防止递归

        if (!config.allowedTools().isEmpty()) {
            // 白名单模式：仅保留指定工具（同时排除 sub_agent）
            List<String> allowed = config.allowedTools().stream()
                    .filter(t -> !excluded.contains(t))
                    .toList();
            return parentToolRegistry.copyWithOnly(allowed);
        } else {
            // 黑名单模式：排除指定工具
            return parentToolRegistry.copyWithout(excluded);
        }
    }

    /**
     * 构建子 Agent 配置，安全禁用递归功能，选择性继承父配置。
     */
    private AgentConfig buildChildConfig(SubAgentConfig config) {
        AgentConfig.Builder builder = AgentConfig.builder()
                .maxLoopIterations(config.maxIterations())
                .loopTimeout(config.timeout())
                // 子 Agent 强制禁用递归功能
                .enableSubAgent(false)
                // 子 Agent 不启用 TaskBoard（避免副作用）
                .enableTaskBoard(false)
                // 子 Agent 不启用 BackgroundTasks（简化生命周期）
                .enableBackgroundTasks(false)
                // 子 Agent 不启用会话持久化
                .persistSessions(false);

        if (config.inheritConfig()) {
            // 继承父配置的通用设置
            builder.workingDirectory(parentConfig.workingDirectory())
                    .blockedCommands(parentConfig.blockedCommands())
                    .compactionThreshold(parentConfig.compactionThreshold())
                    .toolOutputMaxLines(parentConfig.toolOutputMaxLines())
                    .toolOutputMaxBytes(parentConfig.toolOutputMaxBytes())
                    .doomLoopThreshold(parentConfig.doomLoopThreshold());
        }

        return builder.build();
    }

    /**
     * 过滤父 Agent Hook，移除不适合子 Agent 的 Hook。
     *
     * <p>排除的 Hook 类型：</p>
     * <ul>
     *   <li>{@link TodoReminderHook}：子 Agent 不需要维护 Todo 列表</li>
     *   <li>{@link BackgroundNotificationHook}：子 Agent 不处理后台通知</li>
     * </ul>
     */
    private List<LoopHook> filterHooksForChild(List<LoopHook> hooks) {
        return hooks.stream()
                .filter(h -> !(h instanceof TodoReminderHook))
                .filter(h -> !(h instanceof BackgroundNotificationHook))
                .toList();
    }
}
