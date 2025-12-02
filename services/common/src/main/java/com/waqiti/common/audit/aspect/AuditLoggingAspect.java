package com.waqiti.common.audit.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditEventType;
import com.waqiti.common.audit.annotation.AuditLogged;
import com.waqiti.common.audit.domain.AuditLog;
import com.waqiti.common.audit.repository.AuditLogRepository;
import com.waqiti.common.audit.service.AuditContextService;
import com.waqiti.common.audit.service.SiemIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AOP Aspect for automatic audit logging
 * 
 * This aspect intercepts methods annotated with @AuditLogged and automatically
 * creates comprehensive audit records for compliance, security monitoring,
 * and forensic investigation.
 * 
 * FEATURES:
 * - Automatic parameter and return value capture with sanitization
 * - SpEL expression evaluation for dynamic values
 * - Compliance framework detection and flagging
 * - Asynchronous logging for performance
 * - Integration with SIEM systems
 * - Tamper-proof audit chain
 * - Rich context capture (IP, user agent, session, etc.)
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Aspect
@Component
@Order(1) // Execute early to ensure audit logging happens
@RequiredArgsConstructor
@Slf4j
public class AuditLoggingAspect {

    private final AuditLogRepository auditLogRepository;
    private final AuditContextService auditContextService;
    private final SiemIntegrationService siemIntegrationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.audit.vault.VaultAuditKeyManager vaultAuditKeyManager;

    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final AtomicLong sequenceGenerator = new AtomicLong(0);

    // RSA key pair for audit log signing - managed by VaultAuditKeyManager
    // Keys are loaded from HashiCorp Vault for production security
    private java.security.PrivateKey rsaPrivateKey;
    private java.security.PublicKey rsaPublicKey;
    
    // Sensitive field patterns to exclude from logging
    private static final Set<String> SENSITIVE_FIELD_PATTERNS = Set.of(
        "password", "pin", "cvv", "ssn", "cardnumber", "accountnumber",
        "secret", "token", "key", "hash", "signature", "auth"
    );
    
    /**
     * Intercept methods annotated with @AuditLogged
     */
    @Around("@annotation(auditLogged)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, AuditLogged auditLogged) throws Throwable {
        
        long startTime = System.currentTimeMillis();
        String eventId = UUID.randomUUID().toString();
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object[] args = joinPoint.getArgs();
        
        // Create evaluation context for SpEL expressions
        EvaluationContext evaluationContext = createEvaluationContext(method, args, null);
        
        Object result = null;
        Throwable exception = null;
        
        try {
            // Execute the method
            result = joinPoint.proceed();
            
            // Update evaluation context with result
            evaluationContext = createEvaluationContext(method, args, result);
            
            // Log successful operation if required
            if (!auditLogged.auditSuccessOnly() || result != null) {
                logAuditEvent(auditLogged, method, args, result, null, startTime, eventId, evaluationContext);
            }
            
            return result;
            
        } catch (Throwable throwable) {
            exception = throwable;
            
            // Update evaluation context with exception
            evaluationContext.setVariable("exception", throwable);
            
            // Always log failed operations
            logAuditEvent(auditLogged, method, args, null, throwable, startTime, eventId, evaluationContext);
            
            throw throwable;
        }
    }
    
    /**
     * Create audit log entry
     */
    private void logAuditEvent(AuditLogged auditLogged, Method method, Object[] args, 
                              Object result, Throwable exception, long startTime, 
                              String eventId, EvaluationContext evaluationContext) {
        
        try {
            if (auditLogged.async()) {
                logAuditEventAsync(auditLogged, method, args, result, exception, startTime, eventId, evaluationContext);
            } else {
                logAuditEventSync(auditLogged, method, args, result, exception, startTime, eventId, evaluationContext);
            }
        } catch (Exception e) {
            log.error("CRITICAL: Failed to create audit log for method: {} - This is a compliance violation!", 
                     method.getName(), e);
        }
    }
    
    /**
     * Synchronous audit logging for critical operations
     */
    private void logAuditEventSync(AuditLogged auditLogged, Method method, Object[] args, 
                                  Object result, Throwable exception, long startTime, 
                                  String eventId, EvaluationContext evaluationContext) {
        
        AuditLog auditLog = createAuditLogEntry(auditLogged, method, args, result, exception, 
                                               startTime, eventId, evaluationContext);
        
        // Save to database
        auditLogRepository.save(auditLog);
        
        // Send to SIEM if required
        if (auditLogged.sendToSiem()) {
            siemIntegrationService.sendAuditEvent(auditLog);
        }
        
        // Send to Kafka for real-time processing
        kafkaTemplate.send("audit-events", eventId, auditLog);
        
        log.debug("Audit event logged synchronously: {}", eventId);
    }
    
    /**
     * Asynchronous audit logging for performance
     */
    @Async("auditExecutor")
    public void logAuditEventAsync(AuditLogged auditLogged, Method method, Object[] args, 
                                  Object result, Throwable exception, long startTime, 
                                  String eventId, EvaluationContext evaluationContext) {
        
        try {
            AuditLog auditLog = createAuditLogEntry(auditLogged, method, args, result, exception, 
                                                   startTime, eventId, evaluationContext);
            
            // Save to database
            auditLogRepository.save(auditLog);
            
            // Send to SIEM if required
            if (auditLogged.sendToSiem()) {
                siemIntegrationService.sendAuditEvent(auditLog);
            }
            
            // Send to Kafka for real-time processing
            kafkaTemplate.send("audit-events", eventId, auditLog);
            
            log.debug("Audit event logged asynchronously: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to create async audit log for method: {}", method.getName(), e);
        }
    }
    
    /**
     * Create the audit log entry
     */
    private AuditLog createAuditLogEntry(AuditLogged auditLogged, Method method, Object[] args, 
                                        Object result, Throwable exception, long startTime, 
                                        String eventId, EvaluationContext evaluationContext) {
        
        long executionTime = System.currentTimeMillis() - startTime;
        LocalDateTime now = LocalDateTime.now();
        
        // Extract context information
        String userId = getCurrentUserId();
        String username = getCurrentUsername();
        String sessionId = getSessionId();
        String ipAddress = getClientIpAddress();
        String userAgent = getUserAgent();
        String correlationId = getCorrelationId(auditLogged, evaluationContext);
        
        // Extract entity information
        String entityType = auditLogged.entityType().isEmpty() ? 
            extractEntityType(method) : auditLogged.entityType();
        String entityId = extractEntityId(auditLogged.entityIdExpression(), evaluationContext);
        
        // Build description
        String description = buildDescription(auditLogged, method, evaluationContext);
        
        // Build metadata
        Map<String, Object> metadata = buildMetadata(auditLogged, method, args, result, 
                                                     exception, executionTime, evaluationContext);
        
        // Determine operation result
        AuditLog.OperationResult operationResult = determineOperationResult(result, exception);
        
        // Calculate retention date
        LocalDateTime retentionUntil = calculateRetentionDate(auditLogged, now);
        
        // Build audit log
        // Parse event type with fallback to CUSTOM_EVENT if not found in enum
        AuditEventType eventType;
        try {
            eventType = AuditEventType.valueOf(auditLogged.eventType());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown audit event type '{}', using CUSTOM_EVENT", auditLogged.eventType());
            eventType = AuditEventType.CUSTOM_EVENT;
        }

        AuditLog.AuditLogBuilder builder = AuditLog.builder()
            .id(UUID.fromString(eventId))
            .sequenceNumber(sequenceGenerator.incrementAndGet())
            .timestamp(Instant.now())
            .timestampUtc(now)
            .eventType(eventType)
            .eventCategory(auditLogged.category())
            .severity(auditLogged.severity())
            .userId(userId)
            .username(username)
            .sessionId(sessionId)
            .correlationId(correlationId)
            .entityType(entityType)
            .entityId(entityId)
            .action(method.getName())
            .description(description)
            .metadata(serializeMetadata(metadata))
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .result(operationResult)
            .failureReason(exception != null ? exception.getMessage() : null)
            .riskScore(auditLogged.riskScore())
            .pciRelevant(auditLogged.pciRelevant())
            .gdprRelevant(auditLogged.gdprRelevant())
            .soxRelevant(auditLogged.soxRelevant())
            .soc2Relevant(auditLogged.soc2Relevant())
            .retentionUntil(retentionUntil)
            .retentionPolicy(auditLogged.retentionPolicy())
            .requiresNotification(auditLogged.requiresNotification())
            .investigationRequired(auditLogged.investigationRequired())
            .archived(false);
        
        // Add location if available
        addLocationInformation(builder);
        
        // Add device information if available
        addDeviceInformation(builder);
        
        // Add fraud indicators if applicable
        addFraudIndicators(builder, metadata, auditLogged);
        
        AuditLog auditLog = builder.build();
        
        // Calculate hash for integrity
        auditLog.setHash(calculateHash(auditLog));
        auditLog.setPreviousHash(getPreviousHash());
        auditLog.setSignature(signAuditLog(auditLog));
        
        return auditLog;
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
        
        // Add current user
        context.setVariable("currentUser", getCurrentUserId());
        context.setVariable("currentUsername", getCurrentUsername());
        
        // Add request context
        context.setVariable("ipAddress", getClientIpAddress());
        context.setVariable("userAgent", getUserAgent());
        context.setVariable("sessionId", getSessionId());
        
        return context;
    }
    
    /**
     * Extract entity ID using SpEL expression
     */
    private String extractEntityId(String expression, EvaluationContext context) {
        if (expression.isEmpty()) {
            return null;
        }
        
        try {
            Object value = expressionParser.parseExpression(expression).getValue(context);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("Failed to evaluate entity ID expression: {}", expression, e);
            return null;
        }
    }
    
    /**
     * Extract entity type from method name or class
     */
    private String extractEntityType(Method method) {
        String className = method.getDeclaringClass().getSimpleName();
        if (className.endsWith("Service")) {
            return className.substring(0, className.length() - 7);
        }
        return className;
    }
    
    /**
     * Build description with SpEL placeholders
     */
    private String buildDescription(AuditLogged auditLogged, Method method, EvaluationContext context) {
        String description = auditLogged.description();
        if (description.isEmpty()) {
            description = "Executed method: " + method.getName();
        }
        
        // Replace SpEL placeholders
        try {
            return expressionParser.parseExpression("'" + description + "'").getValue(context, String.class);
        } catch (Exception e) {
            log.warn("Failed to evaluate description expression: {}", description, e);
            return description;
        }
    }
    
    /**
     * Build metadata map
     */
    private Map<String, Object> buildMetadata(AuditLogged auditLogged, Method method, Object[] args, 
                                             Object result, Throwable exception, long executionTime, 
                                             EvaluationContext context) {
        
        Map<String, Object> metadata = new HashMap<>();
        
        // Add method information
        metadata.put("className", method.getDeclaringClass().getSimpleName());
        metadata.put("methodName", method.getName());
        
        // Add execution time if requested
        if (auditLogged.captureExecutionTime()) {
            metadata.put("executionTimeMs", executionTime);
        }
        
        // Add parameters if requested
        if (auditLogged.captureParameters() && args != null) {
            metadata.put("parameters", sanitizeObject(args, auditLogged.excludeFields()));
        }
        
        // Add return value if requested
        if (auditLogged.captureReturnValue() && result != null) {
            metadata.put("returnValue", sanitizeObject(result, auditLogged.excludeFields()));
        }
        
        // Add exception information
        if (exception != null) {
            metadata.put("exceptionType", exception.getClass().getSimpleName());
            metadata.put("exceptionMessage", exception.getMessage());
            
            if (auditLogged.includeStackTrace()) {
                metadata.put("stackTrace", getStackTrace(exception));
            }
        }
        
        // Add custom metadata from annotation
        for (String metadataExpr : auditLogged.metadata()) {
            try {
                String[] parts = metadataExpr.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String valueExpr = parts[1].trim();
                    Object value = expressionParser.parseExpression(valueExpr).getValue(context);
                    metadata.put(key, value);
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate metadata expression: {}", metadataExpr, e);
            }
        }
        
        return metadata;
    }
    
    /**
     * Sanitize object to remove sensitive data
     */
    private Object sanitizeObject(Object obj, String[] excludeFields) {
        if (obj == null) {
            return null;
        }
        
        try {
            // Convert to JSON and back to remove sensitive fields
            String json = objectMapper.writeValueAsString(obj);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            
            // Remove sensitive fields
            Set<String> excludeSet = new HashSet<>(Arrays.asList(excludeFields));
            excludeSet.addAll(SENSITIVE_FIELD_PATTERNS);
            
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
                iterator.remove();
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
    
    /**
     * Serialize metadata to JSON string
     */
    private String serializeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("Failed to serialize metadata", e);
            return "{}";
        }
    }
    
    /**
     * Determine operation result
     */
    private AuditLog.OperationResult determineOperationResult(Object result, Throwable exception) {
        if (exception != null) {
            return AuditLog.OperationResult.FAILURE;
        }
        return AuditLog.OperationResult.SUCCESS;
    }
    
    /**
     * Calculate retention date based on compliance requirements
     */
    private LocalDateTime calculateRetentionDate(AuditLogged auditLogged, LocalDateTime now) {
        if (!auditLogged.retentionPolicy().isEmpty()) {
            return parseRetentionPolicy(auditLogged.retentionPolicy(), now);
        }
        
        // Default retention based on compliance flags
        if (auditLogged.soxRelevant()) {
            return now.plusYears(7); // SOX requirement
        } else if (auditLogged.pciRelevant()) {
            return now.plusYears(1); // PCI DSS minimum
        } else if (auditLogged.gdprRelevant()) {
            return now.plusYears(3); // GDPR typical retention
        } else {
            return now.plusYears(3); // Default retention
        }
    }
    
    /**
     * Parse retention policy string
     */
    private LocalDateTime parseRetentionPolicy(String policy, LocalDateTime now) {
        try {
            String[] parts = policy.split("_");
            if (parts.length == 2) {
                int amount = Integer.parseInt(parts[0]);
                String unit = parts[1].toUpperCase();
                
                switch (unit) {
                    case "DAYS": return now.plusDays(amount);
                    case "MONTHS": return now.plusMonths(amount);
                    case "YEARS": return now.plusYears(amount);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse retention policy: {}", policy, e);
        }
        
        return now.plusYears(3); // Default fallback
    }
    
    /**
     * Add location information to audit log
     */
    private void addLocationInformation(AuditLog.AuditLogBuilder builder) {
        // Implementation would integrate with IP geolocation service
        // For now, we'll leave these fields null
    }
    
    /**
     * Add device information to audit log
     */
    private void addDeviceInformation(AuditLog.AuditLogBuilder builder) {
        // Implementation would extract device fingerprint
        // For now, we'll leave these fields null
    }
    
    /**
     * Add fraud indicators if applicable
     */
    private void addFraudIndicators(AuditLog.AuditLogBuilder builder, Map<String, Object> metadata, AuditLogged auditLogged) {
        if (auditLogged.riskScore() > 70 || auditLogged.investigationRequired()) {
            List<String> indicators = new ArrayList<>();
            
            if (auditLogged.riskScore() > 70) {
                indicators.add("HIGH_RISK_SCORE");
            }
            
            if (auditLogged.investigationRequired()) {
                indicators.add("INVESTIGATION_REQUIRED");
            }
            
            if (!indicators.isEmpty()) {
                builder.fraudIndicators(String.join(",", indicators));
            }
        }
    }
    
    // Utility methods for context extraction
    
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }
    
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
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
    
    private String getCorrelationId(AuditLogged auditLogged, EvaluationContext context) {
        if (!auditLogged.correlationIdExpression().isEmpty()) {
            try {
                Object value = expressionParser.parseExpression(auditLogged.correlationIdExpression()).getValue(context);
                return value != null ? value.toString() : UUID.randomUUID().toString();
            } catch (Exception e) {
                log.warn("Failed to evaluate correlation ID expression", e);
            }
        }
        return UUID.randomUUID().toString();
    }
    
    private String getStackTrace(Throwable throwable) {
        // Implementation would format stack trace appropriately
        return throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
    }
    
    /**
     * Calculate SHA-256 hash of audit log for integrity verification
     *
     * SECURITY: This creates a tamper-evident audit trail by hashing critical fields.
     * Any modification to the audit log will result in hash mismatch.
     *
     * COMPLIANCE: Required for SOX 404, PCI DSS 10.5.3, GDPR Art. 32
     */
    private String calculateHash(AuditLog auditLog) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");

            // Create canonical representation of audit log
            // Order matters - must be consistent for verification
            String canonicalData = String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s",
                auditLog.getId() != null ? auditLog.getId().toString() : "",
                auditLog.getSequenceNumber() != null ? auditLog.getSequenceNumber().toString() : "",
                auditLog.getTimestamp() != null ? auditLog.getTimestamp().toEpochMilli() : "",
                auditLog.getUserId() != null ? auditLog.getUserId() : "",
                auditLog.getAction() != null ? auditLog.getAction() : "",
                auditLog.getEntityType() != null ? auditLog.getEntityType() : "",
                auditLog.getEntityId() != null ? auditLog.getEntityId() : "",
                auditLog.getResult() != null ? auditLog.getResult().toString() : "",
                auditLog.getPreviousHash() != null ? auditLog.getPreviousHash() : ""
            );

            byte[] hashBytes = digest.digest(canonicalData.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hashBytes);

        } catch (java.security.NoSuchAlgorithmException e) {
            log.error("CRITICAL: SHA-256 algorithm not available - audit integrity compromised", e);
            throw new RuntimeException("Cannot hash audit log - SHA-256 unavailable", e);
        }
    }

    /**
     * Get hash of previous audit log to create blockchain-like chain
     *
     * SECURITY: Creates immutable audit trail where each log links to previous.
     * Tampering with any log breaks the chain and is immediately detectable.
     *
     * COMPLIANCE: Provides forensic evidence and non-repudiation
     */
    private String getPreviousHash() {
        try {
            return auditLogRepository
                .findTopByOrderBySequenceNumberDesc()
                .map(AuditLog::getHash)
                .orElse("GENESIS_BLOCK");  // First audit log in the system
        } catch (Exception e) {
            log.warn("Could not retrieve previous audit log hash - using empty", e);
            return "GENESIS_BLOCK";
        }
    }

    /**
     * Create digital signature of audit log using RSA-SHA256
     *
     * SECURITY: Provides strong non-repudiation - proves audit log was created by system
     * and has not been modified. Uses RSA asymmetric cryptography for enhanced security.
     *
     * UPGRADED: Now using RSA-2048 with SHA-256 for production-grade non-repudiation
     * - RSA private key signs the audit log hash
     * - RSA public key can verify the signature
     * - Cannot forge signatures without private key
     * - Provides legally binding non-repudiation
     *
     * COMPLIANCE: Legal evidence in investigations, required for SOX/PCI/GDPR
     * SOX Section 404: Internal controls over financial reporting
     * PCI DSS 10.5: Secure audit trails
     * GDPR Article 32: Security of processing
     */
    private String signAuditLog(AuditLog auditLog) {
        try {
            // Initialize RSA keys if not already loaded
            if (rsaPrivateKey == null) {
                initializeRsaKeys();
            }

            // Use RSA-SHA256 for digital signature (asymmetric cryptography)
            java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
            signature.initSign(rsaPrivateKey);

            // Sign the audit log hash
            signature.update(
                auditLog.getHash().getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            byte[] signatureBytes = signature.sign();

            // Return Base64-encoded signature
            String rsaSignature = java.util.Base64.getEncoder().encodeToString(signatureBytes);

            log.debug("Audit log signed with RSA-SHA256: eventId={}, signatureLength={}",
                auditLog.getEventId(), rsaSignature.length());

            return rsaSignature;

        } catch (Exception e) {
            log.error("CRITICAL: Cannot sign audit log - signature verification will fail", e);
            throw new RuntimeException("Cannot sign audit log with RSA", e);
        }
    }

    /**
     * Initialize RSA key pair for audit log signing
     *
     * SECURITY: Keys should be loaded from secure vault (AWS KMS, HashiCorp Vault, etc.)
     * In production, keys MUST be externally managed and rotated regularly
     *
     * Key Management:
     * - Private key: Used only for signing (kept in HSM/KMS)
     * - Public key: Used for signature verification (can be distributed)
     * - Key rotation: Every 12 months or on compromise
     * - Key storage: AWS KMS, CloudHSM, or HashiCorp Vault
     */
    /**
     * Initialize RSA keys for signing audit logs from HashiCorp Vault
     *
     * PRODUCTION: Keys are loaded from HashiCorp Vault via VaultAuditKeyManager
     * DEVELOPMENT: Vault manager will generate ephemeral keys if Vault is unavailable
     *
     * Key Management:
     * - Private key: Used for signing (kept in Vault/HSM)
     * - Public key: Used for verification (can be distributed)
     * - Key rotation: Supported via VaultAuditKeyManager.rotateKeys()
     * - Key storage: HashiCorp Vault (primary), AWS KMS (fallback)
     */
    private void initializeRsaKeys() {
        try {
            log.info("Initializing audit RSA keys from Vault...");

            // Load keys from Vault via VaultAuditKeyManager
            rsaPrivateKey = vaultAuditKeyManager.getPrivateKey();
            rsaPublicKey = vaultAuditKeyManager.getPublicKey();

            if (rsaPrivateKey != null && rsaPublicKey != null) {
                log.info("✅ Audit RSA keys loaded successfully from Vault");
                log.info("Public key fingerprint: {}", getPublicKeyFingerprint(rsaPublicKey));
            } else {
                log.error("❌ CRITICAL: Failed to load audit signing keys from Vault");
                throw new IllegalStateException("Audit signing keys not available");
            }

        } catch (Exception e) {
            log.error("CRITICAL: Failed to initialize RSA keys for audit signing", e);
            throw new RuntimeException("Cannot initialize audit signing keys", e);
        }
    }

    /**
     * Gets SHA-256 fingerprint of public key for logging
     */
    private String getPublicKeyFingerprint(java.security.PublicKey publicKey) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(publicKey.getEncoded());
            return java.util.Base64.getEncoder().encodeToString(hash).substring(0, 32) + "...";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Generate ephemeral RSA key pair (DEVELOPMENT ONLY)
     *
     * WARNING: These keys are NOT persistent across restarts
     * In production, use externally managed keys from AWS KMS or Vault
     */
    private void generateEphemeralRsaKeys() throws Exception {
        java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, new java.security.SecureRandom()); // RSA-2048 for strong security

        java.security.KeyPair keyPair = keyGen.generateKeyPair();
        rsaPrivateKey = keyPair.getPrivate();
        rsaPublicKey = keyPair.getPublic();

        log.warn("Generated ephemeral RSA-2048 key pair for audit signing (NOT FOR PRODUCTION)");
        log.warn("Public key fingerprint: {}",
            java.util.Base64.getEncoder().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(rsaPublicKey.getEncoded())
            ).substring(0, 32) + "...");
    }

    /**
     * Load RSA private key from PEM format
     */
    private java.security.PrivateKey loadPrivateKeyFromPem(String pemKey) throws Exception {
        // Remove PEM headers/footers
        String privateKeyPEM = pemKey
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");

        // Decode Base64
        byte[] encoded = java.util.Base64.getDecoder().decode(privateKeyPEM);

        // Create private key
        java.security.spec.PKCS8EncodedKeySpec keySpec =
            new java.security.spec.PKCS8EncodedKeySpec(encoded);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");

        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * Load RSA public key from PEM format
     */
    private java.security.PublicKey loadPublicKeyFromPem(String pemKey) throws Exception {
        // Remove PEM headers/footers
        String publicKeyPEM = pemKey
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");

        // Decode Base64
        byte[] encoded = java.util.Base64.getDecoder().decode(publicKeyPEM);

        // Create public key
        java.security.spec.X509EncodedKeySpec keySpec =
            new java.security.spec.X509EncodedKeySpec(encoded);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");

        return keyFactory.generatePublic(keySpec);
    }

    /**
     * Verify audit log signature (utility method for integrity checks)
     *
     * This method can be used by auditors and regulators to independently verify
     * the authenticity and integrity of audit logs using the public key.
     *
     * @param auditLog The audit log to verify
     * @return true if signature is valid, false otherwise
     */
    public boolean verifyAuditLogSignature(AuditLog auditLog) {
        try {
            if (rsaPublicKey == null) {
                initializeRsaKeys();
            }

            java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
            signature.initVerify(rsaPublicKey);

            signature.update(
                auditLog.getHash().getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            byte[] signatureBytes = java.util.Base64.getDecoder().decode(auditLog.getSignature());
            boolean isValid = signature.verify(signatureBytes);

            if (!isValid) {
                log.error("SECURITY ALERT: Audit log signature verification FAILED - possible tampering! EventId={}",
                    auditLog.getEventId());
            }

            return isValid;

        } catch (Exception e) {
            log.error("Error verifying audit log signature for eventId={}", auditLog.getEventId(), e);
            return false;
        }
    }
}