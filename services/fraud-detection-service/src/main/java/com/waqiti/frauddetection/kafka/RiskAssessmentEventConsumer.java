package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.frauddetection.service.RiskAssessmentService;
import com.waqiti.frauddetection.service.RiskScoringService;
import com.waqiti.frauddetection.service.FraudNotificationService;
import com.waqiti.frauddetection.service.RiskMitigationService;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class RiskAssessmentEventConsumer {
    
    private final RiskAssessmentService riskAssessmentService;
    private final RiskScoringService riskScoringService;
    private final FraudNotificationService fraudNotificationService;
    private final RiskMitigationService riskMitigationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"risk-assessment-events", "risk-assessments", "risk-scoring-events"},
        groupId = "fraud-service-risk-assessment-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleRiskAssessmentEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("RISK ASSESSMENT: Processing risk event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID assessmentId = null;
        UUID userId = null;
        String riskLevel = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            assessmentId = UUID.fromString((String) event.get("assessmentId"));
            userId = event.containsKey("userId") ? UUID.fromString((String) event.get("userId")) : null;
            UUID merchantId = event.containsKey("merchantId") ? 
                    UUID.fromString((String) event.get("merchantId")) : null;
            String assessmentType = (String) event.get("assessmentType");
            riskLevel = (String) event.get("riskLevel");
            Integer riskScore = (Integer) event.get("riskScore");
            String assessmentReason = (String) event.get("assessmentReason");
            UUID transactionId = event.containsKey("transactionId") ? 
                    UUID.fromString((String) event.get("transactionId")) : null;
            BigDecimal transactionAmount = event.containsKey("transactionAmount") ? 
                    new BigDecimal(event.get("transactionAmount").toString()) : BigDecimal.ZERO;
            String currency = (String) event.get("currency");
            LocalDateTime assessmentTimestamp = LocalDateTime.parse((String) event.get("timestamp"));
            @SuppressWarnings("unchecked")
            List<String> riskFactors = (List<String>) event.getOrDefault("riskFactors", List.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> riskIndicators = (Map<String, Object>) event.getOrDefault("riskIndicators", Map.of());
            Boolean requiresManualReview = (Boolean) event.getOrDefault("requiresManualReview", false);
            Boolean isAutomatedDecision = (Boolean) event.getOrDefault("isAutomatedDecision", true);
            String recommendedAction = (String) event.get("recommendedAction");
            
            log.info("Risk assessment - AssessmentId: {}, UserId: {}, MerchantId: {}, Type: {}, Level: {}, Score: {}, Reason: {}, TxnId: {}, Amount: {} {}", 
                    assessmentId, userId, merchantId, assessmentType, riskLevel, riskScore, 
                    assessmentReason, transactionId, transactionAmount, currency);
            
            validateRiskAssessment(assessmentId, assessmentType, riskLevel, riskScore, assessmentTimestamp);
            
            processAssessmentByType(assessmentId, userId, merchantId, assessmentType, riskLevel, 
                    riskScore, assessmentReason, transactionId, transactionAmount, currency, 
                    assessmentTimestamp, riskFactors, riskIndicators, recommendedAction);
            
            if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
                handleHighRisk(assessmentId, userId, merchantId, riskLevel, riskScore, assessmentReason, 
                        transactionId, transactionAmount, currency, riskFactors, recommendedAction);
            }
            
            if ("MEDIUM".equals(riskLevel)) {
                handleMediumRisk(assessmentId, userId, merchantId, riskScore, transactionId, 
                        transactionAmount, currency);
            }
            
            if (requiresManualReview) {
                requestManualReview(assessmentId, userId, merchantId, riskLevel, riskScore, 
                        transactionId, riskFactors);
            }
            
            if (isAutomatedDecision && recommendedAction != null) {
                executeAutomatedAction(assessmentId, userId, transactionId, recommendedAction, 
                        riskLevel, riskScore);
            }
            
            notifyRelevantParties(userId, merchantId, assessmentId, riskLevel, riskScore, 
                    transactionId);
            
            updateRiskMetrics(assessmentType, riskLevel, riskScore, transactionAmount, currency);
            
            auditRiskAssessment(assessmentId, userId, merchantId, assessmentType, riskLevel, 
                    riskScore, transactionId, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Risk assessment processed - AssessmentId: {}, Level: {}, Score: {}, ProcessingTime: {}ms", 
                    assessmentId, riskLevel, riskScore, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Risk assessment processing failed - AssessmentId: {}, UserId: {}, Level: {}, Error: {}", 
                    assessmentId, userId, riskLevel, e.getMessage(), e);
            
            if (assessmentId != null) {
                handleAssessmentFailure(assessmentId, userId, riskLevel, e);
            }
            
            throw new RuntimeException("Risk assessment processing failed", e);
        }
    }
    
    private void validateRiskAssessment(UUID assessmentId, String assessmentType, String riskLevel,
                                       Integer riskScore, LocalDateTime assessmentTimestamp) {
        if (assessmentId == null) {
            throw new IllegalArgumentException("Assessment ID is required");
        }
        
        if (assessmentType == null || assessmentType.trim().isEmpty()) {
            throw new IllegalArgumentException("Assessment type is required");
        }
        
        if (riskLevel == null || riskLevel.trim().isEmpty()) {
            throw new IllegalArgumentException("Risk level is required");
        }
        
        if (riskScore == null || riskScore < 0 || riskScore > 100) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100");
        }
        
        if (assessmentTimestamp == null) {
            throw new IllegalArgumentException("Assessment timestamp is required");
        }
        
        log.debug("Risk assessment validation passed - AssessmentId: {}", assessmentId);
    }
    
    private void processAssessmentByType(UUID assessmentId, UUID userId, UUID merchantId,
                                        String assessmentType, String riskLevel, Integer riskScore,
                                        String assessmentReason, UUID transactionId,
                                        BigDecimal transactionAmount, String currency,
                                        LocalDateTime assessmentTimestamp, List<String> riskFactors,
                                        Map<String, Object> riskIndicators, String recommendedAction) {
        try {
            switch (assessmentType) {
                case "TRANSACTION_RISK" -> processTransactionRisk(assessmentId, userId, transactionId, 
                        riskLevel, riskScore, transactionAmount, currency, riskFactors, riskIndicators);
                
                case "USER_RISK" -> processUserRisk(assessmentId, userId, riskLevel, riskScore, 
                        assessmentReason, riskFactors, riskIndicators);
                
                case "MERCHANT_RISK" -> processMerchantRisk(assessmentId, merchantId, riskLevel, 
                        riskScore, assessmentReason, riskFactors, riskIndicators);
                
                case "ACCOUNT_TAKEOVER" -> processAccountTakeoverRisk(assessmentId, userId, riskLevel, 
                        riskScore, assessmentReason, riskFactors);
                
                case "PAYMENT_FRAUD" -> processPaymentFraudRisk(assessmentId, userId, transactionId, 
                        riskLevel, riskScore, transactionAmount, currency, riskFactors);
                
                case "IDENTITY_FRAUD" -> processIdentityFraudRisk(assessmentId, userId, riskLevel, 
                        riskScore, assessmentReason, riskFactors);
                
                case "CHARGEBACK_RISK" -> processChargebackRisk(assessmentId, merchantId, transactionId, 
                        riskLevel, riskScore, transactionAmount, currency);
                
                default -> {
                    log.warn("Unknown assessment type: {}", assessmentType);
                    processGenericAssessment(assessmentId, userId, assessmentType);
                }
            }
            
            log.debug("Assessment type processing completed - AssessmentId: {}, Type: {}", 
                    assessmentId, assessmentType);
            
        } catch (Exception e) {
            log.error("Failed to process assessment by type - AssessmentId: {}, Type: {}", 
                    assessmentId, assessmentType, e);
            throw new RuntimeException("Assessment type processing failed", e);
        }
    }
    
    private void processTransactionRisk(UUID assessmentId, UUID userId, UUID transactionId,
                                       String riskLevel, Integer riskScore, BigDecimal transactionAmount,
                                       String currency, List<String> riskFactors,
                                       Map<String, Object> riskIndicators) {
        log.info("Processing TRANSACTION RISK - AssessmentId: {}, TxnId: {}, Level: {}, Score: {}, Amount: {} {}", 
                assessmentId, transactionId, riskLevel, riskScore, transactionAmount, currency);
        
        riskAssessmentService.processTransactionRisk(assessmentId, userId, transactionId, riskLevel, 
                riskScore, transactionAmount, currency, riskFactors, riskIndicators);
    }
    
    private void processUserRisk(UUID assessmentId, UUID userId, String riskLevel, Integer riskScore,
                                String assessmentReason, List<String> riskFactors,
                                Map<String, Object> riskIndicators) {
        log.info("Processing USER RISK - AssessmentId: {}, UserId: {}, Level: {}, Score: {}, Reason: {}", 
                assessmentId, userId, riskLevel, riskScore, assessmentReason);
        
        riskAssessmentService.processUserRisk(assessmentId, userId, riskLevel, riskScore, 
                assessmentReason, riskFactors, riskIndicators);
    }
    
    private void processMerchantRisk(UUID assessmentId, UUID merchantId, String riskLevel,
                                    Integer riskScore, String assessmentReason, List<String> riskFactors,
                                    Map<String, Object> riskIndicators) {
        log.info("Processing MERCHANT RISK - AssessmentId: {}, MerchantId: {}, Level: {}, Score: {}", 
                assessmentId, merchantId, riskLevel, riskScore);
        
        riskAssessmentService.processMerchantRisk(assessmentId, merchantId, riskLevel, riskScore, 
                assessmentReason, riskFactors, riskIndicators);
    }
    
    private void processAccountTakeoverRisk(UUID assessmentId, UUID userId, String riskLevel,
                                           Integer riskScore, String assessmentReason,
                                           List<String> riskFactors) {
        log.warn("Processing ACCOUNT TAKEOVER RISK - AssessmentId: {}, UserId: {}, Level: {}, Score: {}", 
                assessmentId, userId, riskLevel, riskScore);
        
        riskAssessmentService.processAccountTakeoverRisk(assessmentId, userId, riskLevel, riskScore, 
                assessmentReason, riskFactors);
    }
    
    private void processPaymentFraudRisk(UUID assessmentId, UUID userId, UUID transactionId,
                                        String riskLevel, Integer riskScore, BigDecimal transactionAmount,
                                        String currency, List<String> riskFactors) {
        log.warn("Processing PAYMENT FRAUD RISK - AssessmentId: {}, TxnId: {}, Level: {}, Score: {}, Amount: {} {}", 
                assessmentId, transactionId, riskLevel, riskScore, transactionAmount, currency);
        
        riskAssessmentService.processPaymentFraudRisk(assessmentId, userId, transactionId, riskLevel, 
                riskScore, transactionAmount, currency, riskFactors);
    }
    
    private void processIdentityFraudRisk(UUID assessmentId, UUID userId, String riskLevel,
                                         Integer riskScore, String assessmentReason,
                                         List<String> riskFactors) {
        log.warn("Processing IDENTITY FRAUD RISK - AssessmentId: {}, UserId: {}, Level: {}, Score: {}", 
                assessmentId, userId, riskLevel, riskScore);
        
        riskAssessmentService.processIdentityFraudRisk(assessmentId, userId, riskLevel, riskScore, 
                assessmentReason, riskFactors);
    }
    
    private void processChargebackRisk(UUID assessmentId, UUID merchantId, UUID transactionId,
                                      String riskLevel, Integer riskScore, BigDecimal transactionAmount,
                                      String currency) {
        log.info("Processing CHARGEBACK RISK - AssessmentId: {}, MerchantId: {}, TxnId: {}, Level: {}, Score: {}", 
                assessmentId, merchantId, transactionId, riskLevel, riskScore);
        
        riskAssessmentService.processChargebackRisk(assessmentId, merchantId, transactionId, riskLevel, 
                riskScore, transactionAmount, currency);
    }
    
    private void processGenericAssessment(UUID assessmentId, UUID userId, String assessmentType) {
        log.info("Processing generic risk assessment - AssessmentId: {}, Type: {}", 
                assessmentId, assessmentType);
        
        riskAssessmentService.processGeneric(assessmentId, userId, assessmentType);
    }
    
    private void handleHighRisk(UUID assessmentId, UUID userId, UUID merchantId, String riskLevel,
                               Integer riskScore, String assessmentReason, UUID transactionId,
                               BigDecimal transactionAmount, String currency, List<String> riskFactors,
                               String recommendedAction) {
        try {
            log.error("Processing HIGH/CRITICAL risk assessment - AssessmentId: {}, Level: {}, Score: {}, Action: {}", 
                    assessmentId, riskLevel, riskScore, recommendedAction);
            
            riskAssessmentService.recordHighRiskEvent(assessmentId, userId, merchantId, riskLevel, 
                    riskScore, assessmentReason, transactionId, transactionAmount, currency, riskFactors);
            
            if ("CRITICAL".equals(riskLevel)) {
                riskMitigationService.blockTransaction(transactionId, userId, "Critical risk detected");
                
                if (userId != null) {
                    riskMitigationService.freezeAccount(userId, assessmentId, "Critical risk score");
                }
            }
            
            fraudNotificationService.sendHighRiskAlert(assessmentId, userId, merchantId, riskLevel, 
                    riskScore, transactionId);
            
        } catch (Exception e) {
            log.error("Failed to handle high risk - AssessmentId: {}", assessmentId, e);
        }
    }
    
    private void handleMediumRisk(UUID assessmentId, UUID userId, UUID merchantId, Integer riskScore,
                                 UUID transactionId, BigDecimal transactionAmount, String currency) {
        try {
            log.warn("Processing MEDIUM risk assessment - AssessmentId: {}, Score: {}, TxnId: {}", 
                    assessmentId, riskScore, transactionId);
            
            riskAssessmentService.recordMediumRiskEvent(assessmentId, userId, merchantId, riskScore, 
                    transactionId, transactionAmount, currency);
            
            if (transactionAmount.compareTo(new BigDecimal("5000")) >= 0) {
                riskAssessmentService.enhanceMonitoring(userId, assessmentId);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle medium risk - AssessmentId: {}", assessmentId, e);
        }
    }
    
    private void requestManualReview(UUID assessmentId, UUID userId, UUID merchantId, String riskLevel,
                                    Integer riskScore, UUID transactionId, List<String> riskFactors) {
        try {
            log.info("Requesting manual review - AssessmentId: {}, Level: {}, Score: {}", 
                    assessmentId, riskLevel, riskScore);
            
            riskAssessmentService.requestManualReview(assessmentId, userId, merchantId, riskLevel, 
                    riskScore, transactionId, riskFactors);
            
        } catch (Exception e) {
            log.error("Failed to request manual review - AssessmentId: {}", assessmentId, e);
        }
    }
    
    private void executeAutomatedAction(UUID assessmentId, UUID userId, UUID transactionId,
                                       String recommendedAction, String riskLevel, Integer riskScore) {
        try {
            log.info("Executing automated action - AssessmentId: {}, Action: {}, Level: {}", 
                    assessmentId, recommendedAction, riskLevel);
            
            switch (recommendedAction) {
                case "BLOCK" -> riskMitigationService.blockTransaction(transactionId, userId, 
                        "Automated risk decision");
                
                case "CHALLENGE" -> riskMitigationService.requireAdditionalAuth(userId, transactionId, 
                        assessmentId);
                
                case "MONITOR" -> riskMitigationService.enhanceMonitoring(userId, assessmentId);
                
                case "ALLOW" -> riskMitigationService.allowTransaction(transactionId, assessmentId);
                
                default -> log.warn("Unknown recommended action: {}", recommendedAction);
            }
            
        } catch (Exception e) {
            log.error("Failed to execute automated action - AssessmentId: {}", assessmentId, e);
        }
    }
    
    private void notifyRelevantParties(UUID userId, UUID merchantId, UUID assessmentId, String riskLevel,
                                      Integer riskScore, UUID transactionId) {
        try {
            if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
                fraudNotificationService.sendRiskAlert(userId, merchantId, assessmentId, riskLevel, 
                        riskScore, transactionId);
            }
            
            log.debug("Risk notifications sent - AssessmentId: {}, Level: {}", assessmentId, riskLevel);
            
        } catch (Exception e) {
            log.error("Failed to notify parties - AssessmentId: {}", assessmentId, e);
        }
    }
    
    private void updateRiskMetrics(String assessmentType, String riskLevel, Integer riskScore,
                                  BigDecimal transactionAmount, String currency) {
        try {
            riskAssessmentService.updateRiskMetrics(assessmentType, riskLevel, riskScore, 
                    transactionAmount, currency);
        } catch (Exception e) {
            log.error("Failed to update risk metrics - Type: {}, Level: {}", 
                    assessmentType, riskLevel, e);
        }
    }
    
    private void auditRiskAssessment(UUID assessmentId, UUID userId, UUID merchantId,
                                    String assessmentType, String riskLevel, Integer riskScore,
                                    UUID transactionId, LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditEvent(
                    "RISK_ASSESSMENT_PROCESSED",
                    userId != null ? userId.toString() : (merchantId != null ? merchantId.toString() : "SYSTEM"),
                    String.format("Risk assessment processed - Type: %s, Level: %s, Score: %d", 
                            assessmentType, riskLevel, riskScore),
                    Map.of(
                            "assessmentId", assessmentId.toString(),
                            "userId", userId != null ? userId.toString() : "N/A",
                            "merchantId", merchantId != null ? merchantId.toString() : "N/A",
                            "assessmentType", assessmentType,
                            "riskLevel", riskLevel,
                            "riskScore", riskScore,
                            "transactionId", transactionId != null ? transactionId.toString() : "N/A",
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit risk assessment - AssessmentId: {}", assessmentId, e);
        }
    }
    
    private void handleAssessmentFailure(UUID assessmentId, UUID userId, String riskLevel,
                                        Exception error) {
        try {
            riskAssessmentService.handleAssessmentFailure(assessmentId, userId, riskLevel, 
                    error.getMessage());
            
            auditService.auditEvent(
                    "RISK_ASSESSMENT_PROCESSING_FAILED",
                    userId != null ? userId.toString() : "SYSTEM",
                    "Failed to process risk assessment: " + error.getMessage(),
                    Map.of(
                            "assessmentId", assessmentId.toString(),
                            "userId", userId != null ? userId.toString() : "N/A",
                            "riskLevel", riskLevel != null ? riskLevel : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle assessment failure - AssessmentId: {}", assessmentId, e);
        }
    }
    
    @KafkaListener(
        topics = {"risk-assessment-events.DLQ", "risk-assessments.DLQ", "risk-scoring-events.DLQ"},
        groupId = "fraud-service-risk-assessment-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Risk assessment event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID assessmentId = event.containsKey("assessmentId") ? 
                    UUID.fromString((String) event.get("assessmentId")) : null;
            UUID userId = event.containsKey("userId") ? 
                    UUID.fromString((String) event.get("userId")) : null;
            String assessmentType = (String) event.get("assessmentType");
            
            log.error("DLQ: Risk assessment failed permanently - AssessmentId: {}, UserId: {}, Type: {} - MANUAL REVIEW REQUIRED", 
                    assessmentId, userId, assessmentType);
            
            if (assessmentId != null) {
                riskAssessmentService.markForManualReview(assessmentId, userId, assessmentType, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse risk assessment DLQ event: {}", eventJson, e);
        }
    }
}