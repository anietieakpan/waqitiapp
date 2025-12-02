package com.waqiti.common.service;

import com.waqiti.common.exception.ServiceException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Circuit Breaker Service for fault tolerance and resilience
 * 
 * Provides circuit breaking, retries, timeouts, and fallback mechanisms
 * for external service calls and critical operations.
 * 
 * Features:
 * - Circuit breaker pattern implementation
 * - Automatic retry with exponential backoff
 * - Timeout protection
 * - Fallback mechanisms
 * - Metrics and monitoring
 * - Bulkhead isolation
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Slf4j
@Service
public class CircuitBreakerService {
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    private final Map<String, CircuitBreakerStats> statsMap;
    
    @Value("${circuit-breaker.failure-rate-threshold:50}")
    private float failureRateThreshold;
    
    @Value("${circuit-breaker.wait-duration-in-open-state:60}")
    private long waitDurationInOpenState;
    
    @Value("${circuit-breaker.sliding-window-size:100}")
    private int slidingWindowSize;
    
    @Value("${circuit-breaker.minimum-number-of-calls:10}")
    private int minimumNumberOfCalls;
    
    @Value("${circuit-breaker.slow-call-duration-threshold:3}")
    private long slowCallDurationThreshold;
    
    @Value("${circuit-breaker.slow-call-rate-threshold:50}")
    private float slowCallRateThreshold;
    
    @Value("${retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${retry.wait-duration:1}")
    private long retryWaitDuration;
    
    @Value("${retry.exponential-backoff-multiplier:2}")
    private double exponentialBackoffMultiplier;
    
    @Value("${timeout.duration:30}")
    private long timeoutDuration;
    
    public CircuitBreakerService() {
        this.circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        this.retryRegistry = RetryRegistry.ofDefaults();
        this.executorService = Executors.newFixedThreadPool(20);
        this.scheduledExecutor = Executors.newScheduledThreadPool(5);
        this.statsMap = new ConcurrentHashMap<>();
    }
    
    @PostConstruct
    public void init() {
        // Schedule periodic stats logging
        scheduledExecutor.scheduleAtFixedRate(this::logCircuitBreakerStats, 
            0, 60, TimeUnit.SECONDS);
        
        // Schedule health checks
        scheduledExecutor.scheduleAtFixedRate(this::performHealthChecks, 
            30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Execute operation with circuit breaker and fallback
     */
    public <T> T executeWithFallback(String serviceName, 
                                     Supplier<T> operation, 
                                     Supplier<T> fallback) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serviceName);
        Retry retry = getOrCreateRetry(serviceName);
        
        // Decorate the operation with circuit breaker and retry
        Supplier<T> decoratedOperation = CircuitBreaker.decorateSupplier(circuitBreaker, operation);
        decoratedOperation = Retry.decorateSupplier(retry, decoratedOperation);
        
        // Execute with fallback
        return Try.ofSupplier(decoratedOperation)
            .recover(throwable -> {
                log.warn("Operation failed for service: {}, falling back. Error: {}", 
                    serviceName, throwable.getMessage());
                recordFailure(serviceName, throwable);
                return fallback.get();
            })
            .get();
    }
    
    /**
     * Execute async operation with circuit breaker
     */
    public <T> CompletableFuture<T> executeAsync(String serviceName, 
                                                 Supplier<T> operation) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serviceName);
        Retry retry = getOrCreateRetry(serviceName);
        
        return CompletableFuture.supplyAsync(() -> {
            Supplier<T> decoratedOperation = CircuitBreaker.decorateSupplier(circuitBreaker, operation);
            decoratedOperation = Retry.decorateSupplier(retry, decoratedOperation);
            
            return Try.ofSupplier(decoratedOperation)
                .recover(throwable -> {
                    log.error("Async operation failed for service: {}", serviceName, throwable);
                    recordFailure(serviceName, throwable);
                    throw new ServiceException("Service unavailable: " + serviceName, throwable);
                })
                .get();
        }, executorService);
    }
    
    /**
     * Execute operation with timeout
     */
    public <T> T executeWithTimeout(String serviceName, 
                                    Callable<T> operation, 
                                    long timeoutSeconds) {
        TimeLimiter timeLimiter = TimeLimiter.of(
            TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(timeoutSeconds))
                .build()
        );
        
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serviceName);
        
        Supplier<CompletableFuture<T>> futureSupplier = () -> 
            CompletableFuture.supplyAsync(() -> {
                try {
                    return operation.call();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, executorService);
        
        Callable<T> restrictedCall = TimeLimiter.decorateFutureSupplier(timeLimiter, futureSupplier);
        Callable<T> decoratedCall = CircuitBreaker.decorateCallable(circuitBreaker, restrictedCall);
        
        try {
            return decoratedCall.call();
        } catch (Exception e) {
            log.error("Operation with timeout failed for service: {}", serviceName, e);
            recordFailure(serviceName, e);
            throw new ServiceException("Operation timed out: " + serviceName, e);
        }
    }
    
    /**
     * Execute bulk operations with bulkhead isolation
     */
    public <T> List<CompletableFuture<T>> executeBulk(String serviceName,
                                                      List<Supplier<T>> operations,
                                                      int maxConcurrency) {
        Semaphore bulkhead = new Semaphore(maxConcurrency);
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serviceName);
        
        return operations.stream()
            .map(operation -> CompletableFuture.supplyAsync(() -> {
                try {
                    bulkhead.acquire();
                    try {
                        Supplier<T> decoratedOperation = 
                            CircuitBreaker.decorateSupplier(circuitBreaker, operation);
                        return decoratedOperation.get();
                    } finally {
                        bulkhead.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ServiceException("Bulk operation interrupted", e);
                }
            }, executorService))
            .toList();
    }
    
    /**
     * Get or create circuit breaker for service
     */
    private CircuitBreaker getOrCreateCircuitBreaker(String serviceName) {
        // Create config supplier
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(Duration.ofSeconds(waitDurationInOpenState))
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .slowCallDurationThreshold(Duration.ofSeconds(slowCallDurationThreshold))
                .slowCallRateThreshold(slowCallRateThreshold)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(ServiceException.class, TimeoutException.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();

        // Get or create circuit breaker from registry
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(serviceName, config);

        // Register event listeners (idempotent - safe to call multiple times)
        breaker.getEventPublisher()
            .onStateTransition(event ->
                log.warn("Circuit breaker state transition for {}: {} -> {}",
                    serviceName, event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()))
            .onSlowCallRateExceeded(event ->
                log.warn("Slow call rate exceeded for {}: {}%",
                    serviceName, event.getSlowCallRate()))
            .onFailureRateExceeded(event ->
                log.warn("Failure rate exceeded for {}: {}%",
                    serviceName, event.getFailureRate()));

        return breaker;
    }
    
    /**
     * Get or create retry mechanism for service
     */
    private Retry getOrCreateRetry(String serviceName) {
        // Create retry config
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(maxRetryAttempts)
            .waitDuration(Duration.ofSeconds(retryWaitDuration))
            .intervalBiFunction((attempts, outcome) -> {
                // Exponential backoff
                long waitTime = (long) (retryWaitDuration * 1000 *
                    Math.pow(exponentialBackoffMultiplier, attempts - 1));
                return waitTime;
            })
            .retryOnException(throwable ->
                throwable instanceof ServiceException ||
                throwable instanceof TimeoutException)
            .retryOnResult(result -> result == null)
            .build();

        // Get or create retry from registry
        Retry retry = retryRegistry.retry(serviceName, config);

        // Register event listeners (idempotent - safe to call multiple times)
        retry.getEventPublisher()
            .onRetry(event ->
                log.debug("Retry attempt {} for service: {}",
                    event.getNumberOfRetryAttempts(), serviceName))
            .onError(event ->
                log.error("Retry exhausted for service: {} after {} attempts",
                    serviceName, event.getNumberOfRetryAttempts()));

        return retry;
    }
    
    /**
     * Check circuit breaker state
     */
    public CircuitBreakerState getCircuitBreakerState(String serviceName) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.find(serviceName)
            .orElse(null);
        
        if (circuitBreaker == null) {
            return CircuitBreakerState.UNKNOWN;
        }
        
        return switch (circuitBreaker.getState()) {
            case CLOSED -> CircuitBreakerState.CLOSED;
            case OPEN -> CircuitBreakerState.OPEN;
            case HALF_OPEN -> CircuitBreakerState.HALF_OPEN;
            case DISABLED -> CircuitBreakerState.DISABLED;
            case FORCED_OPEN -> CircuitBreakerState.FORCED_OPEN;
            default -> CircuitBreakerState.UNKNOWN;
        };
    }
    
    /**
     * Force open circuit breaker
     */
    public void forceOpenCircuitBreaker(String serviceName) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serviceName);
        circuitBreaker.transitionToForcedOpenState();
        log.warn("Circuit breaker forced open for service: {}", serviceName);
    }
    
    /**
     * Reset circuit breaker
     */
    public void resetCircuitBreaker(String serviceName) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serviceName);
        circuitBreaker.reset();
        log.info("Circuit breaker reset for service: {}", serviceName);
    }
    
    /**
     * Get circuit breaker metrics
     */
    public CircuitBreakerMetrics getMetrics(String serviceName) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.find(serviceName)
            .orElse(null);
        
        if (circuitBreaker == null) {
            return CircuitBreakerMetrics.empty();
        }
        
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        
        return CircuitBreakerMetrics.builder()
            .serviceName(serviceName)
            .state(getCircuitBreakerState(serviceName))
            .failureRate(metrics.getFailureRate())
            .slowCallRate(metrics.getSlowCallRate())
            .numberOfBufferedCalls(metrics.getNumberOfBufferedCalls())
            .numberOfFailedCalls(metrics.getNumberOfFailedCalls())
            .numberOfSuccessfulCalls(metrics.getNumberOfSuccessfulCalls())
            .numberOfSlowCalls(metrics.getNumberOfSlowCalls())
            .build();
    }
    
    /**
     * Record failure for metrics
     */
    private void recordFailure(String serviceName, Throwable throwable) {
        statsMap.computeIfAbsent(serviceName, k -> new CircuitBreakerStats())
            .recordFailure(throwable);
    }
    
    /**
     * Log circuit breaker statistics
     */
    private void logCircuitBreakerStats() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(circuitBreaker -> {
            CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
            log.info("Circuit Breaker Stats - Service: {}, State: {}, Failure Rate: {}%, " +
                    "Slow Call Rate: {}%, Buffered Calls: {}", 
                circuitBreaker.getName(),
                circuitBreaker.getState(),
                metrics.getFailureRate(),
                metrics.getSlowCallRate(),
                metrics.getNumberOfBufferedCalls());
        });
    }
    
    /**
     * Perform health checks on circuit breakers
     */
    private void performHealthChecks() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(circuitBreaker -> {
            if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                log.warn("Health Check - Circuit breaker OPEN for service: {}", 
                    circuitBreaker.getName());
                
                // Check if it should transition to half-open
                CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
                if (metrics.getNumberOfBufferedCalls() > 0 && 
                    metrics.getFailureRate() < failureRateThreshold * 0.8) {
                    log.info("Attempting to transition {} to HALF_OPEN", circuitBreaker.getName());
                    circuitBreaker.transitionToHalfOpenState();
                }
            }
        });
    }
    
    /**
     * Shutdown executor services
     */
    public void shutdown() {
        executorService.shutdown();
        scheduledExecutor.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

// Supporting classes

enum CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN,
    DISABLED,
    FORCED_OPEN,
    UNKNOWN
}

@lombok.Data
@lombok.Builder
class CircuitBreakerMetrics {
    private String serviceName;
    private CircuitBreakerState state;
    private float failureRate;
    private float slowCallRate;
    private int numberOfBufferedCalls;
    private int numberOfFailedCalls;
    private int numberOfSuccessfulCalls;
    private int numberOfSlowCalls;
    
    public static CircuitBreakerMetrics empty() {
        return CircuitBreakerMetrics.builder()
            .state(CircuitBreakerState.UNKNOWN)
            .failureRate(0)
            .slowCallRate(0)
            .numberOfBufferedCalls(0)
            .numberOfFailedCalls(0)
            .numberOfSuccessfulCalls(0)
            .numberOfSlowCalls(0)
            .build();
    }
}

class CircuitBreakerStats {
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong failedCalls = new AtomicLong(0);
    private final AtomicLong successfulCalls = new AtomicLong(0);
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    
    public void recordFailure(Throwable throwable) {
        totalCalls.incrementAndGet();
        failedCalls.incrementAndGet();
        errorCounts.computeIfAbsent(throwable.getClass().getSimpleName(), 
            k -> new AtomicLong(0)).incrementAndGet();
    }
    
    public void recordSuccess() {
        totalCalls.incrementAndGet();
        successfulCalls.incrementAndGet();
    }
    
    public double getFailureRate() {
        long total = totalCalls.get();
        return total > 0 ? (double) failedCalls.get() / total * 100 : 0;
    }
}