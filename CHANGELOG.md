# 更新日志

本文件记录项目的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/spec/v2.0.0.html)。

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

#### Claw API 扩展
- **`chat(String)`**：连续上下文多轮对话
- **`resume(String, String)`**：从磁盘恢复会话并继续对话
- **`sessionId()`**：获取当前会话 ID
- **`listSessions()`**：列出所有可恢复的会话

#### ClawBuilder 新增配置方法
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
- `sprinkle-claw-core` 新增 `jackson-datatype-jsr310` 依赖（用于 Instant 序列化）
- `sprinkle-claw-bootstrap` 新增 `sprinkle-claw-tool-builtin` 依赖（用于注册 TodoWriteTool/CompactTool）

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
