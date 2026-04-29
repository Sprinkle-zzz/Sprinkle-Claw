package icu.sprinkle.loom.spring.autoconfigure;

import icu.sprinkle.loom.bootstrap.Loom;
import icu.sprinkle.loom.core.observability.AgentMetrics;
import icu.sprinkle.loom.spring.actuator.MicrometerAgentMetrics;
import icu.sprinkle.loom.spring.actuator.SprinkleLoomHealthIndicator;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Actuator / Micrometer 自动配置。
 * <p>Actuator 或 Micrometer 不在 classpath 时对应子配置不生效。</p>
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
@AutoConfiguration(after = SprinkleLoomAutoConfiguration.class)
public class ActuatorAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HealthIndicator.class)
    static class HealthConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "sprinkleLoomHealthIndicator")
        @ConditionalOnBean(Loom.class)
        public HealthIndicator sprinkleLoomHealthIndicator(Loom claw) {
            return new SprinkleLoomHealthIndicator(claw);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean(AgentMetrics.class)
        @ConditionalOnBean(MeterRegistry.class)
        public AgentMetrics sprinkleLoomAgentMetrics(MeterRegistry registry) {
            return new MicrometerAgentMetrics(registry);
        }
    }
}
