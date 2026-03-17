package com.sprinkleclaw.llm;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * LLM 提供者配置，包含 API 密钥、模型名称、连接参数等。
 *
 * @param apiKey      API 密钥
 * @param model       模型名称（如 "claude-sonnet-4-20250514"、"deepseek-chat"）
 * @param baseUrl     API 基础 URL（为空时使用提供者默认值）
 * @param maxTokens   最大生成 token 数
 * @param temperature 温度参数
 * @param timeout     请求超时时间
 * @param headers     自定义 HTTP 请求头（用于适配不同厂商的认证方式等）
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
        Map<String, String> headers
) {
    public LlmConfig {
        headers = headers != null ? Collections.unmodifiableMap(new HashMap<>(headers)) : Map.of();
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
         *
         * @param name  请求头名称
         * @param value 请求头值
         * @return 构建器自身
         */
        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        /**
         * 批量设置自定义 HTTP 请求头。
         *
         * @param headers 请求头键值对
         * @return 构建器自身
         */
        public Builder headers(Map<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        /**
         * 构建不可变的 LlmConfig 实例。
         */
        public LlmConfig build() {
            return new LlmConfig(apiKey, model, baseUrl, maxTokens, temperature, timeout, headers);
        }
    }
}
