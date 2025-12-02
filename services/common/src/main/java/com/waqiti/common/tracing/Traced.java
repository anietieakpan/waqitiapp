package com.waqiti.common.tracing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for enabling distributed tracing on methods.
 * This annotation can be used to mark methods that should be traced
 * for monitoring and debugging purposes.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Traced {
    
    /**
     * The name of the operation being traced.
     * This will appear in tracing systems like Jaeger or Zipkin.
     */
    String operationName() default "";
    
    /**
     * Alias for operationName for backward compatibility.
     */
    String operation() default "";
    
    /**
     * The business operation category.
     * Used for grouping related operations in monitoring dashboards.
     */
    String businessOperation() default "";
    
    /**
     * The priority level of this traced operation.
     * Used for filtering and alerting in monitoring systems.
     */
    TracingPriority priority() default TracingPriority.MEDIUM;
    
    /**
     * Whether to include method parameters in the trace span.
     */
    boolean includeParameters() default false;
    
    /**
     * Whether to include the return value in the trace span.
     */
    boolean includeResult() default false;
    
    /**
     * Custom tags to be added to the trace span.
     * Format: ["key1=value1", "key2=value2"]
     */
    String[] tags() default {};
    
    /**
     * Enum defining the priority levels for traced operations.
     */
    enum TracingPriority {
        /**
         * Critical priority - always traced, used for essential business operations
         */
        CRITICAL,
        
        /**
         * High priority - traced in production, important operations
         */
        HIGH,
        
        /**
         * Medium priority - standard operations
         */
        MEDIUM,
        
        /**
         * Low priority - may be sampled or disabled in production
         */
        LOW
    }
}