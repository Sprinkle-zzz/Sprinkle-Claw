# 更新日志

本文件记录项目的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/spec/v2.0.0.html)。

## [0.11.0] - 2026-04-29

### 项目改名：sprinkle-claw → sprinkle-loom（破坏性重构）

`Sprinkle-Claw` 正式更名为 **`Sprinkle-Loom`**。`Loom`（织布机）更准确传达 SDK 的实际定位——把 LLM Provider / 工具 / Hook / 记忆 / 技能 编织成一个 Agent Loop。

#### 用户迁移指引

| 项 | 旧 | 新 |
|---|---|---|
| Maven groupId | `icu.sprinkle` | `icu.sprinkle.loom` |
| Maven artifactId | `sprinkle-claw-bootstrap` 等 | `sprinkle-loom-bootstrap` 等 |
| Java import | `com.sprinkleclaw.bootstrap.Claw` | `icu.sprinkle.loom.bootstrap.Loom` |
| Spring 配置 prefix | `sprinkle-claw.*` | `sprinkle-loom.*` |
| 核心类 | `Claw` / `ClawBuilder` | `Loom` / `LoomBuilder` |
| Spring 类前缀 | `SprinkleClaw*`（Properties / AutoConfiguration / Factory / BeanRegistrar） | `SprinkleLoom*` |
| GitHub 仓库 | `Sprinkle-zzz/Sprinkle-Claw` | `Sprinkle-zzz/Sprinkle-Loom` |

#### 影响面

- 17 个 Maven 模块目录改名
- 351 个 .java 文件 package + import 全部更新（`com.sprinkleclaw.*` → `icu.sprinkle.loom.*`）
- 6 个 SPI 文件路径改名（`META-INF/services/com.sprinkleclaw.*` → `META-INF/services/icu.sprinkle.loom.*`）
- 36 个配置文件 `sprinkle-claw.*` prefix 替换
- 核心类 `Claw` / `ClawBuilder` 及所有 `SprinkleClaw*` Spring 类同步改名
- 文件物理重命名：`Claw.java` → `Loom.java` / `ClawBuilder.java` → `LoomBuilder.java` 等

#### 不提供 Deprecated 兼容

由于 0.x 阶段无外部用户，本次直接破坏式重构是当前阶段成本最低的选择。所有变更通过单次 commit 完成，详见 `docs/13-项目改名方案-sprinkle-loom.md`。

---

## [0.10.0] - 2026-04-29

### MVP9 · SDK 定位偏离修正 + 多 LLM 适配优化（破坏性变更）

> 本版本以 SDK-First / 默认零工具哲学为主线，做了一次彻底的设计盲区审计与多 LLM 适配能力增强。包含多处破坏性变更（0.x 阶段直接重构，无外部用户）。

#### 新增 —— `LlmConfig` 三层扩展点

对齐文档 8.1.3 节的设计原则——**不做 vendor capability registry**（参考 LangChain4j Issue #1552），而是通过三层扩展点让用户显式覆盖 / 透传 / 开关：

- **`LlmConfig.capabilities(LlmCapabilities)`**：用户覆盖能力声明。`OpenAiCompatibleProvider.capabilities()` 优先返回 `config.capabilities()`，否则用保守默认值（128K 上下文 / 16K 输出 / 启用 prompt cache）。理由：同一 baseUrl 下不同模型上下文长度差异巨大（DeepSeek-V3 64K vs GLM-4 128K vs Qwen-Max 30K），让用户在知道具体模型时显式覆盖比 SDK 维护一张永远滞后的厂商表更可靠
- **`LlmConfig.customParameters(Map<String, Object>)` / `customParameter(name, value)`**：透传给 LLM 请求体的 vendor 私有字段。`OpenAiCompatibleProvider.buildRequestBody` 末尾平铺合并到 root JSON。一招覆盖所有厂商私有字段（Qwen 的 `enable_search`、DeepSeek 的 `frequency_penalty` / `response_format` 等），SDK 不需要维护任何 vendor 知识
- **`LlmConfig.returnThinking(boolean)` + `thinkingFieldName(String)`**：推理字段开关。默认 `false` + `"reasoning_content"`（deepseek-v4-pro 约定）。启用后 `parseResponse` 和 `dispatchSseEvent` 解析对应字段映射为 `ContentBlock.ThinkingBlock`，与 Anthropic Thinking 处理路径一致

> **设计哲学**：不做 vendor 嗅探（如 "baseUrl 含 deepseek 就特殊处理"），所有 vendor 差异通过这三层显式参数化，避免脆弱的隐式行为。

#### 新增 —— Usage 字段双兼容

OpenAI 标准用 `prompt_tokens_details.cached_tokens`，DeepSeek 用顶层 `prompt_cache_hit_tokens`。`OpenAiCompatibleProvider.parseResponse` / `dispatchSseEvent` 同时尝试读两个字段（谁有取谁），归一到 `Usage.cacheReadInputTokens`——无害兼容，不算"vendor 特殊处理"，自然支持 DeepSeek 自动 prompt cache。

#### 新增 —— `OpenAiResponseCollector` 推理累积

- 新增 `appendThinking(String)` / `setCachedTokens(int)` 方法
- `build()` 中推理内容在 TextBlock 之前插入 `ThinkingBlock`（与 Anthropic 顺序一致）
- `Usage` 构造从 3 参（含 reasoning）改为 5 参（含 cacheCreation + cacheRead）

#### 新增 —— `examples` 增加 MemoryStore 自实现样例

替代原规划中的 `sprinkle-loom-memory-jdbc` / `sprinkle-loom-memory-redis` 模块（已在文档 8.2 节撤销，理由：接口仅 5 个方法、JDBC 关键词检索是反模式、生产应走向量库）：

- **`JdbcMemoryStoreExample.java`**：~80 行 JDBC 实现样例，H2 内存数据库真实可跑（`mvn exec:java -Dexec.mainClass=...JdbcMemoryStoreExample`）。展示 schema 初始化 / MERGE upsert / LIKE 关键词检索 / 完整 CRUD。明确标注关键词检索是反模式，仅作"如何自实现"的参考
- **`PgVectorMemoryStoreExample.java`**：~120 行 pgvector 向量检索样例（生产推荐方向）。展示 PostgreSQL + pgvector 扩展 schema、`<=>` 余弦距离运算符、`EmbeddingClient` 接口契约（可对接 OpenAI Embeddings / DashScope / 本地 ONNX 等）。不实际启动 DB，仅作生产实现的起点
- **`examples/pom.xml`**：新增 H2 2.2.224 依赖（仅 examples 模块，不污染其他模块依赖图）

#### 增强 —— `@Agent` 结构化输出鲁棒性

`StructuredOutputParser` / `JsonExtractor` / `AgentInvocationHandler` 三处改进：

- **`JsonExtractor` 栈式扫描**（替代贪婪正则）：正确处理 JSON 字符串字面量内的大括号（如 `{"template": "use {var} syntax"}`）和转义引号（`{"text": "say \"hi\""}`）；多个 JSON 块时取第一个完整块；不平衡括号返回 `null` 避免抓到不完整片段
- **`StructuredOutputParser` 递归 Schema 校验**：从原"仅类型 + 必填字段"扩展为完整递归校验：
  - 字段类型校验（string / integer / number / boolean / object / array）
  - 数组 `items` 类型校验（含 nested 元素）
  - 嵌套对象递归校验（任意深度）
  - `enum` 值校验
  - 错误信息附带 JSONPath 风格的具体路径（如 `$.address.zipCode expected integer but got string`）
- **`AgentInvocationHandler` 重试策略改进**：
  - History 不无限累积——每次重试只保留 `[原始 user, 上轮 assistant, correction]`（schema 已在 system prompt 中无需重复），避免长 history 浪费 token
  - 新增 SLF4J 日志：`debug`（自纠正成功）/ `warn`（每次解析失败 + 摘要 correction）/ `error`（致命错误 / 重试耗尽）
- **测试补充**：`JsonExtractorTest` 增加 6 个栈式扫描测试（嵌套大括号 / 转义引号 / 多块择第一个 / 数组等）；`StructuredOutputParserTest` 增加 5 个测试覆盖类型不匹配 / 嵌套深路径 / 数组索引路径 / enum 校验 / 完整嵌套验证

---

#### 破坏性变更 —— `enableExtensions()` 删除

一键开启 4 个扩展的便捷方法与全部 opt-in" 哲学相悖，0.x 阶段直接删除：

- **删除 `LoomBuilder.enableExtensions()`**：用户必须显式调用 `enableSubAgent()` / `enableSkill()` / `enableTaskBoard()` / `enableBackgroundTasks()`
- **删除 `SprinkleClawProperties.Agent.enableExtensions` 字段**：`sprinkle-loom.agent.enable-extensions: true` 配置不再生效，启动时 Spring 会按未知属性处理（默认警告，可通过 `--spring-config-on-not-found-fail-on-unknown=false` 屏蔽）
- **删除 `SprinkleClawAutoConfiguration` 中对 `isEnableExtensions()` 的引用**

迁移：用户改为在 yaml 中按需启用对应开关（如 `sprinkle-loom.agent.enable-skill: true`，待 9.11 多 model 配置重构后下沉到 instance）。

#### 修复 —— `SystemPromptBuilder` 默认行为修正

修正纯 chat agent 的 system prompt 被无条件污染的问题：

- **工具列表为空时**：不再注入 `# Available Tools` 段和 `# Important Rules` 段；纯 chat agent 的 effective system prompt 完全等于用户传入的 `systemPrompt(...)`
- **`# Environment` 段（cwd / platform）按需注入**：新增 `boolean includeEnvironment` 参数，由 `LoomBuilder` 在 `enableFileTools || enableBashTool` 启用时自动设为 true；其他场景不再泄漏 `Working directory: /xxx` 和 `Platform: Linux` 到上下文
- **签名变更**：`SystemPromptBuilder.build(tools, workingDirectory, customPrompt)` → `build(tools, workingDirectory, customPrompt, includeEnvironment)`，新增 `build(tools, customPrompt)` 便捷重载
- **指令措辞调整**：`Always use tools to interact with the system` 改为 `Use the provided tools when they are needed to complete the task`，避免命令式语气

#### 破坏性变更 —— 目录类配置默认值删除

避免静默落到调用方进程 cwd 的"陷阱默认值"，工作 / Skill / Task 目录均改为按需配置：

- **`AgentConfig` 默认值删除**：`workingDirectory` / `skillsDirectory` / `tasksDirectory` 默认值由 `Path.of(".")` / `Path.of("skills")` / `Path.of(".tasks")` 改为 `null`；`AgentConfig.DEFAULT` 与 `Builder` 同步
- **删除 `AgentConfig.sessionDirectory` 字段**：经全仓库 grep 确认零引用，纯死字段；同时移除 `AgentConfig.Builder.sessionDirectory(Path)` 方法和 record 第 15 个参数（参数顺序变更）
- **`LoomBuilder` 启用开关时校验目录非空**：`enableFileTools` / `enableBashTool` / `enableFileSnapshot` / `enableBackgroundTasks` 启用任一即要求 `workingDirectory(Path)`；`enableSkill` 启用且无 `addSkill(...)` 编程式注册时要求 `skillsDirectory(Path)`；`enableTaskBoard` 启用且无 `taskStore(Object)` 自定义存储时要求 `tasksDirectory(Path)`。校验失败抛 `IllegalStateException` 并提示对应方法名
- **`LoomBuilder` 新增**：`skillsDirectory(Path)` / `tasksDirectory(Path)` 两个 builder 方法
- **`AutoCompactor` 移除 transcript 写盘**：transcript 持久化是 CLI 调试场景特性，不属于 SDK 默认职责。删除 `transcriptDir` 字段、构造器 `Path` 参数、`saveTranscript()` / `serializeTranscript()` / `escapeJson()` 等方法；构造器签名 `(LlmProvider, TokenEstimator, Path)` 简化为 `(LlmProvider, TokenEstimator)`。如需观察压缩前后状态，订阅 `AgentEvent.Compaction` 自行处理
- **`ToolOutputTruncator` 写盘绑定 `enableFileTools`**：超大输出文件保存仅在 `enableFileTools` 启用时才有意义（LLM 必须能调 `read_file` 才能读取保存内容）。`LoomBuilder` 在创建 truncator 时仅当 `enableFileTools=true` 才传 `workingDirectory`，否则传 `null`——避免在无 file tools 场景下产生 LLM 无法访问的孤儿文件，也避免污染未启用 file tools 但开了 bash tool 的场景下的 cwd
- **`SprinkleClawProperties.Agent.workingDirectory` 默认值删除**：从 `"."` 改为 `null`；`SprinkleClawAutoConfiguration` 仅当非空时调 `builder.workingDirectory(...)`

> 净效果：默认路径上不再有任何 `.sprinkle-loom/` 子目录被偷偷创建。仅当用户显式 `enableFileTools(...) + workingDirectory(...)` 时才会在工作区下创建截断输出子目录，且此时 LLM 能 `read_file` 真正使用这些文件。

#### 新增 —— Streaming API 门面

`Loom` 暴露流式入口，对齐文档 8.3 节的"内核已就绪 / 门面缺最后一公里"缺口：

- **`Claw.runStreaming(String)`** / **`chatStreaming(String)`** / **`resumeStreaming(String, String)`**：返回 `Flow.Publisher<AgentEvent>`，逐步推送 17 种事件类型（`LlmToken` / `ThinkingToken` / `ToolStart` / `ToolEnd` / `IterationComplete` / `AgentComplete` / `AgentError` 等）
- **并发守卫复用**：与同步 `run/chat/resume` 和 `runAsync/chatAsync/resumeAsync` 共享 `running` AtomicBoolean。流式循环结束（含异常）时自动释放守卫，订阅者无需手动 close
- **`AgentLoop.runStreaming(Runnable onLifecycleEnd)`**：新增重载，virtual thread `finally` 块调用 callback；保留 0 参版本
- **`Claw.errorPublisher`**：lazy publisher 实现（订阅者首次 request 时触发 onError），用于并发守卫拒绝场景，避免 hot publisher 在订阅前 submit 丢事件
- **`examples/StreamingAgent.java`**：DeepSeek 模型 + `Flow.Subscriber` 控制台示例，展示 `LlmToken` 逐字符渲染、`ToolStart/ToolEnd` 进度提示、`AgentComplete` 终态汇总

#### 破坏性变更 —— Spring Boot Starter 多 model 配置

`sprinkle-loom-spring-boot-starter` 从单 LLM 配置升级为多实例模型，对齐"嵌入 agent + 构建 agent 应用"双场景下的多 agent 共存需求：

**配置 schema 变更**（旧 schema 不兼容）：
```yaml
# 旧（已删除）
sprinkle-loom:
  llm:
    api-key: xxx
    model: claude-opus-4-7
    base-url: ...

# 新
sprinkle-loom:
  agent:                    # 全局默认（被 instance 同名字段覆盖）
    max-iterations: 200
    system-prompt: "你是助手"
  llm:
    primary: claude         # ≥2 instance 必填，1 instance 时自动作为 primary
    instances:
      claude:
        provider: anthropic
        api-key: ${ANTHROPIC_API_KEY}
        model: claude-opus-4-7
      qa-bot:
        provider: openai
        base-url: https://api.deepseek.com/v1
        api-key: ${DEEPSEEK_API_KEY}
        model: deepseek-v4-flash
        system-prompt: "你是客服机器人"   # 覆盖全局
        max-iterations: 5                # 覆盖全局
```

**用户代码变化**：
```java
@Autowired Loom loom;                          // 注入 primary（claude）
@Autowired @Qualifier("qa-bot") Loom qaBot;
```

**实现要点**：
- **`SprinkleClawProperties.Llm`**：移除平铺 `provider` / `apiKey` / `model` / `baseUrl` 4 字段，新增 `String primary` + `Map<String, Instance> instances`（`LinkedHashMap` 保插入顺序）；`Instance` 内部类含 LLM 字段 + agent/tools 覆盖字段（包装类型 `Integer` / `Duration` / `String` / `List`，`null` 沿用全局）
- **`SprinkleClawFactory`**：新增工厂 bean，`create(instanceName)` 方法将 instance 字段 merge 全局 `agent`/`tools` 默认后调 `LoomBuilder` 构建 Claw；`@ConditionalOnMissingBean` 允许测试 mock 覆盖
- **`SprinkleClawBeanRegistrar`**：新增 `BeanDefinitionRegistryPostProcessor`，使用 `Binder` 在 BeanDefinition 阶段读取 properties，遍历 `instances` 注册命名 `RootBeanDefinition`（`factoryBeanName=sprinkleClawFactory` + `factoryMethodName=create` + 构造参数 instance 名 + `destroyMethodName=close`）。primary 实例 `setPrimary(true)`。必须以 `static @Bean` 暴露避免 BeanFactory 过早实例化
- **Primary 选择规则**：显式 `primary` 字段 ≥ 仅 1 个 instance 自动作为 primary ≥ ≥2 instance 未指定 primary → 启动报错；primary 指向不存在的 instance 名 → 启动报错（错误消息列出所有可用 instance）
- **0 instance 场景**：BeanRegistrar 不注册任何 Loom bean，`@Autowired Loom` 抛 `NoSuchBeanDefinitionException`（不再有"默认配置 Loom"兜底）
- **资源释放**：每个 BeanDefinition 设置 `destroyMethodName=close`，Spring 容器关闭时正确释放 MCP 连接等资源
- **`SprinkleClawMultiInstanceTest`**：6 个测试覆盖 0/1/≥2 instance、显式/缺失/错指 primary、agent 全局默认 + instance 覆盖等关键路径

#### 文档 —— README 按"嵌入 / 构建"两条路径分流

`README.md` 从 294 行扁平特性堆叠重写为按用户场景分流的导航文档：

- **顶部"选你的路径"导航表**：用户 30 秒内识别自己属于"嵌入 agent 能力"还是"构建 agent 应用"，明确两类场景的工具开关边界
- **路径 A（嵌入）**：5 个段落覆盖最小代码 / 业务 `@Tool` 注册 / Spring Boot 多 model 配置 / 流式输出 SSE / 长期记忆，对应客服 / 审批 / 数据分析等业务场景
- **路径 B（构建）**：4 个段落覆盖 `enableCodingTools()` 一键启用 / 工具开关详细表 / MCP & Skill / 多 Agent 工作流编排，对应编码助手 / 研究工具场景
- **工具开关边界明示**：`BashTool` / `FileSnapshot` / `TodoWrite` / `BackgroundTasks` 在 B.2 表格中标注 "嵌入场景禁止开启"
- **删除 99 行扁平特性表**：替换为按"协议层 / LLM Provider / 核心引擎 / 可观测性 / 会话与记忆 / 构建场景扩展 / 协议适配 / 企业级网关 / Spring Boot / Workflow"分层的紧凑摘要
- **模块说明新增"路径 A/B 必需"列**：`tool-builtin` 路径 A 不需要、路径 B 必需；`agent-ext` 路径 A 仅 Skill/Guardrails，路径 B 全需要
- **路线图更新**：MVP9（进行中）+ MVP10（规划中）

#### 破坏性变更 —— 删除 BuiltinToolProvider 死类

`tool-builtin/.../BuiltinToolProvider.java`：MVP8 移除 SPI 文件后该类已无任何真实引用，仅 `LoomBuilder.discoverAndRegisterTools` 中残留一行字符串类名过滤"防御性"提及。按"质疑特性存在合理性"原则直接删除：

- **删除 `BuiltinToolProvider.java`** 整个类
- **简化 `LoomBuilder.discoverAndRegisterTools`**：移除 `tp.getClass().getName().equals("...BuiltinToolProvider")` 字符串比较，ServiceLoader 循环回归正常发现逻辑
- 内置工具仍然通过 `enableFileTools()` / `enableBashTool()` / `enableCodingTools()` 显式启用（MVP8 路径不变）

#### 新增 —— LoomBuilder Profile 预设

降低新用户认知负担，按典型场景提供 3 个静态工厂方法：

- **`LoomBuilder.chatBot()`**：嵌入式 Chat Bot 预设（零工具 + `InMemorySessionStore` 多轮会话）
- **`LoomBuilder.codingAgent(Path workdir)`**：编码 Agent 预设（`enableCodingTools` + `enableFileSnapshot`，`workingDirectory` 强制传入）
- **`LoomBuilder.businessAgent()`**：业务 Agent 预设（零内置工具 + 多轮会话，面向 `addSkill` + `@Tool` 业务嵌入场景）
- 三个方法均标 `@Experimental`，预设组合可能根据实际反馈调整

#### 新增 —— API 稳定性注解

新增 `icu.sprinkle.loom.api.Stable` / `icu.sprinkle.loom.api.Experimental`（在 `sprinkle-loom-protocol` 模块，所有上层模块免依赖即可使用）。已标注关键 API：

| API | 等级 | 备注 |
|---|---|---|
| `Loom` | `@Stable` | 主门面 |
| `Claw.runStreaming/chatStreaming/resumeStreaming` | `@Experimental` | MVP9 新引入，订阅时序与并发守卫策略可能在 MVP10 调整 |
| `LoomBuilder` | `@Stable` | 主 Builder |
| `LoomBuilder.chatBot/codingAgent/businessAgent` | `@Experimental` | MVP9 新引入预设 |
| `LlmProvider` SPI | `@Stable` | 多厂商适配核心契约 |
| `AgentTool` SPI | `@Stable` | 工具契约 |
| `ToolProvider` SPI | `@Stable` | 工具发现契约 |
| `SessionStore` SPI | `@Stable` | 会话持久化契约 |
| `MemoryStore` SPI | `@Experimental` | 检索语义（关键词 vs 向量）和分页 API 可能在 MVP10 调整 |

约定：未明确标 `@Stable` 的 API 默认按 `@Experimental` 对待——0.x 阶段大部分仍属实验性。

---

## [0.9.0] - 2026-04-25

### MVP8 · SDK 核心清理 + 生产就绪

#### 新增 —— 工具注册 opt-in 重构

内置工具从 ServiceLoader 自动注册改为显式启用，SDK 嵌入场景默认零工具：

- **删除 `META-INF/services/icu.sprinkle.loom.tool.ToolProvider`**：`BuiltinToolProvider` 不再通过 SPI 自动注册
- **`LoomBuilder.enableFileTools()`**：显式启用 `read_file` / `write_file` / `edit_file`
- **`LoomBuilder.enableBashTool()`**：显式启用 `bash`（高风险工具独立控制）
- **`LoomBuilder.enableCodingTools()`**：一键启用 file + bash + todo + compact
- **`LoomBuilder.enableManualCompact()`**：显式启用 `compact` 工具
- **`discoverAndRegisterTools()` 重构**：ServiceLoader 过滤 `BuiltinToolProvider` 类名，防止手动重新注册 SPI

#### 新增 —— 异步 API

- **`Claw.runAsync()` / `chatAsync()` / `resumeAsync()`**：返回 `CompletableFuture<AgentResult>`，通过 `Executors.newThreadPerTaskExecutor(Thread.ofVirtual())` 在 Virtual Thread 中执行
- **`AtomicBoolean running` 并发守卫**：同一 Loom 实例不可并发调用，违反时返回 `failedFuture(IllegalStateException)`

#### 新增 —— Skill 编程式 API

- **`LoomBuilder.addSkill(name, description, body)`**：编程式注册 Skill，无需文件系统
- **`LoomBuilder.addSkill(name, description, tags, body)`**：带标签的重载
- **`SkillEntry` 便捷构造器**：2 参数 / 3 参数构造（无 path 参数），适合编程式创建
- **`ExtensionRegistrar` 合并注册**：文件扫描 + 编程式 Skill 统一注册到 `SkillLoader`
- **自动启用**：调用 `addSkill()` 后自动 `enableSkill = true`

#### 新增 —— TaskStore 可注入

- **`LoomBuilder.taskStore(Object)`**：使用 `Object` 类型避免对 `agent-ext` 的编译依赖
- **`ExtensionRegistrar` 类型检查**：优先使用自定义 TaskStore（`instanceof TaskStore`），类型不匹配抛 `IllegalArgumentException`

#### 新增 —— SessionSnapshotSerializer

- **`SessionSnapshotSerializer`**：会话快照序列化/反序列化工具类，处理 `Message` sealed interface 多态和 6 种 `ContentBlock` 多态
- **`SessionObjectMapper` 多模态补全**：`ContentBlockMixin` 补充 `ImageBlock` / `DocumentBlock` / `AudioBlock`
- **`CacheControlMixin`**：`CacheControl` sealed interface 的 Jackson Mixin

#### 新增 —— HttpClient 连接池统一

- **`SharedHttpClient`**（`sprinkle-loom-llm-api` 模块）：DCL 单例 + HTTP/2 + Virtual Thread executor + 10s 连接超时 + NORMAL 重定向
- **`SharedHttpClient.override(HttpClient)`**：测试入口，支持注入自定义 HttpClient
- **三个 Provider 改造**：`AnthropicProvider` / `OpenAiCompatibleProvider` / `OllamaHttpClient` 从各自创建 HttpClient 改为共享 `SharedHttpClient.get()`

#### 新增 —— 双层记忆架构

- **`MemoryStore` SPI**（`sprinkle-loom-core` 模块）：长期跨会话记忆接口，`record()` / `retrieve(query, topK)` / `delete()` / `listAll()` / `size()`
- **`MemoryEntry` record**：记忆条目（id / content / metadata / createdAt）+ 便捷构造器
- **`InMemoryMemoryStore`**：`ConcurrentHashMap` 实现，基于关键词匹配的检索（分词 → 匹配率评分 → topK 排序）
- **`MemoryEnricherHook`**：`LoopHook` 实现（priority=45），首次 LLM 调用前检索相关记忆并注入 `<relevant-memories>` 系统提醒
- **`LoomBuilder.memoryStore(Object)`**：注入 MemoryStore，自动注册 MemoryEnricherHook

#### 新增 —— Agent 评估框架

- **`AgentEvaluator`**（`sprinkle-loom-core` 模块）：LLM-as-judge 评估框架，接受 `Function<String, AgentResult>` + 场景列表
- **`EvalScenario` record**：评估场景（name / input / expectedBehaviors），支持 varargs 构造
- **`EvalResult` record**：评估结果（scenario / score / passed / feedback / output）

#### 新增 —— 示例项目 (`sprinkle-loom-examples`)

4 个 SDK 使用示例（DeepSeek 模型）：

- **`MinimalAgent`**：零工具纯对话，最简 Agent 构建
- **`CustomerServiceAgent`**：业务嵌入客服 Agent，展示 `addSkill()` + `chatAsync()` 多轮对话
- **`CodingAgent`**：`enableCodingTools()` 编码 Agent
- **`MultiAgentCollaboration`**：`@Agent` 声明式代理 + `SequentialWorkflow` 串联 Researcher → Writer

#### 修复

- **AgentLoop LLM 调用异常日志**：同步路径和流式路径 `catch` 块新增 `log.warn("LLM 调用失败 (迭代 {}): {}", iteration, e.getMessage(), e)`，不再吞掉错误信息

#### 测试

- **LoomBuilderToolRegistrationTest**：工具 opt-in 注册验证
- **ClawAsyncApiTest**：异步 API + 并发守卫验证
- **SessionSnapshotSerializerTest**：6 种 ContentBlock 往返序列化
- **SharedHttpClientTest**：DCL 单例 + override 测试
- **InMemoryMemoryStoreTest**：CRUD + 关键词检索
- **MemoryEnricherHookTest**：preLlmCall 记忆注入
- **AgentEvaluatorTest**：10 个测试用例（完整流程 / 批量场景 / 异常处理 / 边界解析）

#### ⚠️ 不兼容变更

- **内置工具不再自动注册**：`LoomBuilder.create().build()` 默认零工具。迁移方式：
  - 编码 Agent：添加 `.enableCodingTools()`
  - 仅文件工具：添加 `.enableFileTools()`
  - 仅 bash：添加 `.enableBashTool()`
- **`CompactTool` 需显式启用**：原 `compactionThreshold > 0` 即注册，现需同时 `enableManualCompact()`（`enableCodingTools()` 已包含）

---

## [0.8.0] - 2026-04-21

### MVP7 · Prompt Caching + 多模态内容支持

#### 新增 —— Prompt Caching 框架

全链路 Prompt Cache 支持，从协议层到引擎层自动标记+命中统计：

- **`CacheControl` sealed interface**（`protocol` 模块）：`None`（无缓存）+ `Ephemeral(ttlSeconds)`（默认 300s），用于标记 `ContentBlock` 是否参与 Provider 的 Prompt Caching
- **`CachePolicy` SPI**（`llm-api` 模块）：函数式接口，在 LLM 调用前修饰 `ChatRequest`，给需要缓存的 ContentBlock 打 `CacheControl` 标记
- **`CacheStrategy` 枚举**：4 种预定义策略 —— `MANUAL`（手动）/ `AUTO_SYSTEM_PROMPT`（仅系统提示）/ `AUTO_SYSTEM_AND_TOOLS`（系统提示+工具定义）/ `AUTO_AGGRESSIVE`（激进模式，含对话前缀稳定部分）
- **`DefaultCachePolicy`**：基于 `CacheStrategy` 的默认实现，自动检测 `LlmCapabilities.supportsPromptCache()` 跳过不支持的 Provider
- **`ChatRequest` 扩展**：新增 `systemCacheControl` / `toolsCacheControl` 字段 + `toBuilder()` 方法，支持 CachePolicy 非侵入修饰
- **`Usage` 扩展**：新增 `cacheCreationInputTokens` / `cacheReadInputTokens` 字段 + `cacheHitRateBp()` 万分比命中率计算
- **`AgentMetrics.recordCacheTokens()`**：默认方法，记录缓存创建与命中 token 数
- **`AgentLoop` 集成**：同步/流式两条路径均注入 `CachePolicy.decide()`，缓存命中时自动打印 debug 日志（命中 token 数 + 命中率）

#### 新增 —— 多模态内容支持

`ContentBlock` 从纯文本/工具/推理扩展为完整的多模态内容模型：

- **`ContentBlock.ImageBlock`**：图片内容块，支持 `base64Data`（内联）或 `url`（外链）二选一 + `CacheControl`，提供 `ofBase64()` / `ofUrl()` 静态工厂
- **`ContentBlock.DocumentBlock`**：文档内容块（PDF 等），支持 `base64Data` / `url` + `name` 文档名 + `CacheControl`，提供 `ofBase64()` / `ofUrl()` 静态工厂
- **`ContentBlock.AudioBlock`**：音频内容块，支持 `base64Data` / `url` + `CacheControl`，提供 `ofBase64()` / `ofUrl()` 静态工厂
- **`ContentBlock` MIME 常量**：`MIME_PNG` / `MIME_JPEG` / `MIME_GIF` / `MIME_WEBP` / `MIME_PDF` / `MIME_WAV` / `MIME_MP3`
- **`TextBlock` / `ToolUseBlock` 扩展**：新增 `CacheControl` 字段，保留无参兼容构造器

#### 新增 —— LlmCapabilities 多模态能力声明

- **4 个新能力字段**：`supportsPromptCache` / `supportsVision` / `supportsPdfInput` / `supportsAudioInput`
- **兼容旧代码**：保留 4 参数、7 参数构造器，新字段默认 `false`
- **Builder 扩展**：对应 4 个新 setter 方法

#### 变更 —— Anthropic Provider

- **Prompt Cache 序列化**：system prompt 带 `Ephemeral` 时转为 `[{type: "text", text: ..., cache_control: {type: "ephemeral"}}]` 数组格式；工具定义列表最后一个工具附加 `cache_control`
- **多模态序列化**：`serializeUserContentBlock()` 统一处理 UserMessage 中的 Text / Image / Document / Audio / ToolUse / Thinking 块；`serializeImageBlock()` / `serializeDocumentBlock()` 支持 base64 / URL 双模式
- **Cache Token 解析**：`parseResponse()` 提取 `cache_creation_input_tokens` / `cache_read_input_tokens`
- **不支持降级**：AudioBlock 在 Anthropic 中序列化为 `[Unsupported]` 文本提示

#### 变更 —— OpenAI 兼容 Provider

- **多模态序列化**：`serializeOpenAiUserBlock()` 支持 `image_url`（data URI / URL）+ 不支持内容降级文本
- **Cache Token 解析**：`parseResponse()` 提取 `prompt_tokens_details.cached_tokens`

#### 变更 —— Ollama Provider

- **Vision 模型注册表**：新增 `VISION_MODELS` 集合（llava / bakllava / moondream / qwen2-vl）+ `supportsVision()` 查询方法
- **images 字段序列化**：`OllamaProtocolMapper` 提取 `ImageBlock.base64Data` 写入 Ollama `images[]` 数组
- **能力声明更新**：所有已知模型 + 保守默认值补充 4 个新能力字段

#### 变更 —— Core 模块适配

- **`AutoCompactor`**：`switch` 表达式新增 `ImageBlock` / `DocumentBlock` / `AudioBlock` 分支，生成 `[image: ...]` / `[document: ...]` / `[audio]` 占位符
- **`TokenEstimator`**：新增多模态 token 估算 —— `ImageBlock` 固定 1500、`DocumentBlock` / `AudioBlock` 按 base64 长度 /4 估算（兜底 2000）

#### 兼容性

- `ChatRequest` 保留 8 参数构造器（不带缓存控制），新增 10 参数完整构造器
- `Usage` 保留 2 参数、3 参数构造器，新增 5 参数完整构造器
- `LlmCapabilities` 保留 4 参数、7 参数构造器，新增 11 参数完整构造器
- `ContentBlock.TextBlock` / `ToolUseBlock` 保留无 CacheControl 的旧构造器
- `AgentLoop` 保留 9 参数构造器（不带 CachePolicy），新增 10 参数完整构造器
- `AgentMetrics.recordCacheTokens()` 为 `default` 方法，不影响现有实现

---

## [0.7.0] - 2026-04-19

### MVP6 · 企业级网关 + Spring Boot Starter + MCP 官方 SDK

#### 新增 —— `sprinkle-loom-gateway`（全新模块）

纯 Java SPI 设计的企业级管控过滤器链，零 Spring 依赖，可在任何框架中使用：

- **过滤器骨架**：`GatewayFilter` SPI（`order` + `preFilter` + `postFilter`）+ `FilterResult` sealed (Pass / Reject) + `GatewayFilterChain`（pre 升序 → Agent → post 降序，Reject 短路）+ `FilterOrder` 顺序常量
- **请求/响应模型**：`GatewayRequest` / `GatewayResponse` / `GatewayException` / `ErrorCode` 错误码枚举
- **认证**：`AuthProvider` SPI + `AuthFilter`（多 Provider 短路）
  - `ApiKeyAuthProvider` + `ApiKeyStore` SPI + `InMemoryApiKeyStore`
  - `JwtAuthProvider`（基于 nimbus-jose-jwt，optional 依赖）：JWKS 公钥验签 + iss/aud/exp 校验 + sub/tenant_id/plan/permissions claim 提取
- **限流**：`RateLimiter` SPI + `RateLimitResult` + `Bucket4jRateLimiter`（Bucket4j 8.10 + Caffeine 缓存）+ `RateLimitFilter`（超限附 X-RateLimit-* 响应头）
- **多租户**：`TenantPlan` (FREE/BASIC/PRO 配额) + `TenantContext` + `TenantQuota`（AtomicLong 日配额）+ `TenantFilter`
- **ACL**：`AccessControlList`（IP 黑/白名单）+ `AclFilter`
- **审计**：`AuditLogger` SPI + `AsyncBufferedAuditLogger`（异步缓冲 + 定时 flush）+ `AuditFilter`（pre 记请求 / post 记响应+token）+ `AuditEvent` record
- **Token 计量**：`TokenMeteringFilter` 从 `Usage` 提取 + `UsageReporter` SPI + `AsyncBufferedUsageReporter`
- **安全**：`PromptInjectionGuard` + `KeywordInjectionDetector`（预编译正则）+ `OutputValidator` + `SensitivePatternRule`

#### 新增 —— `sprinkle-loom-spring-boot-starter`（全新模块）

Spring Boot 3.2+ 自动配置，从 `application.yml` 一行集成：

- **`SprinkleClawProperties`**：`@ConfigurationProperties("sprinkle-loom")`，覆盖 `llm.*` / `agent.*` / `tools.*` / `gateway.*` / `mcp.*` / `security.*`
- **`SprinkleClawAutoConfiguration`**：`@Bean Loom`（按 properties 装配 `LoomBuilder`，含 metrics / mcp servers）
- **`GatewayAutoConfiguration`**：`sprinkle-loom.gateway.enabled=true` 时按配置装配 `GatewayFilterChain`、`ApiKeyStore`、`RateLimiter`、`AccessControlList`
- **`ActuatorAutoConfiguration`**：可选条件装配 `SprinkleClawHealthIndicator`（LLM Provider 健康检查）+ `MicrometerAgentMetrics`（实现 `AgentMetrics` SPI 桥接 Micrometer）

#### 新增 —— MCP 模块迁移至官方 SDK

- **依赖：`io.modelcontextprotocol.sdk:mcp:1.1.1`**（`<optional>true</optional>`），通过 `McpAvailability` 在运行时检测，缺失时给出明确指引
- **`icu.sprinkle.loom.mcp.config.McpServerConfig`**：record + Builder，统一覆盖 STDIO / SSE / STREAMABLE_HTTP 三种传输模式
- **`icu.sprinkle.loom.mcp.config.McpTransportFactory`**：根据配置创建 SDK 原生 `McpClientTransport`
- **`icu.sprinkle.loom.mcp.bridge`**：`McpToolAdapter` / `McpToolProvider` / `McpToolDefinitionMapper`，把 SDK `McpSyncClient` 暴露的远端工具桥接为 SC 的 `AgentTool`
- **`icu.sprinkle.loom.mcp.health`**：`McpAvailability`（运行时检测 SDK）+ `McpHealthState`（UP/DEGRADED/DOWN，连续 3 次失败转 DOWN）
- **`icu.sprinkle.loom.mcp.lifecycle`**：`McpProcessManager`（封装 SDK 客户端 + 握手）/ `McpServerRegistry`（多服务器聚合 + 30 秒周期 ping 探活）
- **`icu.sprinkle.loom.mcp.error.McpErrorMapper`**：SDK 异常 → `ToolResult.error`，隔离上层
- **`icu.sprinkle.loom.mcp.server.SprinkleClawMcpServer` + `ToolRegistryBridge`**：基于官方 SDK `McpSyncServer` 重写服务端，把 `ToolRegistry` 暴露给外部 MCP 客户端（如 Claude Desktop / MCP Inspector）
- **主链路接入**：`LoomBuilder.enableMcp(List<McpServerConfig>)` / `addMcpServer(...)`；`Loom` 实现 `AutoCloseable`，构建期分配的 MCP 资源在 `close()` 中统一释放
- **Spring Boot 装配**：`SprinkleClawProperties.Mcp.servers[]` 支持 `id/transport/command/args/env/url/headers/requestTimeout`，`SprinkleClawAutoConfiguration` 自动映射并调用 `enableMcp(...)`

#### 移除（破坏性）—— 旧自研 MCP

- 整目录删除：`icu.sprinkle.loom.mcp.protocol`（`JsonRpc` / `McpError` / `McpMethod`）
- 整目录删除：`icu.sprinkle.loom.mcp.transport`（`McpTransport` / `StdioTransport` / `SseTransport`）
- 整目录删除：`icu.sprinkle.loom.mcp.client`（`McpClient` / `DefaultMcpClient` / `McpClientConfig`）
- 整目录删除：`icu.sprinkle.loom.mcp.tool`（`McpToolAdapter` / `McpToolProvider` / `McpToolDefinitionMapper` —— 已迁至 `bridge` 包，签名改为接受 SDK 类型）
- 旧 server：`icu.sprinkle.loom.mcp.server.{McpServer, McpServerConfig, McpRequestHandler}`
- 直接依赖 `com.fasterxml.jackson.core:jackson-databind` 已从 `sprinkle-loom-mcp` 中移除（SDK 自带 Jackson 3 `tools.jackson.*`）

#### 兼容性

- 项目此前无外部用户，故 MCP 采用原子替换、未保留 `Sdk*` 过渡前缀；调用方需将 `enableMcp(...)` 改为基于 `McpServerConfig` 的新 API
- `sprinkle-loom-mcp` 主代码量 1505 → ~830 行（约 -45%）
- Gateway / Spring Boot Starter 为新增模块，对历史 API 无影响

## [0.6.0] - 2026-04-13

### 新增

#### ToolChoice 策略与能力声明
- **`ToolChoice` sealed interface**：4 种策略 —— `Auto` / `None` / `Required` / `Forced(toolName)`，嵌入 `ChatRequest` 作为可选策略字段
- **`LlmCapabilities` 扩展**：新增 `supportsToolChoice` / `supportsStreaming` / `supportsToolUse` 三个布尔能力声明，保留 4 参数向后兼容构造器
- **Anthropic / OpenAI Provider**：实现 `serializeToolChoice()` 将策略映射为各自 API 格式

#### Ollama 本地 LLM 提供者 (`sprinkle-loom-llm-ollama`)
- **`OllamaProvider`**：实现 `LlmProvider` SPI，支持同步 `/api/chat` 调用
- **`OllamaStreamParser`**：NDJSON 流消费（非 SSE），支持 `StreamCallback` token/工具回调
- **`OllamaCapabilityRegistry`**：已知模型能力查表（Llama 3.x / Qwen 2.5 / Mistral / DeepSeek 等），标签剥离 + 未知模型保守默认值
- **`OllamaToolBridge`**：Prompt 注入式工具调用降级 —— 将工具 Schema 注入系统提示词，解析围栏/裸 JSON 响应提取工具调用
- **`OllamaProtocolMapper`**：请求/响应序列化 —— 工具、选项、keepAlive 映射
- **`OllamaHttpClient`**：JDK HttpClient 封装 `/api/chat` 和 `/api/tags` 端点
- **`OllamaConfig` record**：host / model / keepAlive / numCtx / timeout + Builder + `from(LlmConfig)` 工厂
- **`OllamaProviderFactory`**：SPI 工厂，`supports("ollama")` 自动发现

#### 声明式 Agent 与工作流编排 (`sprinkle-loom-workflow`)
- **`@Agent` 注解 + `AgentFactory`**：接口代理模式 —— `@Agent(model, temperature)` + `@UserMessage` + `@SystemPrompt` 注解，JDK Proxy 动态生成代理
- **结构化输出**：双模式解析 —— `GENERATE_RESPONSE_TOOL`（强制工具调用）和 `PROMPT_JSON`（Schema 注入 + 自纠正重试）
- **`JsonSchemaGenerator`**：Java 类型 → JSON Schema 转换（原语/枚举/Record/List/Map/嵌套 + `@Description` 注解）
- **`JsonExtractor`**：从 LLM 文本输出中提取 JSON（围栏块/裸 JSON/嵌入式）
- **`StructuredOutputParser`**：解析/校验/反序列化 + `ParseResult` sealed interface（Success/Retry/Fatal）
- **六模式编排引擎**：
  - `SequentialWorkflow`：链式顺序执行，支持 FAIL_FAST / CONTINUE 策略
  - `ParallelWorkflow`：虚拟线程并行执行 + 合并函数 + FAIL_FAST / CONTINUE
  - `DagWorkflow`：有向无环图拓扑排序执行，DFS 三色环检测，多上游 Map 输入合并
  - `LoopWorkflow`：循环执行 + 反馈函数 + 终止条件 + 最大迭代 + 停滞检测
  - `ConditionalWorkflow`：谓词条件路由（then/otherwise 二分支）
  - `RouterWorkflow`：多路由分发 + 默认路由 + 无匹配失败
- **`WorkflowBuilder`**：类型安全的 Fluent API 构建器
- **`WorkflowContext`**：执行上下文 + 取消传播 + 共享属性 + 步骤记录
- **`WorkflowLoopGuard`**：滑动窗口哈希停滞检测

#### MCP 协议适配 (`sprinkle-loom-mcp`)
- **协议层**：`JsonRpc` JSON-RPC 2.0 消息 record（Request/Response/Error）、`McpMethod` 方法常量、`McpError` 错误码
- **传输层**：`McpTransport` SPI + `StdioTransport`（子进程 stdin/stdout）+ `SseTransport`（HTTP SSE）
- **客户端**：`McpClient` 门面接口 + `DefaultMcpClient` 实现（initialize 握手 / tools/list / tools/call / ping）
- **工具桥接**：`McpToolAdapter`（MCP 远程工具 → AgentTool）+ `McpToolProvider`（实现 ToolProvider SPI）+ `McpToolDefinitionMapper`（Schema 映射）
- **服务端**：`McpServer` Stdio 模式 + `McpRequestHandler` JSON-RPC 分发（将 SDK AgentTool 暴露为 MCP 工具）
- **生命周期**：`McpProcessManager`（子进程启动 + 初始化握手）+ `McpServerRegistry`（多服务器注册表）

### 测试
- Ollama 模块：4 个测试类 32 用例
- Workflow Agent：5 个测试类 37 用例
- Workflow 编排：7 个测试类 43 用例
- MCP 模块：5 个测试类 27 用例

### 修复
- `OllamaToolBridge.toPromptTools()` 中 Jackson ObjectNode 转字符串的 ClassCastException

---

## [0.5.0] - 2026-04-11

### 新增

#### 流式输出（AgentEvent + LLM SSE）
- **`AgentEvent` sealed interface**：15 种事件 record —— `LlmToken` / `LlmThinkingToken` / `LlmCallStart` / `LlmCallEnd` / `ToolStart` / `ToolEnd` / `Compaction` / `IterationComplete` / `AgentComplete` / `AgentError` / `ErrorRecovered` / `FallbackActivated` / `SessionResumed` / `ApprovalRequired` / `ApprovalResolved`
- **`StreamCallback` SPI**：流式回调接口，包含 `onToken` / `onThinkingToken` / `onToolUseInput` / `onContentBlockStart` / `onContentBlockStop` 五个方法
- **`LlmProvider.streamChat()`**：默认方法签名，支持流式请求分发
- **Anthropic SSE 流式实现**：`SseLineParser`（多行 data 拼接）+ `AnthropicResponseCollector`（增量 → ChatResponse 累积）+ thinking/tool_use delta 分发
- **OpenAI 兼容 SSE 流式实现**：`SseLineParser`（`[DONE]` 哨兵检测）+ `OpenAiResponseCollector`（delta 累积）+ 并行 tool_calls 支持
- **`AgentLoop.runStreaming()`**：返回 `Flow.Publisher<AgentEvent>`，`SubmissionPublisher(256, DROP_OLDEST)` + 虚拟线程运行循环，StreamCallback 桥接事件发射
- **`Claw.runStreaming()`**：委托方法，返回流式事件 Publisher
- **`SseEventAdapter`**：`AgentEvent` → SSE 格式字符串，单调递增 id + Last-Event-ID replay 支持

#### 引擎韧性框架
- **`ErrorType` 枚举**：13 种错误类型（`RATE_LIMIT` / `AUTH_FAILED` / `CONTEXT_OVERFLOW` / `MODEL_UNAVAILABLE` / `NETWORK_ERROR` / `TIMEOUT` / `INVALID_REQUEST` / `TOOL_EXECUTION` / `OUTPUT_TRUNCATED` / `STREAM_IDLE` / `INTERRUPTED` / `INTERNAL_ERROR` / `UNKNOWN`）
- **`ErrorClassifier` SPI + `DefaultErrorClassifier`**：HTTP 状态码映射 + errorCode/message 模式匹配，识别 Anthropic / OpenAI 错误响应
- **`ErrorRecovery` sealed interface**：8 种恢复策略（`Retry` / `Abort` / `Fallback` / `CompactAndRetry` / `TruncateAndRetry` / `EscalateOutput` / `Suspend` / `Ignore`）
- **`ErrorRecoveryMatrix`**：错误类型 × 尝试次数 → 恢复策略决策矩阵，含指数退避、降级切换、压缩重试等
- **`RecoveryContext` record**：attempt / hasFallbackModel / retryAfter / failureReason 恢复决策上下文
- **`ResilienceConfig` record**：韧性配置（maxRetries / baseBackoff / maxBackoff / fallbackThreshold / idleTimeout）
- **`FallbackProvider`**：主/备 LLM Provider 包装，consecutiveFailures 计数 + 阈值触发自动切换 + 恢复窗口
- **`MaxOutputTokensEscalator`**：工具输出截断时三级 maxOutputTokens 升级（4096 → 8192 → 16384）
- **`StreamIdleWatchdog` + `StreamIdleTimeoutException`**：SSE 空闲超时检测（默认 90s），防止流式请求永久挂起
- **`InterruptContext` record**：中断上下文，记录中断原因与 Suspend 恢复数据
- **`LoopTrace` record**：结构化迭代记录（attempt / error / recoveryAction / duration）
- **`HookManager`**：按 `LoopHook.priority()` 排序执行，Skip 短路所有后续 Hook

#### 工具行为标记与预处理管线
- **`RiskLevel` 枚举**：LOW / MEDIUM / HIGH，用于 HITL 审批分级
- **`AgentTool` 默认方法**：`isReadOnly()` / `isConcurrencySafe()` / `riskLevel()` 行为声明
- **`@Tool` 注解扩展**：新增 `readOnly` / `concurrencySafe` / `riskLevel` 属性
- **`AnnotatedToolAdapter`**：读取新注解属性并映射为 AgentTool 行为方法
- **内置工具行为声明**：`ReadFileTool` / `WriteFileTool` / `EditFileTool` / `BashTool` / `TodoWriteTool` / `CompactTool` 分别标注读写性、并发安全性与风险等级
- **`ConcurrencyAwareToolExecutor`**：工具调用分区执行 —— concurrencySafe 工具并行（虚拟线程）+ 非安全工具串行，IndexedCall/IndexedResult 保持原始顺序
- **`ToolDefinitionSorter`**：Partition-Sort 工具定义稳定排序（内置工具固定序 + 外部工具按名），提升 LLM prompt cache 命中率
- **`LoopPreProcessor`**：5 阶段统一预处理管线 —— ToolResultBudget → MicroCompact → PruneCompact → AutoCompact → NotificationDrain
- **`PreProcessReport` record**：预处理报告（MicroCompactResult / CompactResult / 总耗时 / 节省 token）+ Builder
- **`ContentHashValidator`**：SHA-256 双重校验文件外部修改，mtime 快速路径 + size check + 内容哈希，过滤云盘同步 mtime 误报

#### 状态持久化 SPI
- **`StatePersistable` 接口**：通用组件状态持久化 SPI（`stateId` / `saveState` / `restoreState` / `isDirty`）
- **`StateManager`**：组件注册表 + `collectStates()` 仅收集 dirty 组件 + `restoreAll()` 分发状态，CopyOnWriteArrayList 并发安全

#### HITL 异步审批
- **`ApprovalRequest` record**：审批请求（requestId / toolName / input / description / riskLevel / timestamp / timeout），默认 5 分钟超时
- **`ApprovalResponse` record**：审批响应（approved / reason / modifiedInput / respondedAt）+ 静态工厂 `approve` / `approveWithModifiedInput` / `deny` / `timeout`
- **`ApprovalCallback` 函数式接口**：SDK 嵌入模式审批逻辑自定义
- **`ApprovalManager`**：双模式审批管理 —— SDK 嵌入模式（虚拟线程中回调）+ Server 模式（`resolve()` 外部端点），CompletableFuture 阻塞虚拟线程 + 超时自动拒绝

#### AgentConfig 新增配置
- **`resilienceConfig`**：引擎韧性配置（默认 `ResilienceConfig.DEFAULT`）
- **`enableFallback`**：是否启用 Fallback 模型降级

#### LoopHook 增强
- **`priority()` 默认方法**：Hook 执行优先级（低值高优先，默认 100）

### 变更

#### AnthropicProvider / OpenAiCompatibleProvider
- 新增 `streamChat()` 实现：`stream=true` + `BodyHandlers.ofLines()` + SseLineParser + Collector，thinking/tool_use delta 实时分发到 StreamCallback

#### AgentLoop 流程增强
- 错误处理路径重构：`ErrorClassifier` → `ErrorRecoveryMatrix` → 恢复策略执行（重试 / 降级 / 压缩重试 / 截断重试 / 输出升级 / 挂起）
- 新增 `runStreaming()` 虚拟线程循环 + StreamCallback 事件发射

### 测试
- **SseLineParserTest (Anthropic)**：11 个测试（简单事件、默认事件类型、多行 data、注释、reset、连续事件）
- **AnthropicResponseCollectorTest**：8 个测试（文本累积、tool_use、thinking、unfinalizedBlocks、stopReason 映射、无效 JSON 回退）
- **SseLineParserTest (OpenAI)**：7 个测试（`[DONE]` 哨兵检测）
- **OpenAiResponseCollectorTest**：9 个测试（并行 tool calls、文本+工具组合、finishReason 映射、null content 处理）
- **SseEventAdapterTest**：10 个测试（SSE 格式输出、单调 id、Last-Event-ID replay、日志 trim）
- **DefaultErrorClassifierTest**：HTTP 状态码映射覆盖
- **ErrorRecoveryMatrixTest**：12 种错误类型 × 不同重试次数
- **FallbackProviderTest**：主模型失败 N 次后切换备用 + 恢复窗口
- **HookManagerTest**：priority 排序 + Skip 短路
- **ToolDefinitionSorterTest**：9 个测试（幂等排序验证）
- **PreProcessReportTest**：8 个测试
- **ContentHashValidatorTest**：8 个测试（mtime-only 变更 false、内容变更 true、size check 跳过 hash）
- **ToolBehaviorMarkersTest**：6 个测试
- **StateManagerTest**：8 个测试
- **ApprovalManagerTest**：7 个测试（callback 批准 / 拒绝 / 修改输入、超时拒绝、外部 resolve、回调异常）

---

## [0.4.0] - 2026-04-04

### 新增

#### 新模块：`sprinkle-loom-agent-ext`
- **`SubAgentSpawner`**：子 Agent 派生核心逻辑，隔离上下文 + 独立 LoopGuard + 工具过滤 + 摘要返回
- **`SubAgentConfig`**：子 Agent 配置 record（轮次限制、超时、工具黑白名单、模型覆盖），内置 `explore()` / `execute()` / `plan()` 三种预设
- **`SubAgentTool`**：`sub_agent` 工具封装，支持 task / tools / max_iterations / model 参数
- **`SkillLoader`**：Skill 两层加载器，Layer1 system prompt 元数据注入 + Layer2 `load_skill` 按需加载
- **`SkillEntry`**：Skill 元数据 record（name, description, tags, body, path）
- **`SkillRegistry`**：Skill 注册表，按名称索引
- **`SkillFrontmatterParser`**：YAML Frontmatter 纯手写解析（不引入 YAML 库）
- **`LoadSkillTool`**：`load_skill` 工具封装
- **`TaskManager`**：持久化任务板管理（CRUD + blockedBy/blocks 依赖图 + 完成时自动清除依赖）
- **`Task`**：任务 record，含状态（pending/in_progress/completed/cancelled）和依赖关系
- **`TaskStore` SPI + `FileTaskStore`**：`.tasks/` 目录 JSON 文件持久化
- **`TaskCreateTool` / `TaskUpdateTool` / `TaskListTool` / `TaskGetTool`**：任务板 CRUD 工具集
- **`BackgroundManager`**：后台任务管理，Virtual Thread 非阻塞执行 + 完成通知队列 + 取消支持
- **`BackgroundTask`**：后台任务状态 record（含 stdout/stderr 输出）
- **`TaskNotification`**：完成通知 record
- **`BackgroundNotificationHook`**：每轮 LLM 调用前 drain 通知队列，自动注入完成通知
- **`BackgroundRunTool` / `CheckBackgroundTool`**：`background_run`（非阻塞执行）+ `check_background`（查询/取消）工具
- **`IdentityReinjectHook`**：上下文压缩后自动注入 `<identity>` 块恢复 Agent 身份
- **`InputSanitizer`**：输入安全检查器（长度限制 + 5 种 prompt injection 模式检测）
- **`TrustBoundaryFilter`**：信任边界过滤器 LoopHook，检测工具输入中的注入尝试（告警/拦截可配置）
- **8 个工具描述文件**：`sub_agent.txt` / `load_skill.txt` / `task_create.txt` / `task_update.txt` / `task_list.txt` / `task_get.txt` / `background_run.txt` / `check_background.txt`

#### 上下文压缩增强（`sprinkle-loom-core`）
- **`MicroCompactor` 多策略扩展**：去重（SHA-256 input hash，相同工具+参数保留最新）+ 错误裁剪（N 轮后清除失败工具的 input）+ 受保护工具列表
- **`PruneCompactor` 受保护工具**：新增 `protectedTools` 参数，跳过指定工具的输出
- **`AutoCompactor` 锚定迭代摘要**：首次全量摘要，后续增量合并（替代全量重建），通过 compaction count 追踪
- **`ToolOutputTruncator` 大输出转文件引用**：超大工具输出（可配置阈值）写入 `.sprinkle-loom/large-outputs/`，上下文仅保留文件路径引用
- **`TokenEstimator` jtokkit 集成**：可选依赖 `com.knuddels:jtokkit:1.1.0`，使用 CL100K_BASE 编码精确计算 token，反射加载失败时回退到字符估算

#### MVP1 补丁：推理模型支持 + 能力声明
- **`ReasoningEffort` 枚举**：LOW / MEDIUM / HIGH，映射 OpenAI `reasoning_effort` 参数
- **`ThinkingConfig` 泛化**：新增 `reasoningEffort` 字段 + `withEffort()` 工厂方法，兼容旧双参数构造
- **`Usage.reasoningTokens`**：新增推理 token 计数字段，兼容旧双参数构造
- **`LlmCapabilities` record**：能力声明（`supportsReasoning` / `supportsStructuredOutput` / `contextWindowTokens` / `maxOutputTokens`）+ Builder API
- **`LlmProvider.capabilities()`**：新增默认方法，返回 Provider 的能力声明
- **OpenAI Provider reasoning 支持**：`buildRequestBody()` 注入 `reasoning_effort`；`parseResponse()` 解析 `completion_tokens_details.reasoning_tokens`
- **Anthropic / OpenAI `capabilities()` 覆盖**：各 Provider 声明实际能力参数

#### LoomBuilder 集成 MVP3
- **`enableSubAgent()` / `enableSkill()` / `enableTaskBoard()` / `enableBackgroundTasks()`**：细粒度启用扩展
- **`enableExtensions()`**：一键启用所有 agent-ext 扩展
- **`identityPrompt(String)`**：设置 Agent 身份提示（压缩后重注入）
- **`ExtensionRegistrar`**：classpath 检测 agent-ext 模块，自动注册扩展工具和 Hook

#### AgentConfig 新增配置
- **`enableSubAgent`** / **`subAgentMaxIterations`** / **`subAgentToolFilter`**：子 Agent 配置
- **`enableSkill`** / **`skillsDirectory`**：Skill 加载配置
- **`enableTaskBoard`** / **`tasksDirectory`**：任务板配置
- **`enableBackgroundTasks`** / **`backgroundTaskTimeout`**：后台任务配置
- **`protectedTools`**：受保护工具集合（Micro/Prune 压缩跳过）
- **`identityPrompt`**：Agent 身份提示
- **`largeOutputFileThreshold`**：大输出转文件引用阈值

#### ToolRegistry 增强
- **`copyWithout(Set<String>)`**：黑名单过滤，返回排除指定工具的新注册表
- **`copyWithOnly(List<String>)`**：白名单过滤，返回仅包含指定工具的新注册表

### 变更

#### Javadoc 修正
- `ThinkingBlock`：移除"仅 Anthropic 支持"标注，改为通用描述
- `ChatRequest.thinkingConfig`：更新为"Anthropic 使用 budgetTokens，OpenAI 使用 reasoningEffort"

#### 依赖变更
- `sprinkle-loom-core` 新增可选依赖 `com.knuddels:jtokkit:1.1.0`（Token 精确估算）
- `sprinkle-loom-bootstrap` 新增可选依赖 `sprinkle-loom-agent-ext`（classpath 存在时自动注册扩展）

### 测试
- **MicroCompactorTest**：6 个测试（去重保留最新、不同输入不去重、受保护工具不去重、错误裁剪、近期错误保留、受保护工具不替换）
- **TokenEstimatorTest**：5 个测试（英文/中文估算、null/空处理、消息列表求和、forModel 回退）
- **SkillLoaderTest**：9 个测试（目录扫描、多 Skill、不存在目录、内容加载、system prompt 段、frontmatter 解析错误）
- **TaskManagerTest**：10 个测试（CRUD、依赖图、完成清除依赖、无效状态、列表/未认领、认领、阻塞认领、跨实例持久化）
- **BackgroundManagerTest**：5 个测试（运行返回 ID、获取任务、drain 通知、取消运行中任务、列出所有任务）

---

## [0.3.0] - 2026-03-27

### 新增

#### 三层上下文压缩
- **`TokenEstimator`**：CJK 自适应 token 估算器，根据中日韩字符比例动态调整分词因子
- **`MicroCompactor`**：每轮执行的微压缩，将旧 tool_result 替换为占位符文本，保留最近 N 个
- **`PruneCompactor`**：基于动态阈值的裁剪压缩，保护区大小随模型上下文窗口自适应（20%，40K-200K）
- **`AutoCompactor`**：LLM 结构化摘要压缩，生成 Goal/Instructions/Discoveries/Accomplished/Files 五段式摘要，压缩前将完整会话存入 transcript 文件
- **`ContextManager`**：三层压缩调度中枢，支持 API usage 精确溢出判断（双轨策略）+ 手动压缩请求处理
- **`CompactionResult`**：压缩结果 record，包含压缩类型、前后 token 数、摘要内容

#### 会话管理与持久化
- **`SessionStore` SPI**：会话持久化接口，定义 save/load/delete/listSessions 操作
- **`InMemorySessionStore`**：基于 ConcurrentHashMap 的内存会话存储
- **`FileSessionStore`**：基于文件系统的会话存储，使用原子写入（temp + rename）保证数据安全，列表操作使用 Jackson Streaming API 优化性能
- **`SessionSnapshot`**：会话快照 record，封装完整会话状态（消息、配置、元数据）
- **`SessionManager`**：会话生命周期管理器，支持创建、恢复、手动保存和自动保存
- **`SessionObjectMapper`**：Jackson Mixin 序列化方案，无需修改 protocol 模块即可处理 sealed interface 多态

#### TodoWrite 工具
- **`TodoWriteTool`**：结构化任务管理工具，支持创建/合并/替换待办事项列表
- **`TodoState`**：待办事项状态 record，支持合并模式（按 id 更新）和替换模式（全量替换），以及压缩时的活跃项精简
- **`TodoItem`**：单个待办事项 record（id、content、status）
- **`TodoReminderHook`**：Nag Reminder 钩子，连续 N 轮未更新 todo 时注入提醒，压缩后自动注入 todo-snapshot

#### Compact 工具
- **`CompactTool`**：手动上下文压缩工具，模型可主动触发压缩释放 token 空间，支持 focus 参数指定摘要焦点

#### 文件快照与变更追踪
- **`FileSnapshot` SPI**：文件快照接口，支持 snapshot/undo/redo/history 操作
- **`NoopFileSnapshot`**：默认空实现，未启用快照时使用
- **`GitFileSnapshot`**：基于独立 Shadow Git 仓库的实现，不影响项目版本控制；自动跳过大文件（>10MB）和二进制文件；Git 操作 synchronized 串行化
- **`FileTimestampCache`**：文件时间戳缓存，记录 Agent 读写文件时的 lastModified，编辑前校验外部修改
- **`SnapshotException`**：快照操作异常类

#### ToolContext 扩展
- **`ToolContext`**：从 record 重构为 class，新增 `attributes` 通用属性映射，支持 `getAttribute`/`setAttribute` 方法，实现 AgentLoop 与工具间的数据共享
- **`FileToolHelper`**：文件工具辅助类，通过 MethodHandle 反射桥接 core 模块的 FileTimestampCache 和 FileSnapshot，保持 tool-builtin 模块的轻量依赖

#### AgentConfig 新增配置
- **`compactionThreshold`**（默认 100K）：触发自动压缩的 token 阈值
- **`microCompactKeepRecent`**（默认 3）：微压缩保留最近 N 个 tool_result
- **`pruneProtectTokens`**/**`pruneMinimumTokens`**：Prune 保护区与最小裁剪量
- **`persistSessions`**：是否启用会话持久化
- **`sessionDirectory`**：会话存储目录
- **`autoSaveInterval`**（默认 5）：每 N 轮自动保存
- **`enableTodoWrite`**：是否启用 TodoWrite 工具
- **`todoNagThreshold`**（默认 3）：Todo 提醒间隔
- **`enableFileSnapshot`**：是否启用文件快照追踪
- **`toolOutputDynamicTruncation`**：工具输出截断阈值是否随上下文使用率动态调整
- **`modelContextWindow`**：模型上下文窗口大小

#### Loom API 扩展
- **`chat(String)`**：连续上下文多轮对话
- **`resume(String, String)`**：从磁盘恢复会话并继续对话
- **`sessionId()`**：获取当前会话 ID
- **`listSessions()`**：列出所有可恢复的会话

#### LoomBuilder 新增配置方法
- **`enableTodoWrite()`**：一键启用 TodoWrite 工具 + TodoReminderHook
- **`enableFileSnapshot()`**：一键启用 GitFileSnapshot + FileTimestampCache
- **`sessionStore(SessionStore)`**：设置会话存储后端
- **`compactionThreshold(int)`**：设置压缩阈值（同时自动注册 CompactTool）
- **`modelContextWindow(int)`**：设置模型上下文窗口大小

### 变更

#### 内置工具文件安全增强
- `ReadFileTool`：读取成功后自动记录文件时间戳到 FileTimestampCache
- `WriteFileTool`：写入前对已存在文件进行时间戳校验（检测外部修改），写入后记录时间戳并创建快照
- `EditFileTool`：编辑前进行时间戳校验，编辑后记录时间戳并创建快照

#### AgentLoop 流程增强
- 每轮迭代开始时执行 `ContextManager.compactIfNeeded()` 进行压缩调度
- 将 API usage 写入 AgentContext 供精确溢出判断
- ToolContext 共享 AgentContext 的 attributes 映射
- 跟踪 todo_write 工具使用状态，支持 TodoReminderHook

#### AgentContext 增强
- 新增 `mutableAttributes()` 方法供 ToolContext 共享属性映射
- `setAttribute()` 支持 null 值（自动移除键）
- 新增 `mutableMessages()`、`replaceMessages()`、`prependMessage()` 支持压缩操作
- 新增 `invalidateTokenCache()`、`cachedTokenCount()`、`setCachedTokenCount()` 支持 token 缓存
- 新增 `updateTokenUsage()`、`lastTokenUsage()` 支持 API usage 精确判断
- 新增 `modelContextWindow()`、`modelMaxOutputTokens()` 模型元数据
- 新增 `recordCompaction()`、`compactionCount()`、`lastCompactionAt()` 压缩统计

#### LoopHook 新增钩子
- **`afterCompaction(AgentContext, CompactionResult)`**：压缩完成后触发，用于注入提醒或记录统计

#### ToolOutputTruncator 自适应截断
- 新增 `computeMaxOutputBytes()` 静态方法，根据上下文使用率动态调整截断阈值（50KB→20KB→10KB）
- `ToolExecutor.executeAll()` 支持传入动态截断字节上限

#### 依赖变更
- `sprinkle-loom-core` 新增 `jackson-datatype-jsr310` 依赖（用于 Instant 序列化）
- `sprinkle-loom-bootstrap` 新增 `sprinkle-loom-tool-builtin` 依赖（用于注册 TodoWriteTool/CompactTool）

---

## [0.2.0] - 2026-03-22

### 新增

#### 工具执行拦截
- **`ToolInterception` sealed interface**：定义 `Continue`（继续执行）、`Skip`（跳过并返回原因）、`Modify`（修改输入参数后执行）三种拦截决策
- **`LoopHook.beforeToolExecution()` 钩子**：新增生命周期回调点，在工具执行前允许 Hook 拦截、跳过或修改工具调用，返回 `ToolInterception` 决策

#### 安全策略增强
- **`PolicyRule` record**：定义 `(permission glob, pattern glob, decision)` 三元组规则
- **`GlobToolPolicy` 实现**：基于 Glob 模式匹配的工具安全策略，采用 Last-match-wins 评估策略
- **默认安全规则**：内置 `.env`/`.git/config`/`id_rsa` 等敏感文件拒绝规则，`rm -rf /`/`mkfs`/`dd if=` 等危险命令拒绝规则

#### 循环安全增强
- **`ToolLoopDetector`**：Doom Loop 滑动窗口检测器，跟踪最近工具调用的哈希值，连续相同调用超过阈值（默认 4 次）即判定为死循环
- **`LoopGuard` 集成 Doom Loop 检测**：新增 `checkToolLoop()` 方法，在 `AgentLoop` 每次工具调用前进行死循环检测
- **`AgentLoop` 优雅处理 `LoopExhaustedException`**：循环耗尽时不再抛出异常，而是 break 退出并正常返回 `AgentResult`

#### 工具输出管理
- **`ToolOutputTruncator`**：工具输出截断器，超过 2000 行或 50KB 时将完整输出保存到临时文件（`.sprinkle-loom/truncated/`），返回截断预览并附带文件路径提示
- **`ToolExecutor` 集成输出截断**：工具执行结果自动经过截断处理后再反馈给 LLM

#### EditFileTool 5 层回退匹配
- **`SimpleReplacer`**：精确字符串匹配（第 1 层）
- **`LineTrimmedReplacer`**：逐行 trim 后比较（第 2 层）
- **`WhitespaceNormalizedReplacer`**：所有空白符归一化比较（第 3 层）
- **`IndentationFlexibleReplacer`**：仅比较非空白前缀，忽略缩进差异（第 4 层）
- **`BlockAnchorReplacer`**：基于 Levenshtein 距离的模糊锚定匹配（第 5 层，相似度 ≥ 80%）
- **`replace_all` 参数支持**：新增批量替换所有匹配项功能
- **行尾符标准化**：自动统一 CRLF → LF 处理

#### AgentConfig 新增配置
- **`doomLoopThreshold`**（默认 4）：Doom Loop 检测连续重复次数阈值
- **`toolOutputMaxLines`**（默认 2000）：工具输出最大行数
- **`toolOutputMaxBytes`**（默认 51200 / 50KB）：工具输出最大字节数

#### 工具描述外置
- **`ToolDescriptions`**：从 classpath 资源文件（`tools/<toolName>.txt`）加载内置工具描述，无需修改 Java 代码即可调整工具描述文本
- **4 个描述资源文件**：`bash.txt`、`read_file.txt`、`write_file.txt`、`edit_file.txt`

#### 工具参数前置校验
- **`InputSchemaValidator`**：工具执行前的轻量级 JSON Schema 校验器，检查必填字段（required）缺失和字段类型不匹配
- **`ToolExecutor` 集成前置校验**：工具执行前自动校验 LLM 提供的参数，校验失败直接返回错误信息，避免无效执行

#### Context 管理骨架
- **`ContextManager`**：MVP1 空骨架实现，`compactIfNeeded()` 为 no-op，为 MVP2 三层压缩（Prune/Auto-Summary/Hard-Limit）预留接口

### 变更

#### AgentLoop 流程增强
- 工具执行前新增 Doom Loop 检测阶段
- 工具执行前新增 `LoopHook.beforeToolExecution()` 拦截链调用
- `LoopExhaustedException` 从抛出异常改为优雅退出循环

#### ToolExecutor 流程增强
- 构造器新增 `ToolOutputTruncator` 参数
- `executeOne()` 执行前自动进行 `InputSchemaValidator` 参数校验
- `executeOne()` 执行后自动截断超长输出

#### LoomBuilder 集成更新
- 自动构建 `ToolOutputTruncator`（从 `AgentConfig` 读取截断阈值）并注入 `ToolExecutor`

#### 内置工具描述外置
- `BashTool` / `ReadFileTool` / `WriteFileTool` / `EditFileTool` 的 `definition()` 方法改为通过 `ToolDescriptions.load()` 从资源文件动态加载描述

### 修复
- **`JsonSerializationBenchmark`**：修复 `deserializeChatResponse` 因 `ContentBlock` sealed interface 缺少类型信息导致反序列化失败的问题，使用 Jackson Mixin 注入多态类型映射

### 测试
- **LoopGuardTest**：新增 `checkToolLoop_detectsDoomLoop`、`checkToolLoop_resetsOnDifferentTool` 两个 Doom Loop 检测测试
- **EditFileToolTest**：新增 `edit_lineTrimmedFallback`、`edit_indentationFlexibleFallback`、`edit_replaceAll`、`edit_identicalStringsRejected` 四个回退策略测试；适配新错误消息格式

---

## [0.1.0] - 2026-03-21

### 新增

#### 测试体系
- **10 个单元测试类**：覆盖 Protocol、Tool API、Core、Tool-builtin 模块
- **2 个 SPI 契约测试基类**：`LlmProviderContractTest`、`AgentToolContractTest`
- **5 个集成测试场景**：单轮工具调用、多工具并发、循环保护、错误恢复、Hook 生命周期
- **DeepSeek 可用性测试**：简单对话、工具调用、多轮上下文、token 用量验证

#### 性能基准
- **JMH Benchmark** `sprinkle-loom-benchmark`：工具并发执行（1/4/8 工具对比）、JSON 序列化/反序列化吞吐量基线

---

## [0.0.4] - 2026-03-20

### 新增

#### 内置工具
- **4 个内置工具** `sprinkle-loom-tool-builtin`：bash（Shell 命令执行）、read_file（带行号读取）、write_file（自动创建目录）、edit_file（精确字符串替换）
- **`BuiltinToolProvider`**：通过 SPI 自动注册内置工具

#### Bootstrap 启动器
- **Builder API** `LoomBuilder`：流式配置 Agent 实例，支持 `apiKey` / `model` / `workdir` / `maxIterations` / `toolTimeout` 等参数
- **ServiceLoader 自动发现**：自动发现并注册 `LlmProviderFactory` 和 `ToolProvider`
- **API Key 环境变量回退**：依次尝试 `ANTHROPIC_API_KEY` → `OPENAI_API_KEY`

---

## [0.0.3] - 2026-03-19

### 新增

#### Agent Loop 核心引擎
- **核心执行循环** `sprinkle-loom-core`：`while(TOOL_USE)` 主循环，支持 LLM 调用 → 工具执行 → 结果反馈的完整流程
- **Virtual Threads 并发执行**：`ToolExecutor` 使用 `newVirtualThreadPerTaskExecutor()` 并发执行多个工具调用
- **循环保护** `LoopGuard`：最大迭代次数、超时检测、连续重复响应检测
- **Agent 错误处理** `AgentErrorHandler` SPI：LLM 调用失败时支持重试/终止/忽略策略
- **生命周期钩子** `LoopHook`：preLlmCall / postLlmCall / postToolExecution / onLoopEnd 四个回调点
- **系统提示构建** `SystemPromptBuilder`：自动组装工具定义、环境信息和自定义提示
- **Nag Reminder 支持**：通过 `AgentContext.addReminder()` 注入系统级提醒

#### 并发安全
- **AgentContext 线程安全模型**：`messages` 仅主线程修改（返回防御性拷贝）、`attributes` 使用 `ConcurrentHashMap`、`systemReminders` 使用 `CopyOnWriteArrayList`

#### 可观测性
- **AgentMetrics SPI**：LLM 调用次数/延迟、工具调用次数、token 用量等指标采集接口（默认 NoOp）
- **AgentTracer SPI**：循环开始/LLM 响应/循环结束等追踪接口（默认 NoOp）
- **AgentResult**：包含完整对话历史、token 用量、工具执行记录、总耗时等运行结果

---

## [0.0.2] - 2026-03-18

### 新增

#### LLM 集成
- **Anthropic Claude 实现** `sprinkle-loom-llm-anthropic`：基于 JDK HttpClient 调用 Anthropic Messages API，支持工具调用和 Thinking 模式
- **OpenAI 兼容 API 实现** `sprinkle-loom-llm-openai`：一套实现覆盖 OpenAI、DeepSeek、通义千问（Qwen）、智谱 GLM、豆包等所有 OpenAI 兼容厂商
- **LLM Provider 自动检测**：根据模型名称前缀自动选择 Provider（`claude` → Anthropic，其余 → OpenAI 兼容）

#### 工具体系
- **工具 SPI** `sprinkle-loom-tool-api`：`AgentTool` 接口、`ToolRegistry`（线程安全）、`ToolProvider`（动态工具提供）、`ToolPolicy`（安全策略）
- **`@Tool` / `@ToolParam` 注解**：标记方法为工具，通过 `SchemaGenerator` 自动生成 JSON Schema，`AnnotatedToolAdapter` 完成反射适配
- **`@ToolParam` 支持 `name` 属性**：显式指定参数名称，不依赖 `-parameters` 编译标志
- **工具错误恢复**：`ToolErrorHandler` SPI，支持重试/替代结果/传播三种策略

---

## [0.0.1] - 2026-03-17

### 新增

#### 核心架构
- **Maven 多模块工程骨架**：9 个模块（protocol、llm-api、llm-anthropic、llm-openai、tool-api、tool-builtin、core、bootstrap、benchmark）
- **统一协议层** `sprinkle-loom-protocol`：Message、ContentBlock、ChatRequest/Response、ToolDefinition/Result 等数据模型，支持 Anthropic 和 OpenAI 双协议映射
- **LLM Provider SPI** `sprinkle-loom-llm-api`：`LlmProvider`（函数式接口）、`LlmProviderFactory`、`LlmConfig`（含自定义 headers 支持）、`LlmException`（带可重试标识）

---

<!-- 后续版本模板：

## [0.x.0] - YYYY-MM-DD

### 新增
- 新功能

### 变更
- 已有功能的修改

### 废弃
- 即将移除的功能

### 移除
- 已移除的功能

### 修复
- Bug 修复

### ⚠️ 不兼容变更
- 破坏性变更（附迁移指南）

-->
