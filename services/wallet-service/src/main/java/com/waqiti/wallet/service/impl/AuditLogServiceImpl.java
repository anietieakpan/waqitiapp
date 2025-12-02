package com.waqiti.wallet.service.impl;

import com.waqiti.common.security.SensitiveDataMasker;
import com.waqiti.wallet.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of AuditLogService for wallet freeze audit trail.
 *
 * <p>Publishes audit events to Kafka for centralized audit log aggregation.
 * Events are consumed by audit-service for long-term storage and compliance reporting.
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogServiceImpl implements AuditLogService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String AUDIT_LOG_TOPIC = "audit.wallet.freeze.events";

    @Override
    public void logWalletFreeze(UUID userId, List<String> walletIds, String freezeReason, String severity) {
        log.info("AUDIT: Logging wallet freeze - User: {}, Wallets: {}, Severity: {}",
                SensitiveDataMasker.formatUserIdForLogging(userId), walletIds.size(), severity);

        try {
            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("eventType", "WALLET_FREEZE");
            auditEvent.put("eventId", UUID.randomUUID().toString());
            auditEvent.put("userId", userId.toString());
            auditEvent.put("walletIds", walletIds);
            auditEvent.put("freezeReason", freezeReason);
            auditEvent.put("severity", severity);
            auditEvent.put("timestamp", LocalDateTime.now().toString());
            auditEvent.put("source", "wallet-service");
            auditEvent.put("actor", "SYSTEM");

            kafkaTemplate.send(AUDIT_LOG_TOPIC, userId.toString(), auditEvent);

        } catch (Exception e) {
            log.error("AUDIT: Failed to publish wallet freeze audit event - User: {}",
                    SensitiveDataMasker.formatUserIdForLogging(userId), e);
        }
    }

    @Override
    public void linkToCaseManagement(String walletId, String caseId, String freezeReason) {
        log.info("AUDIT: Linking wallet freeze to case - Wallet: {}, Case: {}",
                walletId, caseId);

        try {
            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("eventType", "WALLET_FREEZE_CASE_LINK");
            auditEvent.put("eventId", UUID.randomUUID().toString());
            auditEvent.put("walletId", walletId);
            auditEvent.put("caseId", caseId);
            auditEvent.put("freezeReason", freezeReason);
            auditEvent.put("timestamp", LocalDateTime.now().toString());
            auditEvent.put("source", "wallet-service");

            kafkaTemplate.send(AUDIT_LOG_TOPIC, walletId, auditEvent);

        } catch (Exception e) {
            log.error("AUDIT: Failed to publish case link audit event - Wallet: {}, Case: {}",
                    walletId, caseId, e);
        }
    }

    @Override
    public void logWalletUnfreeze(UUID userId, List<String> walletIds, String unfreezeReason) {
        log.info("AUDIT: Logging wallet unfreeze - User: {}, Wallets: {}",
                SensitiveDataMasker.formatUserIdForLogging(userId), walletIds.size());

        try {
            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("eventType", "WALLET_UNFREEZE");
            auditEvent.put("eventId", UUID.randomUUID().toString());
            auditEvent.put("userId", userId.toString());
            auditEvent.put("walletIds", walletIds);
            auditEvent.put("unfreezeReason", unfreezeReason);
            auditEvent.put("timestamp", LocalDateTime.now().toString());
            auditEvent.put("source", "wallet-service");
            auditEvent.put("actor", "SYSTEM");

            kafkaTemplate.send(AUDIT_LOG_TOPIC, userId.toString(), auditEvent);

        } catch (Exception e) {
            log.error("AUDIT: Failed to publish wallet unfreeze audit event - User: {}",
                    SensitiveDataMasker.formatUserIdForLogging(userId), e);
        }
    }

    @Override
    public void logFraudEventProcessing(UUID eventId, UUID userId, String action, Double riskScore) {
        log.info("AUDIT: Logging fraud event processing - Event: {}, User: {}, Action: {}, Risk: {}",
                eventId, SensitiveDataMasker.formatUserIdForLogging(userId), action, riskScore);

        try {
            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("eventType", "FRAUD_EVENT_PROCESSED");
            auditEvent.put("eventId", eventId.toString());
            auditEvent.put("userId", userId.toString());
            auditEvent.put("action", action);
            auditEvent.put("riskScore", riskScore);
            auditEvent.put("timestamp", LocalDateTime.now().toString());
            auditEvent.put("source", "wallet-service");

            kafkaTemplate.send(AUDIT_LOG_TOPIC, eventId.toString(), auditEvent);

        } catch (Exception e) {
            log.error("AUDIT: Failed to publish fraud event audit event - Event: {}", eventId, e);
        }
    }

    @Override
    public void logWalletFreezeAction(UUID userId, UUID eventId, int walletsAffected,
                                      String actionType, double riskScore, String fraudReason) {
        log.info("AUDIT: Logging wallet freeze action - User: {}, Event: {}, Action: {}, Wallets: {}, Risk: {}",
                SensitiveDataMasker.formatUserIdForLogging(userId), eventId, actionType,
                walletsAffected, riskScore);

        try {
            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("eventType", "WALLET_FREEZE_ACTION");
            auditEvent.put("eventId", eventId.toString());
            auditEvent.put("userId", userId.toString());
            auditEvent.put("walletsAffected", walletsAffected);
            auditEvent.put("actionType", actionType);
            auditEvent.put("riskScore", riskScore);
            auditEvent.put("fraudReason", fraudReason);
            auditEvent.put("timestamp", LocalDateTime.now().toString());
            auditEvent.put("source", "wallet-service");

            kafkaTemplate.send(AUDIT_LOG_TOPIC, eventId.toString(), auditEvent);

        } catch (Exception e) {
            log.error("AUDIT: Failed to publish wallet freeze action - Event: {}", eventId, e);
        }
    }

    @Override
    public void logFraudReviewFlag(UUID userId, UUID eventId, String reviewType,
                                  double riskScore, String fraudReason) {
        log.info("AUDIT: Logging fraud review flag - User: {}, Event: {}, Review: {}, Risk: {}",
                SensitiveDataMasker.formatUserIdForLogging(userId), eventId, reviewType, riskScore);

        try {
            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("eventType", "FRAUD_REVIEW_FLAG");
            auditEvent.put("eventId", eventId.toString());
            auditEvent.put("userId", userId.toString());
            auditEvent.put("reviewType", reviewType);
            auditEvent.put("riskScore", riskScore);
            auditEvent.put("fraudReason", fraudReason);
            auditEvent.put("timestamp", LocalDateTime.now().toString());
            auditEvent.put("source", "wallet-service");

            kafkaTemplate.send(AUDIT_LOG_TOPIC, eventId.toString(), auditEvent);

        } catch (Exception e) {
            log.error("AUDIT: Failed to publish fraud review flag - Event: {}", eventId, e);
        }
    }

    @Override
    public void logFraudMonitoringEvent(UUID userId, UUID eventId, String monitoringType,
                                       double riskScore, String fraudReason) {
        log.info("AUDIT: Logging fraud monitoring event - User: {}, Event: {}, Monitoring: {}, Risk: {}",
                SensitiveDataMasker.formatUserIdForLogging(userId), eventId, monitoringType, riskScore);

        try {
            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("eventType", "FRAUD_MONITORING");
            auditEvent.put("eventId", eventId.toString());
            auditEvent.put("userId", userId.toString());
            auditEvent.put("monitoringType", monitoringType);
            auditEvent.put("riskScore", riskScore);
            auditEvent.put("fraudReason", fraudReason);
            auditEvent.put("timestamp", LocalDateTime.now().toString());
            auditEvent.put("source", "wallet-service");

            kafkaTemplate.send(AUDIT_LOG_TOPIC, eventId.toString(), auditEvent);

        } catch (Exception e) {
            log.error("AUDIT: Failed to publish fraud monitoring event - Event: {}", eventId, e);
        }
    }
}
