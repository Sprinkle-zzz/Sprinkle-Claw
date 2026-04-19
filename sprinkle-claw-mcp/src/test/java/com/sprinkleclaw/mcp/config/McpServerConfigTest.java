package com.sprinkleclaw.mcp.config;

import com.sprinkleclaw.mcp.config.McpServerConfig.Transport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpServerConfigTest {

    @Test
    void stdio_builder_should_normalize_collections_and_default_timeout() {
        McpServerConfig cfg = McpServerConfig.builder("fs")
                .transport(Transport.STDIO)
                .command("npx")
                .args("-y", "@modelcontextprotocol/server-filesystem", "/tmp")
                .addEnv("FOO", "BAR")
                .build();

        assertThat(cfg.id()).isEqualTo("fs");
        assertThat(cfg.transport()).isEqualTo(Transport.STDIO);
        assertThat(cfg.command()).isEqualTo("npx");
        assertThat(cfg.args()).containsExactly("-y", "@modelcontextprotocol/server-filesystem", "/tmp");
        assertThat(cfg.env()).containsEntry("FOO", "BAR");
        assertThat(cfg.requestTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(cfg.headers()).isEmpty();
        assertThat(cfg.url()).isNull();
    }

    @Test
    void sse_builder_should_accept_url_and_headers() {
        McpServerConfig cfg = McpServerConfig.builder("github")
                .transport(Transport.SSE)
                .url("https://mcp.example.com/sse")
                .header("Authorization", "Bearer token")
                .requestTimeout(Duration.ofSeconds(10))
                .build();

        assertThat(cfg.url()).isEqualTo("https://mcp.example.com/sse");
        assertThat(cfg.headers()).containsEntry("Authorization", "Bearer token");
        assertThat(cfg.requestTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void streamable_http_builder_should_work_like_sse() {
        McpServerConfig cfg = McpServerConfig.builder("svc")
                .transport(Transport.STREAMABLE_HTTP)
                .url("https://api.example.com/mcp")
                .headers(Map.of("X-Tenant", "demo"))
                .build();

        assertThat(cfg.transport()).isEqualTo(Transport.STREAMABLE_HTTP);
        assertThat(cfg.url()).isEqualTo("https://api.example.com/mcp");
        assertThat(cfg.headers()).containsEntry("X-Tenant", "demo");
    }

    @Test
    void blank_id_should_be_rejected() {
        assertThatThrownBy(() -> McpServerConfig.builder(" ").transport(Transport.STDIO).command("x").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void stdio_without_command_should_be_rejected() {
        assertThatThrownBy(() -> McpServerConfig.builder("fs").transport(Transport.STDIO).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command");
    }

    @Test
    void sse_without_url_should_be_rejected() {
        assertThatThrownBy(() -> McpServerConfig.builder("svc").transport(Transport.SSE).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
    }

    @Test
    void streamable_http_without_url_should_be_rejected() {
        assertThatThrownBy(() -> McpServerConfig.builder("svc").transport(Transport.STREAMABLE_HTTP).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
    }

    @Test
    void records_should_be_immutable_collections() {
        McpServerConfig cfg = McpServerConfig.builder("x")
                .transport(Transport.STDIO)
                .command("c")
                .args(List.of("a"))
                .build();

        assertThatThrownBy(() -> cfg.args().add("mutate"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
