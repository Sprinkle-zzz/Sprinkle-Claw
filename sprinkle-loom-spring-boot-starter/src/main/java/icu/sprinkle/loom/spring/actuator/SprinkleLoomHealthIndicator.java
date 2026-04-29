package icu.sprinkle.loom.spring.actuator;

import icu.sprinkle.loom.bootstrap.Loom;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Sprinkle-Loom 健康检查指示器。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class SprinkleLoomHealthIndicator implements HealthIndicator {

    private final Loom claw;

    public SprinkleLoomHealthIndicator(Loom claw) {
        this.claw = claw;
    }

    @Override
    public Health health() {
        if (claw == null) {
            return Health.down().withDetail("reason", "Loom not initialized").build();
        }
        return Health.up().withDetail("status", "Agent ready").build();
    }
}
