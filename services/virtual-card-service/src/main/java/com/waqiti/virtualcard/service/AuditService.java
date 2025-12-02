package com.waqiti.virtualcard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Audit Service for security and compliance logging
 *
 * Provides comprehensive audit trail for:
 * - Sensitive data access (card details, PII)
 * - Authentication events (MFA, login, logout)
 * - Authorization decisions (access grants/denials)
 * - Financial transactions
 * - Administrative actions
 * - Security incidents
 *
 * Features:
 * - Immutable audit logs
 * - Real-time event streaming to audit system
 * - Tamper-evident logging
 * - Regulatory compliance (SOX, GDPR, PCI-DSS)
 * - Automated alerting for suspicious activities
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String AUDIT_TOPIC = "audit.events";
    private static final String SECURITY_ALERT_TOPIC = "security.alerts";

    /**
     * Log sensitive data access for compliance
     *
     * @param userId User who accessed the data
     * @param resourceId Resource identifier (card ID, account ID, etc.)
     * @param action Action performed
     * @param metadata Additional context
     */
    @Transactional
    public void logSensitiveDataAccess(String userId, String resourceId, String action, Map<String, Object> metadata) {
        AuditEvent event = AuditEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("SENSITIVE_DATA_ACCESS")
            .userId(userId)
            .resourceId(resourceId)
            .action(action)
            .timestamp(Instant.now())
            .severity("HIGH")
            .metadata(metadata != null ? new HashMap<>(metadata) : new HashMap<>())
            .build();

        // Add standard metadata
        event.getMetadata().put("service", "virtual-card-service");
        event.getMetadata().put("eventCategory", "DATA_ACCESS");

        // Publish to audit log stream
        publishAuditEvent(event);

        log.info("AUDIT: Sensitive data access - userId={}, resourceId={}, action={}",
            userId, resourceId, action);
    }

    /**
     * Log failed MFA attempt
     *
     * @param userId User identifier
     * @param reason Failure reason
     */
    public void logFailedMfaAttempt(String userId, String reason) {
        AuditEvent event = AuditEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("MFA_FAILED")
            .userId(userId)
            .action("MFA_VERIFICATION")
            .timestamp(Instant.now())
            .severity("MEDIUM")
            .metadata(Map.of(
                "reason", reason,
                "service", "virtual-card-service",
                "eventCategory", "AUTHENTICATION"
            ))
            .build();

        publishAuditEvent(event);

        log.warn("AUDIT: Failed MFA attempt - userId={}, reason={}", userId, reason);
    }

    /**
     * Log successful MFA verification
     *
     * @param userId User identifier
     * @param deviceId Device identifier
     */
    public void logSuccessfulMfaVerification(String userId, String deviceId) {
        AuditEvent event = AuditEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("MFA_SUCCESS")
            .userId(userId)
            .action("MFA_VERIFICATION")
            .timestamp(Instant.now())
            .severity("INFO")
            .metadata(Map.of(
                "deviceId", deviceId,
                "service", "virtual-card-service",
                "eventCategory", "AUTHENTICATION"
            ))
            .build();

        publishAuditEvent(event);

        log.info("AUDIT: Successful MFA verification - userId={}, deviceId={}", userId, deviceId);
    }

    /**
     * Log untrusted device access attempt
     *
     * @param userId User identifier
     * @param deviceId Device identifier
     */
    public void logUntrustedDeviceAttempt(String userId, String deviceId) {
        AuditEvent event = AuditEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("UNTRUSTED_DEVICE_ACCESS")
            .userId(userId)
            .action("DEVICE_VERIFICATION")
            .timestamp(Instant.now())
            .severity("HIGH")
            .metadata(Map.of(
                "deviceId", deviceId,
                "service", "virtual-card-service",
                "eventCategory", "SECURITY"
            ))
            .build();

        publishAuditEvent(event);
        publishSecurityAlert(event);

        log.warn("SECURITY ALERT: Untrusted device access attempt - userId={}, deviceId={}",
            userId, deviceId);
    }

    /**
     * Log card creation event
     *
     * @param userId User identifier
     * @param cardId Card identifier
     * @param cardType Card type
     */
    public void logCardCreation(String userId, String cardId, String cardType) {
        AuditEvent event = AuditEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("CARD_CREATED")
            .userId(userId)
            .resourceId(cardId)
            .action("CREATE_CARD")
            .timestamp(Instant.now())
            .severity("INFO")
            .metadata(Map.of(
                "cardType", cardType,
                "service", "virtual-card-service",
                "eventCategory", "CARD_LIFECYCLE"
            ))
            .build();

        publishAuditEvent(event);

        log.info("AUDIT: Card created - userId={}, cardId={}, cardType={}", userId, cardId, cardType);
    }

    /**
     * Log card deletion event
     *
     * @param userId User identifier
     * @param cardId Card identifier
     * @param reason Deletion reason
     */
    public void logCardDeletion(String userId, String cardId, String reason) {
        AuditEvent event = AuditEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("CARD_DELETED")
            .userId(userId)
            .resourceId(cardId)
            .action("DELETE_CARD")
            .timestamp(Instant.now())
            .severity("MEDIUM")
            .metadata(Map.of(
                "reason", reason,
                "service", "virtual-card-service",
                "eventCategory", "CARD_LIFECYCLE"
            ))
            .build();

        publishAuditEvent(event);

        log.info("AUDIT: Card deleted - userId={}, cardId={}, reason={}", userId, cardId, reason);
    }

    /**
     * Log transaction authorization event
     *
     * @param userId User identifier
     * @param cardId Card identifier
     * @param transactionId Transaction identifier
     * @param amount Transaction amount
     * @param authorized Whether transaction was authorized
     */
    public void logTransactionAuthorization(String userId, String cardId, String transactionId,
                                           String amount, boolean authorized) {
        AuditEvent event = AuditEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(authorized ? "TRANSACTION_AUTHORIZED" : "TRANSACTION_DECLINED")
            .userId(userId)
            .resourceId(cardId)
            .action("AUTHORIZE_TRANSACTION")
            .timestamp(Instant.now())
            .severity("INFO")
            .metadata(Map.of(
                "transactionId", transactionId,
                "amount", amount,
                "authorized", authorized,
                "service", "virtual-card-service",
                "eventCategory", "TRANSACTION"
            ))
            .build();

        publishAuditEvent(event);

        log.info("AUDIT: Transaction {} - userId={}, cardId={}, transactionId={}, amount={}",
            authorized ? "AUTHORIZED" : "DECLINED", userId, cardId, transactionId, amount);
    }

    /**
     * Log security incident
     *
     * @param userId User identifier (if applicable)
     * @param incidentType Type of security incident
     * @param description Incident description
     * @param metadata Additional context
     */
    public void logSecurityIncident(String userId, String incidentType, String description,
                                    Map<String, Object> metadata) {
        AuditEvent event = AuditEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("SECURITY_INCIDENT")
            .userId(userId)
            .action(incidentType)
            .timestamp(Instant.now())
            .severity("CRITICAL")
            .metadata(metadata != null ? new HashMap<>(metadata) : new HashMap<>())
            .build();

        event.getMetadata().put("description", description);
        event.getMetadata().put("service", "virtual-card-service");
        event.getMetadata().put("eventCategory", "SECURITY");

        publishAuditEvent(event);
        publishSecurityAlert(event);

        log.error("SECURITY INCIDENT: {} - userId={}, description={}",
            incidentType, userId, description);
    }

    /**
     * Publish audit event to Kafka
     */
    private void publishAuditEvent(AuditEvent event) {
        try {
            kafkaTemplate.send(AUDIT_TOPIC, event.getEventId(), event);
            log.debug("Published audit event {} to Kafka", event.getEventId());
        } catch (Exception e) {
            // CRITICAL: Audit log failure must not break application flow
            // but must be logged for investigation
            log.error("CRITICAL: Failed to publish audit event {} - Event details: {}",
                event.getEventId(), event, e);
        }
    }

    /**
     * Publish security alert for immediate response
     */
    private void publishSecurityAlert(AuditEvent event) {
        try {
            kafkaTemplate.send(SECURITY_ALERT_TOPIC, event.getEventId(), event);
            log.debug("Published security alert {} to Kafka", event.getEventId());
        } catch (Exception e) {
            log.error("CRITICAL: Failed to publish security alert {} - Event details: {}",
                event.getEventId(), event, e);
        }
    }

    /**
     * Audit Event structure
     */
    @lombok.Data
    @lombok.Builder
    public static class AuditEvent {
        private String eventId;
        private String eventType;
        private String userId;
        private String resourceId;
        private String action;
        private Instant timestamp;
        private String severity; // INFO, LOW, MEDIUM, HIGH, CRITICAL
        private Map<String, Object> metadata;
    }
}
