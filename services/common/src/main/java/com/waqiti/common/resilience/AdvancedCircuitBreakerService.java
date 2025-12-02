package com.waqiti.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import com.waqiti.common.exception.PaymentServiceUnavailableException;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Advanced Circuit Breaker and Resilience Service
 * Provides comprehensive fault tolerance patterns for production systems
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedCircuitBreakerService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Value("${resilience.circuit-breaker.failure-rate-threshold:50}")
    private int failureRateThreshold;

    @Value("${resilience.circuit-breaker.wait-duration-in-open-state:30}")
    private int waitDurationInOpenState;

    @Value("${resilience.circuit-breaker.sliding-window-size:10}")
    private int slidingWindowSize;

    @Value("${resilience.circuit-breaker.minimum-number-of-calls:5}")
    private int minimumNumberOfCalls;

    @Value("${resilience.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${resilience.retry.wait-duration:1000}")
    private int retryWaitDuration;

    @Value("${resilience.bulkhead.max-concurrent-calls:10}")
    private int maxConcurrentCalls;

    @Value("${resilience.timeout.duration:5000}")
    private int timeoutDuration;

    /**
     * Execute operation with comprehensive resilience patterns
     */
    public <T> CompletableFuture<T> executeWithResilience(String serviceName,
                                                         Supplier<T> operation,
                                                         ResilienceConfig config) {

        // Use explicit type witness to help compiler inference
        return CompletableFuture.<T>supplyAsync(() -> {
            try {
                // Get or create circuit breaker
                CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serviceName, config);

                // Get or create retry
                Retry retry = getOrCreateRetry(serviceName, config);

                // Get or create bulkhead
                Bulkhead bulkhead = getOrCreateBulkhead(serviceName, config);

                // Get or create time limiter
                TimeLimiter timeLimiter = getOrCreateTimeLimiter(serviceName, config);

                // Compose all resilience patterns
                Supplier<T> decoratedSupplier = Bulkhead.decorateSupplier(bulkhead,
                    CircuitBreaker.decorateSupplier(circuitBreaker,
                        Retry.decorateSupplier(retry, operation)));

                // Execute with timeout
                CompletableFuture<T> future = CompletableFuture.supplyAsync(decoratedSupplier, executorService);

                T result = timeLimiter.executeFutureSupplier(() -> future);

                // Record success metrics
                recordSuccess(serviceName);

                return result;

            } catch (Exception e) {
                // Record failure metrics
                recordFailure(serviceName, e);

                // Check if fallback is available
                if (config.getFallbackOperation() != null) {
                    log.warn("Operation failed for {}, executing fallback: {}", serviceName, e.getMessage());
                    return config.getFallbackOperation().get();
                }

                throw new ServiceUnavailableException("Service " + serviceName + " is unavailable", e);
            }
        }, executorService);
    }

    /**
     * Execute database operation with specialized resilience patterns
     */
    public <T> T executeDatabaseOperation(String operationName, Supplier<T> operation) {
        ResilienceConfig config = ResilienceConfig.builder()
            .failureRateThreshold(70) // Higher threshold for DB
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .maxRetryAttempts(2)
            .retryWaitDuration(Duration.ofMillis(500))
            .maxConcurrentCalls(20) // Higher concurrency for DB
            .timeoutDuration(Duration.ofSeconds(10))
            .build();

        try {
            return executeWithResilience("database-" + operationName, operation, config).get();
        } catch (Exception e) {
            throw new DatabaseUnavailableException("Database operation failed: " + operationName, e);
        }
    }

    /**
     * Execute external API call with resilience patterns
     */
    public <T> T executeExternalApiCall(String apiName, Supplier<T> operation, T fallbackValue) {
        ResilienceConfig config = ResilienceConfig.builder()
            .failureRateThreshold(40) // Lower threshold for external APIs
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .maxRetryAttempts(3)
            .retryWaitDuration(Duration.ofSeconds(2))
            .maxConcurrentCalls(5) // Lower concurrency for external APIs
            .timeoutDuration(Duration.ofSeconds(5))
            .fallbackOperation(() -> fallbackValue)
            .build();

        try {
            return executeWithResilience("external-api-" + apiName, operation, config).get();
        } catch (Exception e) {
            log.warn("External API call failed, returning fallback value: {}", apiName);
            return fallbackValue;
        }
    }

    /**
     * Execute payment processing with specialized patterns
     */
    public <T> T executePaymentOperation(String operationName, Supplier<T> operation) {
        ResilienceConfig config = ResilienceConfig.builder()
            .failureRateThreshold(20) // Very low threshold for payments
            .waitDurationInOpenState(Duration.ofSeconds(120)) // Longer recovery time
            .maxRetryAttempts(1) // Minimal retry for payments
            .retryWaitDuration(Duration.ofSeconds(1))
            .maxConcurrentCalls(3) // Limited concurrency for payments
            .timeoutDuration(Duration.ofSeconds(30)) // Longer timeout for payments
            .build();

        try {
            return executeWithResilience("payment-" + operationName, operation, config).get();
        } catch (Exception e) {
            // Payments should never have fallbacks - fail fast
            throw new PaymentServiceUnavailableException("Payment operation failed: " + operationName, e);
        }
    }

    /**
     * Get circuit breaker health status
     */
    public CircuitBreakerHealthStatus getCircuitBreakerHealth(String serviceName) {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceName);
            
            return CircuitBreakerHealthStatus.builder()
                .serviceName(serviceName)
                .state(circuitBreaker.getState().name())
                .failureRate(circuitBreaker.getMetrics().getFailureRate())
                .numberOfSuccessfulCalls(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls())
                .numberOfFailedCalls(circuitBreaker.getMetrics().getNumberOfFailedCalls())
                .numberOfNotPermittedCalls(circuitBreaker.getMetrics().getNumberOfNotPermittedCalls())
                .lastFailureTime(getLastFailureTime(serviceName))
                .healthy(circuitBreaker.getState() == CircuitBreaker.State.CLOSED)
                .build();
                
        } catch (Exception e) {
            return CircuitBreakerHealthStatus.builder()
                .serviceName(serviceName)
                .state("UNKNOWN")
                .healthy(false)
                .error(e.getMessage())
                .build();
        }
    }

    /**
     * Get comprehensive resilience metrics
     */
    public ResilienceMetrics getResilienceMetrics(String serviceName) {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(serviceName);
            Retry retry = retryRegistry.retry(serviceName);
            Bulkhead bulkhead = bulkheadRegistry.bulkhead(serviceName);

            return ResilienceMetrics.builder()
                .serviceName(serviceName)
                .circuitBreakerState(cb.getState().name())
                .failureRate(cb.getMetrics().getFailureRate())
                .successfulCalls(cb.getMetrics().getNumberOfSuccessfulCalls())
                .failedCalls(cb.getMetrics().getNumberOfFailedCalls())
                .retryAttempts(retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt())
                .retryFailures(retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt())
                .availableCallsInBulkhead(bulkhead.getMetrics().getAvailableConcurrentCalls())
                .maxConcurrentCalls(bulkhead.getMetrics().getMaxAllowedConcurrentCalls())
                .timestamp(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Failed to get resilience metrics for service: {}", serviceName, e);
            return ResilienceMetrics.builder()
                .serviceName(serviceName)
                .error(e.getMessage())
                .timestamp(Instant.now())
                .build();
        }
    }

    /**
     * Manually open circuit breaker (for maintenance)
     */
    public void openCircuitBreaker(String serviceName, String reason) {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceName);
            circuitBreaker.transitionToOpenState();
            
            // Store reason in Redis
            String reasonKey = "circuit-breaker:manual-open:" + serviceName;
            redisTemplate.opsForValue().set(reasonKey, reason, Duration.ofHours(1));
            
            log.warn("Circuit breaker manually opened for service: {} - Reason: {}", serviceName, reason);
            
        } catch (Exception e) {
            log.error("Failed to manually open circuit breaker for service: {}", serviceName, e);
        }
    }

    /**
     * Manually close circuit breaker (after maintenance)
     */
    public void closeCircuitBreaker(String serviceName) {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceName);
            circuitBreaker.transitionToClosedState();
            
            // Remove manual open reason
            String reasonKey = "circuit-breaker:manual-open:" + serviceName;
            redisTemplate.delete(reasonKey);
            
            log.info("Circuit breaker manually closed for service: {}", serviceName);
            
        } catch (Exception e) {
            log.error("Failed to manually close circuit breaker for service: {}", serviceName, e);
        }
    }

    /**
     * Reset circuit breaker metrics
     */
    public void resetCircuitBreaker(String serviceName) {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceName);
            circuitBreaker.reset();
            
            log.info("Circuit breaker reset for service: {}", serviceName);
            
        } catch (Exception e) {
            log.error("Failed to reset circuit breaker for service: {}", serviceName, e);
        }
    }

    // Private helper methods

    private CircuitBreaker getOrCreateCircuitBreaker(String serviceName, ResilienceConfig config) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(serviceName, () ->
            CircuitBreakerConfig.custom()
                .failureRateThreshold(config.getFailureRateThreshold())
                .waitDurationInOpenState(config.getWaitDurationInOpenState())
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .build()
        );

        // Add event listeners
        cb.getEventPublisher()
            .onStateTransition(event ->
                log.info("Circuit breaker state transition: {} -> {} for service: {}",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState(),
                    serviceName))
            .onFailureRateExceeded(event ->
                log.warn("Circuit breaker failure rate exceeded: {}% for service: {}",
                    event.getFailureRate(), serviceName))
            .onCallNotPermitted(event ->
                log.debug("Circuit breaker call not permitted for service: {}", serviceName));

        return cb;
    }

    private Retry getOrCreateRetry(String serviceName, ResilienceConfig config) {
        Retry retry = retryRegistry.retry(serviceName, () ->
            RetryConfig.custom()
                .maxAttempts(config.getMaxRetryAttempts())
                .waitDuration(config.getRetryWaitDuration())
                .retryOnException(throwable -> !(throwable instanceof PaymentServiceUnavailableException))
                .build()
        );

        // Add event listeners
        retry.getEventPublisher()
            .onRetry(event ->
                log.debug("Retry attempt #{} for service: {} - {}",
                    event.getNumberOfRetryAttempts(), serviceName, event.getLastThrowable().getMessage()))
            .onSuccess(event -> {
                if (event.getNumberOfRetryAttempts() > 0) {
                    log.info("Retry succeeded after {} attempts for service: {}",
                        event.getNumberOfRetryAttempts(), serviceName);
                }
            });

        return retry;
    }

    private Bulkhead getOrCreateBulkhead(String serviceName, ResilienceConfig config) {
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(serviceName, () ->
            BulkheadConfig.custom()
                .maxConcurrentCalls(config.getMaxConcurrentCalls())
                .maxWaitDuration(Duration.ofMillis(500))
                .build()
        );

        // Add event listeners
        bulkhead.getEventPublisher()
            .onCallRejected(event ->
                log.warn("Bulkhead call rejected for service: {} - max concurrent calls reached", serviceName))
            .onCallPermitted(event ->
                log.debug("Bulkhead call permitted for service: {}", serviceName));

        return bulkhead;
    }

    private TimeLimiter getOrCreateTimeLimiter(String serviceName, ResilienceConfig config) {
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(serviceName, () ->
            TimeLimiterConfig.custom()
                .timeoutDuration(config.getTimeoutDuration())
                .cancelRunningFuture(true)
                .build()
        );

        // Add event listeners
        timeLimiter.getEventPublisher()
            .onTimeout(event ->
                log.warn("Timeout occurred for service: {} after {}ms",
                    serviceName, config.getTimeoutDuration().toMillis()));

        return timeLimiter;
    }

    private void recordSuccess(String serviceName) {
        try {
            String successKey = "resilience:success:" + serviceName;
            redisTemplate.opsForValue().increment(successKey);
            redisTemplate.expire(successKey, Duration.ofMinutes(5));
        } catch (Exception e) {
            log.debug("Failed to record success metric", e);
        }
    }

    private void recordFailure(String serviceName, Exception exception) {
        try {
            String failureKey = "resilience:failure:" + serviceName;
            redisTemplate.opsForValue().increment(failureKey);
            redisTemplate.expire(failureKey, Duration.ofMinutes(5));
            
            // Store last failure details
            String lastFailureKey = "resilience:last-failure:" + serviceName;
            FailureDetails details = FailureDetails.builder()
                .timestamp(Instant.now())
                .exceptionType(exception.getClass().getSimpleName())
                .message(exception.getMessage())
                .build();
            
            redisTemplate.opsForValue().set(lastFailureKey, details, Duration.ofHours(1));
            
        } catch (Exception e) {
            log.debug("Failed to record failure metric", e);
        }
    }

    private Instant getLastFailureTime(String serviceName) {
        try {
            String lastFailureKey = "resilience:last-failure:" + serviceName;
            FailureDetails details = (FailureDetails) redisTemplate.opsForValue().get(lastFailureKey);
            return details != null ? details.getTimestamp() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        log.info("Shutting down resilience service");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Data classes

    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ResilienceConfig {
        private int failureRateThreshold;
        private Duration waitDurationInOpenState;
        private int maxRetryAttempts;
        private Duration retryWaitDuration;
        private int maxConcurrentCalls;
        private Duration timeoutDuration;
        private Supplier<?> fallbackOperation;
    }

    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CircuitBreakerHealthStatus {
        private String serviceName;
        private String state;
        private float failureRate;
        private int numberOfSuccessfulCalls;
        private int numberOfFailedCalls;
        private long numberOfNotPermittedCalls;
        private Instant lastFailureTime;
        private boolean healthy;
        private String error;
    }

    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ResilienceMetrics {
        private String serviceName;
        private String circuitBreakerState;
        private float failureRate;
        private int successfulCalls;
        private int failedCalls;
        private long retryAttempts;
        private long retryFailures;
        private int availableCallsInBulkhead;
        private int maxConcurrentCalls;
        private Instant timestamp;
        private String error;
    }

    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FailureDetails {
        private Instant timestamp;
        private String exceptionType;
        private String message;
    }

    // Exception classes
    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class DatabaseUnavailableException extends RuntimeException {
        public DatabaseUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class PaymentServiceUnavailableException extends RuntimeException {
        public PaymentServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}