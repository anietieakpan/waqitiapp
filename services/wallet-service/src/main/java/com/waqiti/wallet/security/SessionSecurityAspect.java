/**
 * SECURITY ENHANCEMENT: Session Security Aspect
 * Automatically validates session security for annotated methods
 */
package com.waqiti.wallet.security;

import com.waqiti.common.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * SECURITY-FOCUSED aspect for automatic session security validation
 * Intercepts methods annotated with @RequireSessionSecurity
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionSecurityAspect {
    
    private final SessionSecurityService sessionSecurityService;
    
    /**
     * Pointcut for methods annotated with @RequireSessionSecurity
     */
    @Pointcut("@annotation(com.waqiti.wallet.security.RequireSessionSecurity)")
    public void sessionSecurityRequired() {}
    
    /**
     * Around advice for session security validation
     */
    @Around("sessionSecurityRequired()")
    public Object validateSessionSecurity(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireSessionSecurity annotation = method.getAnnotation(RequireSessionSecurity.class);
        
        if (annotation == null) {
            // Should not happen, but fallback to normal execution
            return joinPoint.proceed();
        }
        
        try {
            UUID userId = SecurityContextUtil.getAuthenticatedUserId();
            String operationType = getOperationType(annotation, method);
            
            log.debug("SECURITY: Validating session security for method {} with operation type {}", 
                    method.getName(), operationType);
            
            // Perform session security validation
            sessionSecurityService.validateSessionSecurity(operationType, userId);
            
            // If validation passes, proceed with method execution
            Object result = joinPoint.proceed();
            
            log.debug("SECURITY: Session security validation passed for method {} user {}", 
                    method.getName(), userId);
            
            return result;
            
        } catch (SessionSecurityService.SessionRegenerationRequiredException e) {
            log.warn("SECURITY: Session regeneration required for method {} - {}", 
                    method.getName(), e.getMessage());
            throw e;
        } catch (SecurityException e) {
            log.warn("SECURITY: Session security validation failed for method {} - {}", 
                    method.getName(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("SECURITY: Unexpected error during session security validation for method {}", 
                    method.getName(), e);
            throw new SecurityException("Session security validation failed", e);
        }
    }
    
    /**
     * Get operation type from annotation or method name
     */
    private String getOperationType(RequireSessionSecurity annotation, Method method) {
        String operationType = annotation.operationType();
        
        if (operationType == null || operationType.trim().isEmpty()) {
            // Use method name as operation type
            operationType = method.getName().toUpperCase();
        }
        
        return operationType;
    }
}