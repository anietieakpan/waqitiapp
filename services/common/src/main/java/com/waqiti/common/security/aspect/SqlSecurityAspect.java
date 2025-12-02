package com.waqiti.common.security.aspect;

import com.waqiti.common.security.SqlInjectionValidator;
import com.waqiti.common.security.SqlInjectionMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * SQL Security Aspect for Automated SQL Injection Prevention
 * 
 * Provides automatic validation of all repository method parameters
 * and query methods to prevent SQL injection attacks through:
 * - Automatic parameter validation
 * - Repository method interception
 * - Query annotation validation
 * - Real-time monitoring and alerting
 * - Request context tracking
 * 
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2024-01-16
 */
@Slf4j
@Aspect
@Component
@Order(1) // Execute before transaction aspects
@RequiredArgsConstructor
public class SqlSecurityAspect {
    
    private final SqlInjectionValidator sqlInjectionValidator;
    private final SqlInjectionMonitoringService monitoringService;
    
    /**
     * Pointcut for all repository methods
     */
    @Pointcut("within(@org.springframework.stereotype.Repository *) || " +
              "within(org.springframework.data.repository.Repository+)")
    public void repositoryMethods() {}
    
    /**
     * Pointcut for methods with @Query annotation
     */
    @Pointcut("@annotation(org.springframework.data.jpa.repository.Query)")
    public void queryMethods() {}
    
    /**
     * Pointcut for methods with custom SQL annotations
     */
    @Pointcut("@annotation(com.waqiti.common.security.annotation.ValidateSqlInput)")
    public void validateSqlInputMethods() {}
    
    /**
     * Validate all repository method parameters
     */
    @Before("repositoryMethods()")
    public void validateRepositoryParameters(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        
        // Skip framework methods
        if (isFrameworkMethod(methodName)) {
            return;
        }
        
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return;
        }
        
        // Get method signature for parameter names
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        
        // Get request context
        RequestContext context = getRequestContext();
        
        // Validate each parameter
        for (int i = 0; i < args.length && i < parameters.length; i++) {
            Object arg = args[i];
            Parameter parameter = parameters[i];
            
            if (shouldValidateParameter(arg, parameter)) {
                validateParameter(arg, parameter, method, context);
            }
        }
    }
    
    /**
     * Enhanced validation for @Query annotated methods
     */
    @Around("queryMethods()")
    public Object validateQueryMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        log.debug("SQL_SECURITY: Validating query method: {}", method.getName());
        
        // Get @Query annotation
        org.springframework.data.jpa.repository.Query queryAnnotation = 
            method.getAnnotation(org.springframework.data.jpa.repository.Query.class);
        
        if (queryAnnotation != null) {
            String query = queryAnnotation.value();
            
            // Check for dangerous patterns in the query itself
            if (containsDangerousPattern(query)) {
                log.error("SQL_SECURITY: Dangerous pattern in @Query annotation - method: {}", 
                    method.getName());
                throw new SecurityException("Dangerous SQL pattern detected in query");
            }
            
            // Validate parameters
            validateQueryParameters(joinPoint, method);
        }
        
        return joinPoint.proceed();
    }
    
    /**
     * Validate methods with custom @ValidateSqlInput annotation
     */
    @Around("validateSqlInputMethods()")
    public Object validateSqlInput(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        RequestContext context = getRequestContext();
        
        // Enhanced validation for all string parameters
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String) {
                String input = (String) args[i];
                String paramName = getParameterName(method, i);
                
                boolean isSafe = monitoringService.monitorAndValidate(
                    input, 
                    paramName,
                    context.getUserId(),
                    context.getIpAddress(),
                    context.getEndpoint()
                );
                
                if (!isSafe) {
                    log.error("SQL_SECURITY: SQL injection detected in parameter: {} of method: {}", 
                        paramName, method.getName());
                    throw new SecurityException("SQL injection attempt detected");
                }
            }
        }
        
        return joinPoint.proceed();
    }
    
    /**
     * Validate individual parameter
     */
    private void validateParameter(Object arg, Parameter parameter, Method method, 
                                  RequestContext context) {
        if (arg == null) {
            return;
        }
        
        String paramName = getParameterName(parameter);
        
        // Validate string parameters
        if (arg instanceof String) {
            String input = (String) arg;
            
            // Check if parameter is used in query
            if (isQueryParameter(parameter)) {
                boolean isSafe = monitoringService.monitorAndValidate(
                    input,
                    paramName,
                    context.getUserId(),
                    context.getIpAddress(),
                    context.getEndpoint()
                );
                
                if (!isSafe) {
                    log.error("SQL_SECURITY: SQL injection in query parameter: {} of method: {}", 
                        paramName, method.getName());
                    throw new SecurityException("SQL injection attempt detected in query parameter");
                }
            } else if (shouldPerformBasicValidation(method, parameter)) {
                // Basic validation for non-query parameters
                if (!sqlInjectionValidator.isInputSafe(input, paramName)) {
                    log.warn("SQL_SECURITY: Suspicious input in parameter: {} of method: {}", 
                        paramName, method.getName());
                }
            }
        }
        
        // Validate collection parameters
        if (arg instanceof Iterable) {
            for (Object item : (Iterable<?>) arg) {
                if (item instanceof String) {
                    validateParameter(item, parameter, method, context);
                }
            }
        }
    }
    
    /**
     * Validate query method parameters
     */
    private void validateQueryParameters(ProceedingJoinPoint joinPoint, Method method) {
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = method.getParameters();
        RequestContext context = getRequestContext();
        
        for (int i = 0; i < args.length && i < parameters.length; i++) {
            if (args[i] instanceof String) {
                String input = (String) args[i];
                String paramName = getParameterName(parameters[i]);
                
                // Enhanced validation for query parameters
                boolean isSafe = monitoringService.monitorAndValidate(
                    input,
                    paramName,
                    context.getUserId(),
                    context.getIpAddress(),
                    context.getEndpoint()
                );
                
                if (!isSafe) {
                    throw new SecurityException("SQL injection detected in query parameter: " + paramName);
                }
            }
        }
    }
    
    /**
     * Check if method is a framework method
     */
    private boolean isFrameworkMethod(String methodName) {
        return methodName.startsWith("save") ||
               methodName.startsWith("delete") ||
               methodName.startsWith("flush") ||
               methodName.equals("count") ||
               methodName.equals("existsById");
    }
    
    /**
     * Check if parameter should be validated
     */
    private boolean shouldValidateParameter(Object arg, Parameter parameter) {
        if (arg == null) {
            return false;
        }
        
        // Skip entities and DTOs
        if (arg.getClass().getName().contains("entity") || 
            arg.getClass().getName().contains("dto")) {
            return false;
        }
        
        // Validate strings and collections
        return arg instanceof String || arg instanceof Iterable;
    }
    
    /**
     * Check if parameter is used in query
     */
    private boolean isQueryParameter(Parameter parameter) {
        // Check for @Param annotation
        Param paramAnnotation = parameter.getAnnotation(Param.class);
        return paramAnnotation != null;
    }
    
    /**
     * Check if basic validation should be performed
     */
    private boolean shouldPerformBasicValidation(Method method, Parameter parameter) {
        String methodName = method.getName();
        
        // Methods that commonly use string parameters for queries
        return methodName.contains("find") ||
               methodName.contains("search") ||
               methodName.contains("get") ||
               methodName.contains("query") ||
               methodName.contains("By");
    }
    
    /**
     * Get parameter name
     */
    private String getParameterName(Parameter parameter) {
        Param paramAnnotation = parameter.getAnnotation(Param.class);
        if (paramAnnotation != null) {
            return paramAnnotation.value();
        }
        
        if (parameter.isNamePresent()) {
            return parameter.getName();
        }
        
        return "unknown";
    }
    
    /**
     * Get parameter name by index
     */
    private String getParameterName(Method method, int index) {
        Parameter[] parameters = method.getParameters();
        if (index < parameters.length) {
            return getParameterName(parameters[index]);
        }
        return "param" + index;
    }
    
    /**
     * Check for dangerous patterns in query
     */
    private boolean containsDangerousPattern(String query) {
        if (query == null) {
            return false;
        }
        
        String upperQuery = query.toUpperCase();
        
        // Check for string concatenation in query
        if (query.contains("||") || query.contains("' + ") || query.contains("\" + ")) {
            return true;
        }
        
        // Check for dynamic query building
        if (upperQuery.contains("EXEC") || upperQuery.contains("EXECUTE")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get request context for monitoring
     */
    private RequestContext getRequestContext() {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                return RequestContext.builder()
                    .ipAddress(getClientIpAddress(request))
                    .userId(getUserIdFromRequest(request))
                    .endpoint(request.getRequestURI())
                    .build();
            }
        } catch (Exception e) {
            log.debug("Unable to get request context: {}", e.getMessage());
        }
        
        return RequestContext.builder()
            .ipAddress("unknown")
            .userId("system")
            .endpoint("internal")
            .build();
    }
    
    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Get user ID from request
     */
    private String getUserIdFromRequest(HttpServletRequest request) {
        // Try to get from JWT or session
        String userId = request.getHeader("X-User-Id");
        if (userId != null) {
            return userId;
        }
        
        // Try to get from principal
        if (request.getUserPrincipal() != null) {
            return request.getUserPrincipal().getName();
        }
        
        return "anonymous";
    }
    
    /**
     * Request context data class
     */
    @lombok.Data
    @lombok.Builder
    private static class RequestContext {
        private String ipAddress;
        private String userId;
        private String endpoint;
    }
}