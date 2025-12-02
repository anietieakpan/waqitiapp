package com.waqiti.common.resilience.annotation;

import com.waqiti.common.resilience.UniversalResilienceAspect;

import java.lang.annotation.*;

/**
 * Annotation to explicitly mark methods for resilience pattern application.
 *
 * This annotation allows fine-grained control over resilience patterns
 * when automatic detection is not sufficient or when specific configuration
 * is needed.
 *
 * Example usage:
 * <pre>
 * {@code
 * @Resilient(
 *     name = "stripe-payment-processing",
 *     priority = ResiliencePriority.CRITICAL,
 *     circuitBreaker = true,
 *     retry = true,
 *     rateLimiter = true,
 *     bulkhead = true,
 *     timeLimiter = true
 * )
 * public PaymentResult processPayment(PaymentRequest request) {
 *     // Implementation
 * }
 * }
 * </pre>
 *
 * @author Waqiti Platform Team
 * @since P0 Production Readiness Phase
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Resilient {

    /**
     * Unique name for this resilient operation.
     * Used for metrics, logging, and circuit breaker identification.
     * If not specified, defaults to ClassName.methodName
     */
    String name() default "";

    /**
     * Priority level for this operation.
     * Determines the strictness of resilience patterns.
     */
    UniversalResilienceAspect.ResiliencePriority priority()
            default UniversalResilienceAspect.ResiliencePriority.MEDIUM;

    /**
     * Enable circuit breaker pattern.
     * Prevents cascading failures by stopping calls when failure rate exceeds threshold.
     */
    boolean circuitBreaker() default true;

    /**
     * Enable retry pattern.
     * Automatically retries failed operations with exponential backoff.
     */
    boolean retry() default true;

    /**
     * Enable rate limiter pattern.
     * Limits the rate of operations to prevent overload.
     */
    boolean rateLimiter() default false;

    /**
     * Enable bulkhead pattern.
     * Isolates resources to prevent resource exhaustion.
     */
    boolean bulkhead() default true;

    /**
     * Enable time limiter pattern.
     * Prevents operations from hanging indefinitely.
     */
    boolean timeLimiter() default true;

    /**
     * Fallback method name to invoke when all resilience attempts fail.
     * The fallback method must have the same signature as the annotated method.
     */
    String fallbackMethod() default "";

    /**
     * Custom timeout in milliseconds (overrides default time limiter config).
     * Only used if timeLimiter = true.
     */
    long timeoutMs() default 0;

    /**
     * Maximum retry attempts (overrides default retry config).
     * Only used if retry = true.
     */
    int maxRetries() default 0;

    /**
     * Exceptions to retry on.
     * If empty, uses default retry exceptions from configuration.
     */
    Class<? extends Throwable>[] retryExceptions() default {};

    /**
     * Exceptions to ignore (not trigger circuit breaker or retry).
     */
    Class<? extends Throwable>[] ignoreExceptions() default {
            IllegalArgumentException.class,
            IllegalStateException.class
    };
}
