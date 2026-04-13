package com.sprinkleclaw.mcp.client;

import java.time.Duration;

/**
 * MCP 客户端配置。
 *
 * @param clientName    客户端名称（初始化握手时使用）
 * @param clientVersion 客户端版本
 * @param requestTimeout 请求超时时间
 *
 * @author sprinkle
 * @since 2026/4/13
 */
public record McpClientConfig(
        String clientName,
        String clientVersion,
        Duration requestTimeout
) {
    public McpClientConfig {
        if (clientName == null || clientName.isBlank()) clientName = "sprinkle-claw";
        if (clientVersion == null || clientVersion.isBlank()) clientVersion = "0.6.0";
        if (requestTimeout == null) requestTimeout = Duration.ofSeconds(30);
    }

    public static McpClientConfig defaults() {
        return new McpClientConfig("sprinkle-claw", "0.6.0", Duration.ofSeconds(30));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String clientName = "sprinkle-claw";
        private String clientVersion = "0.6.0";
        private Duration requestTimeout = Duration.ofSeconds(30);

        public Builder clientName(String name) { this.clientName = name; return this; }
        public Builder clientVersion(String version) { this.clientVersion = version; return this; }
        public Builder requestTimeout(Duration timeout) { this.requestTimeout = timeout; return this; }

        public McpClientConfig build() {
            return new McpClientConfig(clientName, clientVersion, requestTimeout);
        }
    }
}
