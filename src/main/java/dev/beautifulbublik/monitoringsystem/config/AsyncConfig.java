package dev.beautifulbublik.monitoringsystem.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async} so notification delivery runs off the price-check worker thread.
 * Uses Spring Boot's auto-configured {@code applicationTaskExecutor}; no custom pool is needed
 * for the current low notification volume.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
