package com.sprinkleclaw.core.context;

import com.sprinkleclaw.core.AgentConfig;
import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.message.Message;
import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;

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
     * 设置自定义属性（线程安全）。
     *
     * @param key   属性键
     * @param value 属性值
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
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
}
