<p align="center">
  <h1 align="center">Sprinkle-Claw</h1>
  <p align="center">
    协议驱动的 Java AI Agent SDK<br/>
    <em>Protocol-driven, embeddable AI Agent SDK for Java 21+</em>
  </p>
  <p align="center">
    <a href="#核心特性">核心特性</a> •
    <a href="#架构概览">架构概览</a> •
    <a href="#模块说明">模块说明</a> •
    <a href="#路线图">路线图</a>
  </p>
</p>

---

## 这是什么？

**Sprinkle-Claw** 是一个面向 Java 生态的 AI Agent SDK。它不是又一个 Agent 应用，而是一组可独立引入的 Maven 模块——你可以把 AI Agent 能力嵌入到任何 Java 项目中。

> **设计哲学**：协议驱动 > 功能堆砌 · SDK-First > 独立应用 · 组合 > 继承

---

## 核心特性

| 特性 | 说明 |
|------|------|
| **Agent Loop** | 模型自主决策的核心循环：LLM → 工具调用 → 结果反馈 → LLM → ... 直到完成 |
| **流式输出** | `AgentEvent` 事件体系，实时推送 LLM Token / 工具调用状态 / 压缩事件 |
| **Virtual Threads 工具并发** | JDK 21 Virtual Threads + Structured Concurrency，工具调用真正并行执行 |
| **三层上下文压缩** | 微压缩（占位符替换）→ 自动压缩（LLM 摘要）→ 手动压缩（模型触发），支持无限长对话 |
| **双模式工具定义** | `@Tool` 注解（零配置）+ `AgentTool` SPI 接口（精细控制），自动生成 JSON Schema |
| **动态工具提供** | `ToolProvider` SPI 运行时按上下文决定可用工具集 |
| **错误恢复** | `ToolErrorHandler` 工具级 + `AgentErrorHandler` Loop 级（LLM 失败/死循环/重复检测） |
| **可观测性** | `AgentMetrics` 指标采集 + `AgentTracer` 链路追踪，SPI 可接入 Micrometer / OpenTelemetry |
| **声明式 Agent** | `@Agent` 注解将 Java 接口声明为 AI Service，框架自动生成动态代理 |
| **Workflow 编排** | 5 种模式：Sequential / Parallel / Loop / Conditional / Router |
| **Skill 两层加载** | Layer 1 元数据注入 system prompt + Layer 2 `load_skill` 按需加载完整内容 |
| **子 Agent 派生** | 隔离上下文执行，工具过滤（禁递归）、独立轮次限制、仅摘要返回 |
| **持久化任务板** | `.tasks/` 文件持久化，支持依赖图（blockedBy/blocks），上下文压缩后存活 |
| **后台任务管理** | 非阻塞命令执行，完成后通知队列自动注入 LLM 上下文 |
| **Agent 团队** | 持久命名 Agent + MessageBus (JSONL Inbox) + Shutdown/Plan Approval 协议 |
| **自主 Agent** | WORK/IDLE 双阶段循环，自动认领任务板上的无主任务 |
| **工作树隔离** | Git Worktree 目录级并行隔离，任务绑定 + 生命周期事件 (EventBus) |
| **SPI 插件体系** | 所有扩展点（Provider / Tool / Hook / Metrics / Tracer）通过 Java SPI 实现，编译期类型安全 |
| **OpenAI 兼容 API** | 对外暴露标准 Chat Completion 格式，任何 OpenAI 兼容客户端可直连 |
| **MCP 协议适配** | `McpAdapter` 统一接入 MCP Server 提供的外部工具 |
| **企业级网关** | 认证 / 限流 / 多租户 / Token 计量 |

---

## 架构概览

```
┌────────────────────────────────────────────────────────────────┐
│  sprinkle-claw-app / sprinkle-claw-spring-boot-starter         │  可选启动层
├────────────────────────────────────────────────────────────────┤
│  sprinkle-claw-gateway    │  sprinkle-claw-server              │  可选服务层
├───────────────────────────┼────────────────────────────────────┤
│  sprinkle-claw-team (实验) │  sprinkle-claw-worktree (实验)     │  可选扩展层
├───────────────────────────┼────────────────────────────────────┤
│  sprinkle-claw-agent-ext  │  sprinkle-claw-workflow            │  可选扩展层
├───────────────────────────┼────────────────────────────────────┤
│  sprinkle-claw-core       │  sprinkle-claw-bootstrap           │  核心引擎层
├───────────────────────────┼────────────────────────────────────┤
│  sprinkle-claw-llm-api    │  sprinkle-claw-tool-api            │  接口层
├───────────────────────────┼────────────────────────────────────┤
│                  sprinkle-claw-protocol                         │  协议层（零依赖）
└────────────────────────────────────────────────────────────────┘
```

**依赖规则**：上层不依赖下层 · 同层不互相依赖 · 实现依赖接口 · 核心只依赖协议层 · 扩展模块按需引入

---

## 模块说明

| 模块 | 职责 | 依赖 | 必选 |
|------|------|------|------|
| `sprinkle-claw-protocol` | 纯接口 + 数据模型 | **零依赖** | ✅ |
| `sprinkle-claw-llm-api` | LLM Provider SPI 接口 + 契约测试基类 | protocol | ✅ |
| `sprinkle-claw-tool-api` | 工具 SPI + @Tool 注解 + ToolProvider + ToolErrorHandler | protocol | ✅ |
| `sprinkle-claw-core` | Agent Loop + 流式输出 + 上下文压缩 + 错误恢复 + 可观测性 | protocol, llm-api, tool-api | ✅ |
| `sprinkle-claw-agent-ext` | SubAgent + Skill + 任务板 + 后台任务 + 身份重注入 | core | 可选 |
| `sprinkle-claw-workflow` | @Agent 声明式 + Workflow 编排 (5 种模式) | core | 可选 |
| `sprinkle-claw-team` | Agent 团队 + 协议 + 自主 Agent（实验性） | core, agent-ext | 可选 |
| `sprinkle-claw-worktree` | Git Worktree 隔离 + EventBus（实验性） | core, agent-ext | 可选 |
| `sprinkle-claw-llm-anthropic` | Anthropic Claude 实现 | llm-api | 按需 |
| `sprinkle-claw-llm-openai` | OpenAI / 兼容 API 实现 | llm-api | 按需 |
| `sprinkle-claw-llm-ollama` | Ollama 本地模型实现 | llm-api | 按需 |
| `sprinkle-claw-tool-builtin` | 内置工具：bash / read / write / edit | tool-api | 按需 |
| `sprinkle-claw-bootstrap` | Builder API + ServiceLoader 自动组装 | 全部核心模块 | 推荐 |
| `sprinkle-claw-server` | REST API + WebSocket + SSE | core, bootstrap | 按需 |
| `sprinkle-claw-gateway` | 认证 / 限流 / 多租户 / Token 计量 | server | 按需 |
| `sprinkle-claw-spring-boot-starter` | Spring Boot 自动配置 | core, server | 按需 |
| `sprinkle-claw-benchmark` | JMH 性能基准测试 | core | 开发 |

### 可选扩展

| 扩展 | 说明 |
|------|------|
| `sprinkle-claw-tool-media` | Java 原生图/PDF/文档解析（ImageIO, PDFBox, Tika） |
| `sprinkle-claw-tool-browser` | 浏览器自动化（Playwright4J） |
| `sprinkle-claw-tool-sandbox` | Docker 沙箱隔离执行 |
| `sprinkle-claw-channel-telegram` | Telegram Bot 渠道 |
| `sprinkle-claw-channel-wechat` | 企业微信 / 公众号渠道 |

---

## 使用方式

Sprinkle-Claw 支持三种使用方式：

| 方式 | 说明 | 引入模块 |
|------|------|---------|
| **纯 SDK 嵌入** | Maven 依赖引入，几行代码即可使用 | `sprinkle-claw-core` + LLM Provider |
| **Spring Boot 集成** | 自动配置，YAML 声明即用 | `sprinkle-claw-spring-boot-starter` |
| **独立服务** | 启动 JAR，任何 OpenAI 兼容客户端直连 | `sprinkle-claw-server` |

---

## 与 OpenClaw 的区别

Sprinkle-Claw **不是** OpenClaw 的 Java 翻译，而是针对 Java 生态重新设计的 AI Agent SDK：

| 维度 | OpenClaw | Sprinkle-Claw |
|------|----------|--------------|
| 定位 | 完整的 Agent 应用 | 可嵌入的 Agent SDK |
| 语言 | TypeScript (320K+ 行) | Java 21 (目标 < 15K 行核心) |
| 并发 | Node.js 单线程串行 | Virtual Threads 真并行 |
| 嵌入性 | 不可拆分 | Maven 依赖，一行引入 |
| 工具定义 | 代码硬编码 | @Tool 注解 + SPI 接口双模式 |
| 扩展 | Hook 回调 | SPI 编译期类型安全 |
| 工作流 | 无 | 5 种编排模式 |
| 多 Agent | 无 | SubAgent + Team Agent + 自主 Agent（team 模块，实验性） |
| 任务隔离 | 无 | Git Worktree 目录级并行隔离（worktree 模块，实验性） |
| 任务持久化 | 无 | 持久化任务板（压缩后存活 + 依赖图） |
| API | 私有协议 | OpenAI 兼容 + MCP 协议适配 |
| 管控 | 基础 session | 网关 + 认证 + 限流 + 多租户 |
| 媒体 | 外部进程调用 | Java 原生库 (ImageIO/Tika/JavaCV) |

---

## 技术栈

| 层次 | 选型 |
|------|------|
| JDK | 21 (LTS) — Virtual Threads, Records, Sealed Classes, Pattern Matching |
| 构建 | Maven 3.9+ |
| JSON | Jackson 2.17+ |
| 日志 | SLF4J 2.x + Logback |
| HTTP | JDK HttpClient (LLM 调用) |
| 服务端 | Javalin / JDK HttpServer (非 Spring 场景) |
| Spring | Spring Boot 3.5.x (可选) |
| 测试 | JUnit 5 + Mockito |

---

## 路线图

| 阶段 | 目标 | 状态 |
|------|------|------|
| **MVP1** | Agent Loop + 工具并发 + @Tool 注解 + ToolProvider + ToolErrorHandler + AgentErrorHandler + AgentMetrics + Anthropic | 🚧 进行中 |
| **MVP2** | 三层上下文压缩 + 会话持久化 + Token 估算 + TodoWrite | 📋 计划中 |
| **MVP3** | SubAgent（约束隔离） + Skill 两层加载 + 持久化任务板 + 后台任务 + 身份重注入 | 📋 计划中 |
| **MVP4** | AgentLoop 流式 (AgentEvent) + LLM 流式 + REST API (OpenAI 兼容) + SSE / WebSocket | 📋 计划中 |
| **MVP5** | OpenAI / Ollama Provider + @Agent 声明式 + Workflow 编排 (5 模式) + MCP 协议适配 | 📋 计划中 |
| **MVP6** | 企业级网关（认证 / 限流 / 多租户 / Token 计量）+ Spring Boot Starter | 📋 计划中 |
| **MVP7** | Agent 团队 + 团队协议 + 自主 Agent + 工作树隔离（实验性） | 📋 计划中 |
| **MVP8** | 媒体处理 + 渠道扩展 + 浏览器自动化 + 沙箱执行 + 性能优化 | 📋 计划中 |

---

## 项目结构

```
sprinkle-claw/
├── sprinkle-claw-protocol          ← 协议层（零依赖）
├── sprinkle-claw-core              ← 核心引擎（Agent Loop + 流式 + 压缩 + 可观测性）
├── sprinkle-claw-agent-ext         ← Agent 扩展（SubAgent + Skill + 任务板 + 后台任务）
├── sprinkle-claw-workflow          ← 声明式 Agent + Workflow 编排
├── sprinkle-claw-team              ← 多 Agent 协作（实验性）
├── sprinkle-claw-worktree          ← 工作树隔离（实验性）
├── sprinkle-claw-llm-api           ← LLM Provider SPI
├── sprinkle-claw-llm-anthropic     ← Anthropic 实现
├── sprinkle-claw-llm-openai        ← OpenAI 实现
├── sprinkle-claw-llm-ollama        ← Ollama 实现
├── sprinkle-claw-tool-api          ← 工具 SPI
├── sprinkle-claw-tool-builtin      ← 内置工具
├── sprinkle-claw-bootstrap         ← 组装层
├── sprinkle-claw-server            ← 通信层
├── sprinkle-claw-gateway           ← 网关管控
├── sprinkle-claw-spring-boot-starter
├── sprinkle-claw-benchmark         ← JMH 性能基准
├── extensions/
│   ├── sprinkle-claw-tool-media
│   ├── sprinkle-claw-tool-browser
│   ├── sprinkle-claw-channel-telegram
│   └── ...
└── sprinkle-claw-app               ← 示例应用
```

---

## 质量保障

| 维度 | 方案 |
|------|------|
| 单元测试 | JUnit 5 + Mockito，core ≥ 80% 覆盖率 |
| SPI 契约测试 | 每个 SPI 提供抽象测试基类，实现方继承即可验证 |
| 集成测试 | WireMock 模拟 LLM API，不依赖外部服务 |
| 性能基准 | JMH 基准测试（工具并发 / Agent Loop 延迟 / JSON 序列化），每个 MVP release 建立基线 |
| CI/CD | push → compile → unit-test → integration-test → benchmark (release) |

---

## Contributing

项目处于早期阶段，欢迎提交 Issue 和 PR。

## License

[MIT](LICENSE)
