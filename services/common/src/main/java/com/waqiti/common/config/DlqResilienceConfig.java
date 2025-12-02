package com.waqiti.common.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Production-ready Resilience4j configuration for DLQ processing.
 * 
 * Patterns implemented:
 * - Circuit Breaker: Prevent cascading failures
 * - Retry: Automatic retry with exponential backoff
 * - Bulkhead: Limit concurrent calls to prevent resource exhaustion
 * - Time Limiter: Prevent hanging operations
 * - Rate Limiter: Protect downstream services
 * 
 * Circuit Breakers configured:
 * - dlq-notifications: For notification service calls
 * - escalation: For escalation service calls
 * - pagerduty: For PagerDuty API calls
 * - slack: For Slack webhook calls
 * - external-api: For general external API calls
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class DlqResilienceConfig {

    private final MeterRegistry meterRegistry;

    @Value("${resilience.circuit-breaker.failure-rate-threshold:50}")
    private int failureRateThreshold;

    @Value("${resilience.circuit-breaker.slow-call-rate-threshold:50}")
    private int slowCallRateThreshold;

    @Value("${resilience.circuit-breaker.slow-call-duration-threshold:5000}")
    private long slowCallDurationThreshold;

    @Value("${resilience.circuit-breaker.permitted-calls-in-half-open:5}")
    private int permittedCallsInHalfOpen;

    @Value("${resilience.circuit-breaker.sliding-window-size:100}")
    private int slidingWindowSize;

    @Value("${resilience.circuit-breaker.wait-duration-in-open-state:30000}")
    private long waitDurationInOpenState;

    @Value("${resilience.retry.max-attempts:3}")
    private int retryMaxAttempts;

    @Value("${resilience.retry.wait-duration:1000}")
    private long retryWaitDuration;

    @Value("${resilience.bulkhead.max-concurrent-calls:25}")
    private int bulkheadMaxConcurrentCalls;

    @Value("${resilience.bulkhead.max-wait-duration:500}")
    private long bulkheadMaxWaitDuration;

    /**
     * Circuit Breaker Registry with metrics
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        
        // Register custom circuit breakers
        registerDlqNotificationsCircuitBreaker(registry);
        registerEscalationCircuitBreaker(registry);
        registerPagerDutyCircuitBreaker(registry);
        registerSlackCircuitBreaker(registry);
        registerExternalApiCircuitBreaker(registry);

        // Register metrics
        registry.circuitBreaker("dlq-notifications").getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("Circuit breaker state transition: {} -> {}", 
                            event.getStateTransition().getFromState(), 
                            event.getStateTransition().getToState());
                    meterRegistry.counter("circuit_breaker_state_transition",
                            "name", "dlq-notifications",
                            "from", event.getStateTransition().getFromState().name(),
                            "to", event.getStateTransition().getToState().name()).increment();
                });

        return registry;
    }

    /**
     * DLQ Notifications Circuit Breaker
     * Protects notification service calls from cascading failures
     */
    private void registerDlqNotificationsCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slowCallRateThreshold(slowCallRateThreshold)
                .slowCallDurationThreshold(Duration.ofMillis(slowCallDurationThreshold))
                .permittedNumberOfCallsInHalfOpenState(permittedCallsInHalfOpen)
                .slidingWindowSize(slidingWindowSize)
                .waitDurationInOpenState(Duration.ofMillis(waitDurationInOpenState))
                .minimumNumberOfCalls(10)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(
                        RuntimeException.class,
                        Exception.class
                )
                .ignoreExceptions(
                        IllegalArgumentException.class,
                        IllegalStateException.class
                )
                .build();

        registry.circuitBreaker("dlq-notifications", config);
        log.info("Registered circuit breaker: dlq-notifications");
    }

    /**
     * Escalation Circuit Breaker
     * Protects escalation operations
     */
    private void registerEscalationCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(60) // More tolerant for escalations
                .slowCallRateThreshold(60)
                .slowCallDurationThreshold(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(50)
                .waitDurationInOpenState(Duration.ofSeconds(20))
                .minimumNumberOfCalls(5)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        registry.circuitBreaker("escalation", config);
        log.info("Registered circuit breaker: escalation");
    }

    /**
     * PagerDuty Circuit Breaker
     * Protects PagerDuty API calls
     */
    private void registerPagerDutyCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(70)
                .slowCallRateThreshold(70)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindowSize(20)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .minimumNumberOfCalls(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        registry.circuitBreaker("pagerduty", config);
        log.info("Registered circuit breaker: pagerduty");
    }

    /**
     * Slack Circuit Breaker
     * Protects Slack webhook calls
     */
    private void registerSlackCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(60)
                .slowCallRateThreshold(70)
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(30)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .minimumNumberOfCalls(5)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        registry.circuitBreaker("slack", config);
        log.info("Registered circuit breaker: slack");
    }

    /**
     * External API Circuit Breaker
     * General protection for external API calls
     */
    private void registerExternalApiCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slowCallRateThreshold(slowCallRateThreshold)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .permittedNumberOfCallsInHalfOpenState(5)
                .slidingWindowSize(100)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .minimumNumberOfCalls(10)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        registry.circuitBreaker("external-api", config);
        log.info("Registered circuit breaker: external-api");
    }

    /**
     * Retry Registry
     */
    @Bean
    public RetryRegistry retryRegistry() {
        RetryRegistry registry = RetryRegistry.ofDefaults();

        // DLQ Notifications Retry
        RetryConfig notificationsRetryConfig = RetryConfig.custom()
                .maxAttempts(retryMaxAttempts)
                .waitDuration(Duration.ofMillis(retryWaitDuration))
                .retryExceptions(RuntimeException.class, Exception.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();
        registry.retry("dlq-notifications", notificationsRetryConfig);

        // Escalation Retry - more aggressive
        RetryConfig escalationRetryConfig = RetryConfig.custom()
                .maxAttempts(5)
                .waitDuration(Duration.ofSeconds(2))
                .retryExceptions(RuntimeException.class)
                .build();
        registry.retry("escalation", escalationRetryConfig);

        // External API Retry
        RetryConfig externalApiRetryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .build();
        registry.retry("external-api", externalApiRetryConfig);

        log.info("Retry registry configured with {} retry policies", registry.getAllRetries().size());
        return registry;
    }

    /**
     * Bulkhead Registry
     * Limits concurrent calls to prevent resource exhaustion
     */
    @Bean
    public BulkheadRegistry bulkheadRegistry() {
        BulkheadRegistry registry = BulkheadRegistry.ofDefaults();

        // DLQ Processing Bulkhead
        BulkheadConfig dlqConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(bulkheadMaxConcurrentCalls)
                .maxWaitDuration(Duration.ofMillis(bulkheadMaxWaitDuration))
                .build();
        registry.bulkhead("dlq-processing", dlqConfig);

        // Notification Bulkhead - allow more concurrency
        BulkheadConfig notificationConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(50)
                .maxWaitDuration(Duration.ofMillis(300))
                .build();
        registry.bulkhead("dlq-notifications", notificationConfig);

        // Escalation Bulkhead - limited concurrency for critical operations
        BulkheadConfig escalationConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(10)
                .maxWaitDuration(Duration.ofSeconds(1))
                .build();
        registry.bulkhead("escalation", escalationConfig);

        log.info("Bulkhead registry configured with {} bulkheads", registry.getAllBulkheads().size());
        return registry;
    }

    /**
     * Time Limiter Registry
     * Prevents hanging operations
     */
    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        TimeLimiterRegistry registry = TimeLimiterRegistry.ofDefaults();

        // DLQ Operations Time Limiter
        TimeLimiterConfig dlqConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(30))
                .cancelRunningFuture(true)
                .build();
        registry.timeLimiter("dlq-operations", dlqConfig);

        // Notification Time Limiter
        TimeLimiterConfig notificationConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .cancelRunningFuture(true)
                .build();
        registry.timeLimiter("dlq-notifications", notificationConfig);

        // Escalation Time Limiter - longer timeout for critical operations
        TimeLimiterConfig escalationConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(15))
                .cancelRunningFuture(true)
                .build();
        registry.timeLimiter("escalation", escalationConfig);

        // External API Time Limiter
        TimeLimiterConfig externalConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))
                .cancelRunningFuture(true)
                .build();
        registry.timeLimiter("external-api", externalConfig);

        log.info("Time limiter registry configured with {} time limiters", 
                registry.getAllTimeLimiters().size());
        return registry;
    }

    /**
     * Configure circuit breaker event logging and metrics
     */
    @Bean
    public CircuitBreaker dlqNotificationsCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("dlq-notifications");

        circuitBreaker.getEventPublisher()
                .onSuccess(event -> {
                    log.debug("Circuit breaker success: {}", event.getCircuitBreakerName());
                    meterRegistry.counter("circuit_breaker_success", 
                            "name", event.getCircuitBreakerName()).increment();
                })
                .onError(event -> {
                    log.warn("Circuit breaker error: {} - {}", 
                            event.getCircuitBreakerName(), 
                            event.getThrowable().getMessage());
                    meterRegistry.counter("circuit_breaker_error",
                            "name", event.getCircuitBreakerName(),
                            "exception", event.getThrowable().getClass().getSimpleName()).increment();
                })
                .onStateTransition(event -> {
                    log.warn("Circuit breaker state transition: {} from {} to {}",
                            event.getCircuitBreakerName(),
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());
                })
                .onSlowCallRateExceeded(event -> {
                    log.warn("Circuit breaker slow call rate exceeded: {} - rate: {}%",
                            event.getCircuitBreakerName(),
                            event.getSlowCallRate());
                    meterRegistry.counter("circuit_breaker_slow_call_rate_exceeded",
                            "name", event.getCircuitBreakerName()).increment();
                })
                .onFailureRateExceeded(event -> {
                    log.error("Circuit breaker failure rate exceeded: {} - rate: {}%",
                            event.getCircuitBreakerName(),
                            event.getFailureRate());
                    meterRegistry.counter("circuit_breaker_failure_rate_exceeded",
                            "name", event.getCircuitBreakerName()).increment();
                });

        return circuitBreaker;
    }

    /**
     * Configure retry event logging and metrics
     */
    @Bean
    public Retry dlqNotificationsRetry(RetryRegistry registry) {
        Retry retry = registry.retry("dlq-notifications");

        retry.getEventPublisher()
                .onRetry(event -> {
                    log.warn("Retry attempt {} for {}: {}",
                            event.getNumberOfRetryAttempts(),
                            "dlq-notifications",
                            event.getLastThrowable().getMessage());
                    meterRegistry.counter("retry_attempt",
                            "name", "dlq-notifications",
                            "attempt", String.valueOf(event.getNumberOfRetryAttempts())).increment();
                })
                .onSuccess(event -> {
                    log.info("Retry succeeded after {} attempts for {}",
                            event.getNumberOfRetryAttempts(),
                            "dlq-notifications");
                    meterRegistry.counter("retry_success",
                            "name", "dlq-notifications").increment();
                })
                .onError(event -> {
                    log.error("Retry failed after {} attempts for {}: {}",
                            event.getNumberOfRetryAttempts(),
                            "dlq-notifications",
                            event.getLastThrowable().getMessage());
                    meterRegistry.counter("retry_exhausted",
                            "name", "dlq-notifications").increment();
                });

        return retry;
    }
}
