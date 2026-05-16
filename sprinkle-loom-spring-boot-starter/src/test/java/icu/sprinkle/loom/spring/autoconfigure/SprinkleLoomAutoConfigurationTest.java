package icu.sprinkle.loom.spring.autoconfigure;

import icu.sprinkle.loom.gateway.acl.AccessControlList;
import icu.sprinkle.loom.gateway.auth.ApiKeyStore;
import icu.sprinkle.loom.gateway.filter.GatewayFilterChain;
import icu.sprinkle.loom.gateway.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class SprinkleLoomAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SprinkleLoomAutoConfiguration.class,
                    GatewayAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void propertiesAreBoundUnderSprinkleLoomPrefix() {
        // 不配 instances 避免触发 BeanRegistrar 调真实 LoomBuilder.build()
        // （test classpath 无 LLM provider）。多实例字段绑定见 SprinkleLoomMultiInstanceTest。
        runner.withPropertyValues(
                        "sprinkle-loom.agent.max-iterations=50",
                        "sprinkle-loom.agent.tool-timeout=45s",
                        "sprinkle-loom.agent.auto-save-interval=7",
                        "sprinkle-loom.agent.enable-file-tools=true",
                        "sprinkle-loom.agent.enable-bash-tool=true",
                        "sprinkle-loom.agent.enable-manual-compact=true",
                        "sprinkle-loom.agent.enable-todo-write=true",
                        "sprinkle-loom.agent.todo-nag-threshold=4",
                        "sprinkle-loom.agent.enable-file-snapshot=true",
                        "sprinkle-loom.agent.enable-sub-agent=true",
                        "sprinkle-loom.agent.enable-skill=true",
                        "sprinkle-loom.agent.skills-directory=skills",
                        "sprinkle-loom.agent.enable-task-board=true",
                        "sprinkle-loom.agent.tasks-directory=.tasks",
                        "sprinkle-loom.agent.enable-background-tasks=true",
                        "sprinkle-loom.agent.identity-prompt=identity",
                        "sprinkle-loom.llm.context-window-tokens=64000",
                        "sprinkle-loom.llm.max-output-tokens=8192",
                        "sprinkle-loom.llm.max-tokens=4096",
                        "sprinkle-loom.llm.temperature=0.2",
                        "sprinkle-loom.llm.request-timeout=90s",
                        "sprinkle-loom.llm.headers.X-Test=yes",
                        "sprinkle-loom.llm.custom-parameters.enable_search=true")
                .run(ctx -> {
                    SprinkleLoomProperties props = ctx.getBean(SprinkleLoomProperties.class);
                    assertThat(props.getAgent().getMaxIterations()).isEqualTo(50);
                    assertThat(props.getAgent().getToolTimeout()).isEqualTo(java.time.Duration.ofSeconds(45));
                    assertThat(props.getAgent().getAutoSaveInterval()).isEqualTo(7);
                    assertThat(props.getAgent().isEnableFileTools()).isTrue();
                    assertThat(props.getAgent().isEnableBashTool()).isTrue();
                    assertThat(props.getAgent().isEnableManualCompact()).isTrue();
                    assertThat(props.getAgent().isEnableTodoWrite()).isTrue();
                    assertThat(props.getAgent().getTodoNagThreshold()).isEqualTo(4);
                    assertThat(props.getAgent().isEnableFileSnapshot()).isTrue();
                    assertThat(props.getAgent().isEnableSubAgent()).isTrue();
                    assertThat(props.getAgent().isEnableSkill()).isTrue();
                    assertThat(props.getAgent().getSkillsDirectory()).isEqualTo("skills");
                    assertThat(props.getAgent().isEnableTaskBoard()).isTrue();
                    assertThat(props.getAgent().getTasksDirectory()).isEqualTo(".tasks");
                    assertThat(props.getAgent().isEnableBackgroundTasks()).isTrue();
                    assertThat(props.getAgent().getIdentityPrompt()).isEqualTo("identity");
                    assertThat(props.getLlm().getContextWindowTokens()).isEqualTo(64_000);
                    assertThat(props.getLlm().getMaxOutputTokens()).isEqualTo(8_192);
                    assertThat(props.getLlm().getMaxTokens()).isEqualTo(4_096);
                    assertThat(props.getLlm().getTemperature()).isEqualTo(0.2);
                    assertThat(props.getLlm().getRequestTimeout()).isEqualTo(java.time.Duration.ofSeconds(90));
                    assertThat(props.getLlm().getHeaders()).containsEntry("X-Test", "yes");
                    assertThat(props.getLlm().getCustomParameters()).containsEntry("enable_search", "true");
                    assertThat(props.getLlm().getInstances()).isEmpty();
                });
    }

    @Test
    void instanceTokenLimitsAreBoundFromProperties() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues(
                        "sprinkle-loom.llm.instances.fast.api-key=k",
                        "sprinkle-loom.llm.instances.fast.model=m",
                        "sprinkle-loom.llm.instances.fast.context-window-tokens=32000",
                        "sprinkle-loom.llm.instances.fast.max-output-tokens=4096",
                        "sprinkle-loom.llm.instances.fast.max-tokens=2048",
                        "sprinkle-loom.llm.instances.fast.temperature=0.1",
                        "sprinkle-loom.llm.instances.fast.request-timeout=60s",
                        "sprinkle-loom.llm.instances.fast.headers.X-Instance=yes",
                        "sprinkle-loom.llm.instances.fast.custom-parameters.top_p=0.8",
                        "sprinkle-loom.llm.instances.fast.tool-timeout=30s",
                        "sprinkle-loom.llm.instances.fast.auto-save-interval=2",
                        "sprinkle-loom.llm.instances.fast.enable-file-tools=true",
                        "sprinkle-loom.llm.instances.fast.enable-bash-tool=true",
                        "sprinkle-loom.llm.instances.fast.enable-manual-compact=true",
                        "sprinkle-loom.llm.instances.fast.enable-todo-write=true",
                        "sprinkle-loom.llm.instances.fast.todo-nag-threshold=5",
                        "sprinkle-loom.llm.instances.fast.enable-file-snapshot=true",
                        "sprinkle-loom.llm.instances.fast.enable-sub-agent=true",
                        "sprinkle-loom.llm.instances.fast.enable-skill=true",
                        "sprinkle-loom.llm.instances.fast.skills-directory=instance-skills",
                        "sprinkle-loom.llm.instances.fast.enable-task-board=true",
                        "sprinkle-loom.llm.instances.fast.tasks-directory=instance-tasks",
                        "sprinkle-loom.llm.instances.fast.enable-background-tasks=true",
                        "sprinkle-loom.llm.instances.fast.identity-prompt=fast identity")
                .run(ctx -> {
                    SprinkleLoomProperties props = ctx.getBean(SprinkleLoomProperties.class);
                    SprinkleLoomProperties.Llm.Instance fast = props.getLlm().getInstances().get("fast");
                    assertThat(fast.getContextWindowTokens()).isEqualTo(32_000);
                    assertThat(fast.getMaxOutputTokens()).isEqualTo(4_096);
                    assertThat(fast.getMaxTokens()).isEqualTo(2_048);
                    assertThat(fast.getTemperature()).isEqualTo(0.1);
                    assertThat(fast.getRequestTimeout()).isEqualTo(java.time.Duration.ofSeconds(60));
                    assertThat(fast.getHeaders()).containsEntry("X-Instance", "yes");
                    assertThat(fast.getCustomParameters()).containsEntry("top_p", "0.8");
                    assertThat(fast.getToolTimeout()).isEqualTo(java.time.Duration.ofSeconds(30));
                    assertThat(fast.getAutoSaveInterval()).isEqualTo(2);
                    assertThat(fast.getEnableFileTools()).isTrue();
                    assertThat(fast.getEnableBashTool()).isTrue();
                    assertThat(fast.getEnableManualCompact()).isTrue();
                    assertThat(fast.getEnableTodoWrite()).isTrue();
                    assertThat(fast.getTodoNagThreshold()).isEqualTo(5);
                    assertThat(fast.getEnableFileSnapshot()).isTrue();
                    assertThat(fast.getEnableSubAgent()).isTrue();
                    assertThat(fast.getEnableSkill()).isTrue();
                    assertThat(fast.getSkillsDirectory()).isEqualTo("instance-skills");
                    assertThat(fast.getEnableTaskBoard()).isTrue();
                    assertThat(fast.getTasksDirectory()).isEqualTo("instance-tasks");
                    assertThat(fast.getEnableBackgroundTasks()).isTrue();
                    assertThat(fast.getIdentityPrompt()).isEqualTo("fast identity");
                });
    }

    @Test
    void gatewayBeansAreNotCreatedWhenDisabled() {
        runner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(GatewayFilterChain.class);
            assertThat(ctx).doesNotHaveBean(RateLimiter.class);
        });
    }

    @Test
    void gatewayBeansAreCreatedWhenEnabled() {
        runner.withPropertyValues("sprinkle-loom.gateway.enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(ApiKeyStore.class);
                    assertThat(ctx).hasSingleBean(RateLimiter.class);
                    assertThat(ctx).hasSingleBean(AccessControlList.class);
                    assertThat(ctx).hasSingleBean(GatewayFilterChain.class);
                });
    }

    @Test
    void aclReadsIpListsFromProperties() {
        runner.withPropertyValues(
                        "sprinkle-loom.gateway.enabled=true",
                        "sprinkle-loom.gateway.acl.ip-blacklist[0]=10.0.0.1",
                        "sprinkle-loom.gateway.acl.ip-whitelist[0]=192.168.1.1")
                .run(ctx -> {
                    AccessControlList acl = ctx.getBean(AccessControlList.class);
                    assertThat(acl.isAllowed("10.0.0.1")).isFalse();
                    assertThat(acl.isAllowed("192.168.1.1")).isTrue();
                });
    }

    @Test
    void mcpServersBoundFromProperties() {
        runner.withPropertyValues(
                        "sprinkle-loom.mcp.servers[0].id=fs",
                        "sprinkle-loom.mcp.servers[0].transport=STDIO",
                        "sprinkle-loom.mcp.servers[0].command=npx",
                        "sprinkle-loom.mcp.servers[0].args[0]=-y",
                        "sprinkle-loom.mcp.servers[0].args[1]=@modelcontextprotocol/server-filesystem",
                        "sprinkle-loom.mcp.servers[1].id=github",
                        "sprinkle-loom.mcp.servers[1].transport=SSE",
                        "sprinkle-loom.mcp.servers[1].url=https://mcp.example.com/sse",
                        "sprinkle-loom.mcp.servers[1].headers.Authorization=Bearer token")
                .run(ctx -> {
                    SprinkleLoomProperties props = ctx.getBean(SprinkleLoomProperties.class);
                    assertThat(props.getMcp().getServers()).hasSize(2);
                    assertThat(props.getMcp().getServers().get(0).getId()).isEqualTo("fs");
                    assertThat(props.getMcp().getServers().get(0).getCommand()).isEqualTo("npx");
                    assertThat(props.getMcp().getServers().get(0).getArgs()).hasSize(2);
                    assertThat(props.getMcp().getServers().get(1).getTransport()).isEqualTo("SSE");
                    assertThat(props.getMcp().getServers().get(1).getHeaders())
                            .containsEntry("Authorization", "Bearer token");
                });
    }

    @Test
    void apiKeyStoreBoundFromProperties() {
        runner.withPropertyValues(
                        "sprinkle-loom.gateway.enabled=true",
                        "sprinkle-loom.gateway.auth.keys[0].key=sk-test",
                        "sprinkle-loom.gateway.auth.keys[0].tenant=tenant-x",
                        "sprinkle-loom.gateway.auth.keys[0].user-id=user-1",
                        "sprinkle-loom.gateway.auth.keys[0].plan=PRO")
                .run(ctx -> {
                    ApiKeyStore store = ctx.getBean(ApiKeyStore.class);
                    assertThat(store.lookup("sk-test")).isPresent();
                    assertThat(store.lookup("sk-test").get().tenantId()).isEqualTo("tenant-x");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @org.springframework.boot.context.properties.EnableConfigurationProperties(SprinkleLoomProperties.class)
    static class PropertiesConfig {
    }
}
