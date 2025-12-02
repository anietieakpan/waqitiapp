package com.waqiti.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.annotation.Audited;
import com.waqiti.common.audit.dto.AuditRequestDTOs.TransactionAuditRequest;
import com.waqiti.common.events.model.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;

/**
 * AOP aspect for method-level audit logging
 * Complements the HTTP interceptor for non-web operations
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * Intercept methods annotated with @Audited
     */
    @Around("@annotation(audited)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        Exception exception = null;
        
        // Extract method information
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getName();
        String className = method.getDeclaringClass().getSimpleName();
        
        // Prepare audit context
        Map<String, Object> auditContext = new HashMap<>();
        auditContext.put("class", className);
        auditContext.put("method", methodName);
        
        try {
            // Log before execution if configured
            if (audited.logRequest()) {
                logMethodEntry(joinPoint, audited, auditContext);
            }
            
            // Execute the method
            result = joinPoint.proceed();
            
            // Log successful execution
            logMethodSuccess(joinPoint, audited, result, startTime, auditContext);
            
            return result;
            
        } catch (Exception e) {
            exception = e;
            
            // Log failure
            logMethodFailure(joinPoint, audited, e, startTime, auditContext);
            
            throw e;
            
        } finally {
            // Log data access if applicable
            if (audited.logDataAccess()) {
                logDataAccessEvent(joinPoint, audited, result, exception);
            }
        }
    }

    /**
     * Log method entry with parameters
     */
    private void logMethodEntry(ProceedingJoinPoint joinPoint, Audited audited, 
                               Map<String, Object> context) {
        try {
            Object[] args = joinPoint.getArgs();
            String[] paramNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
            
            Map<String, Object> parameters = new HashMap<>();
            for (int i = 0; i < args.length && i < paramNames.length; i++) {
                Object value = args[i];
                String paramName = paramNames[i];
                
                if (audited.maskSensitiveData() && isSensitiveParameter(paramName)) {
                    parameters.put(paramName, "***MASKED***");
                } else if (audited.includePayload()) {
                    parameters.put(paramName, sanitizeValue(value));
                } else {
                    parameters.put(paramName, value != null ? value.getClass().getSimpleName() : "null");
                }
            }
            
            context.put("parameters", parameters);
            
        } catch (Exception e) {
            log.warn("Failed to log method entry", e);
        }
    }

    /**
     * Log successful method execution
     */
    private void logMethodSuccess(ProceedingJoinPoint joinPoint, Audited audited, 
                                 Object result, long startTime, Map<String, Object> context) {
        try {
            long duration = System.currentTimeMillis() - startTime;
            context.put("duration", duration);
            context.put("success", true);
            
            if (audited.logResponse() && result != null) {
                if (audited.includePayload()) {
                    context.put("result", sanitizeValue(result));
                } else {
                    context.put("resultType", result.getClass().getSimpleName());
                }
            }
            
            // Determine event type
            String eventType = !audited.eventType().isEmpty() 
                ? audited.eventType() 
                : inferEventType(joinPoint);
            
            // Create audit event based on category
            if ("FINANCIAL".equals(audited.eventCategory())) {
                logFinancialAuditEvent(joinPoint, audited, context, result);
            } else if ("SECURITY".equals(audited.eventCategory())) {
                logSecurityAuditEvent(joinPoint, audited, context, true);
            } else {
                // Generic audit event
                AuditEvent event = AuditEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .timestamp(java.time.Instant.now())
                        .eventType(eventType)
                        .eventCategory(audited.eventCategory())
                        .eventName(joinPoint.getSignature().getName())
                        .action(audited.action())
                        .success(true)
                        .duration(duration)
                        .metadata(context)
                        .riskLevel(audited.riskLevel().isEmpty() 
                            ? "LOW" 
                            : audited.riskLevel())
                        .userId(extractUserId())
                        .outcome("SUCCESS")
                        .complianceCategory(audited.eventCategory())
                        .retainIndefinitely(audited.alwaysLog())
                        .build();
                
                auditService.saveAuditEvent(event);
            }
            
        } catch (Exception e) {
            log.error("Failed to log method success audit", e);
        }
    }

    /**
     * Log method failure
     */
    private void logMethodFailure(ProceedingJoinPoint joinPoint, Audited audited, 
                                 Exception exception, long startTime, Map<String, Object> context) {
        try {
            long duration = System.currentTimeMillis() - startTime;
            context.put("duration", duration);
            context.put("success", false);
            context.put("errorType", exception.getClass().getSimpleName());
            context.put("errorMessage", exception.getMessage());
            
            String eventType = !audited.eventType().isEmpty() 
                ? audited.eventType() 
                : inferEventType(joinPoint);
            
            if ("SECURITY".equals(audited.eventCategory())) {
                logSecurityAuditEvent(joinPoint, audited, context, false);
            } else {
                AuditEvent event = AuditEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .timestamp(java.time.Instant.now())
                        .eventType(eventType)
                        .eventCategory(audited.eventCategory())
                        .eventName(joinPoint.getSignature().getName())
                        .action(audited.action())
                        .success(false)
                        .errorCode(exception.getClass().getSimpleName())
                        .errorMessage(exception.getMessage())
                        .duration(duration)
                        .metadata(context)
                        .riskLevel("HIGH")
                        .userId(extractUserId())
                        .outcome("FAILURE")
                        .complianceCategory(audited.eventCategory())
                        .retainIndefinitely(true) // Always retain failures
                        .build();
                
                auditService.saveAuditEvent(event);
            }
            
        } catch (Exception e) {
            log.error("Failed to log method failure audit", e);
        }
    }

    /**
     * Log data access event
     */
    private void logDataAccessEvent(ProceedingJoinPoint joinPoint, Audited audited, 
                                   Object result, Exception exception) {
        try {
            // Extract resource information from method parameters or result
            String resourceType = !audited.resourceType().isEmpty() 
                ? audited.resourceType() 
                : inferResourceType(joinPoint);
            
            String resourceId = extractResourceId(joinPoint, result);
            
            if (resourceId != null) {
                String action = inferDataAccessAction(joinPoint);
                
                // Create a proper DataAccessAuditLog
                DataAccessAuditLog dataLog = DataAccessAuditLog.builder()
                    .userId(extractUserId())
                    .tableName(resourceType)
                    .operation(action)
                    .recordId(resourceId)
                    .timestamp(java.time.LocalDateTime.now())
                    .build();
                
                auditService.logDataAccess(dataLog);
            }
            
        } catch (Exception e) {
            log.warn("Failed to log data access event", e);
        }
    }

    /**
     * Log financial audit event
     */
    private void logFinancialAuditEvent(ProceedingJoinPoint joinPoint, Audited audited,
                                       Map<String, Object> context, Object result) {
        try {
            // Extract transaction details from parameters or result
            TransactionAuditRequest transactionAudit = extractTransactionDetails(joinPoint, result);
            
            if (transactionAudit != null) {
                auditService.logTransaction(transactionAudit);
            }
            
        } catch (Exception e) {
            log.warn("Failed to log financial audit event", e);
        }
    }

    /**
     * Log security audit event
     */
    private void logSecurityAuditEvent(ProceedingJoinPoint joinPoint, Audited audited,
                                      Map<String, Object> context, boolean success) {
        try {
            String action = joinPoint.getSignature().getName();
            
            SecurityAuditLog securityLog = SecurityAuditLog.builder()
                    .userId(extractUserId())
                    .eventType("SECURITY_" + action.toUpperCase())
                    .category(success ? SecurityAuditLog.SecurityEventCategory.AUTHORIZATION 
                                     : SecurityAuditLog.SecurityEventCategory.POLICY_VIOLATION)
                    .severity(success ? SecurityAuditLog.SecuritySeverity.LOW 
                                     : SecurityAuditLog.SecuritySeverity.HIGH)
                    .description("Method: " + action)
                    .action(action)
                    .outcome(success ? SecurityAuditLog.SecurityOutcome.SUCCESS 
                                    : SecurityAuditLog.SecurityOutcome.FAILURE)
                    .metadata(context)
                    .requiresAlert(!success)
                    .timestamp(java.time.LocalDateTime.now())
                    .build();
            
            auditService.logSecurityEvent(securityLog);
            
        } catch (Exception e) {
            log.warn("Failed to log security audit event", e);
        }
    }

    // Helper methods

    private String inferEventType(ProceedingJoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        
        if (methodName.startsWith("create") || methodName.startsWith("add")) {
            return "CREATE";
        } else if (methodName.startsWith("update") || methodName.startsWith("modify")) {
            return "UPDATE";
        } else if (methodName.startsWith("delete") || methodName.startsWith("remove")) {
            return "DELETE";
        } else if (methodName.startsWith("get") || methodName.startsWith("find") || 
                   methodName.startsWith("load")) {
            return "READ";
        } else if (methodName.contains("login") || methodName.contains("authenticate")) {
            return "AUTHENTICATION";
        } else if (methodName.contains("authorize") || methodName.contains("permission")) {
            return "AUTHORIZATION";
        }
        
        return "OPERATION";
    }

    private String inferResourceType(ProceedingJoinPoint joinPoint) {
        // Infer from class name
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        if (className.contains("User")) return "USER";
        if (className.contains("Account")) return "ACCOUNT";
        if (className.contains("Payment")) return "PAYMENT";
        if (className.contains("Transaction")) return "TRANSACTION";
        if (className.contains("Card")) return "CARD";
        
        // Infer from method parameters
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg != null) {
                String argType = arg.getClass().getSimpleName();
                if (argType.endsWith("Entity") || argType.endsWith("DTO")) {
                    return argType.replace("Entity", "").replace("DTO", "").toUpperCase();
                }
            }
        }
        
        return "UNKNOWN";
    }

    private String inferDataAccessAction(ProceedingJoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        
        if (methodName.startsWith("get") || methodName.startsWith("find") || 
            methodName.startsWith("load") || methodName.startsWith("read")) {
            return "READ";
        } else if (methodName.startsWith("save") || methodName.startsWith("update") || 
                   methodName.startsWith("modify")) {
            return "WRITE";
        } else if (methodName.startsWith("delete") || methodName.startsWith("remove")) {
            return "DELETE";
        }
        
        return "ACCESS";
    }

    private String extractResourceId(ProceedingJoinPoint joinPoint, Object result) {
        // Try to extract from method parameters
        Object[] args = joinPoint.getArgs();
        String[] paramNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
        
        for (int i = 0; i < args.length && i < paramNames.length; i++) {
            if ("id".equalsIgnoreCase(paramNames[i]) || 
                paramNames[i].toLowerCase().endsWith("id")) {
                return String.valueOf(args[i]);
            }
        }
        
        // Try to extract from result
        if (result != null) {
            try {
                // Use reflection to find getId() method
                Method getIdMethod = result.getClass().getMethod("getId");
                Object id = getIdMethod.invoke(result);
                if (id != null) {
                    return String.valueOf(id);
                }
            } catch (Exception e) {
                // Ignore - no getId method
            }
        }
        
        return null;
    }

    private boolean isSensitiveParameter(String paramName) {
        String lower = paramName.toLowerCase();
        return lower.contains("password") || lower.contains("pin") || 
               lower.contains("ssn") || lower.contains("cvv") ||
               lower.contains("secret") || lower.contains("token");
    }

    private Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }
        
        // Don't log large objects
        String valueStr = value.toString();
        if (valueStr.length() > 1000) {
            return value.getClass().getSimpleName() + "[truncated]";
        }
        
        // Mask sensitive data in strings
        if (value instanceof String) {
            return maskSensitiveData((String) value);
        }
        
        return value;
    }

    private String maskSensitiveData(String data) {
        return data.replaceAll("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b", "****-****-****-****")
                   .replaceAll("\\b\\d{3}-\\d{2}-\\d{4}\\b", "***-**-****")
                   .replaceAll("password[\"'\\s]*[:=][\"'\\s]*[^\"'\\s,}]+", "password=***MASKED***");
    }

    private boolean isPiiResource(String resourceType) {
        return Set.of("USER", "ACCOUNT", "CARD", "KYC", "PROFILE").contains(resourceType.toUpperCase());
    }

    private boolean containsPiiData(Object data) {
        if (data == null) {
            return false;
        }
        
        // Check for common PII field names
        String dataStr = data.toString().toLowerCase();
        return dataStr.contains("ssn") || dataStr.contains("dateofbirth") || 
               dataStr.contains("accountnumber") || dataStr.contains("cardnumber") ||
               dataStr.contains("email") || dataStr.contains("phone");
    }

    private TransactionAuditRequest extractTransactionDetails(ProceedingJoinPoint joinPoint, 
                                                            Object result) {
        // Implementation would extract transaction details from parameters or result
        // This is a simplified example
        return null;
    }
    
    /**
     * Extract the current user ID from the security context
     */
    private String extractUserId() {
        try {
            org.springframework.security.core.Authentication auth = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                return auth.getName();
            }
        } catch (Exception e) {
            log.debug("Could not extract user ID from security context", e);
        }
        return "anonymous";
    }
}