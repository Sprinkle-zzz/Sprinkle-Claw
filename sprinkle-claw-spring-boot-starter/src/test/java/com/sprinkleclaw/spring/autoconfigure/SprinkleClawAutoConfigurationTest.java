package com.sprinkleclaw.spring.autoconfigure;

import com.sprinkleclaw.bootstrap.Claw;
import com.sprinkleclaw.gateway.acl.AccessControlList;
import com.sprinkleclaw.gateway.auth.ApiKeyStore;
import com.sprinkleclaw.gateway.filter.GatewayFilterChain;
import com.sprinkleclaw.gateway.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class SprinkleClawAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GatewayAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void propertiesAreBoundUnderSprinkleClawPrefix() {
        runner.withPropertyValues(
                        "sprinkle-claw.llm.model=claude-sonnet-4-20250514",
                        "sprinkle-claw.agent.max-iterations=50")
                .run(ctx -> {
                    SprinkleClawProperties props = ctx.getBean(SprinkleClawProperties.class);
                    assertThat(props.getLlm().getModel()).isEqualTo("claude-sonnet-4-20250514");
                    assertThat(props.getAgent().getMaxIterations()).isEqualTo(50);
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
        runner.withPropertyValues("sprinkle-claw.gateway.enabled=true")
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
                        "sprinkle-claw.gateway.enabled=true",
                        "sprinkle-claw.gateway.acl.ip-blacklist[0]=10.0.0.1",
                        "sprinkle-claw.gateway.acl.ip-whitelist[0]=192.168.1.1")
                .run(ctx -> {
                    AccessControlList acl = ctx.getBean(AccessControlList.class);
                    assertThat(acl.isAllowed("10.0.0.1")).isFalse();
                    assertThat(acl.isAllowed("192.168.1.1")).isTrue();
                });
    }

    @Test
    void mcpServersBoundFromProperties() {
        runner.withPropertyValues(
                        "sprinkle-claw.mcp.servers[0].id=fs",
                        "sprinkle-claw.mcp.servers[0].transport=STDIO",
                        "sprinkle-claw.mcp.servers[0].command=npx",
                        "sprinkle-claw.mcp.servers[0].args[0]=-y",
                        "sprinkle-claw.mcp.servers[0].args[1]=@modelcontextprotocol/server-filesystem",
                        "sprinkle-claw.mcp.servers[1].id=github",
                        "sprinkle-claw.mcp.servers[1].transport=SSE",
                        "sprinkle-claw.mcp.servers[1].url=https://mcp.example.com/sse",
                        "sprinkle-claw.mcp.servers[1].headers.Authorization=Bearer token")
                .run(ctx -> {
                    SprinkleClawProperties props = ctx.getBean(SprinkleClawProperties.class);
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
                        "sprinkle-claw.gateway.enabled=true",
                        "sprinkle-claw.gateway.auth.keys[0].key=sk-test",
                        "sprinkle-claw.gateway.auth.keys[0].tenant=tenant-x",
                        "sprinkle-claw.gateway.auth.keys[0].user-id=user-1",
                        "sprinkle-claw.gateway.auth.keys[0].plan=PRO")
                .run(ctx -> {
                    ApiKeyStore store = ctx.getBean(ApiKeyStore.class);
                    assertThat(store.lookup("sk-test")).isPresent();
                    assertThat(store.lookup("sk-test").get().tenantId()).isEqualTo("tenant-x");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @org.springframework.boot.context.properties.EnableConfigurationProperties(SprinkleClawProperties.class)
    static class PropertiesConfig {
        @Bean
        Claw claw() {
            return Mockito.mock(Claw.class);
        }
    }
}
