package com.sprinkleclaw.spring.autoconfigure;

import com.sprinkleclaw.bootstrap.Claw;
import com.sprinkleclaw.bootstrap.ClawBuilder;
import com.sprinkleclaw.core.observability.AgentMetrics;
import com.sprinkleclaw.mcp.config.McpServerConfig;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 根据 {@link SprinkleClawProperties} 中的多实例配置构建命名 {@link Claw} 实例。
 *
 * <p>每个 instance 的字段优先级：instance 内非 {@code null} 字段 &gt; {@link SprinkleClawProperties.Agent} /
 * {@link SprinkleClawProperties.Tools} 全局默认值。{@link SprinkleClawProperties.Mcp} 在所有 instance 间共享。</p>
 *
 * <p>由 {@link SprinkleClawBeanRegistrar} 通过 {@code factoryBeanName + factoryMethodName}
 * 引用，每个 instance 注册一个独立的 {@link Claw} BeanDefinition。</p>
 *
 * @author sprinkle
 * @since 0.10.0 (MVP9)
 */
public class SprinkleClawFactory {

    private final SprinkleClawProperties properties;
    private final ObjectProvider<AgentMetrics> metricsProvider;

    public SprinkleClawFactory(SprinkleClawProperties properties,
                               ObjectProvider<AgentMetrics> metricsProvider) {
        this.properties = properties;
        this.metricsProvider = metricsProvider;
    }

    /**
     * 构建指定名称的 {@link Claw} 实例。
     *
     * @param instanceName instance 名称（必须存在于 {@code sprinkle-claw.llm.instances} 中）
     * @return 已构建的 Claw（注册到 Spring 容器后会被 {@code destroyMethod=close} 释放）
     * @throws IllegalStateException 若 instance 不存在
     */
    public Claw create(String instanceName) {
        SprinkleClawProperties.Llm.Instance instance = properties.getLlm().getInstances().get(instanceName);
        if (instance == null) {
            throw new IllegalStateException(
                    "Unknown LLM instance: '" + instanceName + "'. Available: "
                            + properties.getLlm().getInstances().keySet());
        }

        SprinkleClawProperties.Agent agent = properties.getAgent();
        SprinkleClawProperties.Tools tools = properties.getTools();

        ClawBuilder builder = ClawBuilder.create()
                .model(coalesce(instance.getModel(), null))
                .maxIterations(coalesceInt(instance.getMaxIterations(), agent.getMaxIterations()))
                .loopTimeout(coalesceDuration(instance.getLoopTimeout(), agent.getLoopTimeout()))
                .systemPrompt(coalesce(instance.getSystemPrompt(), agent.getSystemPrompt()))
                .compactionThreshold(coalesceInt(instance.getCompactionThreshold(), agent.getCompactionThreshold()));

        // workingDirectory：instance 优先，否则用全局；都为空则不调用（保持 ClawBuilder 默认 null）
        String workingDir = coalesce(instance.getWorkingDirectory(), agent.getWorkingDirectory());
        if (workingDir != null && !workingDir.isEmpty()) {
            builder.workingDirectory(Path.of(workingDir));
        }

        if (notBlank(instance.getApiKey())) {
            builder.apiKey(instance.getApiKey());
        }
        if (notBlank(instance.getProvider())) {
            builder.providerId(instance.getProvider());
        }
        if (notBlank(instance.getBaseUrl())) {
            builder.baseUrl(instance.getBaseUrl());
        }

        // blockedCommands：instance 非 null 覆盖（包括显式空列表 → 不阻断），否则用全局
        List<String> blocked = instance.getBlockedCommands() != null
                ? instance.getBlockedCommands()
                : tools.getBlockedCommands();
        if (blocked != null && !blocked.isEmpty()) {
            builder.blockCommands(blocked);
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

    private static String coalesce(String instanceValue, String globalValue) {
        return notBlank(instanceValue) ? instanceValue : globalValue;
    }

    private static int coalesceInt(Integer instanceValue, int globalValue) {
        return instanceValue != null ? instanceValue : globalValue;
    }

    private static Duration coalesceDuration(Duration instanceValue, Duration globalValue) {
        return instanceValue != null ? instanceValue : globalValue;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isEmpty();
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
            if (s.getCommand() != null) {
                b.command(s.getCommand());
            }
            if (s.getArgs() != null && !s.getArgs().isEmpty()) {
                b.args(s.getArgs());
            }
            if (s.getEnv() != null && !s.getEnv().isEmpty()) {
                b.env(s.getEnv());
            }
            if (s.getUrl() != null) {
                b.url(s.getUrl());
            }
            if (s.getHeaders() != null && !s.getHeaders().isEmpty()) {
                b.headers(s.getHeaders());
            }
            configs.add(b.build());
        }
        return configs;
    }
}
