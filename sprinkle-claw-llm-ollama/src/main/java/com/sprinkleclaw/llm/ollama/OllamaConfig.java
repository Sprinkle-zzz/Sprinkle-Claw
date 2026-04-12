package com.sprinkleclaw.llm.ollama;

import com.sprinkleclaw.llm.LlmConfig;

import java.time.Duration;

/**
 * Ollama 提供者配置。
 *
 * @param host      Ollama 服务地址（默认 http://localhost:11434）
 * @param model     模型名称（如 "llama3.1:8b"、"qwen2.5"）
 * @param keepAlive 模型保持加载时间（Ollama keep_alive 参数）
 * @param numCtx    上下文窗口大小（覆盖模型默认值）
 * @param timeout   请求超时时间
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public record OllamaConfig(
        String host,
        String model,
        Duration keepAlive,
        int numCtx,
        Duration timeout
) {
    private static final String DEFAULT_HOST = "http://localhost:11434";

    /**
     * 从通用 {@link LlmConfig} 构造 Ollama 配置。
     */
    public static OllamaConfig from(LlmConfig config) {
        String host = config.baseUrl().isEmpty() ? DEFAULT_HOST : config.baseUrl();
        // 去除尾部斜杠
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        return new OllamaConfig(
                host,
                config.model(),
                Duration.ofMinutes(5),
                0, // 0 表示使用模型默认值
                config.timeout()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String host = DEFAULT_HOST;
        private String model = "";
        private Duration keepAlive = Duration.ofMinutes(5);
        private int numCtx = 0;
        private Duration timeout = Duration.ofSeconds(120);

        private Builder() {}

        public Builder host(String host) { this.host = host; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder keepAlive(Duration keepAlive) { this.keepAlive = keepAlive; return this; }
        public Builder numCtx(int numCtx) { this.numCtx = numCtx; return this; }
        public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }

        public OllamaConfig build() {
            return new OllamaConfig(host, model, keepAlive, numCtx, timeout);
        }
    }
}
