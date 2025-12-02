package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.compliance.domain.*;
import com.waqiti.compliance.repository.KYCRecordRepository;
import com.waqiti.compliance.repository.ComplianceAlertRepository;
import com.waqiti.compliance.service.KYCService;
import com.waqiti.compliance.service.UserAccountService;
import com.waqiti.compliance.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * CRITICAL COMPLIANCE KAFKA CONSUMER - KYC Verification Expired
 * 
 * Know Your Customer (KYC) Verification Expiration Consumer
 * 
 * Handles events when KYC verification documents or status expire, requiring
 * re-verification to maintain regulatory compliance. KYC expiration requires
 * immediate action to prevent service disruption and regulatory violations.
 * 
 * REGULATORY REQUIREMENTS:
 * - Customer Identification Program (CIP) - 31 CFR 1020.220
 * - Customer Due Diligence (CDD) - 31 CFR 1010.230
 * - Enhanced Due Diligence (EDD) for high-risk customers
 * - Periodic KYC refresh requirements (typically annual)
 * - Document validity periods (typically 12-24 months)
 * 
 * CRITICAL ACTIONS:
 * - Restrict account activity for expired KYC
 * - Initiate re-verification workflow
 * - Send customer notifications
 * - Create compliance alerts
 * - Potential account freeze for high-risk users
 * 
 * REGULATORY RISKS:
 * - Operating with expired KYC: Regulatory violations
 * - Fines: $10,000 - $1,000,000 per violation
 * - Money laundering facilitation risk
 * - License suspension possible
 * 
 * Event Source: kyc-service, scheduled-job-service
 * Topic: compliance.kyc.verification.expired
 * 
 * @author Waqiti Compliance Team
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KYCVerificationExpiredConsumer {

    private final KYCRecordRepository kycRepository;
    private final ComplianceAlertRepository alertRepository;
    private final KYCService kycService;
    private final UserAccountService userAccountService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Grace period before hard restrictions
    private static final int GRACE_PERIOD_DAYS = 7;
    private static final int ESCALATION_THRESHOLD_DAYS = 14;

    /**
     * Processes KYC verification expiration events.
     * 
     * CRITICAL: Must not lose messages - expired KYC requires immediate action
     * to prevent regulatory violations.
     * 
     * Retry Strategy:
     * - 4 attempts with exponential backoff
     * - Failed messages sent to DLQ for immediate manual intervention
     * - Alerts sent to compliance team for every failure
     */
    @KafkaListener(
        topics = "compliance.kyc.verification.expired",
        groupId = "compliance-kyc-expiration-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 20000),
        autoCreateTopics = "false",
        dltTopicSuffix = "-dlt",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(rollbackFor = Exception.class)
    public void handleKYCVerificationExpired(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.warn("COMPLIANCE ALERT: Processing KYC expiration: key={}, partition={}, offset={}", 
                key, partition, record.offset());
            
            // Parse the KYC expiration event
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            
            String kycRecordId = (String) event.get("kycRecordId");
            String userId = (String) event.get("userId");
            String accountId = (String) event.get("accountId");
            String verificationType = (String) event.get("verificationType");
            String expiredAtStr = (String) event.get("expiredAt");
            String riskLevel = (String) event.getOrDefault("riskLevel", "MEDIUM");
            Boolean gracePeriodExpired = (Boolean) event.getOrDefault("gracePeriodExpired", false);
            
            // Validate required fields
            if (kycRecordId == null || userId == null) {
                log.error("Invalid KYC expiration event - missing required fields: {}", event);
                publishKYCExpirationProcessingFailed(kycRecordId, userId, "VALIDATION_ERROR", 
                    "Missing required fields: kycRecordId or userId");
                acknowledgment.acknowledge();
                return;
            }
            
            // Parse expiration timestamp
            LocalDateTime expiredAt = expiredAtStr != null ? 
                LocalDateTime.parse(expiredAtStr) : LocalDateTime.now();
            
            // Load KYC record
            Optional<KYCRecord> kycRecordOpt = kycRepository.findById(UUID.fromString(kycRecordId));
            if (kycRecordOpt.isEmpty()) {
                log.error("KYC record not found: {}", kycRecordId);
                publishKYCExpirationProcessingFailed(kycRecordId, userId, "RECORD_NOT_FOUND", 
                    "KYC record not found");
                acknowledgment.acknowledge();
                return;
            }
            
            KYCRecord kycRecord = kycRecordOpt.get();
            
            // Determine action severity based on risk level and grace period
            KYCExpirationSeverity severity = determineExpirationSeverity(
                riskLevel, 
                gracePeriodExpired,
                expiredAt
            );
            
            // Update KYC record status
            updateKYCRecordStatus(kycRecord, expiredAt, gracePeriodExpired);
            
            // Apply account restrictions based on severity
            applyAccountRestrictions(userId, accountId, kycRecord, severity);
            
            // Create compliance alert
            createKYCExpirationAlert(kycRecord, severity);
            
            // Initiate re-verification workflow
            initiateReVerificationWorkflow(kycRecord, severity);
            
            // Send notifications to user and compliance team
            sendExpirationNotifications(kycRecord, userId, severity, gracePeriodExpired);
            
            // Audit the KYC expiration handling
            auditService.logSecurityEvent("KYC_VERIFICATION_EXPIRED_PROCESSED", Map.of(
                "kycRecordId", kycRecordId,
                "userId", userId,
                "accountId", accountId != null ? accountId : "N/A",
                "verificationType", verificationType,
                "expiredAt", expiredAt.toString(),
                "riskLevel", riskLevel,
                "severity", severity.toString(),
                "gracePeriodExpired", gracePeriodExpired.toString(),
                "restrictionsApplied", getAppliedRestrictions(severity),
                "processingTimeMs", (System.currentTimeMillis() - startTime)
            ));
            
            long duration = System.currentTimeMillis() - startTime;
            log.warn("KYC EXPIRATION PROCESSED: User {} - KYC {} expired - Severity: {} ({}ms)", 
                userId, kycRecordId, severity, duration);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL COMPLIANCE FAILURE: Error processing KYC expiration", e);
            
            // Extract identifiers for error reporting
            String kycRecordId = null;
            String userId = null;
            try {
                Map<String, Object> event = objectMapper.readValue(message, Map.class);
                kycRecordId = (String) event.get("kycRecordId");
                userId = (String) event.get("userId");
            } catch (Exception parseException) {
                log.error("Failed to parse event for error reporting", parseException);
            }
            
            // Publish failure event
            publishKYCExpirationProcessingFailed(kycRecordId, userId, "PROCESSING_ERROR", e.getMessage());
            
            // Send CRITICAL alert to compliance team
            try {
                notificationService.sendCriticalComplianceAlert(
                    "KYC Expiration Processing Failure",
                    "CRITICAL: Failed to process KYC expiration. " +
                    "User ID: " + userId + ", " +
                    "KYC Record: " + kycRecordId + ". " +
                    "Error: " + e.getMessage() + ". " +
                    "IMMEDIATE MANUAL INTERVENTION REQUIRED."
                );
            } catch (Exception notifEx) {
                log.error("Failed to send critical compliance alert", notifEx);
            }
            
            // Audit the critical failure
            auditService.logSecurityEvent("KYC_EXPIRATION_PROCESSING_FAILURE", Map.of(
                "kycRecordId", kycRecordId != null ? kycRecordId : "unknown",
                "userId", userId != null ? userId : "unknown",
                "errorType", e.getClass().getSimpleName(),
                "errorMessage", e.getMessage(),
                "stackTrace", getStackTraceAsString(e),
                "timestamp", LocalDateTime.now().toString()
            ));
            
            // Rethrow to trigger retry mechanism
            throw new RuntimeException("Failed to process KYC expiration", e);
        }
    }
    
    /**
     * Determines the severity of KYC expiration based on multiple factors.
     */
    private KYCExpirationSeverity determineExpirationSeverity(
            String riskLevel, 
            boolean gracePeriodExpired,
            LocalDateTime expiredAt) {
        
        // Calculate days since expiration
        long daysSinceExpiration = java.time.Duration.between(expiredAt, LocalDateTime.now()).toDays();
        
        // High-risk users = immediate action
        if ("HIGH".equals(riskLevel) || "VERY_HIGH".equals(riskLevel)) {
            return gracePeriodExpired ? 
                KYCExpirationSeverity.CRITICAL_IMMEDIATE_ACTION : 
                KYCExpirationSeverity.HIGH_URGENT;
        }
        
        // Grace period expired = escalate
        if (gracePeriodExpired) {
            return KYCExpirationSeverity.HIGH_URGENT;
        }
        
        // Escalate based on time elapsed
        if (daysSinceExpiration > ESCALATION_THRESHOLD_DAYS) {
            return KYCExpirationSeverity.HIGH_URGENT;
        } else if (daysSinceExpiration > GRACE_PERIOD_DAYS) {
            return KYCExpirationSeverity.MEDIUM_WARNING;
        }
        
        return KYCExpirationSeverity.LOW_NOTIFICATION;
    }
    
    /**
     * Updates KYC record status to reflect expiration.
     */
    private void updateKYCRecordStatus(KYCRecord kycRecord, LocalDateTime expiredAt, 
                                      boolean gracePeriodExpired) {
        try {
            kycRecord.setStatus(KYCStatus.EXPIRED);
            kycRecord.setExpiredAt(expiredAt);
            kycRecord.setGracePeriodExpired(gracePeriodExpired);
            kycRecord.setRequiresReverification(true);
            kycRecord.setUpdatedAt(LocalDateTime.now());
            
            kycRepository.save(kycRecord);
            
            log.info("Updated KYC record {} to EXPIRED status", kycRecord.getId());
            
        } catch (Exception e) {
            log.error("Failed to update KYC record status: {}", kycRecord.getId(), e);
            throw new RuntimeException("Failed to update KYC status", e);
        }
    }
    
    /**
     * Applies account restrictions based on expiration severity.
     */
    private void applyAccountRestrictions(String userId, String accountId, 
                                         KYCRecord kycRecord, 
                                         KYCExpirationSeverity severity) {
        try {
            log.info("Applying account restrictions for user {} - Severity: {}", userId, severity);
            
            switch (severity) {
                case CRITICAL_IMMEDIATE_ACTION:
                    // Full account freeze for high-risk expired KYC
                    userAccountService.freezeAccount(userId, accountId,
                        "KYC verification expired - High risk user",
                        "COMPLIANCE_KYC_EXPIRED");
                    
                    // Disable all transactions
                    userAccountService.disableTransactions(userId,
                        "KYC_EXPIRED_HIGH_RISK");
                    
                    log.error("ACCOUNT FROZEN: User {} - High-risk KYC expired", userId);
                    break;
                    
                case HIGH_URGENT:
                    // Restrict high-value transactions
                    userAccountService.applyTransactionLimits(userId,
                        new java.math.BigDecimal("100.00"), // Daily limit
                        "KYC_EXPIRED_RESTRICTIONS");
                    
                    // Disable withdrawals
                    userAccountService.disableWithdrawals(userId,
                        "KYC_EXPIRED_GRACE_PERIOD");
                    
                    log.warn("RESTRICTED: User {} - High-value transactions and withdrawals disabled", userId);
                    break;
                    
                case MEDIUM_WARNING:
                    // Warning mode - reduce limits
                    userAccountService.applyTransactionLimits(userId,
                        new java.math.BigDecimal("500.00"), // Reduced daily limit
                        "KYC_EXPIRING_SOON");
                    
                    log.warn("LIMITED: User {} - Reduced transaction limits applied", userId);
                    break;
                    
                case LOW_NOTIFICATION:
                    // Notification only - no immediate restrictions
                    log.info("NOTIFICATION: User {} - KYC expired but within grace period", userId);
                    break;
            }
            
        } catch (Exception e) {
            log.error("Error applying account restrictions for user: {}", userId, e);
            // Critical - rethrow to trigger retry
            throw new RuntimeException("Failed to apply account restrictions", e);
        }
    }
    
    /**
     * Creates a compliance alert for KYC expiration.
     */
    private void createKYCExpirationAlert(KYCRecord kycRecord, KYCExpirationSeverity severity) {
        try {
            ComplianceAlert alert = ComplianceAlert.builder()
                    .id(UUID.randomUUID())
                    .alertType(ComplianceAlertType.KYC_EXPIRED)
                    .relatedEntityId(kycRecord.getId().toString())
                    .relatedEntityType("KYC_RECORD")
                    .userId(kycRecord.getUserId())
                    .accountId(kycRecord.getAccountId())
                    .severity(mapSeverityToAlertSeverity(severity))
                    .description("KYC verification expired - " + kycRecord.getVerificationType())
                    .status(AlertStatus.OPEN)
                    .deadline(LocalDateTime.now().plusDays(GRACE_PERIOD_DAYS))
                    .assignedTo("COMPLIANCE_TEAM")
                    .requiresAction(severity == KYCExpirationSeverity.CRITICAL_IMMEDIATE_ACTION)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            alertRepository.save(alert);
            
            log.info("Created compliance alert {} for expired KYC {}", alert.getId(), kycRecord.getId());
            
        } catch (Exception e) {
            log.error("Failed to create compliance alert for KYC: {}", kycRecord.getId(), e);
            // Non-critical - log but don't fail
        }
    }
    
    /**
     * Initiates the re-verification workflow for expired KYC.
     */
    private void initiateReVerificationWorkflow(KYCRecord kycRecord, KYCExpirationSeverity severity) {
        try {
            log.info("Initiating re-verification workflow for KYC: {}", kycRecord.getId());
            
            // Create re-verification request
            kycService.createReVerificationRequest(
                kycRecord.getUserId(),
                kycRecord.getVerificationType(),
                severity == KYCExpirationSeverity.CRITICAL_IMMEDIATE_ACTION // isUrgent
            );
            
            // Generate document upload links
            kycService.generateDocumentUploadLinks(kycRecord.getUserId());
            
            // Schedule follow-up reminders
            kycService.scheduleReVerificationReminders(
                kycRecord.getUserId(),
                severity
            );
            
            log.info("Re-verification workflow initiated for user: {}", kycRecord.getUserId());
            
        } catch (Exception e) {
            log.error("Error initiating re-verification workflow: {}", kycRecord.getId(), e);
            // Non-critical - log but don't fail
        }
    }
    
    /**
     * Sends notifications about KYC expiration.
     */
    private void sendExpirationNotifications(KYCRecord kycRecord, String userId, 
                                            KYCExpirationSeverity severity, 
                                            boolean gracePeriodExpired) {
        try {
            // User notifications
            switch (severity) {
                case CRITICAL_IMMEDIATE_ACTION:
                    notificationService.sendCriticalKYCExpirationToUser(
                        userId,
                        "Your account has been restricted due to expired verification. " +
                        "Please complete re-verification immediately to restore access."
                    );
                    notificationService.sendSMSToUser(userId,
                        "URGENT: Your Waqiti account is restricted. Complete verification now.");
                    break;
                    
                case HIGH_URGENT:
                    notificationService.sendUrgentKYCExpirationToUser(
                        userId,
                        "Your account will be restricted soon. Please complete re-verification within 7 days."
                    );
                    break;
                    
                case MEDIUM_WARNING:
                case LOW_NOTIFICATION:
                    notificationService.sendKYCExpirationWarningToUser(
                        userId,
                        "Your verification documents are expiring. Please update your information."
                    );
                    break;
            }
            
            // Compliance team notifications
            if (severity == KYCExpirationSeverity.CRITICAL_IMMEDIATE_ACTION) {
                notificationService.sendCriticalKYCExpirationToCompliance(kycRecord, userId);
            }
            
            log.info("Sent expiration notifications for user: {}", userId);
            
        } catch (Exception e) {
            log.error("Error sending expiration notifications for user: {}", userId, e);
            // Non-critical - log but don't fail
        }
    }
    
    private String getAppliedRestrictions(KYCExpirationSeverity severity) {
        return switch (severity) {
            case CRITICAL_IMMEDIATE_ACTION -> "ACCOUNT_FROZEN,TRANSACTIONS_DISABLED";
            case HIGH_URGENT -> "WITHDRAWALS_DISABLED,TRANSACTION_LIMITS_$100";
            case MEDIUM_WARNING -> "TRANSACTION_LIMITS_$500";
            case LOW_NOTIFICATION -> "NONE";
        };
    }
    
    private AlertSeverity mapSeverityToAlertSeverity(KYCExpirationSeverity severity) {
        return switch (severity) {
            case CRITICAL_IMMEDIATE_ACTION -> AlertSeverity.CRITICAL;
            case HIGH_URGENT -> AlertSeverity.HIGH;
            case MEDIUM_WARNING -> AlertSeverity.MEDIUM;
            case LOW_NOTIFICATION -> AlertSeverity.LOW;
        };
    }
    
    private void publishKYCExpirationProcessingFailed(String kycRecordId, String userId, 
                                                      String errorCode, String errorMessage) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "compliance-kyc-expiration-processing-failed",
                "kycRecordId", kycRecordId != null ? kycRecordId : "unknown",
                "userId", userId != null ? userId : "unknown",
                "errorCode", errorCode,
                "errorMessage", errorMessage,
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("compliance.kyc.expiration.failed", userId, event);
            
            log.debug("Published KYC expiration processing failed event: userId={}, error={}", 
                userId, errorCode);
            
        } catch (Exception e) {
            log.error("Failed to publish KYC expiration failed event", e);
            // Last resort - audit
            try {
                auditService.logSecurityEvent("KYC_EXPIRATION_EVENT_PUBLISH_FAILURE", Map.of(
                    "kycRecordId", kycRecordId != null ? kycRecordId : "unknown",
                    "userId", userId != null ? userId : "unknown",
                    "errorCode", errorCode,
                    "publishError", e.getMessage()
                ));
            } catch (Exception auditException) {
                log.error("CRITICAL: Unable to audit or publish KYC expiration failure", auditException);
            }
        }
    }
    
    private String getStackTraceAsString(Exception e) {
        return org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e);
    }
}