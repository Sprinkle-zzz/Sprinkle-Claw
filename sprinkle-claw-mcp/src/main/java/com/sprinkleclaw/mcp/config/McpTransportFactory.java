package com.sprinkleclaw.mcp.config;

import com.sprinkleclaw.mcp.health.McpAvailability;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpClientTransport;

import java.util.Objects;

/**
 * 根据 {@link McpServerConfig} 构造官方 SDK 的 {@link McpClientTransport}。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public final class McpTransportFactory {

    private McpTransportFactory() {}

    public static McpClientTransport create(McpServerConfig config) {
        Objects.requireNonNull(config, "config");
        McpAvailability.requireAvailable();

        return switch (config.transport()) {
            case STDIO -> createStdio(config);
            case SSE -> createSse(config);
            case STREAMABLE_HTTP -> createStreamableHttp(config);
        };
    }

    private static McpClientTransport createStdio(McpServerConfig config) {
        ServerParameters params = ServerParameters.builder(config.command())
                .args(config.args())
                .env(config.env())
                .build();
        return new StdioClientTransport(params, McpJsonDefaults.getMapper());
    }

    private static McpClientTransport createSse(McpServerConfig config) {
        var builder = HttpClientSseClientTransport.builder(config.url());
        if (!config.headers().isEmpty()) {
            builder.customizeRequest(rb -> config.headers().forEach(rb::header));
        }
        return builder.build();
    }

    private static McpClientTransport createStreamableHttp(McpServerConfig config) {
        var builder = HttpClientStreamableHttpTransport.builder(config.url());
        if (!config.headers().isEmpty()) {
            builder.customizeRequest(rb -> config.headers().forEach(rb::header));
        }
        return builder.build();
    }
}
