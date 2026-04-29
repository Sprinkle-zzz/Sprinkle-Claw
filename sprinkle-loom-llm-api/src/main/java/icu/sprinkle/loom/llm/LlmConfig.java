package icu.sprinkle.loom.llm;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * LLM 提供者配置，包含 API 密钥、模型名称、连接参数等。
 *
 * <h3>三层扩展点（MVP10）</h3>
 * <ol>
 *   <li>{@link #capabilities()} —— 用户覆盖能力声明（如显式指定上下文窗口、是否启用 prompt cache）；
 *       为 {@code null} 时 Provider 使用其默认值</li>
 *   <li>{@link #customParameters()} —— 直接透传给 LLM API 请求体的 JSON 字段（用于厂商私有特性，
 *       如 Qwen 的 {@code enable_search}、DeepSeek 的 {@code response_format} 等），
 *       Provider 在构建请求时将其平铺合并到根对象</li>
 *   <li>{@link #returnThinking()} + {@link #thinkingFieldName()} —— 推理模型字段开关
 *       （deepseek-v4-pro 用 {@code reasoning_content}，OpenAI 标准用
 *       {@code completion_tokens_details.reasoning_tokens}）</li>
 * </ol>
 *
 * @param apiKey            API 密钥
 * @param model             模型名称（如 "claude-opus-4-7"、"deepseek-v4-flash"、"deepseek-v4-pro"）
 * @param baseUrl           API 基础 URL（为空时使用 Provider 默认值）
 * @param maxTokens         最大生成 token 数
 * @param temperature       温度参数
 * @param timeout           请求超时时间
 * @param headers           自定义 HTTP 请求头
 * @param customParameters  透传给 LLM 请求体的 vendor 私有字段（值类型 {@code Object}，序列化时按 JSON 处理）
 * @param capabilities      用户覆盖的能力声明，{@code null} 表示沿用 Provider 默认值
 * @param returnThinking    是否解析推理内容（如 deepseek-v4-pro 的 {@code reasoning_content}），
 *                          解析后映射为 {@code ContentBlock.ThinkingBlock}
 * @param thinkingFieldName 推理字段名，默认 {@code "reasoning_content"}（DeepSeek 约定）
 *
 * @author sprinkle
 * @since 2026/3/17
 */
public record LlmConfig(
        String apiKey,
        String model,
        String baseUrl,
        int maxTokens,
        double temperature,
        Duration timeout,
        Map<String, String> headers,
        Map<String, Object> customParameters,
        LlmCapabilities capabilities,
        boolean returnThinking,
        String thinkingFieldName
) {
    public LlmConfig {
        headers = headers != null ? Collections.unmodifiableMap(new HashMap<>(headers)) : Map.of();
        customParameters = customParameters != null
                ? Collections.unmodifiableMap(new HashMap<>(customParameters)) : Map.of();
        if (thinkingFieldName == null || thinkingFieldName.isEmpty()) {
            thinkingFieldName = "reasoning_content";
        }
    }

    /**
     * 创建构建器实例。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * LlmConfig 构建器。
     */
    public static final class Builder {
        private String apiKey = "";
        private String model = "";
        private String baseUrl = "";
        private int maxTokens = 4096;
        private double temperature = 0.7;
        private Duration timeout = Duration.ofSeconds(120);
        private final Map<String, String> headers = new HashMap<>();
        private final Map<String, Object> customParameters = new HashMap<>();
        private LlmCapabilities capabilities;
        private boolean returnThinking = false;
        private String thinkingFieldName = "reasoning_content";

        private Builder() {
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
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

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * 添加自定义 HTTP 请求头。
         */
        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        /**
         * 批量设置自定义 HTTP 请求头。
         */
        public Builder headers(Map<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        /**
         * 添加单个 vendor 私有请求字段。
         *
         * <p>例如 Qwen 的联网搜索：{@code .customParameter("enable_search", true)}。</p>
         */
        public Builder customParameter(String name, Object value) {
            this.customParameters.put(name, value);
            return this;
        }

        /**
         * 批量设置 vendor 私有请求字段。
         */
        public Builder customParameters(Map<String, Object> params) {
            this.customParameters.putAll(params);
            return this;
        }

        /**
         * 用户覆盖能力声明（如显式 context window、是否启用 prompt cache）。
         */
        public Builder capabilities(LlmCapabilities capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        /**
         * 启用推理内容解析（deepseek-v4-pro 等使用 {@code reasoning_content} 字段的模型）。
         */
        public Builder returnThinking(boolean returnThinking) {
            this.returnThinking = returnThinking;
            return this;
        }

        /**
         * 设置推理字段名（默认 {@code "reasoning_content"}）。
         */
        public Builder thinkingFieldName(String thinkingFieldName) {
            this.thinkingFieldName = thinkingFieldName;
            return this;
        }

        /**
         * 构建不可变的 LlmConfig 实例。
         */
        public LlmConfig build() {
            return new LlmConfig(apiKey, model, baseUrl, maxTokens, temperature, timeout,
                    headers, customParameters, capabilities, returnThinking, thinkingFieldName);
        }
    }
}
