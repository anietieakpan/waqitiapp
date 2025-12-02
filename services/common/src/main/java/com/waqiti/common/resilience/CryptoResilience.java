package com.waqiti.common.resilience;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to apply resilience patterns to cryptocurrency operations
 * Applies: Rate limiting, Circuit breaker, Retry with exponential backoff
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CryptoResilience {
}