# 更新日志

本文件记录项目的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/spec/v2.0.0.html)。

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
- **`ToolOutputTruncator`**：工具输出截断器，超过 2000 行或 50KB 时将完整输出保存到临时文件（`.sprinkle-claw/truncated/`），返回截断预览并附带文件路径提示
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

#### ClawBuilder 集成更新
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
- **JMH Benchmark** `sprinkle-claw-benchmark`：工具并发执行（1/4/8 工具对比）、JSON 序列化/反序列化吞吐量基线

---

## [0.0.4] - 2026-03-20

### 新增

#### 内置工具
- **4 个内置工具** `sprinkle-claw-tool-builtin`：bash（Shell 命令执行）、read_file（带行号读取）、write_file（自动创建目录）、edit_file（精确字符串替换）
- **`BuiltinToolProvider`**：通过 SPI 自动注册内置工具

#### Bootstrap 启动器
- **Builder API** `ClawBuilder`：流式配置 Agent 实例，支持 `apiKey` / `model` / `workdir` / `maxIterations` / `toolTimeout` 等参数
- **ServiceLoader 自动发现**：自动发现并注册 `LlmProviderFactory` 和 `ToolProvider`
- **API Key 环境变量回退**：依次尝试 `ANTHROPIC_API_KEY` → `OPENAI_API_KEY`

---

## [0.0.3] - 2026-03-19

### 新增

#### Agent Loop 核心引擎
- **核心执行循环** `sprinkle-claw-core`：`while(TOOL_USE)` 主循环，支持 LLM 调用 → 工具执行 → 结果反馈的完整流程
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
- **Anthropic Claude 实现** `sprinkle-claw-llm-anthropic`：基于 JDK HttpClient 调用 Anthropic Messages API，支持工具调用和 Thinking 模式
- **OpenAI 兼容 API 实现** `sprinkle-claw-llm-openai`：一套实现覆盖 OpenAI、DeepSeek、通义千问（Qwen）、智谱 GLM、豆包等所有 OpenAI 兼容厂商
- **LLM Provider 自动检测**：根据模型名称前缀自动选择 Provider（`claude` → Anthropic，其余 → OpenAI 兼容）

#### 工具体系
- **工具 SPI** `sprinkle-claw-tool-api`：`AgentTool` 接口、`ToolRegistry`（线程安全）、`ToolProvider`（动态工具提供）、`ToolPolicy`（安全策略）
- **`@Tool` / `@ToolParam` 注解**：标记方法为工具，通过 `SchemaGenerator` 自动生成 JSON Schema，`AnnotatedToolAdapter` 完成反射适配
- **`@ToolParam` 支持 `name` 属性**：显式指定参数名称，不依赖 `-parameters` 编译标志
- **工具错误恢复**：`ToolErrorHandler` SPI，支持重试/替代结果/传播三种策略

---

## [0.0.1] - 2026-03-17

### 新增

#### 核心架构
- **Maven 多模块工程骨架**：9 个模块（protocol、llm-api、llm-anthropic、llm-openai、tool-api、tool-builtin、core、bootstrap、benchmark）
- **统一协议层** `sprinkle-claw-protocol`：Message、ContentBlock、ChatRequest/Response、ToolDefinition/Result 等数据模型，支持 Anthropic 和 OpenAI 双协议映射
- **LLM Provider SPI** `sprinkle-claw-llm-api`：`LlmProvider`（函数式接口）、`LlmProviderFactory`、`LlmConfig`（含自定义 headers 支持）、`LlmException`（带可重试标识）

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
