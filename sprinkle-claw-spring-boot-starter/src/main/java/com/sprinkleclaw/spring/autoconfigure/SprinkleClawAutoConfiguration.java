package com.sprinkleclaw.spring.autoconfigure;

import com.sprinkleclaw.bootstrap.Claw;
import com.sprinkleclaw.core.observability.AgentMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Sprinkle-Claw 核心自动配置。
 *
 * <p>暴露两个基础设施 bean：</p>
 * <ul>
 *   <li>{@link SprinkleClawFactory}：根据 {@link SprinkleClawProperties} 中的 instance 配置构建 {@link Claw}</li>
 *   <li>{@link SprinkleClawBeanRegistrar}：{@link org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor}
 *       动态注册命名 Claw bean（每个 instance 一个）</li>
 * </ul>
 *
 * <p>用户配置示例：</p>
 * <pre>{@code
 * sprinkle-claw:
 *   agent:                    # 全局默认（被 instance 同名字段覆盖）
 *     max-iterations: 200
 *     system-prompt: "你是助手"
 *   llm:
 *     primary: claude         # ≥2 instance 必填，1 instance 时自动作为 primary
 *     instances:
 *       claude:
 *         provider: anthropic
 *         api-key: ${ANTHROPIC_API_KEY}
 *         model: claude-opus-4-7
 *       qa-bot:
 *         provider: openai
 *         api-key: ${DEEPSEEK_API_KEY}
 *         base-url: https://api.deepseek.com/v1
 *         model: deepseek-v4-flash
 *         system-prompt: "你是客服机器人"   # 覆盖全局
 *         max-iterations: 5                # 覆盖全局
 * }</pre>
 *
 * <p>用户使用示例：</p>
 * <pre>{@code
 * @Autowired Claw claw;                          // 注入 primary（claude）
 * @Autowired @Qualifier("qa-bot") Claw qaBot;
 * }</pre>
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)；0.10.0 (MVP9) 改为多实例模型
 */
@AutoConfiguration
@ConditionalOnClass(Claw.class)
@EnableConfigurationProperties(SprinkleClawProperties.class)
public class SprinkleClawAutoConfiguration {

    @Bean(name = SprinkleClawBeanRegistrar.FACTORY_BEAN_NAME)
    @ConditionalOnMissingBean(SprinkleClawFactory.class)
    public SprinkleClawFactory sprinkleClawFactory(SprinkleClawProperties properties, ObjectProvider<AgentMetrics> metricsProvider) {
        return new SprinkleClawFactory(properties, metricsProvider);
    }

    /**
     * 必须为 {@code static} 以避免触发 BeanFactory 过早实例化（Spring 文档要求所有
     * BeanDefinitionRegistryPostProcessor 应作为 static @Bean 暴露）。
     */
    @Bean
    public static SprinkleClawBeanRegistrar sprinkleClawBeanRegistrar() {
        return new SprinkleClawBeanRegistrar();
    }
}
