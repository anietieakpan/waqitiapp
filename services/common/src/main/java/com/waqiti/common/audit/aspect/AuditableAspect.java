package com.waqiti.common.audit.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.common.audit.annotation.Auditable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * AOP Aspect for @Auditable annotation that integrates with ComprehensiveAuditService
 * 
 * This aspect provides automated audit logging using the existing ComprehensiveAuditService
 * infrastructure, including MongoDB persistence, Kafka streaming, and tamper detection.
 * 
 * FEATURES:
 * - Integration with ComprehensiveAuditService
 * - MongoDB and Kafka audit trail
 * - Tamper-proof audit chain
 * - Compliance framework flagging (PCI, SOX, GDPR, SOC2, ISO27001)
 * - SpEL expression evaluation for dynamic values
 * - Automatic parameter and return value sanitization
 * - Asynchronous logging for performance
 * - Risk scoring and fraud detection integration
 * - SIEM integration for security events
 * 
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-09-28
 */
@Aspect
@Component
@Order(2) // Execute after existing AuditLoggingAspect to avoid conflicts
@RequiredArgsConstructor
@Slf4j
public class AuditableAspect {
    
    private final ComprehensiveAuditService comprehensiveAuditService;
    private final ObjectMapper objectMapper;
    
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    
    // Sensitive field patterns for automatic sanitization
    private static final Set<String> SENSITIVE_PATTERNS = Set.of(
        "password", "pin", "cvv", "ssn", "cardnumber", "accountnumber",
        "routingnumber", "privatekey", "secret", "token", "auth", "key"
    );
    
    /**
     * Intercept methods annotated with @Auditable
     */
    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        
        long startTime = System.currentTimeMillis();
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object[] args = joinPoint.getArgs();
        
        // Create evaluation context for SpEL expressions
        EvaluationContext evaluationContext = createEvaluationContext(method, args, null);
        
        Object result = null;
        Throwable exception = null;
        boolean success = true;
        
        try {
            // Execute the method
            result = joinPoint.proceed();
            
            // Update evaluation context with result
            evaluationContext = createEvaluationContext(method, args, result);
            
            // Log successful operation if required
            if (!auditable.auditSuccessOnly() || result != null) {
                if (auditable.async()) {
                    logAuditEventAsync(auditable, method, args, result, null, startTime, evaluationContext);
                } else {
                    logAuditEventSync(auditable, method, args, result, null, startTime, evaluationContext);
                }
            }
            
            return result;
            
        } catch (Throwable throwable) {
            exception = throwable;
            success = false;
            
            // Update evaluation context with exception
            evaluationContext.setVariable("exception", throwable);
            evaluationContext.setVariable("success", false);
            
            // Always log failed operations
            if (auditable.async()) {
                logAuditEventAsync(auditable, method, args, null, throwable, startTime, evaluationContext);
            } else {
                logAuditEventSync(auditable, method, args, null, throwable, startTime, evaluationContext);
            }
            
            throw throwable;
        }
    }
    
    /**
     * Synchronous audit logging for critical operations
     */
    private void logAuditEventSync(Auditable auditable, Method method, Object[] args, 
                                  Object result, Throwable exception, long startTime, 
                                  EvaluationContext evaluationContext) {
        try {
            Map<String, Object> auditDetails = buildAuditDetails(auditable, method, args, result, 
                                                                 exception, startTime, evaluationContext);
            
            String userId = getCurrentUserId();
            String action = buildAction(auditable, method, evaluationContext);
            String affectedResource = extractAffectedResource(auditable, evaluationContext);
            
            // Call ComprehensiveAuditService
            comprehensiveAuditService.logAuditEvent(
                auditable.category(),
                userId,
                action,
                auditDetails,
                auditable.severity(),
                affectedResource
            );
            
            // Log critical compliance events separately if needed
            if (isCriticalComplianceEvent(auditable)) {
                comprehensiveAuditService.auditCriticalComplianceEvent(
                    auditable.eventType(),
                    userId,
                    buildDescription(auditable, method, evaluationContext),
                    auditDetails
                );
            }
            
            log.debug("Audit event logged synchronously: {} for method: {}", 
                     auditable.eventType(), method.getName());
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to log audit event synchronously for method: {} - Compliance violation risk!", 
                     method.getName(), e);
        }
    }
    
    /**
     * Asynchronous audit logging for performance
     */
    @Async("auditExecutor")
    public CompletableFuture<Void> logAuditEventAsync(Auditable auditable, Method method, Object[] args, 
                                                     Object result, Throwable exception, long startTime, 
                                                     EvaluationContext evaluationContext) {
        try {
            Map<String, Object> auditDetails = buildAuditDetails(auditable, method, args, result, 
                                                                 exception, startTime, evaluationContext);
            
            String userId = getCurrentUserId();
            String action = buildAction(auditable, method, evaluationContext);
            String affectedResource = extractAffectedResource(auditable, evaluationContext);
            
            // Call ComprehensiveAuditService
            comprehensiveAuditService.logAuditEvent(
                auditable.category(),
                userId,
                action,
                auditDetails,
                auditable.severity(),
                affectedResource
            );
            
            // Log critical compliance events separately if needed
            if (isCriticalComplianceEvent(auditable)) {
                comprehensiveAuditService.auditCriticalComplianceEvent(
                    auditable.eventType(),
                    userId,
                    buildDescription(auditable, method, evaluationContext),
                    auditDetails
                );
            }
            
            log.debug("Audit event logged asynchronously: {} for method: {}", 
                     auditable.eventType(), method.getName());
            
        } catch (Exception e) {
            log.error("Failed to log audit event asynchronously for method: {}", method.getName(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Build comprehensive audit details map
     */
    private Map<String, Object> buildAuditDetails(Auditable auditable, Method method, Object[] args, 
                                                 Object result, Throwable exception, long startTime, 
                                                 EvaluationContext evaluationContext) {
        
        Map<String, Object> details = new HashMap<>();
        
        // Basic method information
        details.put("className", method.getDeclaringClass().getSimpleName());
        details.put("methodName", method.getName());
        details.put("eventType", auditable.eventType());
        details.put("severity", auditable.severity().name());
        
        // Compliance flags
        addComplianceFlags(details, auditable);
        
        // Business context
        if (!auditable.businessContext().isEmpty()) {
            details.put("businessContext", auditable.businessContext());
        }
        
        // Risk scoring
        if (auditable.riskScore() > 0) {
            details.put("riskScore", auditable.riskScore());
        }
        
        // Regulatory tags
        if (auditable.regulatoryTags().length > 0) {
            details.put("regulatoryTags", Arrays.asList(auditable.regulatoryTags()));
        }
        
        // Execution time if requested
        if (auditable.captureExecutionTime()) {
            details.put("executionTimeMs", System.currentTimeMillis() - startTime);
        }
        
        // Parameters if requested
        if (auditable.captureParameters() && args != null) {
            details.put("parameters", sanitizeParameters(args, auditable.excludeFields()));
        }
        
        // Return value if requested
        if (auditable.captureReturnValue() && result != null) {
            details.put("returnValue", sanitizeObject(result, auditable.excludeFields()));
        }
        
        // Exception information
        if (exception != null) {
            details.put("exceptionType", exception.getClass().getSimpleName());
            details.put("exceptionMessage", exception.getMessage());
            details.put("success", false);
        } else {
            details.put("success", true);
        }
        
        // Investigation flag
        if (auditable.requiresInvestigation()) {
            details.put("requiresInvestigation", true);
        }
        
        // Critical operation flag
        if (auditable.criticalOperation()) {
            details.put("criticalOperation", true);
        }
        
        // Custom metadata from SpEL expressions
        addCustomMetadata(details, auditable.metadata(), evaluationContext);
        
        // Correlation ID
        String correlationId = extractCorrelationId(auditable, evaluationContext);
        if (correlationId != null) {
            details.put("correlationId", correlationId);
        }
        
        return details;
    }
    
    /**
     * Add compliance framework flags to audit details
     */
    private void addComplianceFlags(Map<String, Object> details, Auditable auditable) {
        if (auditable.pciRelevant()) {
            details.put("pciRelevant", true);
        }
        if (auditable.soxRelevant()) {
            details.put("soxRelevant", true);
        }
        if (auditable.gdprRelevant()) {
            details.put("gdprRelevant", true);
        }
        if (auditable.soc2Relevant()) {
            details.put("soc2Relevant", true);
        }
        if (auditable.iso27001Relevant()) {
            details.put("iso27001Relevant", true);
        }
    }
    
    /**
     * Add custom metadata from SpEL expressions
     */
    private void addCustomMetadata(Map<String, Object> details, String[] metadataExpressions, 
                                  EvaluationContext evaluationContext) {
        for (String metadataExpr : metadataExpressions) {
            try {
                String[] parts = metadataExpr.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String valueExpr = parts[1].trim();
                    Object value = expressionParser.parseExpression(valueExpr).getValue(evaluationContext);
                    details.put(key, value);
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate metadata expression: {}", metadataExpr, e);
            }
        }
    }
    
    /**
     * Create SpEL evaluation context
     */
    private EvaluationContext createEvaluationContext(Method method, Object[] args, Object result) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // Add method parameters
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            context.setVariable(parameters[i].getName(), args[i]);
            context.setVariable("p" + i, args[i]);
        }
        
        // Add result if available
        if (result != null) {
            context.setVariable("result", result);
        }
        
        // Add current user information
        context.setVariable("currentUser", getCurrentUserId());
        context.setVariable("currentUsername", getCurrentUsername());
        
        // Add request context
        context.setVariable("ipAddress", getClientIpAddress());
        context.setVariable("userAgent", getUserAgent());
        context.setVariable("sessionId", getSessionId());
        context.setVariable("timestamp", LocalDateTime.now());
        
        return context;
    }
    
    /**
     * Build action description
     */
    private String buildAction(Auditable auditable, Method method, EvaluationContext evaluationContext) {
        String description = auditable.description();
        if (description.isEmpty()) {
            description = "Executed method: " + method.getName();
        }
        
        // Evaluate SpEL expressions in description
        try {
            return evaluateSpelExpression(description, evaluationContext);
        } catch (Exception e) {
            log.warn("Failed to evaluate action expression: {}", description, e);
            return description;
        }
    }
    
    /**
     * Build detailed description
     */
    private String buildDescription(Auditable auditable, Method method, EvaluationContext evaluationContext) {
        String description = auditable.description();
        if (description.isEmpty()) {
            description = String.format("Method %s executed in %s", 
                                       method.getName(), 
                                       method.getDeclaringClass().getSimpleName());
        }
        
        try {
            return evaluateSpelExpression(description, evaluationContext);
        } catch (Exception e) {
            log.warn("Failed to evaluate description expression: {}", description, e);
            return description;
        }
    }
    
    /**
     * Extract affected resource identifier
     */
    private String extractAffectedResource(Auditable auditable, EvaluationContext evaluationContext) {
        if (auditable.affectedResourceExpression().isEmpty()) {
            return null;
        }
        
        try {
            Object value = expressionParser.parseExpression(auditable.affectedResourceExpression())
                                         .getValue(evaluationContext);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("Failed to extract affected resource: {}", auditable.affectedResourceExpression(), e);
            return null;
        }
    }
    
    /**
     * Extract correlation ID
     */
    private String extractCorrelationId(Auditable auditable, EvaluationContext evaluationContext) {
        if (auditable.correlationIdExpression().isEmpty()) {
            return null;
        }
        
        try {
            Object value = expressionParser.parseExpression(auditable.correlationIdExpression())
                                         .getValue(evaluationContext);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("Failed to extract correlation ID: {}", auditable.correlationIdExpression(), e);
            return UUID.randomUUID().toString();
        }
    }
    
    /**
     * Evaluate SpEL expression with placeholders
     */
    private String evaluateSpelExpression(String expression, EvaluationContext context) {
        // Replace #{var} placeholders with SpEL syntax
        String spelExpression = expression.replaceAll("#\\{([^}]+)}", "#$1");
        
        try {
            Expression expr = expressionParser.parseExpression("'" + spelExpression + "'");
            return expr.getValue(context, String.class);
        } catch (Exception e) {
            log.debug("SpEL evaluation failed, returning original: {}", expression);
            return expression;
        }
    }
    
    /**
     * Determine if this is a critical compliance event
     */
    private boolean isCriticalComplianceEvent(Auditable auditable) {
        return auditable.requiresInvestigation() ||
               auditable.criticalOperation() ||
               auditable.riskScore() >= 80 ||
               (auditable.pciRelevant() && auditable.severity().ordinal() >= 3); // HIGH or CRITICAL
    }
    
    /**
     * Sanitize parameters array
     */
    private Object sanitizeParameters(Object[] args, String[] excludeFields) {
        if (args == null) return null;
        
        Object[] sanitized = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            sanitized[i] = sanitizeObject(args[i], excludeFields);
        }
        return sanitized;
    }
    
    /**
     * Sanitize object to remove sensitive data
     */
    private Object sanitizeObject(Object obj, String[] excludeFields) {
        if (obj == null) {
            return null;
        }
        
        try {
            // Convert to JSON and back to sanitize
            String json = objectMapper.writeValueAsString(obj);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            
            // Build exclude set
            Set<String> excludeSet = new HashSet<>(Arrays.asList(excludeFields));
            excludeSet.addAll(SENSITIVE_PATTERNS);
            
            sanitizeMap(map, excludeSet);
            
            return map;
        } catch (Exception e) {
            log.warn("Failed to sanitize object", e);
            return "[SANITIZATION_FAILED]";
        }
    }
    
    /**
     * Recursively sanitize map
     */
    @SuppressWarnings("unchecked")
    private void sanitizeMap(Map<String, Object> map, Set<String> excludeFields) {
        if (map == null) return;
        
        Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            String key = entry.getKey().toLowerCase();
            
            // Remove sensitive fields
            if (excludeFields.stream().anyMatch(key::contains)) {
                entry.setValue("[REDACTED]");
                continue;
            }
            
            // Recursively sanitize nested objects
            Object value = entry.getValue();
            if (value instanceof Map) {
                sanitizeMap((Map<String, Object>) value, excludeFields);
            } else if (value instanceof List) {
                sanitizeList((List<Object>) value, excludeFields);
            }
        }
    }
    
    /**
     * Recursively sanitize list
     */
    @SuppressWarnings("unchecked")
    private void sanitizeList(List<Object> list, Set<String> excludeFields) {
        if (list == null) return;
        
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof Map) {
                sanitizeMap((Map<String, Object>) item, excludeFields);
            } else if (item instanceof List) {
                sanitizeList((List<Object>) item, excludeFields);
            }
        }
    }
    
    // Utility methods for context extraction
    
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
    
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
    
    private String getSessionId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getSessionId() : UUID.randomUUID().toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
    
    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty()) {
                    ip = request.getRemoteAddr();
                }
                return ip;
            }
        } catch (Exception e) {
            log.debug("Could not extract IP address", e);
        }
        return "unknown";
    }
    
    private String getUserAgent() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.debug("Could not extract user agent", e);
        }
        return "unknown";
    }
}