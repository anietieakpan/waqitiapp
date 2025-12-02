package com.waqiti.common.locking;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to automatically handle distributed locking on method execution
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLocked {
    
    /**
     * The lock key. Can contain placeholders like {0}, {1} for method parameters
     * @return lock key
     */
    String key();
    
    /**
     * Maximum time to wait for acquiring the lock in seconds
     * @return wait time in seconds (default 5)
     */
    long waitTimeSeconds() default 5;
    
    /**
     * Maximum time to hold the lock in seconds
     * @return lease time in seconds (default 30)
     */
    long leaseTimeSeconds() default 30;
    
    /**
     * Whether to throw exception if lock cannot be acquired
     * @return true to throw exception, false to return null (default true)
     */
    boolean throwExceptionOnFailure() default true;
}