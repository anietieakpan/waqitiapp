package com.waqiti.common.security.aspect;

import com.waqiti.common.security.AdvancedInputValidationFramework;
import com.waqiti.common.security.AdvancedInputValidationFramework.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * AOP Aspect for automatic input validation
 * Intercepts method calls and validates input parameters based on annotations
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1) // Execute before other security aspects
public class InputValidationAspect {

    private final AdvancedInputValidationFramework validationFramework;

    /**
     * Intercept controller methods with @ValidatedInput annotations
     */
    @Around("@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PatchMapping) || " +
            "execution(* com.waqiti.*.controller.*.*(.., @com.waqiti.common.security.AdvancedInputValidationFramework.ValidatedInput (*), ..))")
    public Object validateControllerInputs(ProceedingJoinPoint joinPoint) throws Throwable {
        return validateMethodInputs(joinPoint, true);
    }

    /**
     * Intercept service methods with @ValidatedInput annotations
     */
    @Around("execution(* com.waqiti.*.service.*.*(..)) && " +
            "args(.., @com.waqiti.common.security.AdvancedInputValidationFramework.ValidatedInput (*), ..)")
    public Object validateServiceInputs(ProceedingJoinPoint joinPoint) throws Throwable {
        return validateMethodInputs(joinPoint, false);
    }

    /**
     * Core validation logic for method parameters
     */
    private Object validateMethodInputs(ProceedingJoinPoint joinPoint, boolean isControllerMethod) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = method.getParameters();

        // Get client context for rate limiting
        String clientId = extractClientId(isControllerMethod);
        
        // Track validation results
        Map<String, ValidationResult> validationResults = new HashMap<>();
        
        // Validate each parameter that has @ValidatedInput annotation
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            ValidatedInput annotation = parameter.getAnnotation(ValidatedInput.class);
            
            if (annotation != null && args[i] instanceof String) {
                String inputValue = (String) args[i];
                String parameterName = parameter.getName();
                
                // Create validation context
                ValidationContext context = createValidationContext(annotation, parameterName, clientId);
                
                // Perform validation
                ValidationResult result = validationFramework.validateInput(inputValue, context);
                validationResults.put(parameterName, result);
                
                // Handle validation failure
                if (!result.isValid()) {
                    handleValidationFailure(parameterName, result, method, clientId);
                    return createValidationErrorResponse(validationResults, isControllerMethod);
                }
                
                // Replace parameter with sanitized value if available
                if (result.getSanitizedValue() != null) {
                    args[i] = result.getSanitizedValue();
                }
            }
        }

        // Record successful validation
        if (!validationResults.isEmpty()) {
            recordValidationSuccess(method, validationResults, clientId);
        }

        // Proceed with original method execution
        return joinPoint.proceed(args);
    }

    /**
     * Validate request body objects with @ValidatedInput fields
     */
    @Around("@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PatchMapping)")
    public Object validateRequestBodies(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        String clientId = extractClientId(true);
        Map<String, ValidationResult> validationResults = new HashMap<>();

        // Validate object fields with @ValidatedInput annotations
        for (Object arg : args) {
            if (arg != null && shouldValidateObject(arg)) {
                Map<String, ValidationResult> objectResults = validateObjectFields(arg, clientId);
                validationResults.putAll(objectResults);
                
                // Check for any validation failures
                boolean hasFailures = objectResults.values().stream()
                    .anyMatch(result -> !result.isValid());
                
                if (hasFailures) {
                    handleObjectValidationFailure(method, objectResults, clientId);
                    return createValidationErrorResponse(validationResults, true);
                }
            }
        }

        return joinPoint.proceed(args);
    }

    /**
     * Create validation context from annotation
     */
    private ValidationContext createValidationContext(ValidatedInput annotation, String parameterName, String clientId) {
        ValidationContext.ValidationContextBuilder builder = ValidationContext.builder()
            .fieldName(parameterName)
            .validationType(annotation.type())
            .required(annotation.required())
            .minLength(annotation.minLength())
            .maxLength(annotation.maxLength())
            .sqlInjectionCheck(annotation.sqlInjectionCheck())
            .xssCheck(annotation.xssCheck())
            .clientId(clientId);

        // Add pattern if specified
        if (!annotation.pattern().isEmpty()) {
            try {
                builder.pattern(Pattern.compile(annotation.pattern()));
            } catch (Exception e) {
                log.warn("Invalid regex pattern in annotation: {}", annotation.pattern());
            }
        }

        // Add allowed values if specified
        if (annotation.allowedValues().length > 0) {
            builder.allowedValues(Set.of(annotation.allowedValues()));
        }

        return builder.build();
    }

    /**
     * Validate fields of an object using reflection
     */
    private Map<String, ValidationResult> validateObjectFields(Object obj, String clientId) {
        Map<String, ValidationResult> results = new HashMap<>();
        
        try {
            java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
            
            for (java.lang.reflect.Field field : fields) {
                ValidatedInput annotation = field.getAnnotation(ValidatedInput.class);
                
                if (annotation != null) {
                    field.setAccessible(true);
                    Object value = field.get(obj);
                    
                    if (value instanceof String) {
                        String stringValue = (String) value;
                        ValidationContext context = createValidationContext(annotation, field.getName(), clientId);
                        ValidationResult result = validationFramework.validateInput(stringValue, context);
                        results.put(field.getName(), result);
                        
                        // Update field with sanitized value if validation passed
                        if (result.isValid() && result.getSanitizedValue() != null) {
                            field.set(obj, result.getSanitizedValue());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error validating object fields", e);
            results.put("object_validation", ValidationResult.failure("Object validation error", ThreatLevel.HIGH));
        }
        
        return results;
    }

    /**
     * Extract client ID for rate limiting and tracking
     */
    private String extractClientId(boolean isControllerMethod) {
        if (!isControllerMethod) {
            return "service_call";
        }

        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                // Try to get client ID from various sources
                String clientId = request.getHeader("X-Client-ID");
                if (clientId != null) return clientId;
                
                String apiKey = request.getHeader("X-API-Key");
                if (apiKey != null) return "api_" + apiKey.substring(0, Math.min(8, apiKey.length()));
                
                String userAgent = request.getHeader("User-Agent");
                if (userAgent != null) return "ua_" + userAgent.hashCode();
                
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Could not extract client ID", e);
        }
        
        return "unknown";
    }

    /**
     * Handle validation failure
     */
    private void handleValidationFailure(String parameterName, ValidationResult result, Method method, String clientId) {
        log.warn("Input validation failed - Method: {}, Parameter: {}, Threat Level: {}, Message: {}, Client: {}", 
            method.getName(), parameterName, result.getThreatLevel(), result.getMessage(), clientId);

        // Record security incident for high-threat violations
        if (result.getThreatLevel() == ThreatLevel.HIGH || result.getThreatLevel() == ThreatLevel.CRITICAL) {
            recordSecurityIncident(method, parameterName, result, clientId);
        }
    }

    /**
     * Handle object validation failure
     */
    private void handleObjectValidationFailure(Method method, Map<String, ValidationResult> results, String clientId) {
        results.entrySet().stream()
            .filter(entry -> !entry.getValue().isValid())
            .forEach(entry -> handleValidationFailure(entry.getKey(), entry.getValue(), method, clientId));
    }

    /**
     * Record successful validation for metrics
     */
    private void recordValidationSuccess(Method method, Map<String, ValidationResult> results, String clientId) {
        log.debug("Input validation successful - Method: {}, Parameters: {}, Client: {}", 
            method.getName(), results.keySet(), clientId);
    }

    /**
     * Record security incident
     */
    private void recordSecurityIncident(Method method, String parameterName, ValidationResult result, String clientId) {
        // This could integrate with a security incident management system
        log.error("SECURITY_INCIDENT - High-threat input validation failure: Method={}, Parameter={}, ThreatLevel={}, Client={}", 
            method.getName(), parameterName, result.getThreatLevel(), clientId);
    }

    /**
     * Create validation error response
     */
    private Object createValidationErrorResponse(Map<String, ValidationResult> results, boolean isControllerMethod) {
        if (isControllerMethod) {
            // For controller methods, return a structured error response
            return createControllerErrorResponse(results);
        } else {
            // For service methods, throw an exception
            throw new IllegalArgumentException("Input validation failed: " + getFirstErrorMessage(results));
        }
    }

    /**
     * Create controller error response
     */
    private Object createControllerErrorResponse(Map<String, ValidationResult> results) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Validation failed");
        errorResponse.put("timestamp", java.time.Instant.now().toString());
        
        Map<String, String> fieldErrors = new HashMap<>();
        results.entrySet().stream()
            .filter(entry -> !entry.getValue().isValid())
            .forEach(entry -> fieldErrors.put(entry.getKey(), entry.getValue().getMessage()));
        
        errorResponse.put("fieldErrors", fieldErrors);
        
        return errorResponse;
    }

    /**
     * Get first error message from validation results
     */
    private String getFirstErrorMessage(Map<String, ValidationResult> results) {
        return results.values().stream()
            .filter(result -> !result.isValid())
            .map(ValidationResult::getMessage)
            .findFirst()
            .orElse("Unknown validation error");
    }

    /**
     * Check if object should be validated
     */
    private boolean shouldValidateObject(Object obj) {
        if (obj == null) return false;
        
        // Skip validation for primitive types, collections, and framework objects
        Class<?> clazz = obj.getClass();
        String packageName = clazz.getPackage() != null ? clazz.getPackage().getName() : "";
        
        return packageName.startsWith("com.waqiti") && 
               !clazz.isPrimitive() && 
               !clazz.getName().startsWith("java.") &&
               !clazz.getName().startsWith("org.springframework.");
    }
}