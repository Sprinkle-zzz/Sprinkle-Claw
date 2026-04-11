<p align="center">
  <h1 align="center">🦀 Sprinkle-Claw</h1>
  <p align="center">
    <strong>协议驱动的 Java AI Agent SDK</strong><br/>
    <em>Protocol-driven, embeddable AI Agent SDK for Java 21+</em>
  </p>
  <p align="center">
    <img src="https://img.shields.io/badge/Java-21+-blue?logo=openjdk&logoColor=white" alt="Java 21+"/>
    <img src="https://img.shields.io/badge/Build-Maven-C71A36?logo=apachemaven&logoColor=white" alt="Maven"/>
    <img src="https://img.shields.io/badge/License-MIT-green" alt="License"/>
    <img src="https://img.shields.io/badge/Status-MVP4-orange" alt="Status"/>
  </p>
  <p align="center">
    <a href="#核心特性">核心特性</a> •
    <a href="#快速开始">快速开始</a> •
    <a href="#架构概览">架构概览</a> •
    <a href="#模块说明">模块说明</a> •
    <a href="#路线图">路线图</a> •
    <a href="#灵感来源">灵感来源</a>
  </p>
</p>

---

## 这是什么？

**Sprinkle-Claw** 是一个面向 Java 生态的 AI Agent SDK。它不是又一个 Agent 应用，而是一组可独立引入的 Maven 模块 —— 无论你想在已有项目中嵌入 AI Agent 能力，还是从零构建 Agent 应用，都可以按需引入。

> **设计哲学**：协议驱动 > 功能堆砌 · SDK-First > 独立应用 · 组合 > 继承

---

## 核心特性

### ✅ 已实现

| 特性                       | 说明                                                                                        |
|--------------------------|-------------------------------------------------------------------------------------------|
| **Agent Loop**           | 模型自主决策的核心循环：LLM → 工具调用 → 结果反馈 → LLM → ... 直到完成                                            |
| **Virtual Threads 工具并发** | JDK 21 Virtual Threads + Structured Concurrency，工具调用真正并行执行                                |
| **双模式工具定义**              | `@Tool` 注解（零配置）+ `AgentTool` SPI 接口（精细控制），自动生成 JSON Schema                                |
| **动态工具提供**               | `ToolProvider` SPI 运行时按上下文决定可用工具集                                                         |
| **5 层回退编辑**              | `EditFileTool` 支持精确匹配 → 行Trim → 空白归一化 → 缩进弹性 → Levenshtein 模糊锚定                           |
| **工具安全策略**               | `GlobToolPolicy` 基于 Glob 模式的 Last-match-wins 安全规则（内置敏感文件/危险命令拦截）                          |
| **工具执行拦截**               | `LoopHook.beforeToolExecution()` 钩子，支持 Continue / Skip / Modify 三种拦截决策                    |
| **循环安全保护**               | `LoopGuard` 最大迭代 + 超时 + 重复响应检测 + Doom Loop 滑动窗口检测                                         |
| **工具输出截断**               | `ToolOutputTruncator` 超长输出自动保存文件 + 截断预览，避免 token 浪费                                       |
| **错误恢复**                 | `ToolErrorHandler` 工具级 + `AgentErrorHandler` Loop 级，支持重试/替代/传播策略                          |
| **可观测性**                 | `AgentMetrics` 指标 + `AgentTracer` 链路追踪，SPI 可接入 Micrometer / OpenTelemetry                 |
| **多厂商 LLM**              | Anthropic Claude + OpenAI 兼容协议（DeepSeek、Qwen、GLM、豆包等），切模型只需换 baseUrl                      |
| **SPI 插件体系**             | Provider / Tool / Hook / Metrics / Tracer 全部通过 Java SPI，编译期类型安全                           |
| **三层上下文压缩**              | Micro（占位符替换）→ Prune（动态阈值裁剪）→ Auto（LLM 结构化摘要），API usage 精确溢出判断                             |
| **会话持久化**                | SessionStore SPI（File / InMemory），原子写入，Jackson Mixin 多态序列化，自动保存 + resume                  |
| **Token 估算**             | CJK 自适应 token 估算，上下文使用率感知的动态工具输出截断（50KB→20KB→10KB）                                        |
| **TodoWrite 工具**         | 结构化任务管理，Nag Reminder 机制，压缩后 todo-snapshot 自动注入                                            |
| **Compact 工具**           | 模型主动触发压缩，支持 focus 参数指定摘要焦点                                                                |
| **文件快照追踪**               | Shadow Git 仓库追踪变更，Undo/Redo 支持，文件时间戳校验防止外部修改冲突                                            |
| **多轮对话 API**             | `chat()` 连续上下文 / `resume()` 会话恢复 / `listSessions()` 会话列表                                  |
| **SubAgent 子 Agent 派生**  | 隔离上下文 + 工具过滤 + 轮次限制 + 摘要返回，内置 explore/execute/plan 三种预设                                   |
| **Skill 两层加载**           | Layer1 system prompt 元数据注入 + Layer2 `load_skill` 按需加载全文，YAML Frontmatter 解析               |
| **持久化任务板**               | `.tasks/` JSON 持久化，blockedBy/blocks 依赖图，CRUD 工具集，压缩后存活                                    |
| **后台任务管理**               | `background_run` 非阻塞 Virtual Thread 执行 + 通知队列自动 drain + 取消支持                              |
| **身份重注入**                | 上下文压缩后自动注入 `<identity>` 块恢复 Agent 身份                                                      |
| **上下文压缩增强**              | MicroCompactor 去重/错误裁剪、AutoCompactor 锚定迭代摘要、受保护工具列表、大输出转文件引用                              |
| **Token 精确估算**           | jtokkit CL100K_BASE tokenizer 集成（可选依赖，反射加载）                                               |
| **基础 Guardrails**        | InputSanitizer prompt injection 检测 + TrustBoundaryFilter 信任边界过滤                           |
| **推理模型支持**               | ThinkingConfig 泛化（Anthropic budgetTokens + OpenAI reasoningEffort）+ Usage.reasoningTokens |
| **LLM 能力声明**             | `LlmCapabilities` record + `LlmProvider.capabilities()` 默认方法                              |
| **AgentEvent 流式输出**      | 15 种事件 sealed interface + `runStreaming()` 返回 `Flow.Publisher` + SSE 适配（含 Last-Event-ID replay） |
| **LLM SSE 流式**           | Anthropic / OpenAI 兼容双实现，thinking + tool_use delta 增量累积，StreamCallback 回调分发              |
| **引擎韧性框架**               | 13 种错误分类 × 8 种恢复策略矩阵（重试/降级/压缩重试/截断重试/输出升级/挂起），FallbackProvider 主备切换                  |
| **工具行为标记**               | `isReadOnly` / `isConcurrencySafe` / `riskLevel` SPI + `@Tool` 注解扩展，内置工具分级声明                |
| **并发分级执行**               | `ConcurrencyAwareToolExecutor` 安全工具并行 + 非安全工具串行，保持原始顺序                                    |
| **预处理管线**                | `LoopPreProcessor` 5 阶段统一管线：Budget → Micro → Prune → Auto → NotificationDrain           |
| **工具定义稳定排序**             | `ToolDefinitionSorter` 内置工具固定序 + 外部按名排序，提升 LLM prompt cache 命中率                           |
| **内容哈希校验**               | `ContentHashValidator` SHA-256 双重校验，过滤云盘同步 mtime 误报                                       |
| **组件状态持久化**              | `StatePersistable` SPI + `StateManager` 收集/恢复（仅 dirty 组件）                                 |
| **HITL 异步审批**            | `ApprovalManager` SDK 回调 + Server 端点 `resolve()` 双模式，CompletableFuture 阻塞虚拟线程              |
| **Hook 优先级**             | `LoopHook.priority()` + `HookManager` 排序执行 + Skip 短路                                      |

### 📋 规划中

| 特性 | 目标阶段 |
|------|---------|
| @Agent 声明式 + Workflow 编排（5 种模式） | MVP5 |
| MCP 协议适配 + Ollama 本地模型 | MVP5 |
| REST/SSE/WebSocket（OpenAI 兼容）+ Spring Boot Starter + 企业级网关 | MVP6 |

---

## 快速开始

### 环境要求

- **JDK 21+**
- **Maven 3.9+**

### 构建项目

```bash
git clone https://github.com/Sprinkle-zzz/Sprinkle-Claw.git
cd sprinkle-claw
mvn clean install -DskipTests
```

### 代码示例

```java
import com.sprinkleclaw.bootstrap.ClawBuilder;
import com.sprinkleclaw.core.AgentResult;

var agent = ClawBuilder.create()
        .apiKey("sk-your-api-key")
        .model("deepseek-chat")
        .baseUrl("https://api.deepseek.com")
        .workdir(Path.of("."))
        .maxIterations(20)
        .enableTodoWrite()             // MVP2: 任务管理
        .compactionThreshold(100_000)  // MVP2: 自动压缩
        .enableExtensions()            // MVP3: 一键启用 SubAgent/Skill/任务板/后台任务
        .identityPrompt("你是一个 Java 架构师") // MVP3: 压缩后身份重注入
        .build();

AgentResult result = agent.run("读取 pom.xml，告诉我项目用了哪些依赖");

System.out.println(result.finalText());
System.out.printf("Token 用量: %d input / %d output%n",
        result.totalUsage().inputTokens(),
        result.totalUsage().outputTokens());
```

### 自定义工具

```java
import com.sprinkleclaw.tool.annotation.Tool;
import com.sprinkleclaw.tool.annotation.ToolParam;

public class MyTools {

    @Tool(description = "查询用户订单列表")
    public String queryOrders(
            @ToolParam(name = "userId", description = "用户ID") String userId,
            @ToolParam(name = "limit", description = "返回数量", required = false) int limit) {
        // 查询逻辑
        return "订单列表: [...]";
    }
}
```

---

## 架构概览

```
┌────────────────────────────────────────────────────────────────┐
│  sprinkle-claw-gateway    │  sprinkle-claw-spring-boot-starter │  可选服务层 (规划)
├───────────────────────────┼────────────────────────────────────┤
│  sprinkle-claw-team       │  sprinkle-claw-worktree            │  可选扩展层 (规划)
├───────────────────────────┼────────────────────────────────────┤
│  sprinkle-claw-agent-ext  │  sprinkle-claw-workflow            │  可选扩展层 (agent-ext ✅)
├───────────────────────────┼────────────────────────────────────┤
│  sprinkle-claw-core       │  sprinkle-claw-bootstrap           │  核心引擎层 ✅
├───────────────────────────┼────────────────────────────────────┤
│  sprinkle-claw-llm-api    │  sprinkle-claw-tool-api            │  接口层 ✅
├───────────────────────────┼────────────────────────────────────┤
│                  sprinkle-claw-protocol                         │  协议层 ✅
└────────────────────────────────────────────────────────────────┘
```

**依赖规则**：上层依赖下层 · 同层不互相依赖 · 实现依赖接口 · 协议层零外部依赖

---

## 模块说明

| 模块 | 职责 | 依赖 | 状态 |
|------|------|------|------|
| `sprinkle-claw-protocol` | 纯数据模型：Message、ContentBlock、ChatRequest/Response、ToolDefinition/Result | 无 | ✅ 已实现 |
| `sprinkle-claw-llm-api` | LLM Provider SPI：LlmProvider、LlmProviderFactory、LlmConfig、LlmException | protocol | ✅ 已实现 |
| `sprinkle-claw-llm-anthropic` | Anthropic Claude 实现（JDK HttpClient，支持 Thinking 模式） | llm-api | ✅ 已实现 |
| `sprinkle-claw-llm-openai` | OpenAI 兼容 API 实现（覆盖 DeepSeek、Qwen、GLM、豆包等） | llm-api | ✅ 已实现 |
| `sprinkle-claw-llm-ollama` | Ollama 本地模型实现 | llm-api | 📋 MVP5 |
| `sprinkle-claw-tool-api` | 工具 SPI：AgentTool、@Tool 注解、ToolRegistry、ToolProvider、ToolPolicy、GlobToolPolicy | protocol | ✅ 已实现 |
| `sprinkle-claw-tool-builtin` | 6 个内置工具：bash / read_file / write_file / edit_file / todo_write / compact | tool-api | ✅ 已实现 |
| `sprinkle-claw-core` | 核心引擎：AgentLoop、ContextManager（三层压缩）、SessionManager、FileSnapshot、LoopGuard、ToolExecutor | protocol, llm-api, tool-api | ✅ 已实现 |
| `sprinkle-claw-bootstrap` | Builder API + ServiceLoader 自动组装 + 会话管理 | 全部核心模块 | ✅ 已实现 |
| `sprinkle-claw-benchmark` | JMH 性能基准：工具并发 / JSON 序列化 | core | ✅ 已实现 |
| `sprinkle-claw-agent-ext` | SubAgent + Skill + 任务板 + 后台任务 + Guardrails + 身份重注入 | core | ✅ 已实现 |
| `sprinkle-claw-workflow` | @Agent 声明式 + Workflow 编排（5 种模式） | core | 📋 MVP5 |
| `sprinkle-claw-gateway` | 认证 / 限流 / 多租户 / Token 计量 | core | 📋 MVP6 |
| `sprinkle-claw-spring-boot-starter` | Spring Boot 自动配置 + REST/SSE/WebSocket（OpenAI 兼容） | core, bootstrap | 📋 MVP6 |
| `sprinkle-claw-team` | Agent 团队 + 协议 + 自主 Agent（实验性） | core, agent-ext | 📋 MVP7 |
| `sprinkle-claw-worktree` | Git Worktree 隔离 + EventBus（实验性） | core, agent-ext | 📋 MVP7 |

---

## 项目结构

```
sprinkle-claw/
├── sprinkle-claw-protocol          ← 协议层（零依赖）
├── sprinkle-claw-llm-api           ← LLM Provider SPI + LlmCapabilities
├── sprinkle-claw-llm-anthropic     ← Anthropic Claude 实现
├── sprinkle-claw-llm-openai        ← OpenAI 兼容 API 实现（含推理模型支持）
├── sprinkle-claw-tool-api          ← 工具 SPI + @Tool 注解 + GlobToolPolicy
├── sprinkle-claw-tool-builtin      ← 内置工具（bash / read / write / edit / todo_write / compact）
├── sprinkle-claw-core              ← 核心引擎（AgentLoop + 三层压缩 + 会话管理 + 文件快照）
├── sprinkle-claw-agent-ext         ← Agent 扩展（SubAgent + Skill + 任务板 + 后台任务 + Guardrails）
├── sprinkle-claw-bootstrap         ← 组装层（ClawBuilder + ServiceLoader + ExtensionRegistrar）
└── sprinkle-claw-benchmark         ← JMH 性能基准
```

---

## 技术栈

| 层次 | 选型                  |
|------|---------------------|
| JDK | 21                  |
| 构建 | Maven 3.9+          |
| JSON | Jackson 2.17+       |
| 日志 | SLF4J 2.x + Logback |
| HTTP | JDK HttpClient      |
| 测试 | JUnit 5 + Mockito   |
| 基准 | JMH 1.37            |

---

## 路线图

| 阶段 | 目标 | 状态 |
|------|------|------|
| **MVP1** | Agent Loop + 工具并发 + @Tool 注解 + ToolPolicy + 5 层回退编辑 + ToolInterception 钩子 + Doom Loop 检测 + 输出截断 + Anthropic + OpenAI 兼容 | ✅ 已完成 |
| **MVP2** | 三层上下文压缩 + 会话持久化 + Token 估算 + TodoWrite + Compact + FileSnapshot | ✅ 已完成 |
| **MVP3** | SubAgent + Skill 两层加载 + 持久化任务板 + 后台任务 + Guardrails + 上下文压缩增强 + 推理模型支持 | ✅ 已完成 |
| **MVP4** | AgentLoop 流式 (AgentEvent) + LLM 流式 + 引擎韧性（错误恢复 / Fallback / 工具分级 / Hook 增强）+ 状态持久化 + HITL 审批 | ✅ 已完成 |
| **MVP5** | Ollama Provider + @Agent 声明式 + Workflow 编排 (5 模式) + MCP 协议适配 | 📋 计划中 |
| **MVP6** | REST/SSE/WebSocket（OpenAI 兼容）+ Spring Boot Starter + 企业级网关（认证/限流/多租户） | 📋 计划中 |
| **MVP7** | Agent 团队 + 团队协议 + 自主 Agent + 工作树隔离（实验性） | 📋 计划中 |
| **MVP8** | 媒体处理 + 渠道扩展 + 浏览器自动化 + 沙箱执行 + 性能优化 | 📋 计划中 |

---

## 质量保障

| 维度 | 方案 |
|------|------|
| 单元测试 | JUnit 5 + Mockito，core ≥ 80% 覆盖率 |
| SPI 契约测试 | 每个 SPI 提供抽象测试基类，实现方继承即可验证 |
| 集成测试 | Mock LLM Provider，不依赖外部服务 |
| 性能基准 | JMH 基准测试（工具并发 / JSON 序列化），每个 MVP 建立基线 |

---

## 灵感来源

本项目的设计和实现受到以下优秀开源项目的启发：

- **[opencode](https://github.com/anomalyco/opencode)** — AI 编码代理，参考了其工具安全策略（Glob 匹配）、EditTool 多层回退匹配、Doom Loop 检测、工具输出截断等设计
- **[openclaw](https://github.com/openclaw/openclaw)** — 开源 AI Agent 框架，参考了其 Agent Loop 架构和工具编排思路
- **[learn-claude-code](https://github.com/shareAI-lab/learn-claude-code)** — Claude Code 原理分析，参考了其对 AI Agent 工作流程的深入解析
- **[LangChain4j](https://github.com/langchain4j/langchain4j)** — Java LLM 集成框架，参考了其 SPI 设计理念、工具注解体系和多厂商 LLM 抽象架构
- **[Claude Code](https://claude.ai/claude-code)** — Anthropic 官方 CLI Agent，参考了其错误恢复矩阵、ContextContributor SPI、权限策略分层、Hook 聚合决策等工程实践
- **[AgentScope Java](https://github.com/modelscope/agentscope)** — 阿里多 Agent 框架，参考了其 Hook 优先级/事件分类、StateModule 泛化持久化、Toolkit facade、PlanToHint 推理前注入、自纠正结构化输出等设计

---

## License

[MIT License](LICENSE)
