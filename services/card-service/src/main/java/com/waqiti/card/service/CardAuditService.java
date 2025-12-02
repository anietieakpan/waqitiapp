package com.waqiti.card.service;

import com.waqiti.card.entity.CardAuditLog;
import com.waqiti.card.enums.CardAuditEventType;
import com.waqiti.card.repository.CardAuditLogRepository;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CardAuditService - Comprehensive audit logging for all card operations
 *
 * Provides:
 * - Database audit trail for all card events
 * - Kafka event publishing for audit events
 * - PCI-DSS compliant audit logging
 * - Immutable audit records
 * - Audit trail queries and reports
 *
 * All sensitive operations must be audited:
 * - Card creation, activation, blocking, cancellation
 * - PIN changes and failed PIN attempts
 * - Card data access (view, export)
 * - Transaction authorizations and declines
 * - Fraud alerts
 * - Limit changes
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-19
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CardAuditService {

    private final CardAuditLogRepository auditLogRepository;
    private final AuditService commonAuditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String AUDIT_TOPIC = "audit.card.events";

    /**
     * Log card creation event
     *
     * @param userId User who created the card
     * @param cardId Card ID
     * @param cardType Type of card created
     * @param metadata Additional metadata
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCardCreation(UUID userId, UUID cardId, String cardType, Map<String, Object> metadata) {
        log.info("Auditing card creation - User: {}, Card: {}", userId, cardId);

        CardAuditLog auditLog = CardAuditLog.builder()
                .auditId(UUID.randomUUID())
                .userId(userId)
                .cardId(cardId)
                .eventType(CardAuditEventType.CARD_CREATED)
                .eventDescription("Card created: " + cardType)
                .ipAddress(getCurrentIpAddress())
                .userAgent(getCurrentUserAgent())
                .success(true)
                .metadata(metadata)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);
        publishAuditEvent(auditLog);

        // Also log to common audit service for centralized audit trail
        commonAuditService.logEvent("CARD_CREATED", userId.toString(),
                "Card created: " + cardId, metadata);
    }

    /**
     * Log card activation event
     *
     * @param userId User who activated the card
     * @param cardId Card ID
     * @param details Activation details
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCardActivation(UUID userId, UUID cardId, String details) {
        log.info("Auditing card activation - User: {}, Card: {}", userId, cardId);

        CardAuditLog auditLog = CardAuditLog.builder()
                .auditId(UUID.randomUUID())
                .userId(userId)
                .cardId(cardId)
                .eventType(CardAuditEventType.CARD_ACTIVATED)
                .eventDescription(details)
                .ipAddress(getCurrentIpAddress())
                .userAgent(getCurrentUserAgent())
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);
        publishAuditEvent(auditLog);
    }

    /**
     * Log card deactivation event
     *
     * @param userId User who deactivated the card
     * @param cardId Card ID
     * @param reason Deactivation reason
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCardDeactivation(UUID userId, UUID cardId, String reason) {
        log.info("Auditing card deactivation - User: {}, Card: {}", userId, cardId);

        CardAuditLog auditLog = CardAuditLog.builder()
                .auditId(UUID.randomUUID())
                .userId(userId)
                .cardId(cardId)
                .eventType(CardAuditEventType.CARD_DEACTIVATED)
                .eventDescription("Card deactivated: " + reason)
                .ipAddress(getCurrentIpAddress())
                .userAgent(getCurrentUserAgent())
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);
        publishAuditEvent(auditLog);
    }

    /**
     * Log card blocking event
     *
     * @param userId User who blocked the card (or system)
     * @param cardId Card ID
     * @param reason Blocking reason
     * @param isSystemBlocked True if blocked by system (fraud detection)
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCardBlocked(UUID userId, UUID cardId, String reason, boolean isSystemBlocked) {
        log.warn("Auditing card block - User: {}, Card: {}, System: {}", userId, cardId, isSystemBlocked);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("reason", reason);
        metadata.put("systemBlocked", isSystemBlocked);

        CardAuditLog auditLog = CardAuditLog.builder()
                .auditId(UUID.randomUUID())
                .userId(userId)
                .cardId(cardId)
                .eventType(CardAuditEventType.CARD_BLOCKED)
                .eventDescription("Card blocked: " + reason)
                .ipAddress(getCurrentIpAddress())
                .userAgent(getCurrentUserAgent())
                .success(true)
                .metadata(metadata)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);
        publishAuditEvent(auditLog);
    }

    /**
     * Log PIN change event
     *
     * @param userId User ID
     * @param cardId Card ID
     * @param success Whether PIN change was successful
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logPinChange(UUID userId, UUID cardId, boolean success) {
        log.info("Auditing PIN change - User: {}, Card: {}, Success: {}", userId, cardId, success);

        CardAuditLog auditLog = CardAuditLog.builder()
                .auditId(UUID.randomUUID())
                .userId(userId)
                .cardId(cardId)
                .eventType(CardAuditEventType.PIN_CHANGED)
                .eventDescription(success ? "PIN changed successfully" : "PIN change failed")
                .ipAddress(getCurrentIpAddress())
                .userAgent(getCurrentUserAgent())
                .success(success)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);
        publishAuditEvent(auditLog);
    }

    /**
     * Log failed PIN attempt
     *
     * @param userId User ID
     * @param cardId Card ID
     * @param attemptNumber Attempt number
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailedPinAttempt(UUID userId, UUID cardId, int attemptNumber) {
        log.warn("Auditing failed PIN attempt - User: {}, Card: {}, Attempt: {}",
                userId, cardId, attemptNumber);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("attemptNumber", attemptNumber);
        metadata.put("severity", attemptNumber >= 3 ? "HIGH" : "MEDIUM");

        CardAuditLog auditLog = CardAuditLog.builder()
                .auditId(UUID.randomUUID())
                .userId(userId)
                .cardId(cardId)
                .eventType(CardAuditEventType.PIN_VERIFICATION_FAILED)
                .eventDescription("Failed PIN attempt #" + attemptNumber)
                .ipAddress(getCurrentIpAddress())
                .userAgent(getCurrentUserAgent())
                .success(false)
                .metadata(metadata)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);
        publishAuditEvent(auditLog);
    }

    /**
     * Log card data access (viewing card details)
     *
     * @param userId User who accessed the data
     * @param cardId Card ID
     * @param accessType Type of access (VIEW, EXPORT, etc.)
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCardDataAccess(UUID userId, UUID cardId, String accessType) {
        log.info("Auditing card data access - User: {}, Card: {}, Type: {}",
                userId, cardId, accessType);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("accessType", accessType);

        CardAuditLog auditLog = CardAuditLog.builder()
                .auditId(UUID.randomUUID())
                .userId(userId)
                .cardId(cardId)
                .eventType(CardAuditEventType.CARD_DATA_ACCESSED)
                .eventDescription("Card data accessed: " + accessType)
                .ipAddress(getCurrentIpAddress())
                .userAgent(getCurrentUserAgent())
                .success(true)
                .metadata(metadata)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);
        publishAuditEvent(auditLog);
    }

    /**
     * Log transaction authorization
     *
     * @param userId User ID
     * @param cardId Card ID
     * @param authorizationId Authorization ID
     * @param amount Transaction amount
     * @param merchantName Merchant name
     * @param approved Whether transaction was approved
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTransactionAuthorization(UUID userId, UUID cardId, UUID authorizationId,
                                           BigDecimal amount, String merchantName, boolean approved) {
        log.info("Auditing transaction - Card: {}, Auth: {}, Approved: {}",
                cardId, authorizationId, approved);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("authorizationId", authorizationId.toString());
        metadata.put("amount", amount.toString());
        metadata.put("merchantName", merchantName);
        metadata.put("approved", approved);

        CardAuditLog auditLog = CardAuditLog.builder()
                .auditId(UUID.randomUUID())
                .userId(userId)
                .cardId(cardId)
                .eventType(approved ? CardAuditEventType.TRANSACTION_AUTHORIZED :
                                    CardAuditEventType.TRANSACTION_DECLINED)
                .eventDescription((approved ? "Approved" : "Declined") + " - " + merchantName)
                .ipAddress(getCurrentIpAddress())
                .success(approved)
                .metadata(metadata)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);
        publishAuditEvent(auditLog);
    }

    /**
     * Log fraud alert
     *
     * @param userId User ID
     * @param cardId Card ID
     * @param riskScore Fraud risk score
     * @param reason Fraud detection reason
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFraudAlert(UUID userId, UUID cardId, BigDecimal riskScore, String reason) {
        log.warn("Auditing fraud alert - Card: {}, Risk: {}", cardId, riskScore);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("riskScore", riskScore.toString());
        metadata.put("reason", reason);
        metadata.put("severity", "CRITICAL");

        CardAuditLog auditLog = CardAuditLog.builder()
                .auditId(UUID.randomUUID())
                .userId(userId)
                .cardId(cardId)
                .eventType(CardAuditEventType.FRAUD_ALERT)
                .eventDescription("Fraud alert: " + reason + " (Risk: " + riskScore + ")")
                .ipAddress(getCurrentIpAddress())
                .success(true)
                .metadata(metadata)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);
        publishAuditEvent(auditLog);
    }

    /**
     * Log limit change
     *
     * @param userId User ID
     * @param cardId Card ID
     * @param oldLimit Old credit limit
     * @param newLimit New credit limit
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLimitChange(UUID userId, UUID cardId, BigDecimal oldLimit, BigDecimal newLimit) {
        log.info("Auditing limit change - Card: {}, Old: {}, New: {}", cardId, oldLimit, newLimit);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("oldLimit", oldLimit.toString());
        metadata.put("newLimit", newLimit.toString());
        metadata.put("change", newLimit.subtract(oldLimit).toString());

        CardAuditLog auditLog = CardAuditLog.builder()
                .auditId(UUID.randomUUID())
                .userId(userId)
                .cardId(cardId)
                .eventType(CardAuditEventType.LIMIT_CHANGED)
                .eventDescription("Credit limit changed from " + oldLimit + " to " + newLimit)
                .ipAddress(getCurrentIpAddress())
                .userAgent(getCurrentUserAgent())
                .success(true)
                .metadata(metadata)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);
        publishAuditEvent(auditLog);
    }

    /**
     * Get audit trail for a specific card
     *
     * @param cardId Card ID
     * @return List of audit logs
     */
    public List<CardAuditLog> getAuditTrail(UUID cardId) {
        return auditLogRepository.findByCardIdOrderByTimestampDesc(cardId);
    }

    /**
     * Get audit trail for a user's cards
     *
     * @param userId User ID
     * @return List of audit logs
     */
    public List<CardAuditLog> getUserAuditTrail(UUID userId) {
        return auditLogRepository.findByUserIdOrderByTimestampDesc(userId);
    }

    /**
     * Get failed PIN attempts for a card
     *
     * @param cardId Card ID
     * @param since Since timestamp
     * @return Count of failed attempts
     */
    public long getFailedPinAttempts(UUID cardId, LocalDateTime since) {
        return auditLogRepository.countByCardIdAndEventTypeAndTimestampAfter(
                cardId, CardAuditEventType.PIN_VERIFICATION_FAILED, since);
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    private void publishAuditEvent(CardAuditLog auditLog) {
        try {
            kafkaTemplate.send(AUDIT_TOPIC, auditLog.getCardId().toString(), auditLog);
            log.debug("Published audit event to Kafka: {}", auditLog.getEventType());
        } catch (Exception e) {
            log.error("Failed to publish audit event to Kafka", e);
            // Don't fail the audit log if Kafka is down - already saved to database
        }
    }

    private String getCurrentIpAddress() {
        // In production: Extract from HttpServletRequest via RequestContextHolder
        // For now, return placeholder
        return "0.0.0.0";
    }

    private String getCurrentUserAgent() {
        // In production: Extract from HttpServletRequest
        return "CardService/2.0";
    }
}
