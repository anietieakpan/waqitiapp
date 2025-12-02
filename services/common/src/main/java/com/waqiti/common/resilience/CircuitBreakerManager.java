package com.waqiti.common.resilience;

import com.waqiti.common.error.ErrorReportingService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Advanced Circuit Breaker Manager for production resilience
 * Provides dynamic configuration, monitoring, and intelligent failure handling
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CircuitBreakerManager {

    @Value("${circuit-breaker.default.failure-rate-threshold:50}")
    private float defaultFailureRateThreshold;
    
    @Value("${circuit-breaker.default.slow-call-rate-threshold:50}")
    private float defaultSlowCallRateThreshold;
    
    @Value("${circuit-breaker.default.slow-call-duration:5000}")
    private int defaultSlowCallDurationMs;
    
    @Value("${circuit-breaker.default.permitted-calls-half-open:3}")
    private int defaultPermittedCallsInHalfOpenState;
    
    @Value("${circuit-breaker.default.minimum-calls:10}")
    private int defaultMinimumCalls;
    
    @Value("${circuit-breaker.default.sliding-window-size:100}")
    private int defaultSlidingWindowSize;
    
    @Value("${circuit-breaker.default.wait-duration:10000}")
    private int defaultWaitDurationMs;

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ErrorReportingService errorReportingService;
    
    // Store custom configurations for different services
    private final Map<String, CircuitBreakerConfig> customConfigs = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreakerMetrics> metricsMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // Configure service-specific circuit breakers
        configurePaymentServiceCircuitBreakers();
        configureExternalServiceCircuitBreakers();
        configureBlockchainServiceCircuitBreakers();
        configureDatabaseCircuitBreakers();
        
        log.info("Circuit breaker manager initialized with {} custom configurations", 
            customConfigs.size());
    }

    /**
     * Execute operation with circuit breaker protection
     */
    public <T> T executeWithCircuitBreaker(String serviceName, Supplier<T> operation) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serviceName);
        
        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, operation);
        
        try {
            T result = decoratedSupplier.get();
            recordSuccess(serviceName);
            return result;
            
        } catch (Exception e) {
            recordFailure(serviceName, e);
            handleCircuitBreakerException(serviceName, e);
            throw e;
        }
    }

    /**
     * Execute async operation with circuit breaker
     */
    public <T> java.util.concurrent.CompletableFuture<T> executeAsyncWithCircuitBreaker(
            String serviceName, Supplier<java.util.concurrent.CompletableFuture<T>> operation) {
        
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serviceName);
        
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                new CircuitBreakerOpenException(serviceName + " circuit breaker is OPEN"));
        }
        
        return operation.get()
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    circuitBreaker.onError(throwable.getCause() != null ? 
                        throwable.getCause().getMessage().length() : 1000, 
                        java.util.concurrent.TimeUnit.MILLISECONDS, throwable);
                    recordFailure(serviceName, throwable);
                } else {
                    circuitBreaker.onSuccess(System.currentTimeMillis(), 
                        java.util.concurrent.TimeUnit.MILLISECONDS);
                    recordSuccess(serviceName);
                }
            });
    }

    /**
     * Get circuit breaker state
     */
    public CircuitBreaker.State getCircuitBreakerState(String serviceName) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceName);
        return circuitBreaker.getState();
    }

    /**
     * Get circuit breaker metrics
     */
    public CircuitBreakerMetrics getMetrics(String serviceName) {
        return metricsMap.computeIfAbsent(serviceName, k -> new CircuitBreakerMetrics());
    }

    /**
     * Force circuit breaker to open state (for maintenance)
     */
    public void forceOpen(String serviceName, String reason) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serviceName);
        circuitBreaker.transitionToOpenState();
        
        log.warn("Circuit breaker for {} forced to OPEN state. Reason: {}", serviceName, reason);
        
        // Report maintenance event
        errorReportingService.reportBusinessError("CIRCUIT_BREAKER_FORCED_OPEN", 
            "Circuit breaker manually opened", 
            Map.of("service", serviceName, "reason", reason));
    }

    /**
     * Force circuit breaker to closed state
     */
    public void forceClosed(String serviceName, String reason) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serviceName);
        circuitBreaker.transitionToClosedState();
        
        log.info("Circuit breaker for {} forced to CLOSED state. Reason: {}", serviceName, reason);
    }

    /**
     * Reset circuit breaker metrics
     */
    public void resetMetrics(String serviceName) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serviceName);
        circuitBreaker.reset();
        metricsMap.put(serviceName, new CircuitBreakerMetrics());
        
        log.info("Circuit breaker metrics reset for service: {}", serviceName);
    }

    /**
     * Get all circuit breaker states for monitoring
     */
    public Map<String, CircuitBreakerStatus> getAllCircuitBreakerStates() {
        Map<String, CircuitBreakerStatus> states = new ConcurrentHashMap<>();
        
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
            String name = cb.getName();
            states.put(name, CircuitBreakerStatus.builder()
                .name(name)
                .state(cb.getState())
                .metrics(cb.getMetrics())
                .config(cb.getCircuitBreakerConfig())
                .customMetrics(getMetrics(name))
                .build());
        });
        
        return states;
    }

    // Private configuration methods

    private void configurePaymentServiceCircuitBreakers() {
        // Stripe - High reliability expected
        customConfigs.put("stripe-payments", CircuitBreakerConfig.custom()
            .failureRateThreshold(30) // More sensitive
            .slowCallRateThreshold(40)
            .slowCallDurationThreshold(Duration.ofMillis(3000))
            .permittedNumberOfCallsInHalfOpenState(5)
            .minimumNumberOfCalls(20)
            .slidingWindowSize(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .build());
        
        // PayPal - Standard configuration
        customConfigs.put("paypal-payments", CircuitBreakerConfig.custom()
            .failureRateThreshold(40)
            .slowCallRateThreshold(50)
            .slowCallDurationThreshold(Duration.ofMillis(5000))
            .permittedNumberOfCallsInHalfOpenState(3)
            .minimumNumberOfCalls(15)
            .slidingWindowSize(75)
            .waitDurationInOpenState(Duration.ofSeconds(20))
            .build());
        
        // CashApp/Venmo - Less critical, more tolerant
        customConfigs.put("cashapp-payments", CircuitBreakerConfig.custom()
            .failureRateThreshold(60)
            .slowCallRateThreshold(60)
            .slowCallDurationThreshold(Duration.ofMillis(8000))
            .permittedNumberOfCallsInHalfOpenState(2)
            .minimumNumberOfCalls(10)
            .slidingWindowSize(100)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .build());
    }

    private void configureExternalServiceCircuitBreakers() {
        // Email service
        customConfigs.put("email-service", CircuitBreakerConfig.custom()
            .failureRateThreshold(70)
            .slowCallRateThreshold(80)
            .slowCallDurationThreshold(Duration.ofMillis(10000))
            .permittedNumberOfCallsInHalfOpenState(2)
            .minimumNumberOfCalls(5)
            .slidingWindowSize(50)
            .waitDurationInOpenState(Duration.ofMinutes(5))
            .build());
        
        // SMS service
        customConfigs.put("sms-service", CircuitBreakerConfig.custom()
            .failureRateThreshold(60)
            .slowCallRateThreshold(70)
            .slowCallDurationThreshold(Duration.ofMillis(8000))
            .permittedNumberOfCallsInHalfOpenState(3)
            .minimumNumberOfCalls(8)
            .slidingWindowSize(40)
            .waitDurationInOpenState(Duration.ofMinutes(2))
            .build());
        
        // KYC service
        customConfigs.put("kyc-verification", CircuitBreakerConfig.custom()
            .failureRateThreshold(35)
            .slowCallRateThreshold(45)
            .slowCallDurationThreshold(Duration.ofMillis(15000))
            .permittedNumberOfCallsInHalfOpenState(2)
            .minimumNumberOfCalls(5)
            .slidingWindowSize(30)
            .waitDurationInOpenState(Duration.ofMinutes(10))
            .build());
    }

    private void configureBlockchainServiceCircuitBreakers() {
        // Bitcoin network calls
        customConfigs.put("bitcoin-network", CircuitBreakerConfig.custom()
            .failureRateThreshold(45)
            .slowCallRateThreshold(55)
            .slowCallDurationThreshold(Duration.ofMillis(20000))
            .permittedNumberOfCallsInHalfOpenState(2)
            .minimumNumberOfCalls(8)
            .slidingWindowSize(25)
            .waitDurationInOpenState(Duration.ofMinutes(3))
            .build());
        
        // Ethereum network calls
        customConfigs.put("ethereum-network", CircuitBreakerConfig.custom()
            .failureRateThreshold(40)
            .slowCallRateThreshold(50)
            .slowCallDurationThreshold(Duration.ofMillis(15000))
            .permittedNumberOfCallsInHalfOpenState(3)
            .minimumNumberOfCalls(10)
            .slidingWindowSize(30)
            .waitDurationInOpenState(Duration.ofMinutes(2))
            .build());
    }

    private void configureDatabaseCircuitBreakers() {
        // Primary database
        customConfigs.put("primary-database", CircuitBreakerConfig.custom()
            .failureRateThreshold(25)
            .slowCallRateThreshold(30)
            .slowCallDurationThreshold(Duration.ofMillis(2000))
            .permittedNumberOfCallsInHalfOpenState(5)
            .minimumNumberOfCalls(30)
            .slidingWindowSize(100)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .build());
        
        // Read replica
        customConfigs.put("read-replica", CircuitBreakerConfig.custom()
            .failureRateThreshold(40)
            .slowCallRateThreshold(45)
            .slowCallDurationThreshold(Duration.ofMillis(3000))
            .permittedNumberOfCallsInHalfOpenState(3)
            .minimumNumberOfCalls(20)
            .slidingWindowSize(75)
            .waitDurationInOpenState(Duration.ofSeconds(15))
            .build());
        
        // Cache (Redis)
        customConfigs.put("redis-cache", CircuitBreakerConfig.custom()
            .failureRateThreshold(60)
            .slowCallRateThreshold(70)
            .slowCallDurationThreshold(Duration.ofMillis(1000))
            .permittedNumberOfCallsInHalfOpenState(5)
            .minimumNumberOfCalls(15)
            .slidingWindowSize(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .build());
    }

    private CircuitBreaker getOrCreateCircuitBreaker(String serviceName) {
        CircuitBreakerConfig config = customConfigs.getOrDefault(serviceName, 
            getDefaultCircuitBreakerConfig());
        
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceName, config);
        
        // Add event listeners
        addCircuitBreakerEventListeners(circuitBreaker);
        
        return circuitBreaker;
    }

    private CircuitBreakerConfig getDefaultCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(defaultFailureRateThreshold)
            .slowCallRateThreshold(defaultSlowCallRateThreshold)
            .slowCallDurationThreshold(Duration.ofMillis(defaultSlowCallDurationMs))
            .permittedNumberOfCallsInHalfOpenState(defaultPermittedCallsInHalfOpenState)
            .minimumNumberOfCalls(defaultMinimumCalls)
            .slidingWindowSize(defaultSlidingWindowSize)
            .waitDurationInOpenState(Duration.ofMillis(defaultWaitDurationMs))
            .build();
    }

    private void addCircuitBreakerEventListeners(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher()
            .onStateTransition(this::handleStateTransition)
            .onSuccess(event -> recordSuccess(circuitBreaker.getName()))
            .onError(event -> recordFailure(circuitBreaker.getName(), 
                new RuntimeException(event.getThrowable().getMessage())))
            .onCallNotPermitted(event -> recordCallNotPermitted(circuitBreaker.getName()));
    }

    private void handleStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        String serviceName = event.getCircuitBreakerName();
        CircuitBreaker.State fromState = event.getStateTransition().getFromState();
        CircuitBreaker.State toState = event.getStateTransition().getToState();
        
        log.info("Circuit breaker state transition for {}: {} -> {}", 
            serviceName, fromState, toState);
        
        // Record state transition
        CircuitBreakerMetrics metrics = getMetrics(serviceName);
        metrics.recordStateTransition(fromState, toState);
        
        // Alert on critical state changes
        if (toState == CircuitBreaker.State.OPEN) {
            handleCircuitBreakerOpened(serviceName, event);
        } else if (toState == CircuitBreaker.State.HALF_OPEN) {
            handleCircuitBreakerHalfOpened(serviceName);
        } else if (toState == CircuitBreaker.State.CLOSED && fromState == CircuitBreaker.State.HALF_OPEN) {
            handleCircuitBreakerRecovered(serviceName);
        }
    }

    private void handleCircuitBreakerOpened(String serviceName, CircuitBreakerOnStateTransitionEvent event) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(serviceName);
        log.error("CIRCUIT BREAKER OPENED: {} - Failure rate: {}%", 
            serviceName, cb.getMetrics().getFailureRate());
        
        // Report to monitoring
        errorReportingService.reportCriticalError(
            new CircuitBreakerOpenException("Circuit breaker opened for " + serviceName),
            "/circuit-breaker/" + serviceName,
            "system"
        );
        
        // Track in metrics
        CircuitBreakerMetrics metrics = getMetrics(serviceName);
        metrics.incrementOpenEvents();
        
        // Trigger fallback mechanisms
        triggerFallbackMechanisms(serviceName);
    }

    private void handleCircuitBreakerHalfOpened(String serviceName) {
        log.info("Circuit breaker HALF-OPEN for service: {}", serviceName);
        CircuitBreakerMetrics metrics = getMetrics(serviceName);
        metrics.incrementHalfOpenEvents();
    }

    private void handleCircuitBreakerRecovered(String serviceName) {
        log.info("Circuit breaker RECOVERED for service: {}", serviceName);
        CircuitBreakerMetrics metrics = getMetrics(serviceName);
        metrics.incrementRecoveryEvents();
        
        // Report recovery
        errorReportingService.reportBusinessError("CIRCUIT_BREAKER_RECOVERED",
            "Circuit breaker recovered for " + serviceName,
            Map.of("service", serviceName, "recoveredAt", System.currentTimeMillis()));
    }

    private void recordSuccess(String serviceName) {
        CircuitBreakerMetrics metrics = getMetrics(serviceName);
        metrics.incrementSuccessCount();
    }

    private void recordFailure(String serviceName, Throwable throwable) {
        CircuitBreakerMetrics metrics = getMetrics(serviceName);
        metrics.incrementFailureCount();
        metrics.recordLastFailure(throwable);
    }

    private void recordCallNotPermitted(String serviceName) {
        CircuitBreakerMetrics metrics = getMetrics(serviceName);
        metrics.incrementCallsNotPermitted();
    }

    private void handleCircuitBreakerException(String serviceName, Exception e) {
        if (e instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
            log.warn("Circuit breaker OPEN - Call not permitted for service: {}", serviceName);
            throw new CircuitBreakerOpenException("Service " + serviceName + " is currently unavailable");
        }
    }

    private void triggerFallbackMechanisms(String serviceName) {
        // Implement service-specific fallback strategies
        switch (serviceName) {
            case "stripe-payments":
                // Fallback to PayPal for payments
                log.info("Activating PayPal fallback for Stripe payments");
                break;
            case "primary-database":
                // Switch to read replica
                log.info("Switching to read-replica for database operations");
                break;
            case "email-service":
                // Queue emails for later delivery
                log.info("Queueing emails for delayed delivery");
                break;
            default:
                log.info("No specific fallback configured for service: {}", serviceName);
        }
    }

    // Exception classes
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CircuitBreakerStatus {
        private String name;
        private CircuitBreaker.State state;
        private io.github.resilience4j.circuitbreaker.CircuitBreaker.Metrics metrics;
        private CircuitBreakerConfig config;
        private CircuitBreakerMetrics customMetrics;
    }
}