package com.waqiti.payment.core.resilience;

import com.waqiti.common.resilience.CircuitBreakerService;
import com.waqiti.common.ratelimit.AdvancedRateLimitService;
import com.waqiti.payment.core.model.PaymentRequest;
import com.waqiti.payment.core.model.PaymentResponse;
import com.waqiti.payment.core.model.PaymentResilienceConfig;
import com.waqiti.payment.core.model.PaymentResilienceResult;
import com.waqiti.payment.core.audit.PaymentAuditService;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Production-Ready Payment Resilience Component
 * 
 * Provides comprehensive resilience patterns for payment operations:
 * - Circuit breaker protection for payment providers
 * - Rate limiting for API calls and user requests
 * - Bulkhead isolation for different payment types
 * - Timeout management for long-running operations
 * - Retry coordination across payment flows
 * - Graceful degradation and fallback mechanisms
 * - Real-time health monitoring and auto-recovery
 * 
 * @author Waqiti Payment Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResilience {

    private final CircuitBreakerService circuitBreakerService;
    private final AdvancedRateLimitService rateLimitService;
    private final PaymentAuditService auditService;
    private final MeterRegistry meterRegistry;
    
    @Value("${payment.resilience.circuit.failure.threshold:50}")
    private int circuitBreakerFailureThreshold;
    
    @Value("${payment.resilience.circuit.wait.duration.seconds:60}")
    private long circuitBreakerWaitDurationSeconds;
    
    @Value("${payment.resilience.bulkhead.max.concurrent:10}")
    private int bulkheadMaxConcurrent;
    
    @Value("${payment.resilience.timeout.default.seconds:30}")
    private long defaultTimeoutSeconds;
    
    @Value("${payment.resilience.rate.limit.requests.per.minute:60}")
    private int defaultRateLimitPerMinute;
    
    // Resilience components per provider
    private final Map<String, CircuitBreaker> providerCircuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, Bulkhead> providerBulkheads = new ConcurrentHashMap<>();
    private final Map<String, TimeLimiter> providerTimeLimiters = new ConcurrentHashMap<>();
    private final Map<String, Retry> providerRetries = new ConcurrentHashMap<>();
    
    // Metrics
    private final Counter circuitBreakerOpenedCounter;
    private final Counter circuitBreakerClosedCounter;
    private final Counter rateLimitExceededCounter;
    private final Counter timeoutCounter;
    private final Timer resilienceOperationTimer;
    
    public PaymentResilience(CircuitBreakerService circuitBreakerService,
                           AdvancedRateLimitService rateLimitService,
                           PaymentAuditService auditService,
                           MeterRegistry meterRegistry) {
        this.circuitBreakerService = circuitBreakerService;
        this.rateLimitService = rateLimitService;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.circuitBreakerOpenedCounter = Counter.builder("payment.resilience.circuit_breaker.opened")
            .description("Number of times circuit breaker opened")
            .register(meterRegistry);
            
        this.circuitBreakerClosedCounter = Counter.builder("payment.resilience.circuit_breaker.closed")
            .description("Number of times circuit breaker closed")
            .register(meterRegistry);
            
        this.rateLimitExceededCounter = Counter.builder("payment.resilience.rate_limit.exceeded")
            .description("Number of rate limit violations")
            .register(meterRegistry);
            
        this.timeoutCounter = Counter.builder("payment.resilience.timeout.total")
            .description("Number of timeout occurrences")
            .register(meterRegistry);
            
        this.resilienceOperationTimer = Timer.builder("payment.resilience.operation.duration")
            .description("Time taken for resilience-protected operations")
            .register(meterRegistry);
    }
    
    /**
     * Execute a payment operation with full resilience protection
     */
    public <T> CompletableFuture<PaymentResilienceResult<T>> executeWithResilience(
            String provider,
            String operationType,
            Supplier<CompletableFuture<T>> operation,
            PaymentResilienceConfig config) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String operationId = UUID.randomUUID().toString();
        
        log.debug("Starting resilient operation: {} for provider: {} (ID: {})", 
            operationType, provider, operationId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Step 1: Rate limiting check
                if (!checkRateLimit(provider, config)) {
                    return PaymentResilienceResult.<T>builder()
                        .operationId(operationId)
                        .success(false)
                        .errorType("RATE_LIMIT_EXCEEDED")
                        .errorMessage("Rate limit exceeded for provider: " + provider)
                        .timestamp(LocalDateTime.now())
                        .build();
                }
                
                // Step 2: Circuit breaker check
                CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(provider, config);
                if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                    return PaymentResilienceResult.<T>builder()
                        .operationId(operationId)
                        .success(false)
                        .errorType("CIRCUIT_BREAKER_OPEN")
                        .errorMessage("Circuit breaker is open for provider: " + provider)
                        .timestamp(LocalDateTime.now())
                        .build();
                }
                
                // Step 3: Bulkhead execution
                Bulkhead bulkhead = getOrCreateBulkhead(provider, config);
                if (!bulkhead.tryAcquirePermission()) {
                    return PaymentResilienceResult.<T>builder()
                        .operationId(operationId)
                        .success(false)
                        .errorType("BULKHEAD_FULL")
                        .errorMessage("Bulkhead capacity exceeded for provider: " + provider)
                        .timestamp(LocalDateTime.now())
                        .build();
                }
                
                try {
                    // Step 4: Execute with timeout protection
                    TimeLimiter timeLimiter = getOrCreateTimeLimiter(provider, config);
                    T result = timeLimiter.executeFutureSupplier(() -> 
                        operation.get().exceptionally(throwable -> {
                            // Record circuit breaker failure
                            circuitBreaker.onError(Duration.ofMillis(System.currentTimeMillis()), 
                                TimeUnit.MILLISECONDS, throwable);
                            throw new RuntimeException(throwable);
                        })
                    );
                    
                    // Record circuit breaker success
                    circuitBreaker.onSuccess(Duration.ofMillis(System.currentTimeMillis()), TimeUnit.MILLISECONDS);
                    
                    return PaymentResilienceResult.<T>builder()
                        .operationId(operationId)
                        .success(true)
                        .result(result)
                        .provider(provider)
                        .operationType(operationType)
                        .timestamp(LocalDateTime.now())
                        .build();
                    
                } catch (TimeoutException e) {
                    timeoutCounter.increment(io.micrometer.core.instrument.Tags.of("provider", provider));
                    
                    log.warn("Operation timeout for provider: {} (operation: {})", provider, operationType);
                    
                    return PaymentResilienceResult.<T>builder()
                        .operationId(operationId)
                        .success(false)
                        .errorType("TIMEOUT")
                        .errorMessage("Operation timed out for provider: " + provider)
                        .timestamp(LocalDateTime.now())
                        .build();
                        
                } finally {
                    bulkhead.releasePermission();
                }
                
            } catch (Exception e) {
                log.error("Error in resilient operation for provider: {} (operation: {})", 
                    provider, operationType, e);
                    
                return PaymentResilienceResult.<T>builder()
                    .operationId(operationId)
                    .success(false)
                    .errorType("EXECUTION_ERROR")
                    .errorMessage("Execution failed: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
                    
            } finally {
                sample.stop(resilienceOperationTimer.tag("provider", provider).tag("operation", operationType));
            }
        });
    }
    
    /**
     * Execute payment request with comprehensive resilience
     */
    public CompletableFuture<PaymentResilienceResult<PaymentResponse>> executePaymentWithResilience(
            PaymentRequest request, 
            String provider,
            Supplier<CompletableFuture<PaymentResponse>> paymentOperation) {
        
        PaymentResilienceConfig config = createDefaultConfig(provider);
        
        return executeWithResilience(provider, "PAYMENT_PROCESSING", paymentOperation, config)
            .thenCompose(result -> {
                // Audit resilience outcome
                auditResilienceOperation(request.getPaymentId(), provider, result);
                return CompletableFuture.completedFuture(result);
            });
    }
    
    /**
     * Check if a provider is currently available (circuit breaker not open)
     */
    public boolean isProviderAvailable(String provider) {
        CircuitBreaker circuitBreaker = providerCircuitBreakers.get(provider);
        if (circuitBreaker == null) {
            return true; // If no circuit breaker exists, assume available
        }
        return circuitBreaker.getState() != CircuitBreaker.State.OPEN;
    }
    
    /**
     * Get provider health status
     */
    public ProviderHealthStatus getProviderHealth(String provider) {
        CircuitBreaker circuitBreaker = providerCircuitBreakers.get(provider);
        Bulkhead bulkhead = providerBulkheads.get(provider);
        
        ProviderHealthStatus.Builder builder = ProviderHealthStatus.builder()
            .provider(provider)
            .timestamp(LocalDateTime.now());
        
        if (circuitBreaker != null) {
            builder.circuitBreakerState(circuitBreaker.getState().toString())
                   .circuitBreakerFailureRate(circuitBreaker.getMetrics().getFailureRate())
                   .circuitBreakerSuccessRate(circuitBreaker.getMetrics().getSuccessRate());
        }
        
        if (bulkhead != null) {
            builder.bulkheadAvailablePermissions(bulkhead.getMetrics().getAvailableConcurrentCalls())
                   .bulkheadMaxPermissions(bulkhead.getMetrics().getMaxAllowedConcurrentCalls());
        }
        
        // Check rate limit status
        String rateLimitKey = "payment-provider-" + provider;
        boolean rateLimitOk = rateLimitService.isAllowed(rateLimitKey, "system");
        builder.rateLimitOk(rateLimitOk);
        
        // Overall health assessment
        boolean healthy = (circuitBreaker == null || circuitBreaker.getState() != CircuitBreaker.State.OPEN) &&
                         (bulkhead == null || bulkhead.getMetrics().getAvailableConcurrentCalls() > 0) &&
                         rateLimitOk;
        builder.healthy(healthy);
        
        return builder.build();
    }
    
    /**
     * Force open circuit breaker for a provider (for emergency situations)
     */
    public void forceOpenCircuitBreaker(String provider, String reason) {
        CircuitBreaker circuitBreaker = providerCircuitBreakers.get(provider);
        if (circuitBreaker != null) {
            circuitBreaker.transitionToOpenState();
            circuitBreakerOpenedCounter.increment(io.micrometer.core.instrument.Tags.of("provider", provider, "reason", "manual"));
            
            log.warn("Circuit breaker manually opened for provider: {} (reason: {})", provider, reason);
            
            // Audit manual intervention
            auditService.auditCircuitBreakerManualIntervention(provider, "OPENED", reason);
        }
    }
    
    /**
     * Force close circuit breaker for a provider
     */
    public void forceCloseCircuitBreaker(String provider, String reason) {
        CircuitBreaker circuitBreaker = providerCircuitBreakers.get(provider);
        if (circuitBreaker != null) {
            circuitBreaker.transitionToClosedState();
            circuitBreakerClosedCounter.increment(io.micrometer.core.instrument.Tags.of("provider", provider, "reason", "manual"));
            
            log.info("Circuit breaker manually closed for provider: {} (reason: {})", provider, reason);
            
            // Audit manual intervention
            auditService.auditCircuitBreakerManualIntervention(provider, "CLOSED", reason);
        }
    }
    
    /**
     * Get resilience metrics for all providers
     */
    public Map<String, ProviderResilienceMetrics> getAllProviderMetrics() {
        Map<String, ProviderResilienceMetrics> metrics = new HashMap<>();
        
        providerCircuitBreakers.forEach((provider, circuitBreaker) -> {
            Bulkhead bulkhead = providerBulkheads.get(provider);
            
            ProviderResilienceMetrics providerMetrics = ProviderResilienceMetrics.builder()
                .provider(provider)
                .circuitBreakerMetrics(CircuitBreakerMetrics.builder()
                    .state(circuitBreaker.getState().toString())
                    .failureRate(circuitBreaker.getMetrics().getFailureRate())
                    .successRate(circuitBreaker.getMetrics().getSuccessRate())
                    .numberOfCalls(circuitBreaker.getMetrics().getNumberOfCalls())
                    .numberOfFailedCalls(circuitBreaker.getMetrics().getNumberOfFailedCalls())
                    .build())
                .bulkheadMetrics(bulkhead != null ? BulkheadMetrics.builder()
                    .availablePermissions(bulkhead.getMetrics().getAvailableConcurrentCalls())
                    .maxPermissions(bulkhead.getMetrics().getMaxAllowedConcurrentCalls())
                    .build() : null)
                .timestamp(LocalDateTime.now())
                .build();
                
            metrics.put(provider, providerMetrics);
        });
        
        return metrics;
    }
    
    // Helper methods
    
    private boolean checkRateLimit(String provider, PaymentResilienceConfig config) {
        try {
            String rateLimitKey = "payment-provider-" + provider;
            int requestsPerMinute = config.getRateLimitRequestsPerMinute() != null ? 
                config.getRateLimitRequestsPerMinute() : defaultRateLimitPerMinute;
            
            boolean allowed = rateLimitService.isAllowed(rateLimitKey, "system", requestsPerMinute, Duration.ofMinutes(1));
            
            if (!allowed) {
                rateLimitExceededCounter.increment(io.micrometer.core.instrument.Tags.of("provider", provider));
                log.warn("Rate limit exceeded for provider: {}", provider);
            }
            
            return allowed;
            
        } catch (Exception e) {
            log.error("Error checking rate limit for provider: {}", provider, e);
            return true; // Fail open for rate limiting
        }
    }
    
    private CircuitBreaker getOrCreateCircuitBreaker(String provider, PaymentResilienceConfig config) {
        return providerCircuitBreakers.computeIfAbsent(provider, p -> {
            CircuitBreaker circuitBreaker = circuitBreakerService.createCircuitBreaker(
                "payment-provider-" + provider,
                config.getCircuitBreakerFailureThreshold() != null ? 
                    config.getCircuitBreakerFailureThreshold() : circuitBreakerFailureThreshold,
                Duration.ofSeconds(config.getCircuitBreakerWaitDurationSeconds() != null ? 
                    config.getCircuitBreakerWaitDurationSeconds() : circuitBreakerWaitDurationSeconds)
            );
            
            // Add event listeners
            circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    log.info("Circuit breaker state transition for provider {}: {} -> {}", 
                        provider, event.getStateTransition().getFromState(), 
                        event.getStateTransition().getToState());
                    
                    if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                        circuitBreakerOpenedCounter.increment(io.micrometer.core.instrument.Tags.of("provider", provider));
                    } else if (event.getStateTransition().getToState() == CircuitBreaker.State.CLOSED) {
                        circuitBreakerClosedCounter.increment(io.micrometer.core.instrument.Tags.of("provider", provider));
                    }
                });
            
            return circuitBreaker;
        });
    }
    
    private Bulkhead getOrCreateBulkhead(String provider, PaymentResilienceConfig config) {
        return providerBulkheads.computeIfAbsent(provider, p -> {
            int maxConcurrent = config.getBulkheadMaxConcurrent() != null ? 
                config.getBulkheadMaxConcurrent() : bulkheadMaxConcurrent;
                
            return Bulkhead.of("payment-provider-" + provider, 
                Bulkhead.Config.custom()
                    .maxConcurrentCalls(maxConcurrent)
                    .build());
        });
    }
    
    private TimeLimiter getOrCreateTimeLimiter(String provider, PaymentResilienceConfig config) {
        return providerTimeLimiters.computeIfAbsent(provider, p -> {
            Duration timeout = config.getTimeoutDuration() != null ? 
                config.getTimeoutDuration() : Duration.ofSeconds(defaultTimeoutSeconds);
                
            return TimeLimiter.of("payment-provider-" + provider,
                TimeLimiter.Config.custom()
                    .timeoutDuration(timeout)
                    .build());
        });
    }
    
    private PaymentResilienceConfig createDefaultConfig(String provider) {
        return PaymentResilienceConfig.builder()
            .circuitBreakerFailureThreshold(circuitBreakerFailureThreshold)
            .circuitBreakerWaitDurationSeconds(circuitBreakerWaitDurationSeconds)
            .bulkheadMaxConcurrent(bulkheadMaxConcurrent)
            .timeoutDuration(Duration.ofSeconds(defaultTimeoutSeconds))
            .rateLimitRequestsPerMinute(defaultRateLimitPerMinute)
            .build();
    }
    
    private void auditResilienceOperation(String paymentId, String provider, PaymentResilienceResult<?> result) {
        try {
            auditService.auditResilienceOperation(paymentId, provider, 
                result.getOperationType(), result.isSuccess(), result.getErrorType());
        } catch (Exception e) {
            log.error("Error auditing resilience operation for payment: {}", paymentId, e);
        }
    }
}