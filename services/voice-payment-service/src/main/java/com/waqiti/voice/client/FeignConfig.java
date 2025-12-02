package com.waqiti.voice.client;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Feign client configuration
 *
 * Configures:
 * - Connection and read timeouts
 * - Retry logic with exponential backoff
 * - Circuit breaker for fault tolerance
 * - Request/response logging
 * - Error handling
 */
@Configuration
public class FeignConfig {

    /**
     * Configure connection and read timeouts
     */
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
                10, TimeUnit.SECONDS,  // Connection timeout
                30, TimeUnit.SECONDS,  // Read timeout
                true                   // Follow redirects
        );
    }

    /**
     * Configure retry logic
     * Retries with exponential backoff: 100ms, 200ms, 400ms
     */
    @Bean
    public Retryer retryer() {
        return new Retryer.Default(
                100L,      // Initial interval (ms)
                1000L,     // Max interval (ms)
                3          // Max attempts
        );
    }

    /**
     * Configure logging level
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;  // Log headers, body, metadata
    }

    /**
     * Custom error decoder for better error handling
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return new FeignErrorDecoder();
    }

    /**
     * Circuit breaker configuration for all Feign clients
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(30))
                        .build())
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)                    // Last 10 calls
                        .minimumNumberOfCalls(5)                  // Min calls before circuit can open
                        .failureRateThreshold(50.0f)              // Open if 50% failure rate
                        .waitDurationInOpenState(Duration.ofSeconds(30))  // Wait 30s before half-open
                        .permittedNumberOfCallsInHalfOpenState(3) // Allow 3 calls in half-open
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build())
                .build());
    }

    /**
     * Payment service specific circuit breaker
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> paymentServiceCustomizer() {
        return factory -> factory.configure(builder -> builder
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(45))  // Longer timeout for payments
                        .build())
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(30.0f)  // More sensitive for payments
                        .waitDurationInOpenState(Duration.ofMinutes(1))
                        .build())
                .build(), "payment-service");
    }

    /**
     * Fraud detection service circuit breaker
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> fraudServiceCustomizer() {
        return factory -> factory.configure(builder -> builder
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(10))  // Faster timeout for fraud check
                        .build())
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(60.0f)  // Allow more failures (fraud service is optional)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .build())
                .build(), "fraud-detection-service");
    }
}
