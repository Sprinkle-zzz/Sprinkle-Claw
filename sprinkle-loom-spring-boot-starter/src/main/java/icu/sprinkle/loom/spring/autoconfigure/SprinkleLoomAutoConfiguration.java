package icu.sprinkle.loom.spring.autoconfigure;

import icu.sprinkle.loom.bootstrap.Loom;
import icu.sprinkle.loom.core.observability.AgentMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Sprinkle-Loom 核心自动配置。
 *
 * <p>暴露两个基础设施 bean：</p>
 * <ul>
 *   <li>{@link SprinkleLoomFactory}：根据 {@link SprinkleLoomProperties} 中的 instance 配置构建 {@link Loom}</li>
 *   <li>{@link SprinkleLoomBeanRegistrar}：{@link org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor}
 *       动态注册命名 Loom bean（每个 instance 一个）</li>
 * </ul>
 *
 * <p>用户配置示例：</p>
 * <pre>{@code
 * sprinkle-loom:
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
 * @Autowired Loom loom;
 * @Autowired @Qualifier("qa-bot") Loom qaBot;
 * }</pre>
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)；0.10.0 (MVP9) 改为多实例模型
 */
@AutoConfiguration
@ConditionalOnClass(Loom.class)
@EnableConfigurationProperties(SprinkleLoomProperties.class)
public class SprinkleLoomAutoConfiguration {

    @Bean(name = SprinkleLoomBeanRegistrar.FACTORY_BEAN_NAME)
    @ConditionalOnMissingBean(SprinkleLoomFactory.class)
    public SprinkleLoomFactory sprinkleLoomFactory(SprinkleLoomProperties properties, ObjectProvider<AgentMetrics> metricsProvider) {
        return new SprinkleLoomFactory(properties, metricsProvider);
    }

    /**
     * 必须为 {@code static} 以避免触发 BeanFactory 过早实例化（Spring 文档要求所有
     * BeanDefinitionRegistryPostProcessor 应作为 static @Bean 暴露）。
     */
    @Bean
    public static SprinkleLoomBeanRegistrar sprinkleLoomBeanRegistrar() {
        return new SprinkleLoomBeanRegistrar();
    }
}
