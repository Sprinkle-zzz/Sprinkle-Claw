package com.sprinkleclaw.mcp.config;

import com.sprinkleclaw.mcp.config.McpServerConfig.Transport;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpTransportFactoryTest {

    @Test
    void should_build_stdio_transport() {
        McpServerConfig cfg = McpServerConfig.builder("fs")
                .transport(Transport.STDIO)
                .command("echo")
                .args("hello")
                .build();

        McpClientTransport transport = McpTransportFactory.create(cfg);

        assertThat(transport).isInstanceOf(StdioClientTransport.class);
    }

    @Test
    void should_build_sse_transport() {
        McpServerConfig cfg = McpServerConfig.builder("github")
                .transport(Transport.SSE)
                .url("https://mcp.example.com/sse")
                .header("Authorization", "Bearer token")
                .build();

        McpClientTransport transport = McpTransportFactory.create(cfg);

        assertThat(transport).isInstanceOf(HttpClientSseClientTransport.class);
    }

    @Test
    void should_build_streamable_http_transport() {
        McpServerConfig cfg = McpServerConfig.builder("svc")
                .transport(Transport.STREAMABLE_HTTP)
                .url("https://api.example.com/mcp")
                .build();

        McpClientTransport transport = McpTransportFactory.create(cfg);

        assertThat(transport).isInstanceOf(HttpClientStreamableHttpTransport.class);
    }

    @Test
    void create_should_reject_null_config() {
        assertThatThrownBy(() -> McpTransportFactory.create(null))
                .isInstanceOf(NullPointerException.class);
    }
}
