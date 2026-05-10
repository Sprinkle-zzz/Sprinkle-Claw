<p align="center">
  <h1 align="center">Sprinkle-Loom</h1>
  <p align="center">
    <strong>协议驱动的 Java AI Agent SDK</strong><br/>
    <em>Protocol-driven, embeddable AI Agent SDK for Java 21+</em>
  </p>
  <p align="center">
    <img src="https://img.shields.io/badge/Java-21+-blue?logo=openjdk&logoColor=white" alt="Java 21+"/>
    <img src="https://img.shields.io/badge/Build-Maven-C71A36?logo=apachemaven&logoColor=white" alt="Maven"/>
    <img src="https://img.shields.io/badge/License-MIT-green" alt="License"/>
    <img src="https://img.shields.io/badge/Status-Mvp9-blue" alt="Status"/>
  </p>
  <p align="center">
    <a href="#这是什么">这是什么</a> •
    <a href="#选你的路径">选你的路径</a> •
    <a href="#路径-a嵌入-agent-能力">路径 A：嵌入</a> •
    <a href="#路径-b构建-agent-应用">路径 B：构建</a> •
    <a href="#架构概览">架构</a> •
    <a href="#模块说明">模块</a> •
    <a href="#灵感来源">致谢</a>
  </p>
</p>

---

## 这是什么

**Sprinkle-Loom** 是一个面向 Java 生态的 AI Agent SDK。它不是又一个 Agent 应用，而是一组可独立引入的 Maven 模块——无论你想在已有项目中**嵌入** AI Agent 能力，还是从零**构建** Agent 应用，都可以按需引入。

> **设计哲学**：协议驱动 > 功能堆砌 · SDK-First > 独立应用 · 组合 > 继承 · 默认零工具

---

## 选你的路径

| 你的场景 | 选择 | 典型工具开关 |
|---|---|---|
| 在 Spring Boot 业务系统加客服 / 审批 / 数据分析 agent | **路径 A：嵌入** | 不开任何内置工具，仅注册业务 `@Tool` 和 `addSkill(...)` |
| 写编码助手 / 研究工具 / 自动化运维 agent | **路径 B：构建** | `enableCodingTools()` / `enableFileTools()` / `enableBashTool()` 等 |

**核心区别**：嵌入场景的 agent 完全活在你的业务进程里，不该读写宿主机文件、不该执行 shell；构建场景的 agent 本身就是工具，需要操纵工作区。SDK 默认零工具，所有"能动文件 / 起进程 / 写盘"的能力都是 opt-in 的——选错路径不会偷偷污染。

---

## 路径 A：嵌入 agent 能力

### A.1 最小代码示例

```java
import icu.sprinkle.loom.bootstrap.LoomBuilder;
import icu.sprinkle.loom.bootstrap.Loom;
import icu.sprinkle.loom.core.AgentResult;

try (Loom loom = LoomBuilder.create()
        .apiKey(System.getenv("DEEPSEEK_API_KEY"))
        .baseUrl("https://api.deepseek.com/v1")
        .model("deepseek-v4-flash")
        .systemPrompt("你是一个友好的助手")
        .build()) {

    AgentResult result = loom.run("你好");
    System.out.println(result.output());
}
```

零工具 / 零 cwd / 零写盘——`build()` 后的 system prompt 完全等于你传入的 `systemPrompt(...)`，不会被 SDK 注入任何 `# Environment` 段或工具规则。

### A.2 注册业务工具（`@Tool`）

```java
public class OrderTools {
    @Tool(description = "查询用户订单列表")
    public String queryOrders(
            @ToolParam(name = "userId", description = "用户 ID") String userId,
            @ToolParam(name = "limit", description = "返回数量", required = false) int limit) {
        return orderService.query(userId, limit);
    }
}

LoomBuilder.create()
    .apiKey(...).model(...)
    .annotatedTools(new OrderTools())
    .build();
```

### A.3 Spring Boot 多 model 配置

```yaml
sprinkle-loom:
  agent:                          # 全局默认（被 instance 同名字段覆盖）
    max-iterations: 50
    system-prompt: "你是助手"
  llm:
    primary: claude               # ≥2 instance 必填，1 instance 时自动作为 primary
    instances:
      claude:
        provider: anthropic
        api-key: ${ANTHROPIC_API_KEY}
        model: claude-opus-4-7
      qa-bot:
        provider: openai
        api-key: ${DEEPSEEK_API_KEY}
        base-url: https://api.deepseek.com/v1
        model: deepseek-v4-flash
        system-prompt: "你是客服机器人，只回答产品问题"   # 覆盖全局
        max-iterations: 5
```

```java
@Autowired Loom loom;                          // 注入 primary（claude）
@Autowired @Qualifier("qa-bot") Loom qaBot;
```

### A.4 流式输出（SSE 给前端）

```java
import icu.sprinkle.loom.core.loop.event.AgentEvent;
import icu.sprinkle.loom.spring.autoconfigure.LoomFluxAdapters;
import icu.sprinkle.loom.stream.FlowStreams;
import reactor.core.publisher.Flux;

FlowStreams.subscribe(loom.runStreaming("写一首关于秋天的诗"))
        .onNext(event -> {
            if (event instanceof AgentEvent.LlmToken t) {
                System.out.print(t.token());        // 逐 token 渲染
            } else if (event instanceof AgentEvent.ToolResult r) {
                System.out.printf("%n[工具结果: %s]%n%s%n", r.toolName(), r.output());
            }
        })
        .onError(Throwable::printStackTrace)
        .onComplete(() -> System.out.println("\n[完成]"))
        .start();
```

Spring WebFlux 场景可直接使用 starter 提供的 Reactor 适配器：

```java
Flux<AgentEvent> events = LoomFluxAdapters.runFlux(loom, "写一首关于秋天的诗");
```

`AgentEvent` 是 sealed interface（包含 `LlmToken` / `ThinkingToken` / `ToolStart` / `ToolResult` / `ToolEnd` / `IterationComplete` / `AgentComplete` / `AgentError` 等）。应用端面向 `Flow.Publisher<AgentEvent>` 或 `Flux<AgentEvent>` 即可，不需要感知底层是阻塞 HTTP、异步 HTTP、SSE 还是 WebSocket。底层 LLM Provider 也提供 `LlmProvider.streamChatPublisher(ChatRequest)`，用于只需要模型 token / thinking / tool input chunk 的低层场景；业务前端通常优先使用 Agent 层事件流。

### A.5 长期记忆 / 多轮会话

- `loom.chat("...")` —— 多轮对话，自动维护 context
- `loom.resume(sessionId, "...")` —— 恢复历史会话继续
- `LoomBuilder.memoryStore(...)` —— 注入 `MemoryStore` SPI，跨会话长期记忆自动注入相关条目

---

## 路径 B：构建 agent 应用

### B.1 编码 Agent 一键启用

```java
try (Loom loom = LoomBuilder.create()
        .apiKey(System.getenv("DEEPSEEK_API_KEY"))
        .baseUrl("https://api.deepseek.com/v1")
        .model("deepseek-v4-flash")
        .workingDirectory(Path.of("."))
        .enableCodingTools()                // file tools + bash + todo + compact
        .build()) {

    AgentResult result = loom.run("读取 pom.xml，告诉我项目用了哪些依赖");
    System.out.println(result.output());
}
```

`enableCodingTools()` = `enableFileTools()` + `enableBashTool()` + `enableTodoWrite()` + `enableManualCompact()` 的组合。每个都可独立 opt-in。

### B.2 构建场景专属工具

| 开关 | 作用 | 何时用 |
|---|---|---|
| `enableFileTools()` | 注册 `read_file` / `write_file` / `edit_file` 工具 | 让 LLM 直接读写工作区文件 |
| `enableBashTool()` | 注册 `bash` 工具，可配 `blockCommands(...)` | 让 LLM 跑 shell 命令；嵌入场景**禁止开启** |
| `enableFileSnapshot()` | Shadow Git 仓库追踪变更，支持 Undo/Redo | 编码 agent 的安全护网 |
| `enableTodoWrite()` | TodoWrite 工具 + Nag Reminder | 长任务里让 LLM 自己记 TODO |
| `enableBackgroundTasks()` | `background_run` 非阻塞 Virtual Thread 执行 | 跑长测试 / 编译 / 数据处理 |
| `enableSubAgent()` | 子 Agent 派生（explore / execute / plan 三种预设） | 复杂任务拆分隔离上下文 |
| `enableManualCompact()` | `compact` 工具，让 LLM 主动触发上下文压缩 | 长对话的成本优化 |

⚠️ 这些开关**仅适用于路径 B**。在嵌入场景下开它们会读写宿主进程的工作目录，污染业务环境。

### B.3 MCP 协议 / Skill / 自定义工具

- **MCP**：`enableMcp(McpServerConfig...)` 接入官方 MCP 服务器（filesystem / github / postgres 等），STDIO / SSE / Streamable-HTTP 三传输方式可选
- **Skill 加载**：`addSkill(name, description, body)` 编程式注册，或 `enableSkill() + skillsDirectory(Path)` 扫描目录加载 `SKILL.md`（YAML Frontmatter）
- **自定义 ToolPolicy**：`GlobToolPolicy` Last-match-wins 安全规则（内置敏感文件 / 危险命令拦截）

### B.4 工作流编排（多 Agent 协作）

```java
@Agent
interface Researcher {
    @SystemPrompt("你是研究员，深入分析问题")
    @UserMessage("研究 {topic} 的当前进展")
    String research(String topic);
}

@Agent
interface Writer {
    @SystemPrompt("你是作家，把研究结果写成文章")
    String compose(String findings);
}

// 六模式工作流：Sequential / Parallel / DAG / Loop / Conditional / Router
SequentialWorkflow.<String, String>builder()
    .step(researcher::research)
    .step(writer::compose)
    .build()
    .execute("AI Agent 架构");
```

---

## 核心能力（按层）

| 层 | 关键能力 |
|---|---|
| **协议层** | sealed interface 全部内容块（Text / ToolUse / Thinking / Image / Document / Audio）+ CacheControl + ToolChoice 四策略 |
| **LLM Provider** | Anthropic 原生 / OpenAI 兼容（覆盖 DeepSeek / Qwen / GLM / 豆包）/ Ollama 本地，全部支持流式 + 推理 + Prompt Caching |
| **核心引擎** | Agent Loop + Virtual Threads 工具并发 + 三层上下文压缩（Micro / Prune / Auto）+ 错误恢复矩阵（13 错误 × 8 策略）+ HITL 审批 |
| **可观测性** | `AgentMetrics` / `AgentTracer` SPI + Micrometer 桥接 + `AgentEvent` 17 种事件流式 + SSE 适配 |
| **会话与记忆** | `SessionStore`（多轮 + 持久化 + resume）+ `MemoryStore`（跨会话长期记忆 + `MemoryEnricherHook` 自动注入）|
| **构建场景扩展** | FileSnapshot（Shadow Git）+ TodoWrite + SubAgent + Skill 两层加载 + 后台任务 + 持久化任务板 |
| **协议适配** | MCP 1.1.1 官方 SDK（Client + Server 双模式 + 三传输 + 30s 健康探活）|
| **企业级网关** | API Key / JWT 认证 + Bucket4j 限流 + 多租户配额 + IP ACL + 异步审计 + Prompt 注入检测 + 输出敏感过滤 |
| **Spring Boot** | Auto-configuration + 多 model `instances` + `BeanDefinitionRegistryPostProcessor` 命名 bean 注册 + `@Qualifier` 注入 + Actuator HealthIndicator |
| **Workflow** | `@Agent` 声明式代理 + 六模式编排（Sequential / Parallel / DAG / Loop / Conditional / Router）|

更详细变更记录见 [CHANGELOG.md](CHANGELOG.md)。

---

## 架构概览

```
┌────────────────────────────────────────────────────────────────┐
│  sprinkle-loom-gateway    │  sprinkle-loom-spring-boot-starter │  可选服务层
├───────────────────────────┼────────────────────────────────────┤
│  sprinkle-loom-agent-ext  │  sprinkle-loom-workflow            │  可选扩展层
├───────────────────────────┼────────────────────────────────────┤
│  sprinkle-loom-mcp        │  sprinkle-loom-llm-ollama          │  可选适配层
├───────────────────────────┼────────────────────────────────────┤
│  sprinkle-loom-core       │  sprinkle-loom-bootstrap           │  核心引擎层
├───────────────────────────┼────────────────────────────────────┤
│  sprinkle-loom-llm-api    │  sprinkle-loom-tool-api            │  接口层
├───────────────────────────┼────────────────────────────────────┤
│                  sprinkle-loom-protocol                        │  协议层
└────────────────────────────────────────────────────────────────┘
```

**依赖规则**：上层依赖下层 · 同层不互相依赖 · 实现依赖接口 · 协议层零外部依赖

---

## 模块说明

| 模块 | 职责 | 路径 A 必需？ | 路径 B 必需？ |
|------|------|:---:|:---:|
| `sprinkle-loom-protocol` | 数据模型：Message / ContentBlock / ChatRequest / ToolDefinition | ✅ | ✅ |
| `sprinkle-loom-llm-api` | LLM Provider SPI + LlmConfig + LlmCapabilities | ✅ | ✅ |
| `sprinkle-loom-llm-anthropic` | Anthropic Claude 实现（含 Thinking） | 选一 | 选一 |
| `sprinkle-loom-llm-openai` | OpenAI 兼容（DeepSeek / Qwen / GLM / 豆包等） | 选一 | 选一 |
| `sprinkle-loom-llm-ollama` | Ollama 本地模型（NDJSON + Prompt 工具桥接） | 选一 | 选一 |
| `sprinkle-loom-tool-api` | 工具 SPI + `@Tool` 注解 + GlobToolPolicy | ✅ | ✅ |
| `sprinkle-loom-tool-builtin` | 内置工具：bash / read / write / edit / todo_write / compact | — | ✅ |
| `sprinkle-loom-core` | Agent Loop / 三层压缩 / SessionManager / FileSnapshot / 错误恢复 | ✅ | ✅ |
| `sprinkle-loom-bootstrap` | `LoomBuilder` Builder API + ServiceLoader 自动组装 | ✅ | ✅ |
| `sprinkle-loom-agent-ext` | SubAgent / Skill / 任务板 / 后台任务 / Guardrails | 部分（Skill / Guardrails）| ✅ |
| `sprinkle-loom-workflow` | `@Agent` 声明式 + 六模式工作流 | 可选 | 可选 |
| `sprinkle-loom-mcp` | MCP 协议适配（官方 SDK 1.1.1） | 可选 | ✅ |
| `sprinkle-loom-gateway` | 企业级网关（认证 / 限流 / 多租户 / ACL / 审计） | ✅（生产）| 可选 |
| `sprinkle-loom-spring-boot-starter` | Spring Boot 3.2+ 自动配置 + 多 model + Actuator | ✅（Spring）| 可选 |
| `sprinkle-loom-examples` | 示例：Minimal / CustomerService / Coding / MultiAgent / Streaming | 参考 | 参考 |

---

## 环境要求与构建

- **JDK 21+**
- **Maven 3.9+**

```bash
git clone https://github.com/Sprinkle-zzz/Sprinkle-Loom.git
cd sprinkle-loom
mvn clean install -DskipTests
```

---

## 路线图

| 阶段 | 目标 | 状态 |
|------|------|------|
| **MVP1** | Agent Loop + 工具并发 + @Tool 注解 + ToolPolicy + 5 层回退编辑 + Doom Loop 检测 + Anthropic + OpenAI 兼容 | ✅ |
| **MVP2** | 三层上下文压缩 + 会话持久化 + Token 估算 + TodoWrite + Compact + FileSnapshot | ✅ |
| **MVP3** | SubAgent + Skill 两层加载 + 持久化任务板 + 后台任务 + Guardrails + 推理模型 | ✅ |
| **MVP4** | AgentLoop 流式 + LLM 流式 + 引擎韧性（错误恢复 / Fallback / 工具分级）+ 状态持久化 + HITL 审批 | ✅ |
| **MVP5** | ToolChoice 策略 + Ollama Provider + @Agent 声明式 + 六模式 Workflow + MCP 适配 | ✅ |
| **MVP6** | 企业级网关 + Spring Boot Starter + MCP 迁移至官方 SDK | ✅ |
| **MVP7** | Prompt Caching + 多模态内容（Image / Document / Audio）+ 多模态能力声明 | ✅ |
| **MVP8** | SDK 核心清理：工具注册 opt-in + 异步 API + 双层记忆 + HttpClient 连接池统一 + 评估框架 + 示例项目 | ✅ |
| **MVP9** | SDK 定位偏离修正：`Loom` 流式门面 + Spring Boot 多 model + 默认行为审计（删 transcript 写盘 / SystemPromptBuilder 修正 / `enableExtensions` 删除 / 目录默认值删除） | ✅ |

---

## 灵感来源

- **[opencode](https://github.com/anomalyco/opencode)** — 工具安全策略（Glob 匹配）/ EditTool 多层回退 / Doom Loop 检测
- **[openclaw](https://github.com/openclaw/openclaw)** — Agent Loop 架构和工具编排思路
- **[learn-claude-code](https://github.com/shareAI-lab/learn-claude-code)** — Claude Code 原理分析
- **[LangChain4j](https://github.com/langchain4j/langchain4j)** — SPI 设计理念 / `@Tool` 注解体系 / 多厂商 LLM 抽象
- **[Claude Code](https://claude.ai/claude-code)** — 错误恢复矩阵 / ContextContributor / 权限策略分层 / Hook 聚合决策
- **[AgentScope](https://github.com/modelscope/agentscope)** — Hook 优先级 / StateModule 持久化 / Toolkit facade / 自纠正结构化输出 / 重点厂商专属 wrapper

---

## License

[MIT License](LICENSE)
