package com.waqiti.apigateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Circuit breaker configuration for API Gateway
 * Implements resilience patterns for downstream service calls
 */
@Configuration
@Slf4j
public class CircuitBreakerConfig {

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
            .circuitBreakerConfig(createDefaultCircuitBreakerConfig())
            .timeLimiterConfig(createDefaultTimeLimiterConfig())
            .build());
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.of(createDefaultCircuitBreakerConfig());
    }

    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        return TimeLimiterRegistry.of(createDefaultTimeLimiterConfig());
    }

    /**
     * Service-specific circuit breaker configurations
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> paymentServiceCustomizer() {
        return factory -> factory.configure(builder -> builder
            .circuitBreakerConfig(createPaymentServiceCircuitBreakerConfig())
            .timeLimiterConfig(createPaymentServiceTimeLimiterConfig())
            .build(), "payment-service", "crypto-service", "wallet-service");
    }

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> authServiceCustomizer() {
        return factory -> factory.configure(builder -> builder
            .circuitBreakerConfig(createAuthServiceCircuitBreakerConfig())
            .timeLimiterConfig(createAuthServiceTimeLimiterConfig())
            .build(), "keycloak", "auth-service", "user-service");
    }

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> kycServiceCustomizer() {
        return factory -> factory.configure(builder -> builder
            .circuitBreakerConfig(createKycServiceCircuitBreakerConfig())
            .timeLimiterConfig(createKycServiceTimeLimiterConfig())
            .build(), "kyc-service", "compliance-service");
    }

    /**
     * Default circuit breaker configuration
     */
    private io.github.resilience4j.circuitbreaker.CircuitBreakerConfig createDefaultCircuitBreakerConfig() {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .slidingWindowType(SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .slowCallRateThreshold(100)
            .slowCallDurationThreshold(Duration.ofSeconds(5))
            .recordExceptions(
                Exception.class,
                TimeoutException.class,
                RuntimeException.class
            )
            .ignoreExceptions(IllegalArgumentException.class)
            .build();
    }

    /**
     * Payment service circuit breaker configuration - stricter settings
     */
    private io.github.resilience4j.circuitbreaker.CircuitBreakerConfig createPaymentServiceCircuitBreakerConfig() {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .slidingWindowType(SlidingWindowType.TIME_BASED)
            .slidingWindowSize(60) // 60 seconds window
            .minimumNumberOfCalls(10)
            .failureRateThreshold(30) // Lower threshold for payment services
            .waitDurationInOpenState(Duration.ofSeconds(60)) // Longer wait time
            .permittedNumberOfCallsInHalfOpenState(5)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .slowCallRateThreshold(80)
            .slowCallDurationThreshold(Duration.ofSeconds(3))
            .recordExceptions(
                Exception.class,
                TimeoutException.class,
                RuntimeException.class
            )
            .ignoreExceptions(IllegalArgumentException.class)
            .writableStackTraceEnabled(true)
            .registerHealthIndicator(true)
            .eventConsumerBufferSize(10)
            .recordFailurePredicate(throwable -> {
                // Custom failure predicate for payment services
                if (throwable instanceof PaymentException) {
                    return !((PaymentException) throwable).isRetryable();
                }
                return true;
            })
            .build();
    }

    /**
     * Auth service circuit breaker configuration
     */
    private io.github.resilience4j.circuitbreaker.CircuitBreakerConfig createAuthServiceCircuitBreakerConfig() {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .slidingWindowType(SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .failureRateThreshold(40)
            .waitDurationInOpenState(Duration.ofSeconds(45))
            .permittedNumberOfCallsInHalfOpenState(5)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .slowCallRateThreshold(90)
            .slowCallDurationThreshold(Duration.ofSeconds(4))
            .build();
    }

    /**
     * KYC service circuit breaker configuration - relaxed settings for async operations
     */
    private io.github.resilience4j.circuitbreaker.CircuitBreakerConfig createKycServiceCircuitBreakerConfig() {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .slidingWindowType(SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(15)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(60) // Higher tolerance for KYC services
            .waitDurationInOpenState(Duration.ofSeconds(20))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .slowCallRateThreshold(100)
            .slowCallDurationThreshold(Duration.ofSeconds(10)) // Longer timeout for KYC
            .build();
    }

    /**
     * Default time limiter configuration
     */
    private TimeLimiterConfig createDefaultTimeLimiterConfig() {
        return TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(10))
            .cancelRunningFuture(true)
            .build();
    }

    /**
     * Payment service time limiter configuration - strict timeouts
     */
    private TimeLimiterConfig createPaymentServiceTimeLimiterConfig() {
        return TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(5)) // Strict 5-second timeout
            .cancelRunningFuture(true)
            .build();
    }

    /**
     * Auth service time limiter configuration
     */
    private TimeLimiterConfig createAuthServiceTimeLimiterConfig() {
        return TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(8))
            .cancelRunningFuture(true)
            .build();
    }

    /**
     * KYC service time limiter configuration - relaxed timeouts
     */
    private TimeLimiterConfig createKycServiceTimeLimiterConfig() {
        return TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(30)) // Longer timeout for KYC operations
            .cancelRunningFuture(false) // Don't cancel KYC operations
            .build();
    }

    /**
     * Circuit breaker event listeners for monitoring
     */
    @Bean
    public CircuitBreakerEventListener circuitBreakerEventListener(CircuitBreakerRegistry registry) {
        CircuitBreakerEventListener listener = new CircuitBreakerEventListener();
        
        registry.getAllCircuitBreakers().forEach(circuitBreaker -> {
            circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("Circuit breaker {} state transition: {} -> {}", 
                        event.getCircuitBreakerName(),
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState());
                    listener.onStateTransition(event);
                })
                .onFailureRateExceeded(event -> {
                    log.error("Circuit breaker {} failure rate exceeded: {}%", 
                        event.getCircuitBreakerName(),
                        event.getFailureRate());
                    listener.onFailureRateExceeded(event);
                })
                .onSlowCallRateExceeded(event -> {
                    log.warn("Circuit breaker {} slow call rate exceeded: {}%", 
                        event.getCircuitBreakerName(),
                        event.getSlowCallRate());
                    listener.onSlowCallRateExceeded(event);
                });
        });
        
        return listener;
    }

    /**
     * Circuit breaker event listener for metrics and alerting
     */
    public static class CircuitBreakerEventListener {
        
        public void onStateTransition(io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent event) {
            // Send metrics to monitoring system
            // Trigger alerts for critical state transitions
            if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                // Alert: Circuit breaker opened
                sendAlert("Circuit breaker opened: " + event.getCircuitBreakerName());
            }
        }
        
        public void onFailureRateExceeded(io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnFailureRateExceededEvent event) {
            // Log and monitor failure rates
            sendMetric("circuit_breaker.failure_rate", event.getFailureRate(), event.getCircuitBreakerName());
        }
        
        public void onSlowCallRateExceeded(io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnSlowCallRateExceededEvent event) {
            // Log and monitor slow call rates
            sendMetric("circuit_breaker.slow_call_rate", event.getSlowCallRate(), event.getCircuitBreakerName());
        }
        
        private void sendAlert(String message) {
            // Integration with alerting system (PagerDuty, Slack, etc.)
            log.error("ALERT: {}", message);
        }
        
        private void sendMetric(String metricName, float value, String circuitBreakerName) {
            // Integration with metrics system (Prometheus, Grafana, etc.)
            log.info("METRIC: {} = {} for {}", metricName, value, circuitBreakerName);
        }
    }

    /**
     * Custom exception for payment services
     */
    public static class PaymentException extends RuntimeException {
        private final boolean retryable;
        
        public PaymentException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }
        
        public boolean isRetryable() {
            return retryable;
        }
    }
}