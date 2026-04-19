package com.sprinkleclaw.spring.autoconfigure;

import com.sprinkleclaw.bootstrap.Claw;
import com.sprinkleclaw.core.observability.AgentMetrics;
import com.sprinkleclaw.spring.actuator.MicrometerAgentMetrics;
import com.sprinkleclaw.spring.actuator.SprinkleClawHealthIndicator;
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
@AutoConfiguration(after = SprinkleClawAutoConfiguration.class)
public class ActuatorAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HealthIndicator.class)
    static class HealthConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "sprinkleClawHealthIndicator")
        @ConditionalOnBean(Claw.class)
        public HealthIndicator sprinkleClawHealthIndicator(Claw claw) {
            return new SprinkleClawHealthIndicator(claw);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean(AgentMetrics.class)
        @ConditionalOnBean(MeterRegistry.class)
        public AgentMetrics sprinkleClawAgentMetrics(MeterRegistry registry) {
            return new MicrometerAgentMetrics(registry);
        }
    }
}
