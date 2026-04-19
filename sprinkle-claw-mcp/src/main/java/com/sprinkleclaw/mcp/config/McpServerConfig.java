package com.sprinkleclaw.mcp.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 单个 MCP 服务器的连接配置，统一覆盖三种官方 SDK 传输模式。
 *
 * <h3>STDIO（本地子进程）</h3>
 * <pre>
 * McpServerConfig.builder("fs")
 *     .transport(Transport.STDIO)
 *     .command("npx")
 *     .args(List.of("-y", "&#64;modelcontextprotocol/server-filesystem", "/workspace"))
 *     .build();
 * </pre>
 *
 * <h3>SSE / Streamable HTTP（远程）</h3>
 * <pre>
 * McpServerConfig.builder("github")
 *     .transport(Transport.SSE)
 *     .url("https://mcp.example.com/github/sse")
 *     .header("Authorization", "Bearer " + token)
 *     .build();
 * </pre>
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public record McpServerConfig(
        String id,
        Transport transport,
        String command,
        List<String> args,
        Map<String, String> env,
        String url,
        Map<String, String> headers,
        Duration requestTimeout
) {

    public McpServerConfig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(transport, "transport");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        args = args == null ? List.of() : List.copyOf(args);
        env = env == null ? Map.of() : Map.copyOf(env);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        if (requestTimeout == null) {
            requestTimeout = Duration.ofSeconds(30);
        }
        switch (transport) {
            case STDIO -> {
                if (command == null || command.isBlank()) {
                    throw new IllegalArgumentException(
                            "STDIO transport requires non-blank 'command'");
                }
            }
            case SSE, STREAMABLE_HTTP -> {
                if (url == null || url.isBlank()) {
                    throw new IllegalArgumentException(
                            transport + " transport requires non-blank 'url'");
                }
            }
        }
    }

    public enum Transport {
        STDIO,
        SSE,
        STREAMABLE_HTTP
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private Transport transport = Transport.STDIO;
        private String command;
        private List<String> args = new ArrayList<>();
        private Map<String, String> env = new HashMap<>();
        private String url;
        private Map<String, String> headers = new HashMap<>();
        private Duration requestTimeout = Duration.ofSeconds(30);

        private Builder(String id) {
            this.id = id;
        }

        public Builder transport(Transport transport) {
            this.transport = transport;
            return this;
        }

        public Builder command(String command) {
            this.command = command;
            return this;
        }

        public Builder args(List<String> args) {
            this.args = new ArrayList<>(args);
            return this;
        }

        public Builder args(String... args) {
            this.args = new ArrayList<>(List.of(args));
            return this;
        }

        public Builder env(Map<String, String> env) {
            this.env = new HashMap<>(env);
            return this;
        }

        public Builder addEnv(String name, String value) {
            this.env.put(name, value);
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = new HashMap<>(headers);
            return this;
        }

        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public Builder requestTimeout(Duration timeout) {
            this.requestTimeout = timeout;
            return this;
        }

        public McpServerConfig build() {
            return new McpServerConfig(id, transport, command, args, env,
                    url, headers, requestTimeout);
        }
    }
}
