package com.waqiti.common.resilience;

import com.waqiti.common.exception.ServiceException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

/**
 * Payment resilience aspect that implements the @PaymentResilience annotation
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Aspect
@Component("paymentResilience")
@Slf4j
@RequiredArgsConstructor
public class PaymentResilienceService {
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final MeterRegistry meterRegistry;
    
    @Value("${resilience.payment.enabled:true}")
    private boolean resilienceEnabled;
    
    @Around("@annotation(com.waqiti.common.resilience.PaymentResilience)")
    public Object applyResilience(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!resilienceEnabled) {
            return joinPoint.proceed();
        }
        
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String operationName = className + "." + methodName;
        
        log.debug("Applying payment resilience to: {}", operationName);
        
        // Get or create resilience components
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(operationName);
        Retry retry = retryRegistry.retry(operationName);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(operationName);
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(operationName);
        
        // Create callable from join point
        Callable<Object> callable = () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable throwable) {
                throw new Exception(throwable);
            }
        };
        
        // Decorate with resilience patterns
        callable = CircuitBreaker.decorateCallable(circuitBreaker, callable);
        callable = Retry.decorateCallable(retry, callable);
        callable = RateLimiter.decorateCallable(rateLimiter, callable);
        callable = Bulkhead.decorateCallable(bulkhead, callable);
        
        try {
            Object result = callable.call();
            recordSuccess(operationName);
            return result;
        } catch (Exception e) {
            recordFailure(operationName, e);
            throw new ServiceException("Payment operation failed: " + operationName, e);
        }
    }
    
    private void recordSuccess(String operationName) {
        meterRegistry.counter("payment.resilience.success", "operation", operationName).increment();
    }
    
    private void recordFailure(String operationName, Exception e) {
        meterRegistry.counter("payment.resilience.failure", 
            "operation", operationName,
            "exception", e.getClass().getSimpleName()).increment();
    }
}