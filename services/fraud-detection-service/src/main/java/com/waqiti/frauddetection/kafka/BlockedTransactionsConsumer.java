package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.frauddetection.service.FraudBlockingService;
import com.waqiti.frauddetection.service.TransactionReviewService;
import com.waqiti.frauddetection.service.FraudAnalyticsService;
import com.waqiti.frauddetection.service.FraudNotificationService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FRAUD PREVENTION CONSUMER: Blocked Transactions
 * 
 * Processes blocked transaction events when fraud detection systems
 * have identified and blocked suspicious transactions.
 * 
 * KEY RESPONSIBILITIES:
 * - Record blocked transaction details
 * - Create fraud investigation cases
 * - Notify users and fraud team
 * - Track fraud patterns
 * - Generate fraud reports
 * - Update risk models
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BlockedTransactionsConsumer {
    
    private final FraudBlockingService fraudBlockingService;
    private final TransactionReviewService transactionReviewService;
    private final FraudAnalyticsService fraudAnalyticsService;
    private final FraudNotificationService fraudNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = "blocked-transactions",
        groupId = "fraud-service-blocked-transactions-group",
        containerFactory = "criticalFraudKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 15000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleBlockedTransaction(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.warn("FRAUD BLOCK: Processing blocked transaction - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID transactionId = null;
        UUID userId = null;
        String blockReason = null;
        
        try {
            // Parse blocked transaction event
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            transactionId = UUID.fromString((String) event.get("transactionId"));
            userId = UUID.fromString((String) event.get("userId"));
            String transactionType = (String) event.get("transactionType");
            BigDecimal amount = new BigDecimal(event.get("amount").toString());
            String currency = (String) event.get("currency");
            blockReason = (String) event.get("blockReason");
            String blockType = (String) event.get("blockType"); // RULE_BASED, ML_MODEL, MANUAL
            Integer riskScore = event.containsKey("riskScore") ? (Integer) event.get("riskScore") : 0;
            @SuppressWarnings("unchecked")
            List<String> fraudIndicators = (List<String>) event.getOrDefault("fraudIndicators", List.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> transactionDetails = (Map<String, Object>) event.getOrDefault("transactionDetails", Map.of());
            LocalDateTime blockTimestamp = LocalDateTime.parse((String) event.get("blockTimestamp"));
            String merchantId = (String) event.get("merchantId");
            String deviceId = (String) event.get("deviceId");
            String ipAddress = (String) event.get("ipAddress");
            String location = (String) event.get("location");
            
            log.warn("BLOCKED TRANSACTION - TransactionId: {}, UserId: {}, Type: {}, Amount: {} {}, Reason: {}, BlockType: {}, RiskScore: {}, Indicators: {}", 
                    transactionId, userId, transactionType, amount, currency, blockReason, 
                    blockType, riskScore, fraudIndicators.size());
            
            // Validate blocked transaction data
            validateBlockedTransaction(transactionId, userId, blockReason, amount);
            
            // Record blocked transaction
            String caseId = recordBlockedTransaction(transactionId, userId, transactionType, amount, 
                    currency, blockReason, blockType, riskScore, fraudIndicators, transactionDetails,
                    blockTimestamp, merchantId, deviceId, ipAddress, location);
            
            // Create fraud investigation case
            createFraudInvestigationCase(transactionId, caseId, userId, blockReason, blockType, 
                    riskScore, fraudIndicators, amount, currency);
            
            // Analyze fraud patterns
            analyzeFraudPatterns(transactionId, userId, blockReason, fraudIndicators, 
                    transactionType, amount, deviceId, ipAddress, location);
            
            // Update risk models
            updateRiskModels(userId, blockReason, riskScore, fraudIndicators, transactionType);
            
            // Check for related suspicious activity
            checkRelatedActivity(userId, transactionId, blockReason, fraudIndicators);
            
            // Notify user about blocked transaction
            notifyUserOfBlock(userId, transactionId, amount, currency, blockReason, caseId);
            
            // Notify fraud team for high-risk cases
            if (riskScore >= 80 || isHighPriorityBlock(blockReason, fraudIndicators)) {
                notifyFraudTeam(transactionId, caseId, userId, blockReason, riskScore, 
                        fraudIndicators, amount);
            }
            
            // Update fraud metrics
            updateFraudMetrics(blockReason, blockType, riskScore, amount, transactionType);
            
            // Comprehensive audit trail
            auditBlockedTransaction(transactionId, caseId, userId, blockReason, blockType, 
                    riskScore, amount, currency, fraudIndicators, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Blocked transaction processed - TransactionId: {}, CaseId: {}, Reason: {}, ProcessingTime: {}ms", 
                    transactionId, caseId, blockReason, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Blocked transaction processing failed - TransactionId: {}, UserId: {}, Reason: {}, Error: {}", 
                    transactionId, userId, blockReason, e.getMessage(), e);
            
            if (transactionId != null) {
                handleBlockedTransactionFailure(transactionId, userId, blockReason, e);
            }
            
            throw new RuntimeException("Blocked transaction processing failed", e);
        }
    }
    
    private void validateBlockedTransaction(UUID transactionId, UUID userId, String blockReason, BigDecimal amount) {
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID is required");
        }
        
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (blockReason == null || blockReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Block reason is required");
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid transaction amount");
        }
        
        log.debug("Blocked transaction validation passed - TransactionId: {}", transactionId);
    }
    
    private String recordBlockedTransaction(UUID transactionId, UUID userId, String transactionType,
                                           BigDecimal amount, String currency, String blockReason,
                                           String blockType, Integer riskScore, List<String> fraudIndicators,
                                           Map<String, Object> transactionDetails, LocalDateTime blockTimestamp,
                                           String merchantId, String deviceId, String ipAddress, String location) {
        try {
            String caseId = fraudBlockingService.recordBlockedTransaction(
                    transactionId, userId, transactionType, amount, currency, blockReason,
                    blockType, riskScore, fraudIndicators, transactionDetails, blockTimestamp,
                    merchantId, deviceId, ipAddress, location);
            
            log.info("Blocked transaction recorded - TransactionId: {}, CaseId: {}, Reason: {}", 
                    transactionId, caseId, blockReason);
            
            return caseId;
            
        } catch (Exception e) {
            log.error("Failed to record blocked transaction - TransactionId: {}", transactionId, e);
            throw new RuntimeException("Blocked transaction recording failed", e);
        }
    }
    
    private void createFraudInvestigationCase(UUID transactionId, String caseId, UUID userId,
                                             String blockReason, String blockType, Integer riskScore,
                                             List<String> fraudIndicators, BigDecimal amount, String currency) {
        try {
            String investigationPriority = determineInvestigationPriority(riskScore, blockReason, 
                    fraudIndicators, amount);
            
            transactionReviewService.createInvestigationCase(
                    transactionId, caseId, userId, blockReason, blockType, riskScore,
                    fraudIndicators, amount, currency, investigationPriority);
            
            log.info("Fraud investigation case created - TransactionId: {}, CaseId: {}, Priority: {}", 
                    transactionId, caseId, investigationPriority);
            
        } catch (Exception e) {
            log.error("Failed to create fraud investigation case - TransactionId: {}", transactionId, e);
        }
    }
    
    private void analyzeFraudPatterns(UUID transactionId, UUID userId, String blockReason,
                                     List<String> fraudIndicators, String transactionType,
                                     BigDecimal amount, String deviceId, String ipAddress, String location) {
        try {
            fraudAnalyticsService.analyzeFraudPattern(
                    transactionId, userId, blockReason, fraudIndicators, transactionType,
                    amount, deviceId, ipAddress, location);
            
            // Check for velocity patterns
            fraudAnalyticsService.checkVelocityPatterns(userId, transactionType, amount);
            
            // Check for geographic anomalies
            if (location != null) {
                fraudAnalyticsService.checkGeographicAnomalies(userId, location);
            }
            
            // Check for device fingerprint anomalies
            if (deviceId != null) {
                fraudAnalyticsService.checkDeviceAnomalies(userId, deviceId);
            }
            
            log.debug("Fraud pattern analysis completed - TransactionId: {}", transactionId);
            
        } catch (Exception e) {
            log.error("Failed to analyze fraud patterns - TransactionId: {}", transactionId, e);
        }
    }
    
    private void updateRiskModels(UUID userId, String blockReason, Integer riskScore,
                                 List<String> fraudIndicators, String transactionType) {
        try {
            fraudAnalyticsService.updateRiskModel(userId, blockReason, riskScore, 
                    fraudIndicators, transactionType);
            
            // Update ML models with new fraud data
            fraudAnalyticsService.feedMLModel(userId, blockReason, fraudIndicators, 
                    riskScore, transactionType);
            
            log.debug("Risk models updated - UserId: {}, Reason: {}", userId, blockReason);
            
        } catch (Exception e) {
            log.error("Failed to update risk models - UserId: {}", userId, e);
        }
    }
    
    private void checkRelatedActivity(UUID userId, UUID transactionId, String blockReason,
                                     List<String> fraudIndicators) {
        try {
            List<String> relatedTransactions = fraudAnalyticsService.findRelatedSuspiciousActivity(
                    userId, transactionId, blockReason, fraudIndicators);
            
            if (!relatedTransactions.isEmpty()) {
                log.warn("RELATED SUSPICIOUS ACTIVITY FOUND - UserId: {}, TransactionId: {}, RelatedCount: {}", 
                        userId, transactionId, relatedTransactions.size());
                
                // Flag related transactions for review
                transactionReviewService.flagRelatedTransactions(userId, transactionId, 
                        relatedTransactions, blockReason);
            }
            
        } catch (Exception e) {
            log.error("Failed to check related activity - UserId: {}, TransactionId: {}", 
                    userId, transactionId, e);
        }
    }
    
    private void notifyUserOfBlock(UUID userId, UUID transactionId, BigDecimal amount,
                                  String currency, String blockReason, String caseId) {
        try {
            fraudNotificationService.sendBlockNotification(
                    userId, transactionId, amount, currency, blockReason, caseId);
            
            log.info("User notified of blocked transaction - UserId: {}, TransactionId: {}", 
                    userId, transactionId);
            
        } catch (Exception e) {
            log.error("Failed to notify user of block - UserId: {}, TransactionId: {}", 
                    userId, transactionId, e);
        }
    }
    
    private void notifyFraudTeam(UUID transactionId, String caseId, UUID userId, String blockReason,
                                Integer riskScore, List<String> fraudIndicators, BigDecimal amount) {
        try {
            fraudNotificationService.sendFraudTeamAlert(
                    transactionId, caseId, userId, blockReason, riskScore, fraudIndicators, amount);
            
            log.warn("Fraud team notified - TransactionId: {}, CaseId: {}, RiskScore: {}", 
                    transactionId, caseId, riskScore);
            
        } catch (Exception e) {
            log.error("Failed to notify fraud team - TransactionId: {}", transactionId, e);
        }
    }
    
    private void updateFraudMetrics(String blockReason, String blockType, Integer riskScore,
                                   BigDecimal amount, String transactionType) {
        try {
            fraudAnalyticsService.recordBlockMetrics(blockReason, blockType, riskScore, 
                    amount, transactionType);
        } catch (Exception e) {
            log.error("Failed to update fraud metrics - Reason: {}", blockReason, e);
        }
    }
    
    private String determineInvestigationPriority(Integer riskScore, String blockReason,
                                                 List<String> fraudIndicators, BigDecimal amount) {
        if (riskScore >= 90 || amount.compareTo(new BigDecimal("50000")) > 0) {
            return "CRITICAL";
        }
        
        if (riskScore >= 80 || amount.compareTo(new BigDecimal("10000")) > 0 ||
            isHighPriorityBlock(blockReason, fraudIndicators)) {
            return "HIGH";
        }
        
        if (riskScore >= 60 || amount.compareTo(new BigDecimal("1000")) > 0) {
            return "MEDIUM";
        }
        
        return "LOW";
    }
    
    private boolean isHighPriorityBlock(String blockReason, List<String> fraudIndicators) {
        List<String> highPriorityReasons = List.of(
                "ACCOUNT_TAKEOVER", "CARD_TESTING", "IDENTITY_THEFT", 
                "STOLEN_CREDENTIALS", "FRAUDULENT_MERCHANT", "SYNTHETIC_IDENTITY"
        );
        
        if (highPriorityReasons.contains(blockReason)) {
            return true;
        }
        
        List<String> highPriorityIndicators = List.of(
                "STOLEN_CARD", "COMPROMISED_ACCOUNT", "BLACKLIST_MATCH", "KNOWN_FRAUDSTER"
        );
        
        return fraudIndicators.stream().anyMatch(highPriorityIndicators::contains);
    }
    
    private void auditBlockedTransaction(UUID transactionId, String caseId, UUID userId,
                                        String blockReason, String blockType, Integer riskScore,
                                        BigDecimal amount, String currency, List<String> fraudIndicators,
                                        LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFraudEvent(
                    "TRANSACTION_BLOCKED",
                    userId.toString(),
                    String.format("Transaction blocked - Reason: %s, RiskScore: %d, Amount: %s %s", 
                            blockReason, riskScore, amount, currency),
                    Map.of(
                            "transactionId", transactionId.toString(),
                            "caseId", caseId,
                            "userId", userId.toString(),
                            "blockReason", blockReason,
                            "blockType", blockType,
                            "riskScore", riskScore,
                            "amount", amount.toString(),
                            "currency", currency,
                            "fraudIndicators", String.join(",", fraudIndicators),
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit blocked transaction - TransactionId: {}", transactionId, e);
        }
    }
    
    private void handleBlockedTransactionFailure(UUID transactionId, UUID userId, String blockReason, Exception error) {
        try {
            fraudBlockingService.handleBlockProcessingFailure(transactionId, userId, 
                    blockReason, error.getMessage());
            
            auditService.auditFraudEvent(
                    "BLOCKED_TRANSACTION_PROCESSING_FAILED",
                    userId != null ? userId.toString() : "UNKNOWN",
                    "Failed to process blocked transaction: " + error.getMessage(),
                    Map.of(
                            "transactionId", transactionId.toString(),
                            "userId", userId != null ? userId.toString() : "UNKNOWN",
                            "blockReason", blockReason != null ? blockReason : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle blocked transaction failure - TransactionId: {}", transactionId, e);
        }
    }
    
    @KafkaListener(
        topics = "blocked-transactions.DLQ",
        groupId = "fraud-service-blocked-transactions-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Blocked transaction sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID transactionId = event.containsKey("transactionId") ? 
                    UUID.fromString((String) event.get("transactionId")) : null;
            UUID userId = event.containsKey("userId") ? 
                    UUID.fromString((String) event.get("userId")) : null;
            String blockReason = (String) event.get("blockReason");
            
            log.error("DLQ: Blocked transaction processing failed permanently - TransactionId: {}, UserId: {}, Reason: {} - MANUAL REVIEW REQUIRED", 
                    transactionId, userId, blockReason);
            
            if (transactionId != null) {
                fraudBlockingService.markForManualReview(transactionId, userId, blockReason, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse blocked transaction DLQ event: {}", eventJson, e);
        }
    }
}