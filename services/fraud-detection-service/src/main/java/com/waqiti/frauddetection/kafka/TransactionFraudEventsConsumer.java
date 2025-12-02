package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.frauddetection.service.TransactionFraudService;
import com.waqiti.frauddetection.service.FraudRiskService;
import com.waqiti.frauddetection.service.FraudNotificationService;
import com.waqiti.frauddetection.service.MachineLearningFraudService;
import com.waqiti.common.exception.FraudProcessingException;
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
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Consumer for Transaction Fraud Events
 * Handles real-time fraud detection, scoring, and response actions
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionFraudEventsConsumer {
    
    private final TransactionFraudService fraudService;
    private final FraudRiskService riskService;
    private final FraudNotificationService notificationService;
    private final MachineLearningFraudService mlService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"transaction-fraud-events", "fraud-detected", "suspicious-pattern-identified", "fraud-case-created"},
        groupId = "fraud-service-transaction-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000)
    )
    @Transactional
    public void handleTransactionFraudEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID fraudEventId = null;
        UUID transactionId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            fraudEventId = UUID.fromString((String) event.get("fraudEventId"));
            transactionId = UUID.fromString((String) event.get("transactionId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String fraudType = (String) event.get("fraudType"); // CARD_NOT_PRESENT, ACCOUNT_TAKEOVER, SYNTHETIC_IDENTITY
            Integer fraudScore = (Integer) event.get("fraudScore"); // 0-1000
            String riskLevel = (String) event.get("riskLevel"); // LOW, MEDIUM, HIGH, CRITICAL
            BigDecimal transactionAmount = new BigDecimal((String) event.get("transactionAmount"));
            String currency = (String) event.get("currency");
            LocalDateTime detectionTime = LocalDateTime.parse((String) event.get("detectionTime"));
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            // Fraud indicators
            String suspiciousPatterns = (String) event.get("suspiciousPatterns");
            String geolocationRisk = (String) event.get("geolocationRisk");
            String deviceRisk = (String) event.get("deviceRisk");
            String behaviorRisk = (String) event.get("behaviorRisk");
            String velocityRisk = (String) event.get("velocityRisk");
            Boolean crossBorderTransaction = (Boolean) event.getOrDefault("crossBorderTransaction", false);
            
            // Machine learning predictions
            BigDecimal mlProbability = new BigDecimal((String) event.get("mlProbability"));
            String modelVersion = (String) event.get("modelVersion");
            String featureImportance = (String) event.get("featureImportance");
            
            // Case management
            String caseId = (String) event.get("caseId");
            String caseStatus = (String) event.get("caseStatus"); // OPEN, INVESTIGATING, RESOLVED
            String assignedAnalyst = (String) event.get("assignedAnalyst");
            Boolean autoBlocked = (Boolean) event.getOrDefault("autoBlocked", false);
            
            log.info("Processing transaction fraud event - FraudEventId: {}, TransactionId: {}, Type: {}, Score: {}", 
                    fraudEventId, transactionId, eventType, fraudScore);
            
            // Step 1: Validate fraud event data
            Map<String, Object> validationResult = fraudService.validateFraudEventData(
                    fraudEventId, transactionId, customerId, fraudType, fraudScore,
                    transactionAmount, currency, detectionTime, timestamp);
            
            if ("INVALID".equals(validationResult.get("status"))) {
                fraudService.logInvalidFraudEvent(fraudEventId, 
                        (String) validationResult.get("reason"), timestamp);
                log.warn("Invalid fraud event data: {}", validationResult.get("reason"));
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 2: Customer risk profile assessment
            Map<String, Object> customerRiskProfile = riskService.assessCustomerFraudRisk(
                    customerId, fraudScore, fraudType, transactionAmount, timestamp);
            
            String customerRiskRating = (String) customerRiskProfile.get("riskRating");
            Boolean isHighRiskCustomer = (Boolean) customerRiskProfile.get("isHighRisk");
            
            // Step 3: Process based on event type
            switch (eventType) {
                case "FRAUD_DETECTED":
                    fraudService.processFraudDetection(fraudEventId, transactionId, customerId,
                            fraudType, fraudScore, riskLevel, suspiciousPatterns, detectionTime, timestamp);
                    break;
                    
                case "SUSPICIOUS_PATTERN_IDENTIFIED":
                    fraudService.processSuspiciousPattern(fraudEventId, transactionId, customerId,
                            suspiciousPatterns, geolocationRisk, deviceRisk, behaviorRisk, timestamp);
                    break;
                    
                case "FRAUD_CASE_CREATED":
                    fraudService.processFraudCaseCreation(fraudEventId, caseId, transactionId,
                            customerId, fraudType, caseStatus, assignedAnalyst, timestamp);
                    break;
                    
                default:
                    fraudService.processGenericFraudEvent(fraudEventId, eventType, event, timestamp);
            }
            
            // Step 4: Real-time decision engine
            Map<String, Object> decisionResult = fraudService.executeDecisionEngine(
                    fraudEventId, fraudScore, riskLevel, fraudType, customerRiskProfile,
                    transactionAmount, crossBorderTransaction, timestamp);
            
            String recommendedAction = (String) decisionResult.get("action"); // ALLOW, CHALLENGE, BLOCK
            
            // Step 5: Implement fraud response actions
            switch (recommendedAction) {
                case "BLOCK":
                    fraudService.blockTransaction(transactionId, fraudEventId, 
                            "Automated fraud prevention", timestamp);
                    autoBlocked = true;
                    break;
                case "CHALLENGE":
                    fraudService.challengeTransaction(transactionId, customerId,
                            "Additional verification required", timestamp);
                    break;
                case "ALLOW":
                    fraudService.allowTransaction(transactionId, fraudEventId,
                            "Passed fraud checks", timestamp);
                    break;
            }
            
            // Step 6: Machine learning model feedback
            mlService.provideFeedbackToModel(modelVersion, fraudEventId, fraudScore,
                    mlProbability, recommendedAction, featureImportance, timestamp);
            
            // Step 7: Velocity and pattern analysis
            Map<String, Object> velocityAnalysis = fraudService.analyzeTransactionVelocity(
                    customerId, transactionAmount, currency, detectionTime, timestamp);
            
            if ("HIGH_VELOCITY".equals(velocityAnalysis.get("status"))) {
                fraudService.escalateHighVelocityPattern(fraudEventId, customerId,
                        velocityAnalysis, timestamp);
            }
            
            // Step 8: Geolocation and device analysis
            fraudService.analyzeGeolocationRisk(fraudEventId, customerId, geolocationRisk,
                    deviceRisk, crossBorderTransaction, timestamp);
            
            // Step 9: Account protection measures
            if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
                fraudService.implementAccountProtection(customerId, fraudEventId,
                        riskLevel, recommendedAction, timestamp);
            }
            
            // Step 10: Fraud network analysis
            fraudService.updateFraudNetwork(fraudEventId, customerId, suspiciousPatterns,
                    deviceRisk, geolocationRisk, timestamp);
            
            // Step 11: Regulatory reporting
            if (autoBlocked || "CRITICAL".equals(riskLevel)) {
                fraudService.generateRegulatoryReport(fraudEventId, transactionId, customerId,
                        fraudType, fraudScore, transactionAmount, recommendedAction, timestamp);
            }
            
            // Step 12: Send fraud notifications
            notificationService.sendFraudNotification(fraudEventId, transactionId, customerId,
                    eventType, fraudType, riskLevel, recommendedAction, autoBlocked, timestamp);
            
            // Step 13: Update fraud analytics
            fraudService.updateFraudAnalytics(fraudEventId, fraudType, fraudScore, riskLevel,
                    recommendedAction, customerRiskRating, timestamp);
            
            // Step 14: Audit logging
            auditService.auditFinancialEvent(
                    "TRANSACTION_FRAUD_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Transaction fraud event processed - Type: %s, Score: %d, Risk: %s, Action: %s", 
                            eventType, fraudScore, riskLevel, recommendedAction),
                    Map.of(
                            "fraudEventId", fraudEventId.toString(),
                            "transactionId", transactionId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "fraudType", fraudType,
                            "fraudScore", fraudScore.toString(),
                            "riskLevel", riskLevel,
                            "transactionAmount", transactionAmount.toString(),
                            "currency", currency,
                            "mlProbability", mlProbability.toString(),
                            "modelVersion", modelVersion,
                            "recommendedAction", recommendedAction,
                            "autoBlocked", autoBlocked.toString(),
                            "customerRiskRating", customerRiskRating,
                            "isHighRiskCustomer", isHighRiskCustomer.toString(),
                            "crossBorderTransaction", crossBorderTransaction.toString(),
                            "caseId", caseId != null ? caseId : "N/A"
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed transaction fraud event - FraudEventId: {}, Score: {}, Action: {}", 
                    fraudEventId, fraudScore, recommendedAction);
            
        } catch (Exception e) {
            log.error("Transaction fraud event processing failed - FraudEventId: {}, TransactionId: {}, Error: {}", 
                    fraudEventId, transactionId, e.getMessage(), e);
            throw new FraudProcessingException("Transaction fraud event processing failed", e);
        }
    }
}