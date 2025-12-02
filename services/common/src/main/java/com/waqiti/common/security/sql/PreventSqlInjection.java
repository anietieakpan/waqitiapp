package com.waqiti.common.security.sql;

import java.lang.annotation.*;

/**
 * Annotation to mark methods that handle user input for SQL queries
 * and require SQL injection prevention measures.
 * 
 * This annotation serves as both documentation and a reminder
 * to developers to implement proper input validation and sanitization.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PreventSqlInjection {
    
    /**
     * Description of the SQL injection prevention measures implemented
     */
    String value() default "";
    
    /**
     * Parameters that need special validation
     */
    String[] validatedParams() default {};
    
    /**
     * Whether this method uses parameterized queries
     */
    boolean parameterized() default true;
    
    /**
     * Whether this method validates user input
     */
    boolean inputValidated() default true;
    
    /**
     * Whether this method sanitizes user input
     */
    boolean inputSanitized() default false;
}