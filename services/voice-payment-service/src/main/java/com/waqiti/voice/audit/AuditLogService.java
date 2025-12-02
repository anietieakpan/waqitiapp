package com.waqiti.voice.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Audit Log Service
 *
 * CRITICAL COMPLIANCE: Comprehensive security event logging
 *
 * Audit Events:
 * - Authentication events (login, logout, biometric verification)
 * - Authorization events (access attempts, permission checks)
 * - Data access events (view profile, view transactions)
 * - Data modification events (create, update, delete)
 * - Payment events (initiate, complete, cancel)
 * - Security events (rate limit exceeded, suspicious activity)
 * - Administrative actions (user management, configuration changes)
 *
 * Storage:
 * - Primary: Kafka topics for real-time monitoring
 * - Secondary: Database for compliance/forensics
 * - Tertiary: ELK stack for analysis
 *
 * Compliance Requirements:
 * - PCI-DSS Requirement 10 (Track and monitor all access)
 * - GDPR Article 30 (Records of processing activities)
 * - SOC 2 (Logging and monitoring)
 * - HIPAA Security Rule (Audit controls)
 *
 * Audit Log Contents:
 * - WHO: User ID, session ID, IP address
 * - WHAT: Action performed, resource accessed
 * - WHEN: Timestamp (UTC)
 * - WHERE: Service, endpoint, method
 * - WHY: Success/failure, error details
 * - HOW: Request metadata, user agent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    private static final String AUDIT_TOPIC = "voice-payment.audit-events";

    /**
     * Log authentication event
     */
    public void logAuthenticationEvent(UUID userId, AuthenticationEventType eventType,
                                      boolean success, String details, Map<String, Object> metadata) {
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .eventCategory(EventCategory.AUTHENTICATION)
                .eventType(eventType.name())
                .success(success)
                .details(details)
                .metadata(enrichMetadata(metadata))
                .timestamp(LocalDateTime.now())
                .build();

        logAuditEvent(auditLog);
    }

    /**
     * Log voice command processing
     */
    public void logVoiceCommandEvent(UUID userId, UUID commandId, String commandType,
                                    boolean success, String details, Map<String, Object> metadata) {
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .resourceId(commandId.toString())
                .resourceType("VOICE_COMMAND")
                .eventCategory(EventCategory.VOICE_COMMAND)
                .eventType(commandType)
                .success(success)
                .details(details)
                .metadata(enrichMetadata(metadata))
                .timestamp(LocalDateTime.now())
                .build();

        logAuditEvent(auditLog);
    }

    /**
     * Log payment transaction
     */
    public void logPaymentEvent(UUID userId, UUID transactionId, PaymentEventType eventType,
                               boolean success, String details, Map<String, Object> metadata) {
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .resourceId(transactionId.toString())
                .resourceType("PAYMENT_TRANSACTION")
                .eventCategory(EventCategory.PAYMENT)
                .eventType(eventType.name())
                .success(success)
                .details(details)
                .metadata(enrichMetadata(metadata))
                .timestamp(LocalDateTime.now())
                .severity(determineSeverity(eventType, success))
                .build();

        logAuditEvent(auditLog);
    }

    /**
     * Log data access event
     */
    public void logDataAccessEvent(UUID userId, String resourceType, String resourceId,
                                   DataAccessAction action, boolean success, String details) {
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .resourceId(resourceId)
                .resourceType(resourceType)
                .eventCategory(EventCategory.DATA_ACCESS)
                .eventType(action.name())
                .success(success)
                .details(details)
                .metadata(enrichMetadata(null))
                .timestamp(LocalDateTime.now())
                .build();

        logAuditEvent(auditLog);
    }

    /**
     * Log security event
     */
    public void logSecurityEvent(UUID userId, SecurityEventType eventType, Severity severity,
                                 String details, Map<String, Object> metadata) {
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .eventCategory(EventCategory.SECURITY)
                .eventType(eventType.name())
                .success(false) // Security events are typically failures/alerts
                .details(details)
                .metadata(enrichMetadata(metadata))
                .timestamp(LocalDateTime.now())
                .severity(severity)
                .build();

        logAuditEvent(auditLog);

        // Alert on critical security events
        if (severity == Severity.CRITICAL || severity == Severity.HIGH) {
            log.error("SECURITY EVENT: userId={}, type={}, severity={}, details={}",
                    userId, eventType, severity, details);
        }
    }

    /**
     * Log biometric event
     */
    public void logBiometricEvent(UUID userId, BiometricEventType eventType,
                                  boolean success, double confidenceScore, String details) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("confidenceScore", confidenceScore);

        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .eventCategory(EventCategory.BIOMETRIC)
                .eventType(eventType.name())
                .success(success)
                .details(details)
                .metadata(enrichMetadata(metadata))
                .timestamp(LocalDateTime.now())
                .severity(success ? Severity.INFO : Severity.WARNING)
                .build();

        logAuditEvent(auditLog);
    }

    /**
     * Log administrative action
     */
    public void logAdminEvent(UUID adminUserId, UUID targetUserId, AdminAction action,
                             boolean success, String details, Map<String, Object> metadata) {
        AuditLog auditLog = AuditLog.builder()
                .userId(adminUserId)
                .resourceId(targetUserId != null ? targetUserId.toString() : null)
                .resourceType("USER")
                .eventCategory(EventCategory.ADMIN)
                .eventType(action.name())
                .success(success)
                .details(details)
                .metadata(enrichMetadata(metadata))
                .timestamp(LocalDateTime.now())
                .severity(Severity.HIGH)
                .build();

        logAuditEvent(auditLog);
    }

    /**
     * Core audit logging method
     */
    @Transactional
    private void logAuditEvent(AuditLog auditLog) {
        try {
            // 1. Send to Kafka for real-time monitoring
            kafkaTemplate.send(AUDIT_TOPIC, auditLog.getUserId().toString(), auditLog);

            // 2. Store in database for compliance
            auditLogRepository.save(auditLog);

            log.debug("Audit event logged: category={}, type={}, userId={}, success={}",
                    auditLog.getEventCategory(), auditLog.getEventType(),
                    auditLog.getUserId(), auditLog.isSuccess());

        } catch (Exception e) {
            // CRITICAL: Audit logging must NEVER fail the business operation
            log.error("Failed to log audit event (non-fatal): {}", auditLog, e);
        }
    }

    /**
     * Enrich metadata with request context
     */
    private Map<String, Object> enrichMetadata(Map<String, Object> metadata) {
        Map<String, Object> enriched = metadata != null ? new HashMap<>(metadata) : new HashMap<>();

        // Add context information
        enriched.put("service", "voice-payment-service");
        enriched.put("timestamp", System.currentTimeMillis());

        // TODO: Add request context (IP, user agent, session ID) from security context

        return enriched;
    }

    /**
     * Determine severity based on event type and success
     */
    private Severity determineSeverity(PaymentEventType eventType, boolean success) {
        if (eventType == PaymentEventType.PAYMENT_FAILED || eventType == PaymentEventType.FRAUD_DETECTED) {
            return Severity.HIGH;
        }
        if (eventType == PaymentEventType.PAYMENT_CANCELLED) {
            return Severity.MEDIUM;
        }
        return success ? Severity.INFO : Severity.WARNING;
    }

    // Enums

    public enum EventCategory {
        AUTHENTICATION,
        AUTHORIZATION,
        VOICE_COMMAND,
        PAYMENT,
        DATA_ACCESS,
        SECURITY,
        BIOMETRIC,
        ADMIN,
        SYSTEM
    }

    public enum AuthenticationEventType {
        LOGIN_SUCCESS,
        LOGIN_FAILED,
        LOGOUT,
        BIOMETRIC_AUTH_SUCCESS,
        BIOMETRIC_AUTH_FAILED,
        TOKEN_REFRESH,
        SESSION_EXPIRED
    }

    public enum PaymentEventType {
        PAYMENT_INITIATED,
        PAYMENT_COMPLETED,
        PAYMENT_FAILED,
        PAYMENT_CANCELLED,
        FRAUD_DETECTED,
        DUPLICATE_PREVENTED
    }

    public enum DataAccessAction {
        VIEW,
        CREATE,
        UPDATE,
        DELETE,
        EXPORT,
        SEARCH
    }

    public enum SecurityEventType {
        RATE_LIMIT_EXCEEDED,
        UNAUTHORIZED_ACCESS_ATTEMPT,
        SUSPICIOUS_ACTIVITY,
        MALWARE_DETECTED,
        INVALID_TOKEN,
        ACCOUNT_LOCKED,
        PASSWORD_RESET_REQUESTED,
        MFA_CHALLENGE_FAILED
    }

    public enum BiometricEventType {
        ENROLLMENT_STARTED,
        ENROLLMENT_COMPLETED,
        ENROLLMENT_FAILED,
        VERIFICATION_SUCCESS,
        VERIFICATION_FAILED,
        LIVENESS_CHECK_FAILED,
        SPOOFING_DETECTED
    }

    public enum AdminAction {
        USER_CREATED,
        USER_UPDATED,
        USER_DELETED,
        USER_LOCKED,
        USER_UNLOCKED,
        PERMISSIONS_CHANGED,
        CONFIGURATION_CHANGED,
        DATA_EXPORTED,
        RATE_LIMIT_RESET
    }

    public enum Severity {
        INFO,
        WARNING,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
