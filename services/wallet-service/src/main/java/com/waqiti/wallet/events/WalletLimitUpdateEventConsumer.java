package com.waqiti.wallet.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.service.WalletLimitService;
import com.waqiti.wallet.service.WalletNotificationService;
import com.waqiti.wallet.service.ComprehensiveTransactionAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Event consumer for wallet limit update events.
 * 
 * Handles various types of wallet limit updates including:
 * - KYC-based limit adjustments
 * - Risk-based limit changes
 * - Compliance-driven restrictions
 * - User-requested limit changes
 * - Temporary restrictions
 * 
 * @author Waqiti Wallet Team
 * @since 2.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletLimitUpdateEventConsumer {

    private final WalletLimitService walletLimitService;
    private final WalletNotificationService notificationService;
    private final ComprehensiveTransactionAuditService auditService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;

    /**
     * Handle wallet limit update events
     */
    @KafkaListener(topics = "wallet.limit.update", groupId = "wallet-service")
    @Transactional
    public void handleWalletLimitUpdate(@Payload String payload,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                       @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                       @Header(KafkaHeaders.OFFSET) long offset,
                                       Acknowledgment acknowledgment) {
        
        log.info("Received wallet limit update event from topic: {} partition: {} offset: {}", 
                topic, partition, offset);
        
        try {
            WalletLimitUpdateEvent event = objectMapper.readValue(payload, WalletLimitUpdateEvent.class);
            
            log.info("Processing wallet limit update for wallet: {} type: {} reason: {}", 
                    event.getWalletId(), event.getUpdateType(), event.getReason());
            
            processLimitUpdate(event);
            
            // Acknowledge message processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed wallet limit update for wallet: {}", event.getWalletId());
            
        } catch (Exception e) {
            log.error("Failed to process wallet limit update event: {}", payload, e);

            WalletLimitUpdateEvent event = null;
            try {
                event = objectMapper.readValue(payload, WalletLimitUpdateEvent.class);
            } catch (Exception parseError) {
                log.error("Failed to parse event for DLQ handling", parseError);
            }

            if (event != null) {
                dlqHandler.handleFailedMessage(event, topic, partition, offset, e)
                    .thenAccept(result -> log.info("Wallet limit update event sent to DLQ: walletId={}, destination={}, category={}",
                            event.getWalletId(), result.getDestinationTopic(), result.getFailureCategory()))
                    .exceptionally(dlqError -> {
                        log.error("CRITICAL: DLQ handling failed for wallet limit update event - MESSAGE MAY BE LOST! " +
                                "partition={}, offset={}, error={}",
                                partition, offset, dlqError.getMessage(), dlqError);
                        return null;
                    });
            }

            // Don't acknowledge - let it be retried
            throw new RuntimeException("Failed to process wallet limit update", e);
        }
    }

    /**
     * Handle KYC level change events
     */
    @KafkaListener(topics = "kyc.level.changed", groupId = "wallet-service")
    @Transactional
    public void handleKycLevelChanged(@Payload String payload,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                     Acknowledgment acknowledgment) {
        
        log.info("Received KYC level change event from topic: {}", topic);
        
        try {
            KycLevelChangeEvent event = objectMapper.readValue(payload, KycLevelChangeEvent.class);
            
            log.info("Processing KYC level change for user: {} new level: {}", 
                    event.getUserId(), event.getNewLevel());
            
            // Apply new limits based on KYC level
            applyKycBasedLimits(event);
            
            acknowledgment.acknowledge();
            
            log.info("Successfully applied KYC-based limits for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process KYC level change event: {}", payload, e);

            KycLevelChangeEvent event = null;
            try {
                event = objectMapper.readValue(payload, KycLevelChangeEvent.class);
            } catch (Exception parseError) {
                log.error("Failed to parse event for DLQ handling", parseError);
            }

            if (event != null) {
                dlqHandler.handleFailedMessage(event, topic, 0, 0L, e)
                    .thenAccept(result -> log.info("KYC level change event sent to DLQ: userId={}, destination={}, category={}",
                            event.getUserId(), result.getDestinationTopic(), result.getFailureCategory()))
                    .exceptionally(dlqError -> {
                        log.error("CRITICAL: DLQ handling failed for KYC level change event - MESSAGE MAY BE LOST! " +
                                "error={}", dlqError.getMessage(), dlqError);
                        return null;
                    });
            }

            throw new RuntimeException("Failed to process KYC level change", e);
        }
    }

    /**
     * Handle risk score update events
     */
    @KafkaListener(topics = "risk.score.updated", groupId = "wallet-service")
    @Transactional
    public void handleRiskScoreUpdate(@Payload String payload,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                     Acknowledgment acknowledgment) {
        
        log.info("Received risk score update event from topic: {}", topic);
        
        try {
            RiskScoreUpdateEvent event = objectMapper.readValue(payload, RiskScoreUpdateEvent.class);
            
            log.info("Processing risk score update for wallet: {} new score: {}", 
                    event.getWalletId(), event.getNewRiskScore());
            
            // Adjust limits based on risk score
            adjustLimitsBasedOnRisk(event);
            
            acknowledgment.acknowledge();
            
            log.info("Successfully adjusted limits based on risk for wallet: {}", event.getWalletId());
            
        } catch (Exception e) {
            log.error("Failed to process risk score update event: {}", payload, e);

            RiskScoreUpdateEvent event = null;
            try {
                event = objectMapper.readValue(payload, RiskScoreUpdateEvent.class);
            } catch (Exception parseError) {
                log.error("Failed to parse event for DLQ handling", parseError);
            }

            if (event != null) {
                dlqHandler.handleFailedMessage(event, topic, 0, 0L, e)
                    .thenAccept(result -> log.info("Risk score update event sent to DLQ: walletId={}, destination={}, category={}",
                            event.getWalletId(), result.getDestinationTopic(), result.getFailureCategory()))
                    .exceptionally(dlqError -> {
                        log.error("CRITICAL: DLQ handling failed for risk score update event - MESSAGE MAY BE LOST! " +
                                "error={}", dlqError.getMessage(), dlqError);
                        return null;
                    });
            }

            throw new RuntimeException("Failed to process risk score update", e);
        }
    }

    /**
     * Handle compliance restriction events
     */
    @KafkaListener(topics = "compliance.restriction.applied", groupId = "wallet-service")
    @Transactional
    public void handleComplianceRestriction(@Payload String payload,
                                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                          Acknowledgment acknowledgment) {
        
        log.info("Received compliance restriction event from topic: {}", topic);
        
        try {
            ComplianceRestrictionEvent event = objectMapper.readValue(payload, ComplianceRestrictionEvent.class);
            
            log.info("Processing compliance restriction for wallet: {} type: {}", 
                    event.getWalletId(), event.getRestrictionType());
            
            // Apply compliance-driven restrictions
            applyComplianceRestriction(event);
            
            acknowledgment.acknowledge();
            
            log.info("Successfully applied compliance restriction for wallet: {}", event.getWalletId());
            
        } catch (Exception e) {
            log.error("Failed to process compliance restriction event: {}", payload, e);

            ComplianceRestrictionEvent event = null;
            try {
                event = objectMapper.readValue(payload, ComplianceRestrictionEvent.class);
            } catch (Exception parseError) {
                log.error("Failed to parse event for DLQ handling", parseError);
            }

            if (event != null) {
                dlqHandler.handleFailedMessage(event, topic, 0, 0L, e)
                    .thenAccept(result -> log.info("Compliance restriction event sent to DLQ: walletId={}, destination={}, category={}",
                            event.getWalletId(), result.getDestinationTopic(), result.getFailureCategory()))
                    .exceptionally(dlqError -> {
                        log.error("CRITICAL: DLQ handling failed for compliance restriction event - MESSAGE MAY BE LOST! " +
                                "error={}", dlqError.getMessage(), dlqError);
                        return null;
                    });
            }

            throw new RuntimeException("Failed to process compliance restriction", e);
        }
    }

    // Private helper methods

    private void processLimitUpdate(WalletLimitUpdateEvent event) {
        try {
            UUID walletId = UUID.fromString(event.getWalletId());
            
            switch (event.getUpdateType()) {
                case "DAILY_LIMIT":
                    walletLimitService.updateDailyLimit(walletId, event.getNewDailyLimit(), event.getReason());
                    break;
                
                case "MONTHLY_LIMIT":
                    walletLimitService.updateMonthlyLimit(walletId, event.getNewMonthlyLimit(), event.getReason());
                    break;
                
                case "SINGLE_TRANSACTION_LIMIT":
                    walletLimitService.updateSingleTransactionLimit(walletId, event.getNewSingleTransactionLimit(), event.getReason());
                    break;
                
                case "ALL_LIMITS":
                    walletLimitService.updateAllLimits(
                        walletId,
                        event.getNewDailyLimit(),
                        event.getNewMonthlyLimit(),
                        event.getNewSingleTransactionLimit(),
                        event.getReason()
                    );
                    break;
                
                case "TEMPORARY_RESTRICTION":
                    walletLimitService.applyTemporaryRestriction(
                        walletId,
                        event.getTemporaryLimit(),
                        event.getRestrictionUntil(),
                        event.getReason()
                    );
                    break;
                
                case "REMOVE_RESTRICTION":
                    walletLimitService.removeTemporaryRestriction(walletId);
                    break;
                
                default:
                    log.warn("Unknown update type: {} for wallet: {}", event.getUpdateType(), walletId);
            }
            
            // Audit the limit change
            auditLimitChange(event);
            
            // Send notification to user
            notifyUser(event);
            
        } catch (Exception e) {
            log.error("Failed to process limit update for wallet: {}", event.getWalletId(), e);
            throw e;
        }
    }

    private void applyKycBasedLimits(KycLevelChangeEvent event) {
        try {
            UUID walletId = UUID.fromString(event.getWalletId());
            
            // Define limits based on KYC level
            BigDecimal dailyLimit;
            BigDecimal monthlyLimit;
            BigDecimal singleTransactionLimit;
            
            switch (event.getNewLevel()) {
                case "BASIC":
                    dailyLimit = new BigDecimal("500");
                    monthlyLimit = new BigDecimal("2500");
                    singleTransactionLimit = new BigDecimal("250");
                    break;
                
                case "STANDARD":
                    dailyLimit = new BigDecimal("2500");
                    monthlyLimit = new BigDecimal("10000");
                    singleTransactionLimit = new BigDecimal("1000");
                    break;
                
                case "ENHANCED":
                    dailyLimit = new BigDecimal("10000");
                    monthlyLimit = new BigDecimal("50000");
                    singleTransactionLimit = new BigDecimal("5000");
                    break;
                
                case "PREMIUM":
                    dailyLimit = new BigDecimal("50000");
                    monthlyLimit = new BigDecimal("250000");
                    singleTransactionLimit = new BigDecimal("25000");
                    break;
                
                default:
                    log.warn("Unknown KYC level: {} for wallet: {}", event.getNewLevel(), walletId);
                    return;
            }
            
            walletLimitService.updateAllLimits(
                walletId,
                dailyLimit,
                monthlyLimit,
                singleTransactionLimit,
                "KYC level changed to " + event.getNewLevel()
            );
            
            // Send notification
            Map<String, Object> notificationData = Map.of(
                "type", "KYC_LIMIT_UPDATE",
                "oldLevel", event.getOldLevel(),
                "newLevel", event.getNewLevel(),
                "newDailyLimit", dailyLimit,
                "newMonthlyLimit", monthlyLimit
            );
            
            notificationService.sendNotification(
                event.getUserId(),
                "KYC Level Updated",
                "Your transaction limits have been updated based on your new KYC level",
                notificationData
            );
            
        } catch (Exception e) {
            log.error("Failed to apply KYC-based limits for user: {}", event.getUserId(), e);
            throw e;
        }
    }

    private void adjustLimitsBasedOnRisk(RiskScoreUpdateEvent event) {
        try {
            UUID walletId = UUID.fromString(event.getWalletId());
            double riskScore = event.getNewRiskScore();
            
            // Risk-based limit adjustment logic
            BigDecimal limitMultiplier;
            String restrictionReason = null;
            
            if (riskScore >= 80) {
                // Very high risk - severe restrictions
                limitMultiplier = new BigDecimal("0.1"); // 10% of normal limits
                restrictionReason = "Very high risk detected";
            } else if (riskScore >= 60) {
                // High risk - significant restrictions
                limitMultiplier = new BigDecimal("0.25"); // 25% of normal limits
                restrictionReason = "High risk detected";
            } else if (riskScore >= 40) {
                // Medium risk - moderate restrictions
                limitMultiplier = new BigDecimal("0.5"); // 50% of normal limits
                restrictionReason = "Elevated risk detected";
            } else if (riskScore >= 20) {
                // Low risk - slight restrictions
                limitMultiplier = new BigDecimal("0.75"); // 75% of normal limits
                restrictionReason = "Minor risk detected";
            } else {
                // Minimal risk - normal limits
                limitMultiplier = new BigDecimal("1.0");
            }
            
            // Apply temporary restriction if risk is elevated
            if (riskScore >= 20) {
                BigDecimal temporaryLimit = new BigDecimal("1000").multiply(limitMultiplier);
                LocalDateTime restrictionUntil = LocalDateTime.now().plusDays(7); // 7-day restriction
                
                walletLimitService.applyTemporaryRestriction(
                    walletId,
                    temporaryLimit,
                    restrictionUntil,
                    restrictionReason + " (Risk Score: " + riskScore + ")"
                );
                
                // Send high-priority notification for high risk
                if (riskScore >= 60) {
                    Map<String, Object> alertData = Map.of(
                        "type", "HIGH_RISK_ALERT",
                        "riskScore", riskScore,
                        "restrictionApplied", true,
                        "newLimit", temporaryLimit,
                        "restrictionUntil", restrictionUntil
                    );
                    
                    notificationService.sendUrgentNotification(
                        event.getUserId(),
                        "Security Alert: Account Restrictions Applied",
                        "Temporary restrictions have been applied to your account due to elevated risk",
                        alertData
                    );
                }
            }
            
            // Audit risk-based adjustment
            auditService.logRiskBasedAction(
                walletId.toString(),
                "LIMIT_ADJUSTMENT",
                event.getOldRiskScore(),
                event.getNewRiskScore(),
                restrictionReason
            );
            
        } catch (Exception e) {
            log.error("Failed to adjust limits based on risk for wallet: {}", event.getWalletId(), e);
            throw e;
        }
    }

    private void applyComplianceRestriction(ComplianceRestrictionEvent event) {
        try {
            UUID walletId = UUID.fromString(event.getWalletId());
            
            switch (event.getRestrictionType()) {
                case "SANCTIONS_MATCH":
                    // Severe restriction for sanctions match
                    walletLimitService.updateAllLimits(
                        walletId,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "Compliance restriction: Sanctions screening match"
                    );
                    break;
                
                case "PEP_IDENTIFIED":
                    // Enhanced monitoring limits for PEP
                    walletLimitService.updateAllLimits(
                        walletId,
                        new BigDecimal("5000"),
                        new BigDecimal("25000"),
                        new BigDecimal("2500"),
                        "Compliance restriction: PEP identification"
                    );
                    break;
                
                case "SUSPICIOUS_ACTIVITY":
                    // Temporary restriction pending investigation
                    walletLimitService.applyTemporaryRestriction(
                        walletId,
                        new BigDecimal("100"),
                        LocalDateTime.now().plusDays(14),
                        "Compliance restriction: Suspicious activity under review"
                    );
                    break;
                
                case "REGULATORY_REQUIREMENT":
                    // Apply regulatory-mandated limits
                    BigDecimal regulatoryLimit = event.getRegulatedLimit() != null ? 
                        event.getRegulatedLimit() : new BigDecimal("1000");
                    
                    walletLimitService.updateAllLimits(
                        walletId,
                        regulatoryLimit,
                        regulatoryLimit.multiply(new BigDecimal("30")),
                        regulatoryLimit.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP),
                        "Compliance restriction: Regulatory requirement"
                    );
                    break;
                
                case "COURT_ORDER":
                    // Complete freeze per court order
                    walletLimitService.updateAllLimits(
                        walletId,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "Compliance restriction: Court order - Account frozen"
                    );
                    break;
                
                default:
                    log.warn("Unknown compliance restriction type: {} for wallet: {}", 
                            event.getRestrictionType(), walletId);
            }
            
            // Send compliance notification
            notificationService.sendComplianceNotification(
                event.getUserId(),
                "Account Restriction Applied",
                "Your account has been restricted due to compliance requirements. Please contact support for details.",
                event.getRestrictionType(),
                event.getCaseNumber()
            );
            
            // Log compliance action
            auditService.logComplianceAction(
                walletId.toString(),
                event.getRestrictionType(),
                event.getAppliedBy(),
                event.getCaseNumber(),
                event.getAdditionalDetails()
            );
            
        } catch (Exception e) {
            log.error("Failed to apply compliance restriction for wallet: {}", event.getWalletId(), e);
            throw e;
        }
    }

    private void auditLimitChange(WalletLimitUpdateEvent event) {
        try {
            Map<String, Object> auditData = Map.of(
                "updateType", event.getUpdateType(),
                "oldDailyLimit", event.getOldDailyLimit() != null ? event.getOldDailyLimit() : "N/A",
                "newDailyLimit", event.getNewDailyLimit() != null ? event.getNewDailyLimit() : "N/A",
                "oldMonthlyLimit", event.getOldMonthlyLimit() != null ? event.getOldMonthlyLimit() : "N/A",
                "newMonthlyLimit", event.getNewMonthlyLimit() != null ? event.getNewMonthlyLimit() : "N/A",
                "reason", event.getReason(),
                "requestedBy", event.getRequestedBy()
            );
            
            auditService.logWalletLimitChange(
                event.getWalletId(),
                event.getUpdateType(),
                auditData
            );
            
        } catch (Exception e) {
            log.error("Failed to audit limit change for wallet: {}", event.getWalletId(), e);
            // Don't throw - auditing failure shouldn't stop processing
        }
    }

    private void notifyUser(WalletLimitUpdateEvent event) {
        try {
            String title = "Transaction Limits Updated";
            String message = buildNotificationMessage(event);
            
            Map<String, Object> notificationData = Map.of(
                "type", "LIMIT_UPDATE",
                "updateType", event.getUpdateType(),
                "reason", event.getReason(),
                "timestamp", LocalDateTime.now()
            );
            
            notificationService.sendNotification(
                event.getUserId(),
                title,
                message,
                notificationData
            );
            
        } catch (Exception e) {
            log.error("Failed to notify user about limit change: {}", event.getUserId(), e);
            // Don't throw - notification failure shouldn't stop processing
        }
    }

    private String buildNotificationMessage(WalletLimitUpdateEvent event) {
        StringBuilder message = new StringBuilder("Your wallet limits have been updated. ");
        
        if (event.getNewDailyLimit() != null) {
            message.append("New daily limit: $").append(event.getNewDailyLimit()).append(". ");
        }
        if (event.getNewMonthlyLimit() != null) {
            message.append("New monthly limit: $").append(event.getNewMonthlyLimit()).append(". ");
        }
        if (event.getReason() != null) {
            message.append("Reason: ").append(event.getReason());
        }
        
        return message.toString();
    }

    // Event DTOs

    public static class WalletLimitUpdateEvent {
        private String walletId;
        private String userId;
        private String updateType;
        private BigDecimal oldDailyLimit;
        private BigDecimal newDailyLimit;
        private BigDecimal oldMonthlyLimit;
        private BigDecimal newMonthlyLimit;
        private BigDecimal oldSingleTransactionLimit;
        private BigDecimal newSingleTransactionLimit;
        private BigDecimal temporaryLimit;
        private LocalDateTime restrictionUntil;
        private String reason;
        private String requestedBy;
        private LocalDateTime timestamp;

        // Getters and setters
        public String getWalletId() { return walletId; }
        public void setWalletId(String walletId) { this.walletId = walletId; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getUpdateType() { return updateType; }
        public void setUpdateType(String updateType) { this.updateType = updateType; }
        public BigDecimal getOldDailyLimit() { return oldDailyLimit; }
        public void setOldDailyLimit(BigDecimal oldDailyLimit) { this.oldDailyLimit = oldDailyLimit; }
        public BigDecimal getNewDailyLimit() { return newDailyLimit; }
        public void setNewDailyLimit(BigDecimal newDailyLimit) { this.newDailyLimit = newDailyLimit; }
        public BigDecimal getOldMonthlyLimit() { return oldMonthlyLimit; }
        public void setOldMonthlyLimit(BigDecimal oldMonthlyLimit) { this.oldMonthlyLimit = oldMonthlyLimit; }
        public BigDecimal getNewMonthlyLimit() { return newMonthlyLimit; }
        public void setNewMonthlyLimit(BigDecimal newMonthlyLimit) { this.newMonthlyLimit = newMonthlyLimit; }
        public BigDecimal getOldSingleTransactionLimit() { return oldSingleTransactionLimit; }
        public void setOldSingleTransactionLimit(BigDecimal oldSingleTransactionLimit) { this.oldSingleTransactionLimit = oldSingleTransactionLimit; }
        public BigDecimal getNewSingleTransactionLimit() { return newSingleTransactionLimit; }
        public void setNewSingleTransactionLimit(BigDecimal newSingleTransactionLimit) { this.newSingleTransactionLimit = newSingleTransactionLimit; }
        public BigDecimal getTemporaryLimit() { return temporaryLimit; }
        public void setTemporaryLimit(BigDecimal temporaryLimit) { this.temporaryLimit = temporaryLimit; }
        public LocalDateTime getRestrictionUntil() { return restrictionUntil; }
        public void setRestrictionUntil(LocalDateTime restrictionUntil) { this.restrictionUntil = restrictionUntil; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getRequestedBy() { return requestedBy; }
        public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    public static class KycLevelChangeEvent {
        private String userId;
        private String walletId;
        private String oldLevel;
        private String newLevel;
        private LocalDateTime timestamp;

        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getWalletId() { return walletId; }
        public void setWalletId(String walletId) { this.walletId = walletId; }
        public String getOldLevel() { return oldLevel; }
        public void setOldLevel(String oldLevel) { this.oldLevel = oldLevel; }
        public String getNewLevel() { return newLevel; }
        public void setNewLevel(String newLevel) { this.newLevel = newLevel; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    public static class RiskScoreUpdateEvent {
        private String walletId;
        private String userId;
        private double oldRiskScore;
        private double newRiskScore;
        private String riskFactors;
        private LocalDateTime timestamp;

        // Getters and setters
        public String getWalletId() { return walletId; }
        public void setWalletId(String walletId) { this.walletId = walletId; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public double getOldRiskScore() { return oldRiskScore; }
        public void setOldRiskScore(double oldRiskScore) { this.oldRiskScore = oldRiskScore; }
        public double getNewRiskScore() { return newRiskScore; }
        public void setNewRiskScore(double newRiskScore) { this.newRiskScore = newRiskScore; }
        public String getRiskFactors() { return riskFactors; }
        public void setRiskFactors(String riskFactors) { this.riskFactors = riskFactors; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    public static class ComplianceRestrictionEvent {
        private String walletId;
        private String userId;
        private String restrictionType;
        private BigDecimal regulatedLimit;
        private String caseNumber;
        private String appliedBy;
        private Map<String, Object> additionalDetails;
        private LocalDateTime timestamp;

        // Getters and setters
        public String getWalletId() { return walletId; }
        public void setWalletId(String walletId) { this.walletId = walletId; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getRestrictionType() { return restrictionType; }
        public void setRestrictionType(String restrictionType) { this.restrictionType = restrictionType; }
        public BigDecimal getRegulatedLimit() { return regulatedLimit; }
        public void setRegulatedLimit(BigDecimal regulatedLimit) { this.regulatedLimit = regulatedLimit; }
        public String getCaseNumber() { return caseNumber; }
        public void setCaseNumber(String caseNumber) { this.caseNumber = caseNumber; }
        public String getAppliedBy() { return appliedBy; }
        public void setAppliedBy(String appliedBy) { this.appliedBy = appliedBy; }
        public Map<String, Object> getAdditionalDetails() { return additionalDetails; }
        public void setAdditionalDetails(Map<String, Object> additionalDetails) { this.additionalDetails = additionalDetails; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
}