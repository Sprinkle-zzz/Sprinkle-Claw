package com.sprinkleclaw.bootstrap;

import com.sprinkleclaw.core.AgentConfig;
import com.sprinkleclaw.core.context.AgentContext;
import com.sprinkleclaw.core.context.ContextManager;
import com.sprinkleclaw.core.context.TokenEstimator;
import com.sprinkleclaw.core.context.MicroCompactor;
import com.sprinkleclaw.core.context.PruneCompactor;
import com.sprinkleclaw.core.context.AutoCompactor;
import com.sprinkleclaw.core.loop.*;
import com.sprinkleclaw.core.loop.ToolOutputTruncator;
import com.sprinkleclaw.core.observability.AgentMetrics;
import com.sprinkleclaw.core.observability.AgentTracer;
import com.sprinkleclaw.core.prompt.SystemPromptBuilder;
import com.sprinkleclaw.core.session.SessionManager;
import com.sprinkleclaw.core.session.SessionStore;
import com.sprinkleclaw.core.snapshot.FileTimestampCache;
import com.sprinkleclaw.core.snapshot.FileSnapshot;
import com.sprinkleclaw.core.snapshot.GitFileSnapshot;
import com.sprinkleclaw.core.snapshot.NoopFileSnapshot;
import com.sprinkleclaw.core.snapshot.SnapshotException;
import com.sprinkleclaw.llm.LlmConfig;
import com.sprinkleclaw.llm.LlmProvider;
import com.sprinkleclaw.llm.LlmProviderFactory;
import com.sprinkleclaw.mcp.config.McpServerConfig;
import com.sprinkleclaw.mcp.lifecycle.McpServerRegistry;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolPolicy;
import com.sprinkleclaw.tool.ToolProvider;
import com.sprinkleclaw.tool.ToolRegistry;
import com.sprinkleclaw.tool.annotation.AnnotatedToolAdapter;
import com.sprinkleclaw.tool.error.ToolErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * Agent 流式构建器，用于组装和配置 {@link Claw} 实例。
 *
 * <h3>组装流程</h3>
 * <ol>
 *   <li>通过 {@link java.util.ServiceLoader} 发现 {@link LlmProviderFactory} 创建 LLM 提供者</li>
 *   <li>收集所有工具：手动注册 + {@code @Tool} 注解扫描 + ServiceLoader 发现 {@link ToolProvider}</li>
 *   <li>构建 {@link AgentConfig}、{@link AgentContext}、{@link AgentLoop}</li>
 * </ol>
 *
 * <pre>
 * Claw agent = ClawBuilder.create()
 *     .apiKey("sk-...")
 *     .model("deepseek-v4-flash")
 *     .workingDirectory(Path.of("/project"))
 *     .addTool(new MyCustomTool())
 *     .build();
 * </pre>
 *
 * @author sprinkle
 * @since 2026/3/20
 */
@com.sprinkleclaw.api.Stable
public final class ClawBuilder {

    private static final Logger log = LoggerFactory.getLogger(ClawBuilder.class);

    private String apiKey;
    private String model;
    private String baseUrl;
    private String providerId;
    private Path workingDirectory;
    private Path skillsDirectory;
    private Path tasksDirectory;
    private String systemPrompt = "";
    private int maxIterations = 200;
    private Duration loopTimeout = Duration.ofMinutes(30);
    private Duration toolTimeout = Duration.ofSeconds(120);
    private final List<AgentTool> tools = new ArrayList<>();
    private final List<Object> annotatedToolHolders = new ArrayList<>();
    private final List<LoopHook> hooks = new ArrayList<>();
    private final List<String> blockedCommands = new ArrayList<>();
    private final Map<String, String> customHeaders = new java.util.LinkedHashMap<>();
    private LlmProvider llmProvider;
    private ToolPolicy toolPolicy;
    private ToolErrorHandler toolErrorHandler;
    private AgentErrorHandler agentErrorHandler;
    private AgentMetrics metrics;
    private AgentTracer tracer;
    private SessionStore sessionStore;
    private int autoSaveInterval = 5;
    private int compactionThreshold = 100_000;
    private int modelContextWindow = 0;
    private boolean enableFileTools = false;
    private boolean enableBashTool = false;
    private boolean enableManualCompact = false;
    private boolean enableTodoWrite = false;
    private int todoNagThreshold = 3;
    private boolean enableFileSnapshot = false;
    // MVP3 extension flags
    private boolean enableSubAgent = false;
    private boolean enableSkill = false;
    private boolean enableTaskBoard = false;
    private boolean enableBackgroundTasks = false;
    private String identityPrompt = "";
    private final List<McpServerConfig> mcpServers = new ArrayList<>();
    // MVP8: Skill 编程式注册
    private final List<SkillSpec> programmaticSkills = new ArrayList<>();
    // MVP8: TaskStore 可注入
    private Object customTaskStore;
    // MVP8: 长期记忆
    private Object memoryStore;

    record SkillSpec(String name, String description, List<String> tags, String body) {
    }

    ClawBuilder() {
    }

    /**
     * 设置 API 密钥（也可通过环境变量 ANTHROPIC_API_KEY 提供）。
     */
    public ClawBuilder apiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    /**
     * 设置模型名称（如 "claude-opus-4-7"）。
     */
    public ClawBuilder model(String model) {
        this.model = model;
        return this;
    }

    /**
     * 设置 API 基础 URL（用于自定义代理或兼容端点）。
     */
    public ClawBuilder baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    /**
     * 指定 LLM 提供者 ID（不设置时根据模型名称自动检测）。
     */
    public ClawBuilder providerId(String providerId) {
        this.providerId = providerId;
        return this;
    }

    /**
     * 设置工作目录。
     */
    public ClawBuilder workingDirectory(Path dir) {
        this.workingDirectory = dir;
        return this;
    }

    /**
     * 设置工作目录（{@link #workingDirectory(Path)} 的简写）。
     */
    public ClawBuilder workdir(Path dir) {
        return workingDirectory(dir);
    }

    /**
     * 设置 Skill 扫描目录。仅当 {@link #enableSkill()} 启用且未通过 {@link #addSkill} 编程式注册时必填。
     */
    public ClawBuilder skillsDirectory(Path dir) {
        this.skillsDirectory = dir;
        return this;
    }

    /**
     * 设置 TaskBoard 持久化目录。仅当 {@link #enableTaskBoard()} 启用且未注入自定义 TaskStore 时必填。
     */
    public ClawBuilder tasksDirectory(Path dir) {
        this.tasksDirectory = dir;
        return this;
    }

    /**
     * 设置自定义系统提示词。
     */
    public ClawBuilder systemPrompt(String prompt) {
        this.systemPrompt = prompt;
        return this;
    }

    /**
     * 设置最大循环迭代次数。
     */
    public ClawBuilder maxIterations(int max) {
        this.maxIterations = max;
        return this;
    }

    /**
     * 设置循环总超时时间。
     */
    public ClawBuilder loopTimeout(Duration timeout) {
        this.loopTimeout = timeout;
        return this;
    }

    /**
     * 设置单个工具执行的超时时间。
     */
    public ClawBuilder toolTimeout(Duration timeout) {
        this.toolTimeout = timeout;
        return this;
    }

    /**
     * 添加一个工具。
     */
    public ClawBuilder addTool(AgentTool tool) {
        this.tools.add(tool);
        return this;
    }

    /**
     * 批量添加工具。
     */
    public ClawBuilder addTools(Collection<? extends AgentTool> tools) {
        this.tools.addAll(tools);
        return this;
    }

    /**
     * 添加包含 {@link com.sprinkleclaw.tool.annotation.Tool @Tool} 注解方法的对象。
     */
    public ClawBuilder addAnnotatedTools(Object holder) {
        this.annotatedToolHolders.add(holder);
        return this;
    }

    /**
     * 添加循环生命周期钩子。
     */
    public ClawBuilder addHook(LoopHook hook) {
        this.hooks.add(hook);
        return this;
    }

    /**
     * 添加一个被禁止的命令。
     */
    public ClawBuilder blockCommand(String command) {
        this.blockedCommands.add(command);
        return this;
    }

    /**
     * 批量添加被禁止的命令。
     */
    public ClawBuilder blockCommands(Collection<String> commands) {
        this.blockedCommands.addAll(commands);
        return this;
    }

    /**
     * 添加自定义 HTTP 请求头（用于适配不同厂商的认证差异）。
     */
    public ClawBuilder header(String name, String value) {
        this.customHeaders.put(name, value);
        return this;
    }

    /**
     * 批量添加自定义 HTTP 请求头。
     */
    public ClawBuilder headers(Map<String, String> headers) {
        this.customHeaders.putAll(headers);
        return this;
    }

    /**
     * 直接设置 LLM 提供者（跳过 ServiceLoader 发现）。
     */
    public ClawBuilder llmProvider(LlmProvider provider) {
        this.llmProvider = provider;
        return this;
    }

    /**
     * 设置工具安全策略。
     */
    public ClawBuilder toolPolicy(ToolPolicy policy) {
        this.toolPolicy = policy;
        return this;
    }

    /**
     * 设置工具错误处理器。
     */
    public ClawBuilder toolErrorHandler(ToolErrorHandler handler) {
        this.toolErrorHandler = handler;
        return this;
    }

    /**
     * 设置 Agent 循环错误处理器。
     */
    public ClawBuilder agentErrorHandler(AgentErrorHandler handler) {
        this.agentErrorHandler = handler;
        return this;
    }

    /**
     * 设置指标收集器。
     */
    public ClawBuilder metrics(AgentMetrics metrics) {
        this.metrics = metrics;
        return this;
    }

    /**
     * 设置追踪器。
     */
    public ClawBuilder tracer(AgentTracer tracer) {
        this.tracer = tracer;
        return this;
    }

    /**
     * 设置会话存储后端（启用会话持久化）。
     */
    public ClawBuilder sessionStore(SessionStore store) {
        this.sessionStore = store;
        return this;
    }

    /**
     * 设置自动保存间隔（每 N 轮保存一次，0 禁用）。
     */
    public ClawBuilder autoSaveInterval(int interval) {
        this.autoSaveInterval = interval;
        return this;
    }

    /**
     * 设置触发自动压缩的 token 阈值。
     */
    public ClawBuilder compactionThreshold(int threshold) {
        this.compactionThreshold = threshold;
        return this;
    }

    /**
     * 设置模型上下文窗口大小（用于动态阈值计算，0 表示自动检测）。
     */
    public ClawBuilder modelContextWindow(int contextWindow) {
        this.modelContextWindow = contextWindow;
        return this;
    }

    /**
     * 启用 TodoWrite 工具（自动注册 TodoWriteTool + TodoReminderHook）。
     */
    public ClawBuilder enableTodoWrite() {
        this.enableTodoWrite = true;
        return this;
    }

    /**
     * 设置 TodoWrite 提醒间隔（连续 N 轮未更新后注入提醒，默认 3）。
     */
    public ClawBuilder todoNagThreshold(int threshold) {
        this.todoNagThreshold = Math.max(1, threshold);
        return this;
    }

    /**
     * 启用文件工具（read_file + write_file + edit_file）。
     * <p>文件工具允许 Agent 读写工作目录下的文件。
     * 写操作受 {@link #toolPolicy(ToolPolicy)} 和 {@link #blockCommand(String)} 约束。</p>
     */
    public ClawBuilder enableFileTools() {
        this.enableFileTools = true;
        return this;
    }

    /**
     * 启用 Bash 命令执行工具。
     * <p><b>高风险</b>：允许 Agent 在工作目录下执行任意 shell 命令。
     * 强烈建议配合 {@link #blockCommand(String)} 限制危险命令。</p>
     */
    public ClawBuilder enableBashTool() {
        this.enableBashTool = true;
        return this;
    }

    /**
     * 一键启用全部编码相关工具（file + bash + todo + compact）。
     * <p>等效于：{@code enableFileTools().enableBashTool().enableTodoWrite().enableManualCompact()}</p>
     * <p>适用于编码 Agent 场景（类 Claude Code 体验）。</p>
     */
    public ClawBuilder enableCodingTools() {
        this.enableFileTools = true;
        this.enableBashTool = true;
        this.enableTodoWrite = true;
        this.enableManualCompact = true;
        return this;
    }

    /**
     * 启用手动压缩工具（compact）。
     * <p>允许 LLM 主动触发上下文压缩。通常用于长时间交互式会话。
     * 自动压缩（{@code ContextManager} 阈值驱动）不受此影响，始终启用。</p>
     */
    public ClawBuilder enableManualCompact() {
        this.enableManualCompact = true;
        return this;
    }

    /**
     * 启用文件快照追踪（自动创建 GitFileSnapshot + FileTimestampCache）。
     */
    public ClawBuilder enableFileSnapshot() {
        this.enableFileSnapshot = true;
        return this;
    }

    /**
     * 启用子 Agent 派生（需要 sprinkle-claw-agent-ext 在 classpath）。
     */
    public ClawBuilder enableSubAgent() {
        this.enableSubAgent = true;
        return this;
    }

    /**
     * 启用 Skill 两层加载（需要 sprinkle-claw-agent-ext 在 classpath）。
     */
    public ClawBuilder enableSkill() {
        this.enableSkill = true;
        return this;
    }

    /**
     * 启用持久化任务板（需要 sprinkle-claw-agent-ext 在 classpath）。
     */
    public ClawBuilder enableTaskBoard() {
        this.enableTaskBoard = true;
        return this;
    }

    /**
     * 启用后台任务管理（需要 sprinkle-claw-agent-ext 在 classpath）。
     */
    public ClawBuilder enableBackgroundTasks() {
        this.enableBackgroundTasks = true;
        return this;
    }

    /**
     * 设置 Agent 身份描述（压缩后自动重注入，需要 sprinkle-claw-agent-ext 在 classpath）。
     */
    public ClawBuilder identityPrompt(String prompt) {
        this.identityPrompt = prompt != null ? prompt : "";
        return this;
    }

    /**
     * 编程式注册 Skill。
     * <p>自动启用 Skill 功能（无需额外调用 {@link #enableSkill()}）。</p>
     *
     * @param name        Skill 名称（唯一标识，用于 load_skill 工具参数）
     * @param description 简短描述（注入 system prompt 供 LLM 选择）
     * @param body        Skill 完整内容（load_skill 时返回给 LLM）
     */
    public ClawBuilder addSkill(String name, String description, String body) {
        programmaticSkills.add(new SkillSpec(name, description, List.of(), body));
        return this;
    }

    /**
     * 编程式注册 Skill（带标签）。
     *
     * @param name        Skill 名称
     * @param description 简短描述
     * @param tags        标签列表
     * @param body        Skill 完整内容
     */
    public ClawBuilder addSkill(String name, String description, List<String> tags, String body) {
        programmaticSkills.add(new SkillSpec(name, description, tags, body));
        return this;
    }

    /**
     * 设置自定义 TaskStore 实现。
     * <p>需要 sprinkle-claw-agent-ext 在 classpath。默认使用 {@code FileTaskStore}。
     * 传入的对象必须实现 {@code com.sprinkleclaw.ext.task.TaskStore} 接口。</p>
     *
     * @param taskStore TaskStore 实现实例
     */
    public ClawBuilder taskStore(Object taskStore) {
        this.customTaskStore = taskStore;
        return this;
    }

    /**
     * 设置长期记忆存储。自动注册 {@code MemoryEnricherHook}，在每次 LLM 调用前检索相关记忆。
     * <p>传入的对象必须实现 {@code com.sprinkleclaw.core.memory.MemoryStore} 接口。</p>
     *
     * @param store MemoryStore 实现实例
     */
    public ClawBuilder memoryStore(Object store) {
        this.memoryStore = store;
        return this;
    }

    /**
     * 启用 MCP 客户端：连接每个配置的远程 MCP 服务器，把其工具自动加入 ToolRegistry。
     * 需要 sprinkle-claw-mcp 在 classpath。
     */
    public ClawBuilder enableMcp(List<McpServerConfig> servers) {
        if (servers != null) {
            this.mcpServers.addAll(servers);
        }
        return this;
    }

    /**
     * 添加单个 MCP 服务器（{@link #enableMcp(List)} 的简写）。
     */
    public ClawBuilder addMcpServer(McpServerConfig server) {
        if (server != null) {
            this.mcpServers.add(server);
        }
        return this;
    }

    /**
     * 构建 {@link Claw} Agent 实例。
     *
     * @return 构建好的 Agent
     * @throws IllegalStateException 若未找到匹配的 LLM 提供者
     */
    public Claw build() {
        LlmProvider provider = resolveLlmProvider();

        ToolRegistry registry = new ToolRegistry();
        discoverAndRegisterTools(registry);

        List<AutoCloseable> resources = new ArrayList<>();
        if (!mcpServers.isEmpty()) {
            if (!McpRegistrar.isAvailable()) {
                throw new IllegalStateException(
                        "enableMcp() requires sprinkle-claw-mcp on classpath.");
            }
            McpServerRegistry mcpRegistry = McpRegistrar.register(mcpServers, registry);
            resources.add(mcpRegistry);
        }

        // MVP2: 按需注册 TodoWriteTool
        if (enableTodoWrite) {
            registry.register(new com.sprinkleclaw.tool.builtin.TodoWriteTool());
            hooks.add(new TodoReminderHook(todoNagThreshold));
            log.info("已启用 TodoWrite 工具（nag 阈值: {}）", todoNagThreshold);
        }

        // MVP8: CompactTool 改为显式 opt-in
        if (enableManualCompact && compactionThreshold > 0) {
            registry.register(new com.sprinkleclaw.tool.builtin.CompactTool());
            log.info("已注册 CompactTool（压缩阈值: {}）", compactionThreshold);
        }

        // MVP8: 有编程式 Skill 时自动启用 Skill 功能
        if (!programmaticSkills.isEmpty()) {
            this.enableSkill = true;
        }

        // MVP9.13: 启用对应开关时校验目录非空（默认值已删除，避免静默落到进程 cwd）
        validateDirectories();

        AgentConfig config = AgentConfig.builder()
                .maxLoopIterations(maxIterations)
                .loopTimeout(loopTimeout)
                .workingDirectory(workingDirectory)
                .blockedCommands(blockedCommands)
                .compactionThreshold(compactionThreshold)
                .modelContextWindow(modelContextWindow)
                .persistSessions(sessionStore != null)
                .autoSaveInterval(autoSaveInterval)
                .enableTodoWrite(enableTodoWrite)
                .todoNagThreshold(todoNagThreshold)
                .enableFileSnapshot(enableFileSnapshot)
                .enableSubAgent(enableSubAgent)
                .enableSkill(enableSkill)
                .skillsDirectory(skillsDirectory)
                .enableTaskBoard(enableTaskBoard)
                .tasksDirectory(tasksDirectory)
                .enableBackgroundTasks(enableBackgroundTasks)
                .identityPrompt(identityPrompt)
                .build();

        // MVP2: 构建 FileTimestampCache 和 FileSnapshot
        FileTimestampCache timestampCache = new FileTimestampCache();
        FileSnapshot fileSnapshot = buildFileSnapshot(config);

        // 仅当启用文件类或 bash 工具时才注入 # Environment 段（cwd / platform）
        boolean includeEnvironment = enableFileTools || enableBashTool;
        String fullPrompt = SystemPromptBuilder.build(
                registry.definitions(), workingDirectory, systemPrompt, includeEnvironment);

        AgentContext context = new AgentContext(fullPrompt, config, registry.definitions());
        if (modelContextWindow > 0) {
            context.setModelContextWindow(modelContextWindow);
        }

        // MVP2: 将 FileTimestampCache 和 FileSnapshot 注入到共享 attributes 中
        context.setAttribute("fileTimestampCache", timestampCache);
        context.setAttribute("fileSnapshot", fileSnapshot);

        // MVP3+MVP8: 注册 agent-ext 扩展工具和 Hook
        List<LoopHook> extensionHooks = ExtensionRegistrar.registerExtensions(
                registry, config, provider, systemPrompt, hooks,
                programmaticSkills, customTaskStore);
        hooks.addAll(extensionHooks);

        // MVP8: 长期记忆
        if (memoryStore != null) {
            if (!(memoryStore instanceof com.sprinkleclaw.core.memory.MemoryStore ms)) {
                throw new IllegalArgumentException(
                        "memoryStore must implement com.sprinkleclaw.core.memory.MemoryStore, got: "
                                + memoryStore.getClass().getName());
            }
            hooks.add(new com.sprinkleclaw.core.memory.MemoryEnricherHook(ms));
            log.info("已启用长期记忆（MemoryEnricherHook）");
        }

        // 截断输出文件保存仅在 enableFileTools 时有意义（需 LLM 调 read_file 读取保存内容）；
        // 否则即使有 workingDirectory 也不写盘，避免污染。
        Path truncatorDir = enableFileTools ? workingDirectory : null;
        ToolOutputTruncator truncator = new ToolOutputTruncator(
                config.toolOutputMaxLines(), config.toolOutputMaxBytes(),
                config.largeOutputFileThreshold(), truncatorDir);
        ToolExecutor toolExecutor = new ToolExecutor(registry, toolPolicy, toolErrorHandler, truncator);

        // MVP2: 构建 ContextManager
        ContextManager contextManager = buildContextManager(provider, config);

        // MVP2: 构建 SessionManager
        SessionManager sessionManager = sessionStore != null
                ? new SessionManager(sessionStore, autoSaveInterval)
                : null;

        AgentLoop agentLoop = new AgentLoop(provider, toolExecutor, context,
                hooks, agentErrorHandler, metrics, tracer, contextManager, sessionManager);

        return new Claw(agentLoop, context, sessionManager, resources);
    }

    /**
     * 校验启用对应开关时所需目录已配置。
     * <p>SDK 嵌入场景默认零工具，目录字段无默认值；编码 / 文件场景必须显式提供，
     * 避免静默落到调用方进程的 cwd。</p>
     */
    private void validateDirectories() {
        boolean needsWorkingDir = enableFileTools || enableBashTool
                || enableFileSnapshot || enableBackgroundTasks;
        if (needsWorkingDir && workingDirectory == null) {
            throw new IllegalStateException(
                    "workingDirectory is required when any of enableFileTools / enableBashTool / "
                            + "enableFileSnapshot / enableBackgroundTasks is enabled. "
                            + "Call ClawBuilder.workingDirectory(Path).");
        }
        if (enableSkill && programmaticSkills.isEmpty() && skillsDirectory == null) {
            throw new IllegalStateException(
                    "enableSkill() requires either skillsDirectory(Path) or addSkill(...) (programmatic registration).");
        }
        if (enableTaskBoard && customTaskStore == null && tasksDirectory == null) {
            throw new IllegalStateException(
                    "enableTaskBoard() requires either tasksDirectory(Path) or taskStore(...) (custom store).");
        }
    }

    /**
     * 构建 FileSnapshot 实例。启用时使用 GitFileSnapshot，Git 不可用时降级。
     */
    private FileSnapshot buildFileSnapshot(AgentConfig config) {
        if (!enableFileSnapshot) {
            return NoopFileSnapshot.INSTANCE;
        }
        try {
            return new GitFileSnapshot(config.workingDirectory());
        } catch (SnapshotException e) {
            log.warn("GitFileSnapshot 初始化失败，降级为 NoopFileSnapshot: {}", e.getMessage());
            return NoopFileSnapshot.INSTANCE;
        }
    }

    /**
     * 构建 ContextManager（三层压缩调度器）。
     *
     * @return ContextManager 实例；compactionThreshold <= 0 时返回 null
     */
    private ContextManager buildContextManager(LlmProvider provider, AgentConfig config) {
        if (config.compactionThreshold() <= 0) {
            return null;
        }

        TokenEstimator estimator = new TokenEstimator();
        java.util.Set<String> protectedTools = config.protectedTools();
        MicroCompactor micro = new MicroCompactor(config.microCompactKeepRecent(), estimator, protectedTools);

        PruneCompactor prune;
        if (config.pruneProtectTokens() > 0 && config.pruneMinimumTokens() > 0) {
            prune = new PruneCompactor(config.pruneProtectTokens(),
                    config.pruneMinimumTokens(), estimator, protectedTools);
        } else if (config.modelContextWindow() > 0) {
            prune = new PruneCompactor(config.modelContextWindow(), estimator, protectedTools);
        } else {
            // 使用默认值（假设 200K 上下文窗口）
            prune = new PruneCompactor(200_000, estimator, protectedTools);
        }

        AutoCompactor auto = new AutoCompactor(provider, estimator);

        return new ContextManager(estimator, micro, prune, auto,
                config.compactionThreshold(), hooks);
    }

    /**
     * 解析 LLM 提供者：优先使用手动设置，否则通过 ServiceLoader 自动发现。
     */
    private LlmProvider resolveLlmProvider() {
        if (llmProvider != null) {
            return llmProvider;
        }

        String resolvedApiKey = apiKey;
        if (resolvedApiKey == null) {
            resolvedApiKey = System.getenv("ANTHROPIC_API_KEY");
        }
        if (resolvedApiKey == null) {
            resolvedApiKey = System.getenv("OPENAI_API_KEY");
        }

        LlmConfig config = LlmConfig.builder()
                .apiKey(resolvedApiKey != null ? resolvedApiKey : "")
                .model(model != null ? model : "claude-opus-4-7")
                .baseUrl(baseUrl != null ? baseUrl : "")
                .headers(customHeaders)
                .build();

        String targetProvider = providerId != null ? providerId : detectProvider(config);

        ServiceLoader<LlmProviderFactory> loader = ServiceLoader.load(LlmProviderFactory.class);
        for (LlmProviderFactory factory : loader) {
            if (factory.providerId().equals(targetProvider)) {
                log.info("使用 LLM 提供者: {}", targetProvider);
                return factory.create(config);
            }
        }

        throw new IllegalStateException(
                "No LlmProviderFactory found for provider '" + targetProvider + "'. "
                        + "Make sure sprinkle-claw-llm-anthropic (or another provider) is on the classpath.");
    }

    /**
     * 根据模型名称前缀自动检测 LLM 提供者。
     * <p>Anthropic 模型以 "claude" 开头，其余默认走 OpenAI 兼容协议。</p>
     */
    private String detectProvider(LlmConfig config) {
        String m = config.model().toLowerCase();
        if (m.startsWith("claude")) {
            return "anthropic";
        }
        // OpenAI 原生及所有兼容 OpenAI API 的厂商模型
        // gpt-*, o1-*, deepseek-*, qwen-*, glm-*, doubao-*, 等
        return "openai";
    }

    /**
     * 收集并注册所有工具来源：手动注册 → {@code @Tool} 注解扫描 → 内置工具（显式 opt-in） → ServiceLoader（第三方）。
     */
    private void discoverAndRegisterTools(ToolRegistry registry) {
        // 1) 手动注册
        registry.registerAll(tools);

        // 2) @Tool 注解扫描
        for (Object holder : annotatedToolHolders) {
            registry.registerAll(AnnotatedToolAdapter.from(holder));
        }

        // 3) 内置工具：显式 opt-in（MVP8 改造）
        if (enableFileTools) {
            registry.register(new com.sprinkleclaw.tool.builtin.ReadFileTool());
            registry.register(new com.sprinkleclaw.tool.builtin.WriteFileTool());
            registry.register(new com.sprinkleclaw.tool.builtin.EditFileTool());
            log.info("已启用文件工具（read_file, write_file, edit_file）");
        }
        if (enableBashTool) {
            registry.register(new com.sprinkleclaw.tool.builtin.BashTool());
            log.info("已启用 Bash 工具");
        }

        // 4) ServiceLoader 发现第三方 ToolProvider（MCP、用户自定义等）
        ServiceLoader<ToolProvider> providers = ServiceLoader.load(ToolProvider.class);
        var toolContext = new com.sprinkleclaw.tool.ToolContext(workingDirectory);
        for (ToolProvider tp : providers) {
            log.debug("从 ToolProvider 发现工具: {}", tp.getClass().getName());
            registry.registerAll(tp.provideTools(toolContext));
        }

        log.info("已注册 {} 个工具", registry.size());
    }

    /**
     * 创建构建器实例（最低粒度，无任何预设）。
     *
     * @return 新的 ClawBuilder
     */
    public static ClawBuilder create() {
        return new ClawBuilder();
    }

    /**
     * 嵌入式 Chat Bot 预设：零工具 + 启用多轮会话。
     *
     * <p>适用于客服 / 审批 / 数据分析等业务嵌入场景。后续仍需调用 {@link #apiKey}、
     * {@link #model}（必填），按需 {@link #annotatedTools}、{@link #addSkill} 注册业务工具与技能。</p>
     *
     * <pre>{@code
     * Claw claw = ClawBuilder.chatBot()
     *         .apiKey(...).model(...)
     *         .systemPrompt("你是客服")
     *         .annotatedTools(new OrderTools())
     *         .build();
     * }</pre>
     *
     * @return 预配置的 ClawBuilder（已启用 InMemorySessionStore 支持 chat/resume）
     * @since 0.10.0 (MVP9)
     */
    @com.sprinkleclaw.api.Experimental("MVP9 引入；预设组合可能根据用户反馈调整")
    public static ClawBuilder chatBot() {
        return new ClawBuilder()
                .sessionStore(new com.sprinkleclaw.core.session.InMemorySessionStore());
    }

    /**
     * 编码 Agent 预设：启用文件类 + bash + todo + compact 工具 + 文件快照保护。
     *
     * <p>适用于编码助手 / 自动化运维等"agent 即应用"场景。{@code workingDirectory}
     * 必填，否则 {@link #build()} 会抛 {@link IllegalStateException}。</p>
     *
     * <pre>{@code
     * Claw claw = ClawBuilder.codingAgent(Path.of("/work/myproject"))
     *         .apiKey(...).model(...)
     *         .build();
     * }</pre>
     *
     * @param workingDirectory 工作目录
     * @return 预配置的 ClawBuilder（已 enableCodingTools + enableFileSnapshot）
     * @since 0.10.0 (MVP9)
     */
    @com.sprinkleclaw.api.Experimental
    public static ClawBuilder codingAgent(java.nio.file.Path workingDirectory) {
        return new ClawBuilder()
                .workingDirectory(workingDirectory)
                .enableCodingTools()
                .enableFileSnapshot();
    }

    /**
     * 业务 Agent 预设：零内置工具 + 启用 Skill 加载 + 多轮会话。
     *
     * <p>面向"领域知识 + 业务工具"的嵌入场景：通过 {@link #addSkill} 注入业务知识，
     * 通过 {@link #annotatedTools} 注入业务操作。</p>
     *
     * <pre>{@code
     * Claw claw = ClawBuilder.businessAgent()
     *         .apiKey(...).model(...)
     *         .addSkill("refund-policy", "退款政策", refundPolicyMd)
     *         .annotatedTools(new RefundTools())
     *         .build();
     * }</pre>
     *
     * @return 预配置的 ClawBuilder（无内置工具 + 多轮会话支持）
     * @since 0.10.0 (MVP9)
     */
    @com.sprinkleclaw.api.Experimental
    public static ClawBuilder businessAgent() {
        return new ClawBuilder()
                .sessionStore(new com.sprinkleclaw.core.session.InMemorySessionStore());
    }
}
