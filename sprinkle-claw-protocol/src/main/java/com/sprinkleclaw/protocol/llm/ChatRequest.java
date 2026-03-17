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
 * @param thinkingConfig 思考模式配置（仅 Anthropic 支持，其他 Provider 忽略）
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
        Optional<ThinkingConfig> thinkingConfig
) {
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
         * 设置思考模式配置（仅 Anthropic 支持）。
         *
         * @param thinkingConfig 思考配置
         * @return 构建器自身
         */
        public Builder thinkingConfig(ThinkingConfig thinkingConfig) {
            this.thinkingConfig = thinkingConfig;
            return this;
        }

        /**
         * 构建不可变的 ChatRequest 实例。
         */
        public ChatRequest build() {
            return new ChatRequest(systemPrompt, messages, tools, maxTokens, temperature,
                    stopSequences, Optional.ofNullable(thinkingConfig));
        }
    }
}
