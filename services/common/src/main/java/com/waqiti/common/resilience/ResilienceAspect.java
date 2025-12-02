package com.waqiti.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Aspect for applying resilience patterns to service methods
 * Combines circuit breaker, retry, and rate limiting functionality
 */
@Aspect
@Component
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class ResilienceAspect {

    private final CircuitBreaker paymentCircuitBreaker;
    private final CircuitBreaker externalApiCircuitBreaker;
    private final CircuitBreaker databaseCircuitBreaker;
    private final CircuitBreaker cryptoCircuitBreaker;
    private final CircuitBreaker notificationCircuitBreaker;
    private final CircuitBreaker analyticsCircuitBreaker;
    
    private final Retry paymentRetry;
    private final Retry externalApiRetry;
    private final Retry databaseRetry;
    private final Retry cryptoRetry;
    private final Retry notificationRetry;
    private final Retry complianceRetry;
    private final Retry analyticsRetry;
    
    private final RateLimiter paymentRateLimiter;
    private final RateLimiter externalApiRateLimiter;
    private final RateLimiter databaseRateLimiter;
    private final RateLimiter authenticationRateLimiter;
    private final RateLimiter notificationRateLimiter;
    private final RateLimiter cryptoTransactionRateLimiter;

    /**
     * Apply resilience patterns to payment service methods
     */
    @Around("@annotation(com.waqiti.common.resilience.PaymentResilience)")
    public Object applyPaymentResilience(ProceedingJoinPoint joinPoint) throws Throwable {
        log.debug("Applying payment resilience patterns to: {}", joinPoint.getSignature().getName());
        
        Supplier<Object> supplier = () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        };
        
        // Apply rate limiting first
        supplier = RateLimiter.decorateSupplier(paymentRateLimiter, supplier);
        
        // Apply circuit breaker
        supplier = CircuitBreaker.decorateSupplier(paymentCircuitBreaker, supplier);
        
        // Apply retry with exponential backoff
        supplier = Retry.decorateSupplier(paymentRetry, supplier);
        
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw unwrapException(e);
        }
    }

    /**
     * Apply resilience patterns to external API calls
     */
    @Around("@annotation(com.waqiti.common.resilience.ExternalApiResilience)")
    public Object applyExternalApiResilience(ProceedingJoinPoint joinPoint) throws Throwable {
        log.debug("Applying external API resilience patterns to: {}", joinPoint.getSignature().getName());
        
        Supplier<Object> supplier = () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        };
        
        // Apply rate limiting
        supplier = RateLimiter.decorateSupplier(externalApiRateLimiter, supplier);
        
        // Apply circuit breaker
        supplier = CircuitBreaker.decorateSupplier(externalApiCircuitBreaker, supplier);
        
        // Apply retry
        supplier = Retry.decorateSupplier(externalApiRetry, supplier);
        
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw unwrapException(e);
        }
    }

    /**
     * Apply resilience patterns to database operations
     */
    @Around("@annotation(com.waqiti.common.resilience.DatabaseResilience)")
    public Object applyDatabaseResilience(ProceedingJoinPoint joinPoint) throws Throwable {
        log.debug("Applying database resilience patterns to: {}", joinPoint.getSignature().getName());
        
        Supplier<Object> supplier = () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        };
        
        // Apply rate limiting
        supplier = RateLimiter.decorateSupplier(databaseRateLimiter, supplier);
        
        // Apply circuit breaker
        supplier = CircuitBreaker.decorateSupplier(databaseCircuitBreaker, supplier);
        
        // Apply retry
        supplier = Retry.decorateSupplier(databaseRetry, supplier);
        
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw unwrapException(e);
        }
    }

    /**
     * Apply resilience patterns to cryptocurrency operations
     */
    @Around("@annotation(com.waqiti.common.resilience.CryptoResilience)")
    public Object applyCryptoResilience(ProceedingJoinPoint joinPoint) throws Throwable {
        log.debug("Applying crypto resilience patterns to: {}", joinPoint.getSignature().getName());
        
        Supplier<Object> supplier = () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        };
        
        // Apply rate limiting
        supplier = RateLimiter.decorateSupplier(cryptoTransactionRateLimiter, supplier);
        
        // Apply circuit breaker
        supplier = CircuitBreaker.decorateSupplier(cryptoCircuitBreaker, supplier);
        
        // Apply retry
        supplier = Retry.decorateSupplier(cryptoRetry, supplier);
        
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw unwrapException(e);
        }
    }

    /**
     * Apply resilience patterns to notification services
     */
    @Around("@annotation(com.waqiti.common.resilience.NotificationResilience)")
    public Object applyNotificationResilience(ProceedingJoinPoint joinPoint) throws Throwable {
        log.debug("Applying notification resilience patterns to: {}", joinPoint.getSignature().getName());
        
        Supplier<Object> supplier = () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        };
        
        // Apply rate limiting
        supplier = RateLimiter.decorateSupplier(notificationRateLimiter, supplier);
        
        // Apply circuit breaker
        supplier = CircuitBreaker.decorateSupplier(notificationCircuitBreaker, supplier);
        
        // Apply retry
        supplier = Retry.decorateSupplier(notificationRetry, supplier);
        
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw unwrapException(e);
        }
    }

    /**
     * Apply resilience patterns to compliance operations
     */
    @Around("@annotation(com.waqiti.common.resilience.ComplianceResilience)")
    public Object applyComplianceResilience(ProceedingJoinPoint joinPoint) throws Throwable {
        log.debug("Applying compliance resilience patterns to: {}", joinPoint.getSignature().getName());
        
        Supplier<Object> supplier = () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        };
        
        // Apply retry (no rate limiting or circuit breaker for compliance - must complete)
        supplier = Retry.decorateSupplier(complianceRetry, supplier);
        
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw unwrapException(e);
        }
    }

    /**
     * Apply resilience patterns to analytics operations
     */
    @Around("@annotation(com.waqiti.common.resilience.AnalyticsResilience)")
    public Object applyAnalyticsResilience(ProceedingJoinPoint joinPoint) throws Throwable {
        log.debug("Applying analytics resilience patterns to: {}", joinPoint.getSignature().getName());
        
        Supplier<Object> supplier = () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        };
        
        // Apply circuit breaker
        supplier = CircuitBreaker.decorateSupplier(analyticsCircuitBreaker, supplier);
        
        // Apply retry
        supplier = Retry.decorateSupplier(analyticsRetry, supplier);
        
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw unwrapException(e);
        }
    }

    /**
     * Apply authentication rate limiting
     */
    @Around("@annotation(com.waqiti.common.resilience.AuthenticationResilience)")
    public Object applyAuthenticationResilience(ProceedingJoinPoint joinPoint) throws Throwable {
        log.debug("Applying authentication resilience patterns to: {}", joinPoint.getSignature().getName());
        
        Supplier<Object> supplier = () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        };
        
        // Apply rate limiting only (strict rate limiting for auth)
        supplier = RateLimiter.decorateSupplier(authenticationRateLimiter, supplier);
        
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw unwrapException(e);
        }
    }
    
    /**
     * Helper method to unwrap RuntimeException and rethrow the original cause
     */
    private Throwable unwrapException(RuntimeException e) throws Throwable {
        Throwable cause = e.getCause();
        if (cause != null) {
            throw cause;
        }
        throw e;
    }
}