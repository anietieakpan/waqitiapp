package com.waqiti.account.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit breaker configuration for Feign clients
 *
 * <p>Provides intelligent fault tolerance with:</p>
 * <ul>
 *   <li>Automatic circuit opening on failure threshold</li>
 *   <li>Half-open state for recovery testing</li>
 *   <li>Timeout protection</li>
 *   <li>Fallback invocation</li>
 *   <li>Metrics tracking</li>
 * </ul>
 *
 * <h3>Circuit Breaker States:</h3>
 * <pre>
 * CLOSED → OPEN (on 50% failure rate over 10 calls)
 * OPEN → HALF_OPEN (after 60s wait)
 * HALF_OPEN → CLOSED (on 5 successful calls)
 * HALF_OPEN → OPEN (on failure)
 * </pre>
 *
 * <h3>Configuration Profiles:</h3>
 * <ul>
 *   <li><b>default:</b> Standard services (ledger, compliance, notification)</li>
 *   <li><b>financial:</b> Financial services (stricter thresholds)</li>
 *   <li><b>compliance:</b> Compliance services (longer timeouts)</li>
 * </ul>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Configuration
@Slf4j
public class CircuitBreakerConfiguration {

    /**
     * Default circuit breaker configuration
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
            .timeLimiterConfig(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(3))  // 3s timeout
                .build())
            .circuitBreakerConfig(CircuitBreakerConfig.custom()
                // Failure rate threshold
                .failureRateThreshold(50)  // Open circuit if 50% failures
                .slowCallRateThreshold(50)  // Slow calls threshold
                .slowCallDurationThreshold(Duration.ofSeconds(2))

                // Sliding window
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)  // Last 10 calls
                .minimumNumberOfCalls(5)  // Need at least 5 calls before calculating

                // State transitions
                .waitDurationInOpenState(Duration.ofSeconds(60))  // Wait 60s before HALF_OPEN
                .permittedNumberOfCallsInHalfOpenState(5)  // 5 test calls in HALF_OPEN
                .automaticTransitionFromOpenToHalfOpenEnabled(true)

                // Exceptions
                .ignoreExceptions(IllegalArgumentException.class)  // Don't count as failures

                // Callbacks
                .build())
            .build());
    }

    /**
     * Financial services circuit breaker (stricter thresholds)
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> financialCustomizer() {
        return factory -> factory.configure(builder -> builder
            .timeLimiterConfig(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))  // Longer timeout for financial ops
                .build())
            .circuitBreakerConfig(CircuitBreakerConfig.custom()
                // Stricter thresholds
                .failureRateThreshold(30)  // Open at 30% failure
                .slowCallRateThreshold(40)
                .slowCallDurationThreshold(Duration.ofSeconds(3))

                // Larger sliding window
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)

                // Faster recovery testing
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)

                .build())
            .build(), "ledger-service", "core-banking-service");
    }

    /**
     * Compliance services circuit breaker (longer timeouts)
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> complianceCustomizer() {
        return factory -> factory.configure(builder -> builder
            .timeLimiterConfig(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))  // KYC checks can be slow
                .build())
            .circuitBreakerConfig(CircuitBreakerConfig.custom()
                // More lenient for compliance
                .failureRateThreshold(60)
                .slowCallRateThreshold(60)
                .slowCallDurationThreshold(Duration.ofSeconds(8))

                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)

                // Longer wait before retry
                .waitDurationInOpenState(Duration.ofSeconds(120))  // 2 min
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)

                .build())
            .build(), "compliance-service", "kyc-service");
    }
}
