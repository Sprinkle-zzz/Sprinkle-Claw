package com.sprinkleclaw.spring.autoconfigure;

import com.sprinkleclaw.bootstrap.Claw;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 多实例 LLM 配置注册测试。
 *
 * <p>覆盖 {@link SprinkleClawBeanRegistrar} 的 5 种关键路径：</p>
 * <ul>
 *   <li>0 instance：不注册任何 Claw bean</li>
 *   <li>1 instance：自动作为 primary，{@code @Autowired Claw} 可注入</li>
 *   <li>≥2 instance + 显式 primary：primary bean 标记正确，命名 bean 可通过 qualifier 取出</li>
 *   <li>≥2 instance + 缺失 primary：启动报错（清晰提示）</li>
 *   <li>≥2 instance + primary 错指：启动报错（列出可用 instance）</li>
 * </ul>
 *
 * <p>用 {@link MockFactoryConfig} 替换真实 {@link SprinkleClawFactory}，避免触发 ClawBuilder.build()
 * 走 ServiceLoader 找 LlmProvider（spring-boot-starter test 默认 classpath 无 LLM 模块）。</p>
 *
 * @author sprinkle
 * @since 0.10.0 (MVP9)
 */
class SprinkleClawMultiInstanceTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SprinkleClawAutoConfiguration.class))
            .withUserConfiguration(MockFactoryConfig.class);

    @Test
    void noInstances_registersNoClawBean() {
        runner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(Claw.class);
        });
    }

    @Test
    void singleInstance_autoBecomesPrimary() {
        runner.withPropertyValues(
                        "sprinkle-claw.llm.instances.solo.api-key=k",
                        "sprinkle-claw.llm.instances.solo.model=test-model")
                .run(ctx -> {
                    assertThat(ctx).hasBean("solo");
                    assertThat(ctx.getBean(Claw.class)).isNotNull();
                    assertThat(ctx.getBean("solo", Claw.class)).isNotNull();
                });
    }

    @Test
    void multipleInstances_explicitPrimary_namedBeansResolveByQualifier() {
        runner.withPropertyValues(
                        "sprinkle-claw.llm.primary=claude",
                        "sprinkle-claw.llm.instances.claude.api-key=a",
                        "sprinkle-claw.llm.instances.claude.model=claude-x",
                        "sprinkle-claw.llm.instances.qa-bot.api-key=b",
                        "sprinkle-claw.llm.instances.qa-bot.model=deepseek-v4-flash")
                .run(ctx -> {
                    assertThat(ctx).hasBean("claude");
                    assertThat(ctx).hasBean("qa-bot");
                    // primary 注入：@Autowired Claw → claude
                    assertThat(ctx.getBean(Claw.class)).isSameAs(ctx.getBean("claude", Claw.class));
                    // 命名注入
                    assertThat(ctx.getBean("qa-bot", Claw.class)).isNotNull();
                    assertThat(ctx.getBean("qa-bot", Claw.class))
                            .isNotSameAs(ctx.getBean("claude", Claw.class));
                });
    }

    @Test
    void multipleInstances_missingPrimary_failsAtStartup() {
        runner.withPropertyValues(
                        "sprinkle-claw.llm.instances.a.api-key=k",
                        "sprinkle-claw.llm.instances.a.model=m1",
                        "sprinkle-claw.llm.instances.b.api-key=k",
                        "sprinkle-claw.llm.instances.b.model=m2")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThatThrownBy(() -> {
                        throw ctx.getStartupFailure();
                    }).hasMessageContaining("primary must be explicitly set");
                });
    }

    @Test
    void primary_pointingToUnknownInstance_failsAtStartup() {
        runner.withPropertyValues(
                        "sprinkle-claw.llm.primary=does-not-exist",
                        "sprinkle-claw.llm.instances.real.api-key=k",
                        "sprinkle-claw.llm.instances.real.model=m")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThatThrownBy(() -> {
                        throw ctx.getStartupFailure();
                    }).hasMessageContaining("does not match any configured instance");
                });
    }

    @Test
    void agentBlockProvidesGlobalDefault_instanceFieldsOverride() {
        runner.withPropertyValues(
                        "sprinkle-claw.agent.system-prompt=global",
                        "sprinkle-claw.agent.max-iterations=200",
                        "sprinkle-claw.llm.primary=override",
                        "sprinkle-claw.llm.instances.global-default.api-key=k",
                        "sprinkle-claw.llm.instances.global-default.model=m1",
                        "sprinkle-claw.llm.instances.override.api-key=k",
                        "sprinkle-claw.llm.instances.override.model=m2",
                        "sprinkle-claw.llm.instances.override.system-prompt=local",
                        "sprinkle-claw.llm.instances.override.max-iterations=5")
                .run(ctx -> {
                    SprinkleClawProperties props = ctx.getBean(SprinkleClawProperties.class);
                    assertThat(props.getAgent().getSystemPrompt()).isEqualTo("global");
                    assertThat(props.getLlm().getInstances().get("global-default").getSystemPrompt()).isNull();
                    assertThat(props.getLlm().getInstances().get("override").getSystemPrompt()).isEqualTo("local");
                    assertThat(props.getLlm().getInstances().get("override").getMaxIterations()).isEqualTo(5);
                });
    }

    /**
     * Mock factory：避免触发真实 ClawBuilder.build()（test classpath 无 LLM provider）。
     * 命名为 {@code sprinkleClawFactory} 与 {@link SprinkleClawBeanRegistrar#FACTORY_BEAN_NAME} 一致，
     * 覆盖 AutoConfig 中的默认 factory bean。
     */
    @Configuration(proxyBeanMethods = false)
    static class MockFactoryConfig {
        @Bean(name = "sprinkleClawFactory")
        SprinkleClawFactory sprinkleClawFactory() {
            SprinkleClawFactory factory = mock(SprinkleClawFactory.class);
            when(factory.create(anyString())).thenAnswer(inv -> Mockito.mock(Claw.class));
            return factory;
        }
    }
}
