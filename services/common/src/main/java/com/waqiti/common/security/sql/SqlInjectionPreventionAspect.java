package com.waqiti.common.security.sql;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Aspect to monitor and prevent SQL injection attempts
 * 
 * This aspect intercepts methods annotated with @PreventSqlInjection
 * and provides additional security monitoring and logging.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class SqlInjectionPreventionAspect {

    private final SqlInjectionPreventionUtils preventionUtils;

    /**
     * Intercept methods annotated with @PreventSqlInjection for monitoring
     */
    @Around("@annotation(preventSqlInjection)")
    public Object monitorSqlInjection(ProceedingJoinPoint joinPoint, PreventSqlInjection preventSqlInjection) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().toShortString();
        
        try {
            // Log method entry
            if (log.isDebugEnabled()) {
                log.debug("SQL Injection Prevention - Executing method: {} with validated params: {}", 
                    methodName, Arrays.toString(preventSqlInjection.validatedParams()));
            }
            
            // Execute the method
            Object result = joinPoint.proceed();
            
            // Log successful execution
            long executionTime = System.currentTimeMillis() - startTime;
            if (log.isDebugEnabled()) {
                log.debug("SQL Injection Prevention - Method {} executed successfully in {} ms", 
                    methodName, executionTime);
            }
            
            return result;
            
        } catch (Exception e) {
            // Log the error
            log.error("SQL Injection Prevention - Error in method {}: {}", methodName, e.getMessage());
            throw e;
        }
    }

    /**
     * Check for SQL injection patterns in string parameters
     */
    @Before("execution(* com.waqiti..repository..*Repository*.*(..)) && args(.., query)")
    public void checkForSqlInjection(JoinPoint joinPoint, Object query) {
        if (query instanceof String) {
            String queryString = (String) query;
            
            if (preventionUtils.containsSqlInjection(queryString)) {
                String userId = getCurrentUserId();
                String methodName = joinPoint.getSignature().toShortString();
                
                // Log potential SQL injection attempt
                preventionUtils.logSqlInjectionAttempt(queryString, methodName, userId);
                
                // In production, you might want to throw an exception here
                log.error("SECURITY ALERT: Potential SQL injection detected in method: {} by user: {}", 
                    methodName, userId);
            }
        }
    }

    /**
     * Monitor exceptions that might indicate SQL injection attempts
     */
    @AfterThrowing(
        pointcut = "execution(* com.waqiti..repository..*Repository*.*(..))",
        throwing = "exception"
    )
    public void handleSqlException(JoinPoint joinPoint, Exception exception) {
        String exceptionMessage = exception.getMessage();
        
        if (exceptionMessage != null && 
            (exceptionMessage.toLowerCase().contains("sql") || 
             exceptionMessage.toLowerCase().contains("syntax") ||
             exceptionMessage.toLowerCase().contains("query"))) {
            
            String userId = getCurrentUserId();
            String methodName = joinPoint.getSignature().toShortString();
            
            log.warn("SQL Exception in method {} by user {}: {}", 
                methodName, userId, exceptionMessage);
            
            // Check if this might be a SQL injection attempt
            Object[] args = joinPoint.getArgs();
            for (Object arg : args) {
                if (arg instanceof String && preventionUtils.containsSqlInjection((String) arg)) {
                    preventionUtils.logSqlInjectionAttempt((String) arg, methodName, userId);
                    break;
                }
            }
        }
    }

    /**
     * Get the current user ID from security context
     */
    private String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.debug("Could not get current user ID: {}", e.getMessage());
        }
        return "anonymous";
    }
}