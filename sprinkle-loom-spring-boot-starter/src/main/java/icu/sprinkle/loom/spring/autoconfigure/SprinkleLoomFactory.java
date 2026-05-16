package icu.sprinkle.loom.spring.autoconfigure;

import icu.sprinkle.loom.bootstrap.Loom;
import icu.sprinkle.loom.bootstrap.LoomBuilder;
import icu.sprinkle.loom.core.observability.AgentMetrics;
import icu.sprinkle.loom.mcp.config.McpServerConfig;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 根据 {@link SprinkleLoomProperties} 中的多实例配置构建命名 {@link Loom} 实例。
 *
 * <p>每个 instance 的字段优先级：instance 内非 {@code null} 字段 &gt; {@link SprinkleLoomProperties.Agent} /
 * {@link SprinkleLoomProperties.Tools} 全局默认值。{@link SprinkleLoomProperties.Mcp} 在所有 instance 间共享。</p>
 *
 * <p>由 {@link SprinkleLoomBeanRegistrar} 通过 {@code factoryBeanName + factoryMethodName}
 * 引用，每个 instance 注册一个独立的 {@link Loom} BeanDefinition。</p>
 *
 * @author sprinkle
 * @since 0.10.0 (MVP9)
 */
public class SprinkleLoomFactory {

    private final SprinkleLoomProperties properties;
    private final ObjectProvider<AgentMetrics> metricsProvider;

    public SprinkleLoomFactory(SprinkleLoomProperties properties,
                               ObjectProvider<AgentMetrics> metricsProvider) {
        this.properties = properties;
        this.metricsProvider = metricsProvider;
    }

    /**
     * 构建指定名称的 {@link Loom} 实例。
     *
     * @param instanceName instance 名称（必须存在于 {@code sprinkle-loom.llm.instances} 中）
     * @return 已构建的 Claw（注册到 Spring 容器后会被 {@code destroyMethod=close} 释放）
     * @throws IllegalStateException 若 instance 不存在
     */
    public Loom create(String instanceName) {
        SprinkleLoomProperties.Llm.Instance instance = properties.getLlm().getInstances().get(instanceName);
        if (instance == null) {
            throw new IllegalStateException(
                    "Unknown LLM instance: '" + instanceName + "'. Available: "
                            + properties.getLlm().getInstances().keySet());
        }

        SprinkleLoomProperties.Agent agent = properties.getAgent();
        SprinkleLoomProperties.Tools tools = properties.getTools();

        LoomBuilder builder = LoomBuilder.create()
                .model(coalesce(instance.getModel(), null))
                .maxIterations(coalesceInt(instance.getMaxIterations(), agent.getMaxIterations()))
                .loopTimeout(coalesceDuration(instance.getLoopTimeout(), agent.getLoopTimeout()))
                .toolTimeout(coalesceDuration(instance.getToolTimeout(), agent.getToolTimeout()))
                .systemPrompt(coalesce(instance.getSystemPrompt(), agent.getSystemPrompt()))
                .compactionThreshold(coalesceInt(instance.getCompactionThreshold(), agent.getCompactionThreshold()))
                .autoSaveInterval(coalesceInt(instance.getAutoSaveInterval(), agent.getAutoSaveInterval()))
                .todoNagThreshold(coalesceInt(instance.getTodoNagThreshold(), agent.getTodoNagThreshold()))
                .identityPrompt(coalesce(instance.getIdentityPrompt(), agent.getIdentityPrompt()));

        Integer contextWindowTokens = coalesceInteger(
                instance.getContextWindowTokens(), properties.getLlm().getContextWindowTokens());
        if (contextWindowTokens != null) {
            builder.contextWindowTokens(contextWindowTokens);
        }

        Integer maxOutputTokens = coalesceInteger(
                instance.getMaxOutputTokens(), properties.getLlm().getMaxOutputTokens());
        if (maxOutputTokens != null) {
            builder.maxOutputTokens(maxOutputTokens);
        }

        Integer maxTokens = coalesceInteger(instance.getMaxTokens(), properties.getLlm().getMaxTokens());
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }

        Double temperature = coalesceDouble(instance.getTemperature(), properties.getLlm().getTemperature());
        if (temperature != null) {
            builder.temperature(temperature);
        }

        Duration requestTimeout = coalesceDuration(instance.getRequestTimeout(), properties.getLlm().getRequestTimeout());
        if (requestTimeout != null) {
            builder.requestTimeout(requestTimeout);
        }

        Map<String, String> headers = coalesceMap(instance.getHeaders(), properties.getLlm().getHeaders());
        if (headers != null && !headers.isEmpty()) {
            builder.headers(headers);
        }

        Map<String, Object> customParameters = coalesceMap(
                instance.getCustomParameters(), properties.getLlm().getCustomParameters());
        if (customParameters != null && !customParameters.isEmpty()) {
            builder.customParameters(customParameters);
        }

        // workingDirectory：instance 优先，否则用全局；都为空则不调用（保持 LoomBuilder 默认 null）
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

        String skillsDir = coalesce(instance.getSkillsDirectory(), agent.getSkillsDirectory());
        if (notBlank(skillsDir)) {
            builder.skillsDirectory(Path.of(skillsDir));
        }
        String tasksDir = coalesce(instance.getTasksDirectory(), agent.getTasksDirectory());
        if (notBlank(tasksDir)) {
            builder.tasksDirectory(Path.of(tasksDir));
        }

        if (coalesceBoolean(instance.getEnableFileTools(), agent.isEnableFileTools())) {
            builder.enableFileTools();
        }
        if (coalesceBoolean(instance.getEnableBashTool(), agent.isEnableBashTool())) {
            builder.enableBashTool();
        }
        if (coalesceBoolean(instance.getEnableManualCompact(), agent.isEnableManualCompact())) {
            builder.enableManualCompact();
        }
        if (coalesceBoolean(instance.getEnableTodoWrite(), agent.isEnableTodoWrite())) {
            builder.enableTodoWrite();
        }
        if (coalesceBoolean(instance.getEnableFileSnapshot(), agent.isEnableFileSnapshot())) {
            builder.enableFileSnapshot();
        }
        if (coalesceBoolean(instance.getEnableSubAgent(), agent.isEnableSubAgent())) {
            builder.enableSubAgent();
        }
        if (coalesceBoolean(instance.getEnableSkill(), agent.isEnableSkill())) {
            builder.enableSkill();
        }
        if (coalesceBoolean(instance.getEnableTaskBoard(), agent.isEnableTaskBoard())) {
            builder.enableTaskBoard();
        }
        if (coalesceBoolean(instance.getEnableBackgroundTasks(), agent.isEnableBackgroundTasks())) {
            builder.enableBackgroundTasks();
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

    private static Integer coalesceInteger(Integer instanceValue, Integer globalValue) {
        return instanceValue != null ? instanceValue : globalValue;
    }

    private static Double coalesceDouble(Double instanceValue, Double globalValue) {
        return instanceValue != null ? instanceValue : globalValue;
    }

    private static Duration coalesceDuration(Duration instanceValue, Duration globalValue) {
        return instanceValue != null ? instanceValue : globalValue;
    }

    private static boolean coalesceBoolean(Boolean instanceValue, boolean globalValue) {
        return instanceValue != null ? instanceValue : globalValue;
    }

    private static <K, V> Map<K, V> coalesceMap(Map<K, V> instanceValue, Map<K, V> globalValue) {
        if (instanceValue != null) {
            return instanceValue;
        }
        return globalValue != null ? new LinkedHashMap<>(globalValue) : Map.of();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isEmpty();
    }

    private static List<McpServerConfig> mapMcpServers(SprinkleLoomProperties.Mcp mcp) {
        if (mcp == null || mcp.getServers() == null || mcp.getServers().isEmpty()) {
            return List.of();
        }
        List<McpServerConfig> configs = new ArrayList<>(mcp.getServers().size());
        for (SprinkleLoomProperties.Mcp.Server s : mcp.getServers()) {
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
