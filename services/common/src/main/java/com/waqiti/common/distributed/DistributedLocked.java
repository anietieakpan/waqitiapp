package com.waqiti.common.distributed;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for declarative distributed locking.
 * Methods annotated with @DistributedLocked will automatically acquire a distributed lock
 * before execution and release it after completion.
 * 
 * Example usage:
 * <pre>
 * {@code
 * @DistributedLocked(
 *     key = "payment:{0}:{1}",  // Lock key using method parameters
 *     waitTime = 10,           // Wait up to 10 seconds
 *     leaseTime = 60           // Hold lock for 60 seconds max
 * )
 * public PaymentResult processPayment(UUID userId, BigDecimal amount) {
 *     // This method will be protected by distributed lock
 * }
 * }
 * </pre>
 * 
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2025-09-16
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLocked {
    
    /**
     * Lock key pattern. Can use SpEL expressions and parameter placeholders.
     * 
     * Examples:
     * - "payment:{0}" - Uses first parameter
     * - "transfer:{0}:{1}" - Uses first and second parameters
     * - "wallet:#{#userId}" - Uses SpEL expression
     * - "user:#{@userService.getCurrentUserId()}" - Calls service method
     * 
     * @return Lock key pattern
     */
    String key();
    
    /**
     * Maximum time to wait for lock acquisition in seconds.
     * Default: 10 seconds
     * 
     * @return Wait time in seconds
     */
    int waitTime() default 10;
    
    /**
     * Maximum time to hold the lock in seconds.
     * Default: 30 seconds
     * 
     * @return Lease time in seconds
     */
    int leaseTime() default 30;
    
    /**
     * Whether to fail if lock cannot be acquired.
     * If false, method will execute without lock (degraded mode).
     * Default: true
     * 
     * @return true to fail on lock acquisition timeout
     */
    boolean failOnTimeout() default true;
    
    /**
     * Lock scope for multiple parameter locks.
     * When locking on multiple parameters, defines how to combine them.
     * 
     * Examples:
     * - SINGLE: Single lock for all parameters
     * - MULTIPLE: Multiple locks (one per parameter)
     * - ORDERED: Multiple locks acquired in sorted order to prevent deadlocks
     * 
     * @return Lock scope strategy
     */
    LockScope scope() default LockScope.SINGLE;
    
    /**
     * Custom exception to throw on lock timeout.
     * Must have a constructor that accepts String message.
     * Default: LockTimeoutException
     * 
     * @return Exception class to throw
     */
    Class<? extends RuntimeException> timeoutException() default LockTimeoutException.class;
    
    /**
     * Lock scope enumeration.
     */
    enum LockScope {
        /** Single lock for all parameters */
        SINGLE,
        
        /** Multiple locks (one per parameter) */
        MULTIPLE,
        
        /** Multiple locks acquired in sorted order */
        ORDERED
    }
}