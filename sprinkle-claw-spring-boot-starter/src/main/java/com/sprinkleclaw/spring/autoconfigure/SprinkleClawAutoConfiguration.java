package com.sprinkleclaw.spring.autoconfigure;

import com.sprinkleclaw.bootstrap.Claw;
import com.sprinkleclaw.bootstrap.ClawBuilder;
import com.sprinkleclaw.core.observability.AgentMetrics;
import com.sprinkleclaw.mcp.config.McpServerConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Sprinkle-Claw 核心自动配置：根据 {@link SprinkleClawProperties} 创建 {@link Claw} Bean。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
@AutoConfiguration
@ConditionalOnClass(Claw.class)
@EnableConfigurationProperties(SprinkleClawProperties.class)
public class SprinkleClawAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Claw claw(SprinkleClawProperties properties,
                     org.springframework.beans.factory.ObjectProvider<AgentMetrics> metricsProvider) {
        SprinkleClawProperties.Llm llm = properties.getLlm();
        SprinkleClawProperties.Agent agent = properties.getAgent();
        SprinkleClawProperties.Tools tools = properties.getTools();

        ClawBuilder builder = ClawBuilder.create()
                .model(llm.getModel())
                .maxIterations(agent.getMaxIterations())
                .loopTimeout(agent.getLoopTimeout())
                .systemPrompt(agent.getSystemPrompt())
                .workingDirectory(Path.of(agent.getWorkingDirectory()))
                .compactionThreshold(agent.getCompactionThreshold());

        if (llm.getApiKey() != null && !llm.getApiKey().isEmpty()) {
            builder.apiKey(llm.getApiKey());
        }
        if (llm.getProvider() != null && !llm.getProvider().isEmpty()) {
            builder.providerId(llm.getProvider());
        }
        if (llm.getBaseUrl() != null && !llm.getBaseUrl().isEmpty()) {
            builder.baseUrl(llm.getBaseUrl());
        }
        if (tools.getBlockedCommands() != null && !tools.getBlockedCommands().isEmpty()) {
            builder.blockCommands(tools.getBlockedCommands());
        }
        if (agent.isEnableExtensions()) {
            builder.enableExtensions();
        }

        AgentMetrics metrics = metricsProvider.getIfAvailable();
        if (metrics != null) {
            builder.metrics(metrics);
        }

        List<McpServerConfig> mcpConfigs = mapMcpServers(properties.getMcp());
        if (!mcpConfigs.isEmpty()) {
            builder.enableMcp(mcpConfigs);
        }

        return builder.build();
    }

    private static List<McpServerConfig> mapMcpServers(SprinkleClawProperties.Mcp mcp) {
        if (mcp == null || mcp.getServers() == null || mcp.getServers().isEmpty()) {
            return List.of();
        }
        List<McpServerConfig> configs = new ArrayList<>(mcp.getServers().size());
        for (SprinkleClawProperties.Mcp.Server s : mcp.getServers()) {
            McpServerConfig.Builder b = McpServerConfig.builder(s.getId())
                    .transport(McpServerConfig.Transport.valueOf(s.getTransport()))
                    .requestTimeout(s.getRequestTimeout());
            if (s.getCommand() != null) b.command(s.getCommand());
            if (s.getArgs() != null && !s.getArgs().isEmpty()) b.args(s.getArgs());
            if (s.getEnv() != null && !s.getEnv().isEmpty()) b.env(s.getEnv());
            if (s.getUrl() != null) b.url(s.getUrl());
            if (s.getHeaders() != null && !s.getHeaders().isEmpty()) b.headers(s.getHeaders());
            configs.add(b.build());
        }
        return configs;
    }
}
