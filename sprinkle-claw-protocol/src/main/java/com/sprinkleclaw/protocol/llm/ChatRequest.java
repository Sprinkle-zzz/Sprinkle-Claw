package com.sprinkleclaw.protocol.llm;

import com.sprinkleclaw.protocol.message.Message;
import com.sprinkleclaw.protocol.tool.ToolDefinition;

import java.util.List;
import java.util.Optional;

/**
 * LLM 聊天请求，包含系统提示、对话历史、可用工具及生成参数。
 *
 * @param systemPrompt   系统提示词
 * @param messages       对话历史消息列表
 * @param tools          可用工具定义列表
 * @param maxTokens      最大生成 token 数
 * @param temperature    温度参数（控制随机性）
 * @param stopSequences  停止序列列表
 * @param thinkingConfig 思考/推理模式配置（Anthropic 使用 budgetTokens，OpenAI 使用 reasoningEffort）
 * @param toolChoice     工具选择策略（AUTO/NONE/REQUIRED/Forced），控制 LLM 是否及如何调用工具
 *
 * @author sprinkle
 * @since 2026/3/17
 */
public record ChatRequest(
        String systemPrompt,
        List<Message> messages,
        List<ToolDefinition> tools,
        int maxTokens,
        double temperature,
        List<String> stopSequences,
        Optional<ThinkingConfig> thinkingConfig,
        ToolChoice toolChoice
) {

    /**
     * Compact constructor：兜底 null toolChoice 为 AUTO，保持向后兼容。
     */
    public ChatRequest {
        if (toolChoice == null) {
            toolChoice = ToolChoice.AUTO;
        }
    }

    /**
     * 工具选择策略，控制 LLM 是否及如何调用工具。
     */
    public sealed interface ToolChoice {

        /** 自动决策（默认行为，LLM 自行判断是否调用工具） */
        ToolChoice AUTO = new Auto();

        /** 禁止工具调用 */
        ToolChoice NONE = new None();

        /** 要求 LLM 必须调用至少一个工具 */
        ToolChoice REQUIRED = new Required();

        /** 强制调用指定名称的工具 */
        static ToolChoice forced(String toolName) {
            return new Forced(toolName);
        }

        record Auto() implements ToolChoice {}
        record None() implements ToolChoice {}
        record Required() implements ToolChoice {}
        record Forced(String toolName) implements ToolChoice {}
    }
    /**
     * 创建构建器实例。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * ChatRequest 构建器，提供流式 API 设置各项参数。
     */
    public static final class Builder {
        private String systemPrompt = "";
        private List<Message> messages = List.of();
        private List<ToolDefinition> tools = List.of();
        private int maxTokens = 4096;
        private double temperature = 0.7;
        private List<String> stopSequences = List.of();
        private ThinkingConfig thinkingConfig = null;
        private ToolChoice toolChoice = ToolChoice.AUTO;

        private Builder() {
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder messages(List<Message> messages) {
            this.messages = List.copyOf(messages);
            return this;
        }

        public Builder tools(List<ToolDefinition> tools) {
            this.tools = List.copyOf(tools);
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = List.copyOf(stopSequences);
            return this;
        }

        /**
         * 设置思考/推理模式配置。
         *
         * @param thinkingConfig 思考配置
         * @return 构建器自身
         */
        public Builder thinkingConfig(ThinkingConfig thinkingConfig) {
            this.thinkingConfig = thinkingConfig;
            return this;
        }

        /**
         * 设置工具选择策略。
         *
         * @param toolChoice 工具选择策略
         * @return 构建器自身
         */
        public Builder toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        /**
         * 构建不可变的 ChatRequest 实例。
         */
        public ChatRequest build() {
            return new ChatRequest(systemPrompt, messages, tools, maxTokens, temperature,
                    stopSequences, Optional.ofNullable(thinkingConfig), toolChoice);
        }
    }
}
