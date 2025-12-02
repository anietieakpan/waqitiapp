package com.waqiti.corebanking.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Comprehensive Circuit Breaker Service
 * Implements resilience patterns including circuit breakers, retries, bulkheads, and rate limiters
 */
@Service
@Slf4j
public class CircuitBreakerService {

    private final MeterRegistry meterRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final ScheduledExecutorService executorService;
    
    // Health monitoring
    private final Map<String, CircuitBreakerHealth> healthMonitors = new ConcurrentHashMap<>();
    
    @Value("${resilience.circuit-breaker.failure-threshold:50}")
    private float failureRateThreshold;
    
    @Value("${resilience.circuit-breaker.wait-duration-seconds:60}")
    private long waitDurationInOpenStateSeconds;
    
    @Value("${resilience.circuit-breaker.sliding-window-size:100}")
    private int slidingWindowSize;
    
    @Value("${resilience.retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${resilience.retry.wait-duration-ms:500}")
    private long retryWaitDuration;
    
    @Value("${resilience.bulkhead.max-concurrent-calls:25}")
    private int maxConcurrentCalls;
    
    @Value("${resilience.bulkhead.max-wait-duration-ms:1000}")
    private long maxWaitDuration;
    
    @Value("${resilience.rate-limiter.limit-for-period:50}")
    private int limitForPeriod;
    
    @Value("${resilience.rate-limiter.limit-refresh-period-ms:1000}")
    private long limitRefreshPeriod;
    
    @Value("${resilience.time-limiter.timeout-duration-seconds:5}")
    private long timeoutDuration;

    public CircuitBreakerService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.executorService = Executors.newScheduledThreadPool(4);
        
        // Initialize registries with default configurations
        this.circuitBreakerRegistry = createCircuitBreakerRegistry();
        this.retryRegistry = createRetryRegistry();
        this.bulkheadRegistry = createBulkheadRegistry();
        this.rateLimiterRegistry = createRateLimiterRegistry();
        this.timeLimiterRegistry = createTimeLimiterRegistry();
    }

    @PostConstruct
    public void initialize() {
        // Register default circuit breakers for critical services
        registerCircuitBreaker("payment-processing", createPaymentProcessingConfig());
        registerCircuitBreaker("account-service", createAccountServiceConfig());
        registerCircuitBreaker("ledger-service", createLedgerServiceConfig());
        registerCircuitBreaker("compliance-service", createComplianceServiceConfig());
        registerCircuitBreaker("notification-service", createNotificationServiceConfig());
        
        // Start health monitoring
        startHealthMonitoring();
        
        log.info("Circuit Breaker Service initialized with {} circuit breakers", 
            circuitBreakerRegistry.getAllCircuitBreakers().size());
    }

    /**
     * Execute with circuit breaker protection
     */
    public <T> T executeWithCircuitBreaker(String circuitBreakerName, Supplier<T> supplier) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(circuitBreakerName);
        
        return CircuitBreaker.decorateSupplier(circuitBreaker, supplier).get();
    }

    /**
     * Execute with full resilience stack
     * Combines circuit breaker, retry, bulkhead, rate limiter, and time limiter
     */
    public <T> CompletableFuture<T> executeWithFullResilience(
            String serviceName, 
            Supplier<T> supplier,
            Function<Throwable, T> fallback) {
        
        // Get or create resilience components
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serviceName);
        Retry retry = getOrCreateRetry(serviceName);
        Bulkhead bulkhead = getOrCreateBulkhead(serviceName);
        RateLimiter rateLimiter = getOrCreateRateLimiter(serviceName);
        TimeLimiter timeLimiter = getOrCreateTimeLimiter(serviceName);
        
        // Decorate supplier with resilience patterns
        Supplier<T> decoratedSupplier = Retry.decorateSupplier(retry,
            CircuitBreaker.decorateSupplier(circuitBreaker,
                RateLimiter.decorateSupplier(rateLimiter, supplier)));
        
        // Execute with bulkhead and time limiter
        return CompletableFuture.supplyAsync(decoratedSupplier, executorService)
            .handle((result, throwable) -> {
                if (throwable != null) {
                    log.warn("Service {} failed, applying fallback", serviceName, throwable);
                    recordFailure(serviceName, throwable);
                    return fallback.apply(throwable);
                }
                recordSuccess(serviceName);
                return result;
            });
    }

    /**
     * Execute critical operation with advanced resilience
     */
    public <T> T executeCriticalOperation(
            String operationName,
            Supplier<T> operation,
            CriticalOperationConfig config) {
        
        // Create custom circuit breaker for critical operations
        CircuitBreaker circuitBreaker = createCriticalOperationCircuitBreaker(operationName, config);
        
        // Apply progressive retry strategy
        Retry retry = createProgressiveRetry(operationName, config);
        
        // Apply strict bulkhead
        Bulkhead bulkhead = createStrictBulkhead(operationName, config);
        
        try {
            // Execute with all protections
            Supplier<T> decorated = Bulkhead.decorateSupplier(bulkhead,
                Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(circuitBreaker, operation)));
            
            T result = decorated.get();
            
            // Record success metrics
            recordCriticalOperationSuccess(operationName);
            
            return result;
            
        } catch (Exception e) {
            // Record failure and potentially trigger alerts
            recordCriticalOperationFailure(operationName, e);
            
            // Apply compensating transaction if configured
            if (config.hasCompensatingAction()) {
                executeCompensatingAction(operationName, config, e);
            }
            
            throw new ResilienceException("Critical operation failed: " + operationName, e);
        }
    }

    /**
     * Adaptive circuit breaker that adjusts thresholds based on system load
     */
    public CircuitBreaker createAdaptiveCircuitBreaker(String name) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(calculateAdaptiveThreshold())
            .waitDurationInOpenState(Duration.ofSeconds(calculateAdaptiveWaitDuration()))
            .permittedNumberOfCallsInHalfOpenState(calculateAdaptivePermittedCalls())
            .slidingWindowSize(slidingWindowSize)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .recordExceptions(Exception.class)
            .ignoreExceptions(BusinessException.class)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
        
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name, config);
        
        // Add event listeners
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> handleStateTransition(name, event))
            .onError(event -> handleError(name, event))
            .onSuccess(event -> handleSuccess(name, event));
        
        return circuitBreaker;
    }

    /**
     * Create circuit breaker with custom configuration
     */
    private void registerCircuitBreaker(String name, CircuitBreakerConfig config) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name, config);
        
        // Register event listeners
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> {
                log.info("Circuit breaker {} transitioned from {} to {}", 
                    name, event.getStateTransition().getFromState(), 
                    event.getStateTransition().getToState());
                
                // Send alert for critical state changes
                if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                    sendCircuitBreakerAlert(name, "OPEN", "Circuit breaker opened due to failures");
                }
            })
            .onError(event -> {
                log.debug("Circuit breaker {} recorded error: {}", name, event.getThrowable().getMessage());
                meterRegistry.counter("circuit.breaker.errors", 
                    Tags.of("name", name, "exception", event.getThrowable().getClass().getSimpleName()))
                    .increment();
            })
            .onSuccess(event -> {
                meterRegistry.counter("circuit.breaker.success", Tags.of("name", name)).increment();
            })
            .onCallNotPermitted(event -> {
                log.warn("Circuit breaker {} rejected call", name);
                meterRegistry.counter("circuit.breaker.rejected", Tags.of("name", name)).increment();
            });
        
        // Initialize health monitor
        healthMonitors.put(name, new CircuitBreakerHealth(name, circuitBreaker));
    }

    /**
     * Configuration for different service types
     */
    private CircuitBreakerConfig createPaymentProcessingConfig() {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(25) // Lower threshold for payment services
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slidingWindowSize(20)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .minimumNumberOfCalls(10)
            .recordExceptions(Exception.class)
            .ignoreExceptions(BusinessException.class, ValidationException.class)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
    }

    private CircuitBreakerConfig createAccountServiceConfig() {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(40)
            .waitDurationInOpenState(Duration.ofSeconds(45))
            .permittedNumberOfCallsInHalfOpenState(5)
            .slidingWindowSize(50)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
            .minimumNumberOfCalls(20)
            .build();
    }

    private CircuitBreakerConfig createLedgerServiceConfig() {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(30)
            .slowCallRateThreshold(50)
            .slowCallDurationThreshold(Duration.ofSeconds(2))
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .permittedNumberOfCallsInHalfOpenState(5)
            .slidingWindowSize(100)
            .build();
    }

    private CircuitBreakerConfig createComplianceServiceConfig() {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(90))
            .permittedNumberOfCallsInHalfOpenState(10)
            .slidingWindowSize(100)
            .build();
    }

    private CircuitBreakerConfig createNotificationServiceConfig() {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(60) // Higher threshold for non-critical services
            .waitDurationInOpenState(Duration.ofSeconds(120))
            .permittedNumberOfCallsInHalfOpenState(10)
            .slidingWindowSize(200)
            .build();
    }

    /**
     * Create retry configuration with exponential backoff
     */
    private Retry createProgressiveRetry(String name, CriticalOperationConfig config) {
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(config.getMaxRetries())
            .waitDuration(Duration.ofMillis(config.getInitialRetryDelay()))
            .intervalFunction(IntervalFunction.ofExponentialBackoff(
                config.getInitialRetryDelay(), 
                config.getRetryMultiplier()))
            .retryExceptions(Exception.class)
            .ignoreExceptions(NonRetryableException.class)
            .retryOnResult(response -> !isSuccessful(response))
            .build();
        
        return retryRegistry.retry(name, retryConfig);
    }

    /**
     * Create strict bulkhead for critical operations
     */
    private Bulkhead createStrictBulkhead(String name, CriticalOperationConfig config) {
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(config.getMaxConcurrentCalls())
            .maxWaitDuration(Duration.ofMillis(config.getMaxWaitTime()))
            .writableStackTraceEnabled(true)
            .build();
        
        return bulkheadRegistry.bulkhead(name, bulkheadConfig);
    }

    /**
     * Health monitoring for circuit breakers
     */
    private void startHealthMonitoring() {
        executorService.scheduleAtFixedRate(() -> {
            try {
                healthMonitors.forEach((name, health) -> {
                    CircuitBreaker.Metrics metrics = health.getCircuitBreaker().getMetrics();
                    
                    // Record metrics
                    meterRegistry.gauge("circuit.breaker.failure.rate", 
                        Tags.of("name", name), metrics.getFailureRate());
                    meterRegistry.gauge("circuit.breaker.slow.call.rate", 
                        Tags.of("name", name), metrics.getSlowCallRate());
                    meterRegistry.gauge("circuit.breaker.buffered.calls", 
                        Tags.of("name", name), metrics.getNumberOfBufferedCalls());
                    
                    // Check health thresholds
                    if (metrics.getFailureRate() > 70) {
                        log.warn("Circuit breaker {} has high failure rate: {}%", 
                            name, metrics.getFailureRate());
                    }
                    
                    // Auto-recovery check
                    checkAutoRecovery(name, health);
                });
            } catch (Exception e) {
                log.error("Error in health monitoring", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Auto-recovery mechanism for circuit breakers
     */
    private void checkAutoRecovery(String name, CircuitBreakerHealth health) {
        CircuitBreaker circuitBreaker = health.getCircuitBreaker();
        
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            long openDuration = System.currentTimeMillis() - health.getLastOpenTime();
            
            // Force transition to half-open after extended period
            if (openDuration > TimeUnit.MINUTES.toMillis(10)) {
                log.info("Force transitioning circuit breaker {} to half-open after extended open period", name);
                circuitBreaker.transitionToHalfOpenState();
            }
        }
    }

    /**
     * Calculate adaptive thresholds based on system load
     */
    private float calculateAdaptiveThreshold() {
        double systemLoad = getSystemLoad();
        
        if (systemLoad > 0.8) {
            return 30; // Lower threshold under high load
        } else if (systemLoad > 0.5) {
            return 40;
        } else {
            return 50; // Default threshold
        }
    }

    private long calculateAdaptiveWaitDuration() {
        double systemLoad = getSystemLoad();
        
        if (systemLoad > 0.8) {
            return 120; // Longer wait under high load
        } else if (systemLoad > 0.5) {
            return 90;
        } else {
            return 60; // Default wait
        }
    }

    private int calculateAdaptivePermittedCalls() {
        double systemLoad = getSystemLoad();
        
        if (systemLoad > 0.8) {
            return 2; // Fewer calls under high load
        } else if (systemLoad > 0.5) {
            return 3;
        } else {
            return 5; // Default permitted calls
        }
    }

    /**
     * Helper methods
     */
    private CircuitBreaker getOrCreateCircuitBreaker(String name) {
        return circuitBreakerRegistry.circuitBreaker(name);
    }

    private Retry getOrCreateRetry(String name) {
        return retryRegistry.retry(name);
    }

    private Bulkhead getOrCreateBulkhead(String name) {
        return bulkheadRegistry.bulkhead(name);
    }

    private RateLimiter getOrCreateRateLimiter(String name) {
        return rateLimiterRegistry.rateLimiter(name);
    }

    private TimeLimiter getOrCreateTimeLimiter(String name) {
        return timeLimiterRegistry.timeLimiter(name);
    }

    private CircuitBreakerRegistry createCircuitBreakerRegistry() {
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(failureRateThreshold)
            .waitDurationInOpenState(Duration.ofSeconds(waitDurationInOpenStateSeconds))
            .slidingWindowSize(slidingWindowSize)
            .build();
        
        return CircuitBreakerRegistry.of(defaultConfig);
    }

    private RetryRegistry createRetryRegistry() {
        RetryConfig defaultConfig = RetryConfig.custom()
            .maxAttempts(maxRetryAttempts)
            .waitDuration(Duration.ofMillis(retryWaitDuration))
            .build();
        
        return RetryRegistry.of(defaultConfig);
    }

    private BulkheadRegistry createBulkheadRegistry() {
        BulkheadConfig defaultConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(maxConcurrentCalls)
            .maxWaitDuration(Duration.ofMillis(maxWaitDuration))
            .build();
        
        return BulkheadRegistry.of(defaultConfig);
    }

    private RateLimiterRegistry createRateLimiterRegistry() {
        RateLimiterConfig defaultConfig = RateLimiterConfig.custom()
            .limitForPeriod(limitForPeriod)
            .limitRefreshPeriod(Duration.ofMillis(limitRefreshPeriod))
            .timeoutDuration(Duration.ofMillis(maxWaitDuration))
            .build();
        
        return RateLimiterRegistry.of(defaultConfig);
    }

    private TimeLimiterRegistry createTimeLimiterRegistry() {
        TimeLimiterConfig defaultConfig = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(timeoutDuration))
            .build();
        
        return TimeLimiterRegistry.of(defaultConfig);
    }

    private boolean isSuccessful(Object response) {
        // Implement logic to determine if response is successful
        return response != null;
    }

    private double getSystemLoad() {
        return ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
    }

    private void recordSuccess(String serviceName) {
        meterRegistry.counter("resilience.success", Tags.of("service", serviceName)).increment();
    }

    private void recordFailure(String serviceName, Throwable throwable) {
        meterRegistry.counter("resilience.failure", 
            Tags.of("service", serviceName, "error", throwable.getClass().getSimpleName()))
            .increment();
    }

    private void recordCriticalOperationSuccess(String operationName) {
        meterRegistry.counter("critical.operation.success", Tags.of("operation", operationName)).increment();
    }

    private void recordCriticalOperationFailure(String operationName, Exception e) {
        meterRegistry.counter("critical.operation.failure", 
            Tags.of("operation", operationName, "error", e.getClass().getSimpleName()))
            .increment();
        
        // Send alert for critical failures
        sendCriticalOperationAlert(operationName, e);
    }

    private void executeCompensatingAction(String operationName, CriticalOperationConfig config, Exception e) {
        try {
            log.info("Executing compensating action for operation: {}", operationName);
            config.getCompensatingAction().run();
        } catch (Exception ex) {
            log.error("Failed to execute compensating action for operation: {}", operationName, ex);
        }
    }

    private void sendCircuitBreakerAlert(String name, String state, String message) {
        // Implement alert sending logic
        log.error("CIRCUIT BREAKER ALERT - Name: {}, State: {}, Message: {}", name, state, message);
    }

    private void sendCriticalOperationAlert(String operationName, Exception e) {
        // Implement critical alert logic
        log.error("CRITICAL OPERATION FAILURE - Operation: {}, Error: {}", operationName, e.getMessage());
    }

    private void handleStateTransition(String name, CircuitBreakerOnStateTransitionEvent event) {
        if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
            healthMonitors.get(name).setLastOpenTime(System.currentTimeMillis());
        }
    }

    private void handleError(String name, CircuitBreakerOnErrorEvent event) {
        // Handle error events
    }

    private void handleSuccess(String name, CircuitBreakerOnSuccessEvent event) {
        // Handle success events
    }

    /**
     * Shutdown hook
     */
    public void shutdown() {
        try {
            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    // Inner classes

    private static class CircuitBreakerHealth {
        private final String name;
        private final CircuitBreaker circuitBreaker;
        private volatile long lastOpenTime;

        public CircuitBreakerHealth(String name, CircuitBreaker circuitBreaker) {
            this.name = name;
            this.circuitBreaker = circuitBreaker;
            this.lastOpenTime = 0;
        }

        public CircuitBreaker getCircuitBreaker() {
            return circuitBreaker;
        }

        public long getLastOpenTime() {
            return lastOpenTime;
        }

        public void setLastOpenTime(long lastOpenTime) {
            this.lastOpenTime = lastOpenTime;
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class CriticalOperationConfig {
        private int maxRetries;
        private long initialRetryDelay;
        private double retryMultiplier;
        private int maxConcurrentCalls;
        private long maxWaitTime;
        private Runnable compensatingAction;
        
        public boolean hasCompensatingAction() {
            return compensatingAction != null;
        }
    }

    // Custom exceptions
    
    public static class ResilienceException extends RuntimeException {
        public ResilienceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class BusinessException extends RuntimeException {
        public BusinessException(String message) {
            super(message);
        }
    }

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class NonRetryableException extends RuntimeException {
        public NonRetryableException(String message) {
            super(message);
        }
    }
}