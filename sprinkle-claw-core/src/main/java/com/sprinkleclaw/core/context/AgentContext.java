package com.sprinkleclaw.core.context;

import com.sprinkleclaw.core.AgentConfig;
import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.llm.Usage;
import com.sprinkleclaw.protocol.message.Message;
import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Agent 可变状态上下文，由 AgentLoop 持有和维护。
 *
 * <h3>线程安全契约</h3>
 * <ul>
 *   <li>{@code systemPrompt}、{@code config}、{@code toolDefinitions} 构造后不可变</li>
 *   <li>{@code messages} 仅由 AgentLoop 主线程修改，读取返回防御性拷贝</li>
 *   <li>{@code attributes} 使用 {@link ConcurrentHashMap} 支持跨线程访问</li>
 *   <li>{@code systemReminders} 使用 {@link CopyOnWriteArrayList} 支持 Hook 注入</li>
 *   <li>{@code replaceMessages()}、{@code mutableMessages()} 仅限 AgentLoop 主线程调用</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/3/19
 */
public final class AgentContext {

    private final String systemPrompt;
    private final AgentConfig config;
    private final List<ToolDefinition> toolDefinitions;
    private final List<Message> messages = new ArrayList<>();
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final List<String> systemReminders = new CopyOnWriteArrayList<>();

    // --- MVP2 新增字段 ---

    /**
     * 会话标识（启用持久化后非空）
     */
    private volatile String sessionId;

    /**
     * 本会话逻辑创建时间
     */
    private final Instant createdAt;

    /**
     * 已执行的压缩次数
     */
    private volatile int compactionCount;

    /**
     * 最近一次压缩时间
     */
    private volatile Instant lastCompactionAt;

    /**
     * Token 估算缓存（-1 表示失效）
     */
    private volatile int cachedTokenCount = -1;

    /**
     * 上一轮 LLM 调用返回的实际 token 使用量
     */
    private volatile Usage lastTokenUsage;

    /**
     * 当前模型的上下文窗口大小
     */
    private volatile int modelContextWindow;

    /**
     * 当前模型的最大输出 token 数
     */
    private volatile int modelMaxOutputTokens;

    /**
     * 创建 Agent 上下文。
     *
     * @param systemPrompt    系统提示词
     * @param config          Agent 配置
     * @param toolDefinitions 可用工具定义列表（构造后不可变）
     */
    public AgentContext(String systemPrompt, AgentConfig config, List<ToolDefinition> toolDefinitions) {
        this.systemPrompt = systemPrompt;
        this.config = config;
        this.toolDefinitions = List.copyOf(toolDefinitions);
        this.createdAt = Instant.now();
        this.modelContextWindow = config.modelContextWindow();
    }

    /**
     * 获取系统提示词。
     */
    public String systemPrompt() {
        return systemPrompt;
    }

    /**
     * 获取 Agent 配置。
     */
    public AgentConfig config() {
        return config;
    }

    /**
     * 获取工具定义列表（不可变）。
     */
    public List<ToolDefinition> toolDefinitions() {
        return toolDefinitions;
    }

    /**
     * 获取对话历史快照（不可变拷贝，调用方修改不影响原数据）。
     *
     * @return 消息列表快照
     */
    public List<Message> messages() {
        return List.copyOf(messages);
    }

    /**
     * 追加消息到对话历史。仅限 AgentLoop 主线程调用。
     *
     * @param message 要追加的消息
     */
    public void addMessage(Message message) {
        messages.add(message);
    }

    /**
     * 追加用户文本消息。仅限 AgentLoop 主线程调用。
     *
     * @param text 用户输入文本
     */
    public void appendUserMessage(String text) {
        messages.add(Message.UserMessage.of(text));
    }

    /**
     * 追加助手响应消息。仅限 AgentLoop 主线程调用。
     *
     * @param response LLM 响应
     */
    public void appendAssistantMessage(ChatResponse response) {
        messages.add(new Message.AssistantMessage(response.content(), response.stopReason()));
    }

    /**
     * 批量追加工具执行结果消息。仅限 AgentLoop 主线程调用。
     *
     * @param results 工具结果列表
     */
    public void appendToolResults(java.util.List<ToolResult> results) {
        for (ToolResult result : results) {
            messages.add(result.isError()
                    ? Message.ToolResultMessage.error(result.toolCallId(), result.output())
                    : Message.ToolResultMessage.success(result.toolCallId(), result.output()));
        }
    }

    /**
     * 设置自定义属性（线程安全）。null 值会移除对应键。
     *
     * @param key   属性键
     * @param value 属性值（null 时移除该键）
     */
    public void setAttribute(String key, Object value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    /**
     * 获取自定义属性。
     *
     * @param key 属性键
     * @param <T> 值类型
     * @return 属性值，不存在则返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * 添加系统提醒（线程安全），会被注入到有效系统提示中。
     *
     * @param reminder 提醒内容
     */
    public void addReminder(String reminder) {
        systemReminders.add(reminder);
    }

    /**
     * 获取所有系统提醒的快照。
     *
     * @return 提醒列表
     */
    public List<String> reminders() {
        return List.copyOf(systemReminders);
    }

    /**
     * 构建有效系统提示词（原始提示 + 注入的提醒）。
     *
     * @return 完整的系统提示词
     */
    public String effectiveSystemPrompt() {
        if (systemReminders.isEmpty()) {
            return systemPrompt;
        }
        var sb = new StringBuilder(systemPrompt);
        sb.append("\n\n# Active Reminders\n");
        for (String r : systemReminders) {
            sb.append("- ").append(r).append('\n');
        }
        return sb.toString();
    }

    // ========== MVP2 新增方法 ==========

    /**
     * 获取可变消息列表引用，仅限 ContextManager 内部使用。
     *
     * <p><b>线程安全约束</b>：仅限 AgentLoop 主线程调用，
     * 压缩操作发生在 LLM 调用前，与工具执行不存在并发。</p>
     *
     * @return 内部可变消息列表
     */
    public List<Message> mutableMessages() {
        return messages;
    }

    /**
     * 压缩后替换消息历史（先清空再添加新列表）。仅限 ContextManager 调用。
     *
     * @param newMessages 替换后的消息列表
     */
    public void replaceMessages(List<Message> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        invalidateTokenCache();
    }

    /**
     * 在消息历史头部插入消息（如压缩摘要的身份重注入）。
     *
     * @param message 要插入的消息
     */
    public void prependMessage(Message message) {
        messages.addFirst(message);
        invalidateTokenCache();
    }

    /**
     * 注入通知消息到对话末尾（后台任务完成通知等）。
     *
     * @param text 通知文本
     */
    public void injectNotification(String text) {
        messages.add(Message.UserMessage.of("[System Notification] " + text));
        invalidateTokenCache();
    }

    /**
     * 返回 attributes 的不可变快照，供 SessionSnapshot 等外部读取。
     *
     * @return 属性 Map 的不可变拷贝
     */
    public Map<String, Object> allAttributes() {
        return Map.copyOf(attributes);
    }

    /**
     * 返回 attributes 的可变引用，供 ToolContext 共享属性映射。
     *
     * <p>ToolContext 通过此引用与 AgentContext 共享同一个 attributes 实例，
     * 使得工具（如 TodoWriteTool）可以直接读写 AgentContext 的属性。</p>
     *
     * @return 内部可变属性映射
     */
    public Map<String, Object> mutableAttributes() {
        return attributes;
    }

    /**
     * 使 token 估算缓存失效。在消息列表被修改后应调用此方法。
     */
    public void invalidateTokenCache() {
        this.cachedTokenCount = -1;
    }

    /**
     * 获取缓存的 token 估算值。
     *
     * @return 缓存值，-1 表示缓存已失效需要重新估算
     */
    public int cachedTokenCount() {
        return cachedTokenCount;
    }

    /**
     * 更新 token 估算缓存。
     *
     * @param count 新的估算值
     */
    public void setCachedTokenCount(int count) {
        this.cachedTokenCount = count;
    }

    /**
     * 获取会话 ID。
     *
     * @return 会话标识，未启用持久化时为 null
     */
    public String sessionId() {
        return sessionId;
    }

    /**
     * 设置会话 ID。
     *
     * @param sessionId 会话标识
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 获取会话创建时间。
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * 获取已执行的压缩次数。
     */
    public int compactionCount() {
        return compactionCount;
    }

    /**
     * 记录一次压缩操作完成。
     */
    public void recordCompaction() {
        this.compactionCount++;
        this.lastCompactionAt = Instant.now();
    }

    /**
     * 获取最近一次压缩时间。
     *
     * @return 最近压缩时间，未执行过压缩时为 null
     */
    public Instant lastCompactionAt() {
        return lastCompactionAt;
    }

    /**
     * 获取上一轮 LLM 调用返回的实际 token 使用量。
     *
     * @return Usage 对象，不可用时为 null
     */
    public Usage lastTokenUsage() {
        return lastTokenUsage;
    }

    /**
     * 更新 LLM 调用的 token 使用量。由 AgentLoop 在收到 LLM 响应后调用。
     *
     * @param usage LLM 返回的 token 使用量
     */
    public void updateTokenUsage(Usage usage) {
        this.lastTokenUsage = usage;
    }

    /**
     * 获取当前模型的上下文窗口大小。
     *
     * @return 上下文窗口 token 数，0 表示未设置
     */
    public int modelContextWindow() {
        return modelContextWindow;
    }

    /**
     * 设置模型上下文窗口大小。
     *
     * @param contextWindow 上下文窗口 token 数
     */
    public void setModelContextWindow(int contextWindow) {
        this.modelContextWindow = contextWindow;
    }

    /**
     * 获取当前模型的最大输出 token 数。
     *
     * @return 最大输出 token 数，0 表示未设置
     */
    public int modelMaxOutputTokens() {
        return modelMaxOutputTokens;
    }

    /**
     * 设置模型最大输出 token 数。
     *
     * @param maxOutputTokens 最大输出 token 数
     */
    public void setModelMaxOutputTokens(int maxOutputTokens) {
        this.modelMaxOutputTokens = maxOutputTokens;
    }
}
