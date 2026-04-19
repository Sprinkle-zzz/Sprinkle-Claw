package com.sprinkleclaw.spring.actuator;

import com.sprinkleclaw.bootstrap.Claw;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Sprinkle-Claw 健康检查指示器。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class SprinkleClawHealthIndicator implements HealthIndicator {

    private final Claw claw;

    public SprinkleClawHealthIndicator(Claw claw) {
        this.claw = claw;
    }

    @Override
    public Health health() {
        if (claw == null) {
            return Health.down().withDetail("reason", "Claw not initialized").build();
        }
        return Health.up().withDetail("status", "Agent ready").build();
    }
}
