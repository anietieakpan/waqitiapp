package com.waqiti.account.client;

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
 * Ledger Service Client Configuration
 *
 * Configures Feign client with:
 * - Circuit breaker for fault tolerance
 * - Retry logic with exponential backoff
 * - Request timeouts
 * - Logging for debugging
 * - Error decoding
 *
 * @author Waqiti Platform Team - Platform Engineering
 * @version 1.0.0
 * @since 2025-10-25
 */
@Configuration
public class LedgerServiceClientConfiguration {

    /**
     * Configure Feign request options
     *
     * - Connect timeout: 5 seconds (time to establish connection)
     * - Read timeout: 10 seconds (time to receive response)
     * - Follow redirects: enabled
     */
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
                5, TimeUnit.SECONDS,  // connectTimeout
                10, TimeUnit.SECONDS, // readTimeout
                true                   // followRedirects
        );
    }

    /**
     * Configure retry logic
     *
     * - Max attempts: 3
     * - Period: 1000ms (1 second between retries)
     * - Max period: 3000ms (maximum 3 seconds between retries)
     * - Exponential backoff multiplier: 1.5
     */
    @Bean
    public Retryer retryer() {
        return new Retryer.Default(
                1000,  // period (initial interval)
                3000,  // maxPeriod (max interval)
                3      // maxAttempts
        );
    }

    /**
     * Configure Feign logging level
     *
     * FULL logs headers, body, and metadata for debugging.
     * In production, consider BASIC or HEADERS level for performance.
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    /**
     * Configure circuit breaker
     *
     * Circuit breaker prevents cascading failures by stopping requests
     * to failing services and allowing them time to recover.
     *
     * Configuration:
     * - Failure rate threshold: 50% (half-open after 50% failures)
     * - Wait duration in open state: 30 seconds
     * - Sliding window size: 10 requests
     * - Minimum number of calls: 5
     * - Slow call duration: 2 seconds
     * - Slow call rate threshold: 60%
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> globalCustomConfiguration() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .failureRateThreshold(50.0f)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .slowCallDurationThreshold(Duration.ofSeconds(2))
                        .slowCallRateThreshold(60.0f)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(10))
                        .build())
                .build());
    }

    /**
     * Custom error decoder for handling specific HTTP error codes
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return new LedgerServiceErrorDecoder();
    }
}
