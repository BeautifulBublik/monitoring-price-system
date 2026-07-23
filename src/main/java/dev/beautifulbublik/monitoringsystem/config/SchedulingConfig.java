package dev.beautifulbublik.monitoringsystem.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled}. Turned off with the flag {@code price-monitor.scheduler.enabled=false} —
 * this is needed by tests and local debugging so the background sweep does not reach out to the internet.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "price-monitor.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
