package com.waqiti.analytics.config.properties;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Health Check Configuration Properties
 *
 * <p>Production-grade health check configuration for circuit breaker
 * and rate limiter patterns to ensure system resilience.
 *
 * @author Waqiti Analytics Team
 * @since 1.0.0
 * @version 1.0
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "health")
@Schema(description = "Health check and resilience pattern configuration")
public class HealthProperties {

    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
    private RateLimiterConfig rateLimiter = new RateLimiterConfig();

    @Data
    @Schema(description = "Circuit breaker pattern configuration")
    public static class CircuitBreakerConfig {

        @Schema(description = "Enable circuit breaker pattern", example = "true")
        private boolean enabled = true;
    }

    @Data
    @Schema(description = "Rate limiter pattern configuration")
    public static class RateLimiterConfig {

        @Schema(description = "Enable rate limiting", example = "true")
        private boolean enabled = true;
    }
}
