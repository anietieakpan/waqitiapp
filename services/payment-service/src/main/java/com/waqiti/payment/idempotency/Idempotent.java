package com.waqiti.payment.idempotency;

import java.lang.annotation.*;

/**
 * Annotation to mark endpoints that require idempotency protection
 * Prevents duplicate processing of payment operations
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    
    /**
     * Whether idempotency key is required or optional
     */
    boolean required() default true;
    
    /**
     * TTL for idempotency records in hours
     */
    int ttlHours() default 24;
    
    /**
     * Maximum number of retries for failed requests
     */
    int maxRetries() default 3;
    
    /**
     * Lock timeout in seconds
     */
    int lockTimeout() default 30;
    
    /**
     * Whether to persist idempotency records to database
     */
    boolean persist() default true;
    
    /**
     * Custom key generator class
     */
    Class<? extends IdempotencyKeyGenerator> keyGenerator() default DefaultIdempotencyKeyGenerator.class;
    
    /**
     * HTTP methods to apply idempotency to
     */
    String[] methods() default {"POST", "PUT", "PATCH"};
    
    /**
     * Description of the idempotent operation
     */
    String description() default "";
}

