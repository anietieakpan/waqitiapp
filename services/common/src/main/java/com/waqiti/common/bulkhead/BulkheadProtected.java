package com.waqiti.common.bulkhead;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods for bulkhead protection
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BulkheadProtected {
    
    /**
     * The resource type to use for isolation
     */
    ResourceType resourceType();
    
    /**
     * Operation identifier for monitoring (optional)
     */
    String operationId() default "";
    
    /**
     * Custom timeout in seconds (overrides default)
     */
    int timeoutSeconds() default -1;
}