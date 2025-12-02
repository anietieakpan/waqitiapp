package com.waqiti.common.audit;

import com.waqiti.common.events.model.AuditEvent;
import com.waqiti.common.security.SecurityContextUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Secure audit logging service that logs events without exposing PII
 * Implements comprehensive audit trail with data protection compliance
 */
@Service
@RequiredArgsConstructor
public class SecureAuditLogger {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SecureAuditLogger.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${audit.retention.days:2555}") // 7 years default
    private int retentionDays;

    @Value("${audit.encryption.enabled:true}")
    private boolean encryptionEnabled;

    // PII patterns to mask in logs
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );
    
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "\\b(?:\\+?1[-.]?)?\\(?([0-9]{3})\\)?[-.]?([0-9]{3})[-.]?([0-9]{4})\\b"
    );
    
    private static final Pattern SSN_PATTERN = Pattern.compile(
        "\\b\\d{3}-?\\d{2}-?\\d{4}\\b"
    );
    
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
        "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"
    );

    // Sensitive field names to mask
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "password", "ssn", "socialSecurityNumber", "creditCard", "cardNumber",
        "accountNumber", "routingNumber", "pin", "cvv", "securityCode",
        "firstName", "lastName", "fullName", "dateOfBirth", "address",
        "phoneNumber", "email", "personalEmail", "workEmail"
    );

    /**
     * Logs a financial transaction event
     */
    public void logFinancialEvent(FinancialEventType eventType, UUID entityId, 
                                 Map<String, Object> metadata) {
        AuditEvent event = AuditEvent.builder()
            .eventType("FINANCIAL")
            .action(eventType.name())
            .entityType("FINANCIAL_TRANSACTION")
            .entityId(entityId.toString())
            .metadata(sanitizeMetadata(metadata))
            .build();
            
        logEvent(event);
    }

    /**
     * Logs a security event
     */
    public void logSecurityEvent(SecurityEventType eventType, String details) {
        Map<String, Object> detailsMap = new HashMap<>();
        detailsMap.put("message", sanitizeString(details));
        
        AuditEvent event = AuditEvent.builder()
            .eventType("SECURITY")
            .action(eventType.name())
            .entityType("SECURITY")
            .metadata(detailsMap)
            .build();
            
        logEvent(event);
    }

    /**
     * Logs a user action event
     */
    public void logUserAction(UserActionType actionType, UUID targetUserId, 
                             Map<String, Object> metadata) {
        AuditEvent event = AuditEvent.builder()
            .eventType("USER_ACTION")
            .action(actionType.name())
            .entityType("USER")
            .entityId(targetUserId != null ? targetUserId.toString() : null)
            .metadata(sanitizeMetadata(metadata))
            .build();
            
        logEvent(event);
    }

    /**
     * Logs a data access event
     */
    public void logDataAccess(DataAccessType accessType, String resourceType, 
                             String resourceId, boolean authorized) {
        AuditEvent event = AuditEvent.builder()
            .eventType("DATA_ACCESS")
            .action(accessType.name())
            .entityType(resourceType)
            .entityId(resourceId)
            .authorized(authorized)
            .build();
            
        logEvent(event);
    }

    /**
     * Logs a compliance event
     */
    public void logComplianceEvent(ComplianceEventType eventType, UUID entityId,
                                  String complianceRule, Map<String, Object> metadata) {
        AuditEvent event = AuditEvent.builder()
            .eventType("COMPLIANCE")
            .action(eventType.name())
            .entityType("COMPLIANCE_CHECK")
            .entityId(entityId != null ? entityId.toString() : null)
            .complianceRule(complianceRule)
            .metadata(sanitizeMetadata(metadata))
            .build();
            
        logEvent(event);
    }

    /**
     * Core event logging method
     */
    private void logEvent(AuditEvent event) {
        try {
            // Enrich event with context
            enrichEventWithContext(event);
            
            // Generate unique event ID
            event.setEventId(UUID.randomUUID().toString());
            event.setTimestamp(Instant.now());
            
            // Calculate risk score
            event.setRiskScore(calculateRiskScore(event));
            
            // Convert to JSON and store
            String eventJson = objectMapper.writeValueAsString(event);
            
            // Store in Redis with TTL
            String key = "audit:" + event.getTimestamp().toEpochMilli() + ":" + event.getEventId();
            redisTemplate.opsForValue().set(key, eventJson, retentionDays, TimeUnit.DAYS);
            
            // Log to application logs (structured)
            logStructuredEvent(event);
            
            // Send to external audit system if configured
            sendToExternalAuditSystem(event);
            
        } catch (Exception e) {
            // Critical: Never fail business operations due to audit logging
            log.error("Failed to log audit event: {}", event.getAction(), e);
        }
    }

    /**
     * Enriches event with security context and request information
     */
    private void enrichEventWithContext(AuditEvent event) {
        try {
            // Add user context
            try {
                UUID userId = SecurityContextUtil.getAuthenticatedUserId();
                event.setUserId(userId.toString());
                event.setUserRoles(new ArrayList<>(SecurityContextUtil.getUserRoles()));
            } catch (Exception e) {
                // User might not be authenticated (e.g., public endpoints)
                event.setUserId("ANONYMOUS");
            }
            
            // Add request context
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                event.setIpAddress(extractClientIpAddress(request));
                event.setUserAgent(sanitizeString(request.getHeader("User-Agent")));
                event.setRequestMethod(request.getMethod());
                event.setRequestUri(sanitizeUri(request.getRequestURI()));
                event.setSessionId(generateSessionHash(request.getSession().getId()));
            }
            
            // Add service context
            event.setServiceName(getServiceName());
            event.setServiceVersion(getServiceVersion());
            
        } catch (Exception e) {
            log.warn("Failed to enrich audit event with context", e);
        }
    }

    /**
     * Sanitizes metadata to remove PII
     */
    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        
        Map<String, Object> sanitized = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (SENSITIVE_FIELDS.contains(key.toLowerCase())) {
                sanitized.put(key, maskValue(String.valueOf(value)));
            } else if (value instanceof String) {
                sanitized.put(key, sanitizeString((String) value));
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                sanitized.put(key, sanitizeMetadata(nestedMap));
            } else {
                sanitized.put(key, value);
            }
        }
        
        return sanitized;
    }

    /**
     * Sanitizes strings to remove PII patterns
     */
    private String sanitizeString(String input) {
        if (input == null) {
            return null;
        }
        
        String sanitized = input;
        
        // Mask email addresses
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("****@****.***");
        
        // Mask phone numbers
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("***-***-****");
        
        // Mask SSNs
        sanitized = SSN_PATTERN.matcher(sanitized).replaceAll("***-**-****");
        
        // Mask credit card numbers
        sanitized = CREDIT_CARD_PATTERN.matcher(sanitized).replaceAll("****-****-****-****");
        
        return sanitized;
    }

    /**
     * Masks sensitive values while preserving some information for debugging
     */
    private String maskValue(String value) {
        if (value == null || value.length() <= 2) {
            return "****";
        }
        
        if (value.length() <= 4) {
            return "****";
        }
        
        // Show first and last character with asterisks in between
        return value.charAt(0) + "***" + value.charAt(value.length() - 1);
    }

    /**
     * Sanitizes URIs to remove sensitive path parameters
     */
    private String sanitizeUri(String uri) {
        if (uri == null) {
            return null;
        }
        
        // Remove common sensitive patterns from URIs
        return uri.replaceAll("/users/[0-9a-f-]{36}", "/users/{userId}")
                 .replaceAll("/accounts/[0-9]+", "/accounts/{accountId}")
                 .replaceAll("/transactions/[0-9a-f-]{36}", "/transactions/{transactionId}")
                 .replaceAll("\\?.*", ""); // Remove query parameters
    }

    /**
     * Extracts client IP address handling proxy headers
     */
    private String extractClientIpAddress(HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP", 
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Take first IP if comma-separated
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Generates anonymous session hash for tracking without exposing session ID
     */
    private String generateSessionHash(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        
        return "session_" + Math.abs(sessionId.hashCode());
    }

    /**
     * Calculates risk score based on event characteristics
     */
    private int calculateRiskScore(AuditEvent event) {
        int riskScore = 0;
        
        // Base score by event type
        String eventType = event.getEventType();
        if (eventType != null) {
            switch (eventType) {
                case "SECURITY" -> riskScore += 8;
                case "FINANCIAL" -> riskScore += 6;
                case "COMPLIANCE" -> riskScore += 4;
                case "DATA_ACCESS" -> riskScore += 2;
                default -> riskScore += 1;
            }
        }
        
        // Increase for unauthorized access
        if (Boolean.FALSE.equals(event.getAuthorized())) {
            riskScore += 5;
        }
        
        // Increase for high-value actions
        if (event.getAction() != null) {
            String action = event.getAction().toLowerCase();
            if (action.contains("transfer") || action.contains("payment") || action.contains("withdrawal")) {
                riskScore += 3;
            }
            if (action.contains("admin") || action.contains("delete") || action.contains("modify")) {
                riskScore += 2;
            }
        }
        
        return Math.min(riskScore, 10); // Cap at 10
    }

    /**
     * Logs structured event to application logs
     */
    private void logStructuredEvent(AuditEvent event) {
        log.info("AUDIT_EVENT: eventId={}, type={}, action={}, user={}, entity={}, authorized={}, riskScore={}", 
            event.getEventId(),
            event.getEventType(),
            event.getAction(),
            event.getUserId(),
            event.getEntityId(),
            event.getAuthorized(),
            event.getRiskScore()
        );
    }

    /**
     * Sends to external audit system (placeholder for integration)
     */
    private void sendToExternalAuditSystem(AuditEvent event) {
        // Placeholder for integration with external audit systems
        // (e.g., Splunk, ELK Stack, SIEM systems)
        log.debug("Would send audit event to external system: {}", event.getEventId());
    }

    /**
     * Gets service name from configuration or system property
     */
    private String getServiceName() {
        return System.getProperty("spring.application.name", "waqiti-service");
    }

    /**
     * Gets service version from configuration
     */
    private String getServiceVersion() {
        return System.getProperty("app.version", "1.0.0");
    }

    // Enum definitions for event types
    public enum EventType {
        FINANCIAL, SECURITY, USER_ACTION, DATA_ACCESS, COMPLIANCE, SYSTEM
    }

    public enum FinancialEventType {
        PAYMENT_INITIATED, PAYMENT_COMPLETED, PAYMENT_FAILED,
        TRANSFER_INITIATED, TRANSFER_COMPLETED, TRANSFER_FAILED,
        DEPOSIT_INITIATED, DEPOSIT_COMPLETED, DEPOSIT_FAILED,
        WITHDRAWAL_INITIATED, WITHDRAWAL_COMPLETED, WITHDRAWAL_FAILED,
        BALANCE_INQUIRY, REFUND_ISSUED, CHARGEBACK_RECEIVED
    }

    public enum SecurityEventType {
        LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT, PASSWORD_CHANGE,
        MFA_ENABLED, MFA_DISABLED, ACCOUNT_LOCKED, ACCOUNT_UNLOCKED,
        SUSPICIOUS_ACTIVITY, SECURITY_ALERT, TOKEN_ISSUED, TOKEN_REVOKED
    }

    public enum UserActionType {
        PROFILE_UPDATED, EMAIL_CHANGED, PHONE_CHANGED, ADDRESS_UPDATED,
        DOCUMENT_UPLOADED, KYC_SUBMITTED, CONSENT_GIVEN, CONSENT_WITHDRAWN
    }

    public enum DataAccessType {
        READ, CREATE, UPDATE, DELETE, EXPORT, IMPORT
    }

    public enum ComplianceEventType {
        AML_CHECK, SANCTIONS_SCREENING, KYC_VERIFICATION, RISK_ASSESSMENT,
        REGULATORY_REPORT, AUDIT_TRAIL_ACCESS, POLICY_VIOLATION
    }
}