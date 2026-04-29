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
        runner.withPropertyValues("sprinkle-loom.agent.max-iterations=50")
                .run(ctx -> {
                    SprinkleLoomProperties props = ctx.getBean(SprinkleLoomProperties.class);
                    assertThat(props.getAgent().getMaxIterations()).isEqualTo(50);
                    assertThat(props.getLlm().getInstances()).isEmpty();
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
