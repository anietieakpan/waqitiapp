package com.waqiti.common.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark controller methods that require input validation
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidateInput {
    
    /**
     * Whether to validate query parameters for security threats
     */
    boolean validateQueryParams() default true;
    
    /**
     * Whether to validate request headers for security threats
     */
    boolean validateHeaders() default true;
    
    /**
     * Whether to validate request body (handled at controller level)
     */
    boolean validateBody() default true;
    
    /**
     * Custom validation rules to apply
     */
    String[] rules() default {};
}