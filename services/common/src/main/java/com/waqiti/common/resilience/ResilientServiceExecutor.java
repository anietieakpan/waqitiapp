package com.waqiti.common.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * Service executor with built-in resilience patterns
 * Provides easy-to-use methods for executing external service calls with
 * circuit breaker, retry, bulkhead, rate limiter, and time limiter
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientServiceExecutor {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    
    private final ScheduledExecutorService scheduledExecutor = 
        Executors.newScheduledThreadPool(10);

    /**
     * Execute a synchronous call with full resilience stack
     */
    public <T> T executeWithResilience(String serviceName, Supplier<T> supplier) {
        return executeWithResilience(serviceName, serviceName, serviceName, 
                                    serviceName, serviceName, supplier);
    }

    /**
     * Execute a synchronous call with custom resilience components
     */
    public <T> T executeWithResilience(String circuitBreakerName,
                                      String retryName,
                                      String bulkheadName,
                                      String rateLimiterName,
                                      String timeLimiterName,
                                      Supplier<T> supplier) {
        try {
            // Get resilience components
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
            Retry retry = retryRegistry.retry(retryName);
            Bulkhead bulkhead = bulkheadRegistry.bulkhead(bulkheadName);
            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(rateLimiterName);
            TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(timeLimiterName);

            // Decorate the supplier with resilience patterns
            Supplier<T> decoratedSupplier = Decorators
                .ofSupplier(supplier)
                .withCircuitBreaker(circuitBreaker)
                .withRetry(retry)
                .withBulkhead(bulkhead)
                .withRateLimiter(rateLimiter)
                .decorate();

            Supplier<CompletionStage<T>> asyncSupplier = () -> CompletableFuture.completedFuture(decoratedSupplier.get());

            // Execute with time limiter
            CompletionStage<T> completionStage = timeLimiter.executeCompletionStage(
                scheduledExecutor, asyncSupplier
            );
            CompletableFuture<T> future = completionStage.toCompletableFuture();

            // Get result with explicit timeout (defense in depth - TimeLimiter already enforces timeout)
            // Using 60 seconds as max fallback timeout (TimeLimiter config typically shorter)
            try {
                return future.get(60, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Resilient execution timed out after 60 seconds for service: {}", circuitBreakerName, e);
                throw new ServiceExecutionException("Service call timed out after 60 seconds", e);
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("Resilient execution failed for service: {}", circuitBreakerName, e.getCause());
                throw new ServiceExecutionException("Service call failed: " + e.getCause().getMessage(), e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Resilient execution interrupted for service: {}", circuitBreakerName, e);
                throw new ServiceExecutionException("Service call interrupted", e);
            }

        } catch (ServiceExecutionException e) {
            // Re-throw our own exceptions without wrapping
            throw e;
        } catch (Exception e) {
            log.error("Resilient execution failed for service: {}", circuitBreakerName, e);
            throw new ServiceExecutionException(
                "Service call failed after applying resilience patterns: " + e.getMessage(), e
            );
        }
    }

    /**
     * Execute an asynchronous call with full resilience stack
     */
    public <T> CompletableFuture<T> executeAsyncWithResilience(String serviceName, 
                                                              Supplier<CompletableFuture<T>> supplier) {
        return executeAsyncWithResilience(serviceName, serviceName, serviceName, 
                                         serviceName, serviceName, supplier);
    }

    /**
     * Execute an asynchronous call with custom resilience components
     */
    public <T> CompletableFuture<T> executeAsyncWithResilience(String circuitBreakerName,
                                                              String retryName,
                                                              String bulkheadName,
                                                              String rateLimiterName,
                                                              String timeLimiterName,
                                                              Supplier<CompletableFuture<T>> supplier) {
        try {
            // Get resilience components
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
            Retry retry = retryRegistry.retry(retryName);
            Bulkhead bulkhead = bulkheadRegistry.bulkhead(bulkheadName);
            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(rateLimiterName);
            TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(timeLimiterName);

            // Decorate the supplier - cast CompletableFuture to CompletionStage
            Supplier<CompletionStage<T>> completionStageSupplier = () -> (CompletionStage<T>) supplier.get();
            Supplier<CompletionStage<T>> decoratedSupplier = Decorators
                .ofCompletionStage(completionStageSupplier)
                .withCircuitBreaker(circuitBreaker)
                .withRetry(retry, this.scheduledExecutor)
                .withBulkhead(bulkhead)
                .withRateLimiter(rateLimiter)
                .decorate();

            // Execute with time limiter
            CompletionStage<T> completionStage = timeLimiter.executeCompletionStage(
                scheduledExecutor, decoratedSupplier
            );
            return completionStage.toCompletableFuture();

        } catch (Exception e) {
            log.error("Async resilient execution failed for service: {}", circuitBreakerName, e);
            CompletableFuture<T> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new ServiceExecutionException(
                "Async service call failed: " + e.getMessage(), e
            ));
            return failedFuture;
        }
    }

    /**
     * Execute a payment service call with specialized resilience configuration
     */
    public <T> T executePaymentCall(Supplier<T> supplier) {
        return executeWithResilience(
            "payment-gateway",
            "payment-retry",
            "payment-bulkhead",
            "payment-api",
            "payment-timeout",
            supplier
        );
    }

    /**
     * Execute a KYC service call with specialized resilience configuration
     */
    public <T> T executeKycCall(Supplier<T> supplier) {
        return executeWithResilience(
            "kyc-service",
            "kyc-retry",
            "kyc-bulkhead",
            "kyc-api",
            "kyc-timeout",
            supplier
        );
    }

    /**
     * Execute a notification service call with specialized resilience configuration
     */
    public <T> CompletableFuture<T> executeNotificationCall(Supplier<CompletableFuture<T>> supplier) {
        return executeAsyncWithResilience(
            "notification-service",
            "notification-retry",
            "notification-bulkhead",
            "public-api",
            "notification-timeout",
            supplier
        );
    }

    /**
     * Execute with only circuit breaker (lightweight resilience)
     */
    public <T> T executeWithCircuitBreaker(String circuitBreakerName, Supplier<T> supplier) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
        return circuitBreaker.executeSupplier(supplier);
    }

    /**
     * Execute with circuit breaker and retry
     */
    public <T> T executeWithCircuitBreakerAndRetry(String circuitBreakerName, 
                                                   String retryName, 
                                                   Supplier<T> supplier) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
        Retry retry = retryRegistry.retry(retryName);
        
        Supplier<T> decoratedSupplier = Decorators
            .ofSupplier(supplier)
            .withCircuitBreaker(circuitBreaker)
            .withRetry(retry)
            .decorate();
            
        return decoratedSupplier.get();
    }

    /**
     * Execute callable with resilience
     */
    public <T> T executeCallable(String serviceName, Callable<T> callable) {
        return executeWithResilience(serviceName, () -> {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new ServiceExecutionException("Callable execution failed", e);
            }
        });
    }

    /**
     * Get circuit breaker state
     */
    public CircuitBreaker.State getCircuitBreakerState(String circuitBreakerName) {
        return circuitBreakerRegistry.circuitBreaker(circuitBreakerName).getState();
    }

    /**
     * Get circuit breaker metrics
     */
    public CircuitBreaker.Metrics getCircuitBreakerMetrics(String circuitBreakerName) {
        return circuitBreakerRegistry.circuitBreaker(circuitBreakerName).getMetrics();
    }

    /**
     * Reset circuit breaker
     */
    public void resetCircuitBreaker(String circuitBreakerName) {
        circuitBreakerRegistry.circuitBreaker(circuitBreakerName).reset();
    }

    /**
     * Force open circuit breaker (for testing/emergency)
     */
    public void forceOpenCircuitBreaker(String circuitBreakerName) {
        circuitBreakerRegistry.circuitBreaker(circuitBreakerName).transitionToOpenState();
    }

    /**
     * Service execution exception
     */
    public static class ServiceExecutionException extends RuntimeException {
        public ServiceExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}