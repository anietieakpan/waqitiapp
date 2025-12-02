package com.waqiti.analytics.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j Configuration
 *
 * Configures circuit breakers, retries, and timeouts for analytics service.
 * Protects against cascading failures from external dependencies.
 *
 * Circuit Breaker Strategy:
 * - Fails fast when downstream service is unhealthy
 * - Automatically recovers when service becomes healthy
 * - Provides fallback mechanisms
 *
 * Retry Strategy:
 * - Exponential backoff for transient failures
 * - Max 3 retries for idempotent operations
 * - No retries for validation errors
 *
 * Timeout Strategy:
 * - Prevents long-running operations from blocking threads
 * - Configurable per operation type
 *
 * @author Waqiti Platform Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-10
 */
@Configuration
@Slf4j
public class ResilienceConfig {

    /**
     * Circuit Breaker Registry with custom configurations
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        log.info("Initializing Circuit Breaker Registry");

        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50) // Open circuit if 50% of calls fail
            .slowCallRateThreshold(50) // Open circuit if 50% of calls are slow
            .slowCallDurationThreshold(Duration.ofSeconds(10)) // Call is slow if > 10s
            .permittedNumberOfCallsInHalfOpenState(5) // Try 5 calls when half-open
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(100) // Track last 100 calls
            .minimumNumberOfCalls(10) // Need at least 10 calls before calculating rate
            .waitDurationInOpenState(Duration.ofSeconds(60)) // Wait 60s before half-open
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordExceptions(
                // Record these as failures
                java.io.IOException.class,
                java.util.concurrent.TimeoutException.class,
                org.springframework.web.client.ResourceAccessException.class
            )
            .ignoreExceptions(
                // Don't record these as failures
                IllegalArgumentException.class,
                jakarta.validation.ValidationException.class
            )
            .build();

        // Payment Service Circuit Breaker (Critical - tighter thresholds)
        CircuitBreakerConfig paymentServiceConfig = CircuitBreakerConfig.from(defaultConfig)
            .failureRateThreshold(30) // More sensitive - open at 30% failure
            .slowCallDurationThreshold(Duration.ofSeconds(5)) // Faster timeout
            .build();

        // User Service Circuit Breaker
        CircuitBreakerConfig userServiceConfig = CircuitBreakerConfig.from(defaultConfig)
            .failureRateThreshold(40)
            .slowCallDurationThreshold(Duration.ofSeconds(8))
            .build();

        // Database Circuit Breaker (Looser - DB can handle more load)
        CircuitBreakerConfig databaseConfig = CircuitBreakerConfig.from(defaultConfig)
            .failureRateThreshold(60)
            .slowCallDurationThreshold(Duration.ofSeconds(15))
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .build();

        // Elasticsearch Circuit Breaker
        CircuitBreakerConfig elasticsearchConfig = CircuitBreakerConfig.from(defaultConfig)
            .failureRateThreshold(50)
            .slowCallDurationThreshold(Duration.ofSeconds(10))
            .build();

        // Kafka Circuit Breaker
        CircuitBreakerConfig kafkaConfig = CircuitBreakerConfig.from(defaultConfig)
            .failureRateThreshold(40)
            .slowCallDurationThreshold(Duration.ofSeconds(5))
            .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);

        // Register named circuit breakers
        registry.circuitBreaker("paymentService", paymentServiceConfig);
        registry.circuitBreaker("userService", userServiceConfig);
        registry.circuitBreaker("walletService", userServiceConfig);
        registry.circuitBreaker("database", databaseConfig);
        registry.circuitBreaker("elasticsearch", elasticsearchConfig);
        registry.circuitBreaker("kafka", kafkaConfig);
        registry.circuitBreaker("influxdb", elasticsearchConfig);
        registry.circuitBreaker("redis", databaseConfig);

        log.info("Circuit Breaker Registry initialized with {} configurations", registry.getAllCircuitBreakers().size());

        return registry;
    }

    /**
     * Retry Registry with custom configurations
     */
    @Bean
    public RetryRegistry retryRegistry() {
        log.info("Initializing Retry Registry");

        RetryConfig defaultConfig = RetryConfig.custom()
            .maxAttempts(3) // Max 3 retry attempts
            .waitDuration(Duration.ofMillis(500)) // Initial wait 500ms
            .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(500, 2)) // Exponential backoff
            .retryExceptions(
                // Retry on these exceptions
                java.io.IOException.class,
                java.util.concurrent.TimeoutException.class,
                org.springframework.web.client.ResourceAccessException.class,
                org.springframework.dao.TransientDataAccessException.class
            )
            .ignoreExceptions(
                // Don't retry on these
                IllegalArgumentException.class,
                jakarta.validation.ValidationException.class,
                org.springframework.dao.DataIntegrityViolationException.class
            )
            .build();

        // Idempotent operations - more retries
        RetryConfig idempotentConfig = RetryConfig.from(defaultConfig)
            .maxAttempts(5)
            .build();

        // Non-idempotent operations - fewer retries
        RetryConfig nonIdempotentConfig = RetryConfig.from(defaultConfig)
            .maxAttempts(2)
            .build();

        // Database operations - more aggressive retry
        RetryConfig databaseConfig = RetryConfig.from(defaultConfig)
            .maxAttempts(4)
            .waitDuration(Duration.ofMillis(200))
            .build();

        RetryRegistry registry = RetryRegistry.of(defaultConfig);

        // Register named retry configs
        registry.retry("default", defaultConfig);
        registry.retry("idempotent", idempotentConfig);
        registry.retry("nonIdempotent", nonIdempotentConfig);
        registry.retry("database", databaseConfig);
        registry.retry("paymentService", nonIdempotentConfig);
        registry.retry("userService", idempotentConfig);
        registry.retry("elasticsearch", idempotentConfig);

        log.info("Retry Registry initialized");

        return registry;
    }

    /**
     * Time Limiter Registry with custom configurations
     */
    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        log.info("Initializing Time Limiter Registry");

        TimeLimiterConfig defaultConfig = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(10))
            .cancelRunningFuture(true)
            .build();

        // Fast operations timeout
        TimeLimiterConfig fastConfig = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(3))
            .cancelRunningFuture(true)
            .build();

        // Slow operations timeout (batch jobs, reports)
        TimeLimiterConfig slowConfig = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(60))
            .cancelRunningFuture(true)
            .build();

        TimeLimiterRegistry registry = TimeLimiterRegistry.of(defaultConfig);

        // Register named time limiters
        registry.timeLimiter("default", defaultConfig);
        registry.timeLimiter("fast", fastConfig);
        registry.timeLimiter("slow", slowConfig);
        registry.timeLimiter("paymentService", fastConfig);
        registry.timeLimiter("userService", fastConfig);
        registry.timeLimiter("database", defaultConfig);
        registry.timeLimiter("batchJob", slowConfig);
        registry.timeLimiter("report", slowConfig);

        log.info("Time Limiter Registry initialized");

        return registry;
    }
}
