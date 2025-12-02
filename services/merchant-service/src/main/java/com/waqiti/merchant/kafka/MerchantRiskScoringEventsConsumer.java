package com.waqiti.merchant.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.merchant.service.MerchantRiskService;
import com.waqiti.merchant.service.MerchantComplianceService;
import com.waqiti.merchant.service.MerchantNotificationService;
import com.waqiti.merchant.service.MerchantLimitService;
import com.waqiti.common.exception.MerchantProcessingException;
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
 * Kafka Consumer for Merchant Risk Scoring Events
 * Handles real-time risk assessment, scoring updates, and risk-based decision making
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MerchantRiskScoringEventsConsumer {
    
    private final MerchantRiskService riskService;
    private final MerchantComplianceService complianceService;
    private final MerchantNotificationService notificationService;
    private final MerchantLimitService limitService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"merchant-risk-scoring-events", "risk-score-updated", "fraud-pattern-detected", "risk-threshold-exceeded"},
        groupId = "merchant-service-risk-scoring-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000)
    )
    @Transactional
    public void handleMerchantRiskScoringEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID merchantId = null;
        UUID riskAssessmentId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            merchantId = UUID.fromString((String) event.get("merchantId"));
            riskAssessmentId = UUID.fromString((String) event.get("riskAssessmentId"));
            eventType = (String) event.get("eventType");
            Integer currentRiskScore = (Integer) event.get("currentRiskScore");
            Integer previousRiskScore = (Integer) event.get("previousRiskScore");
            String riskLevel = (String) event.get("riskLevel"); // LOW, MEDIUM, HIGH, CRITICAL
            String previousRiskLevel = (String) event.get("previousRiskLevel");
            String riskCategory = (String) event.get("riskCategory"); // TRANSACTION, CHARGEBACK, COMPLIANCE, OPERATIONAL
            LocalDateTime assessmentDate = LocalDateTime.parse((String) event.get("assessmentDate"));
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            // Risk factors
            BigDecimal chargebackRate = new BigDecimal((String) event.get("chargebackRate"));
            BigDecimal refundRate = new BigDecimal((String) event.get("refundRate"));
            Integer fraudIncidents = (Integer) event.get("fraudIncidents");
            BigDecimal volumeDeviation = new BigDecimal((String) event.get("volumeDeviation"));
            BigDecimal averageTicketDeviation = new BigDecimal((String) event.get("averageTicketDeviation"));
            Integer complianceViolations = (Integer) event.get("complianceViolations");
            String industryRiskRating = (String) event.get("industryRiskRating");
            
            // Transaction patterns
            Integer suspiciousTransactions = (Integer) event.get("suspiciousTransactions");
            BigDecimal highRiskCountryVolume = new BigDecimal((String) event.get("highRiskCountryVolume"));
            Integer velocityViolations = (Integer) event.get("velocityViolations");
            String anomalousPatterns = (String) event.get("anomalousPatterns");
            
            // Risk triggers
            String triggerReason = (String) event.get("triggerReason");
            String riskIndicators = (String) event.get("riskIndicators");
            Boolean requiresManualReview = (Boolean) event.getOrDefault("requiresManualReview", false);
            Boolean autoActionRequired = (Boolean) event.getOrDefault("autoActionRequired", false);
            
            log.info("Processing merchant risk scoring event - MerchantId: {}, Type: {}, Score: {} -> {}, Level: {}", 
                    merchantId, eventType, previousRiskScore, currentRiskScore, riskLevel);
            
            // Step 1: Validate risk scoring data
            Map<String, Object> validationResult = riskService.validateRiskScoringData(
                    riskAssessmentId, merchantId, currentRiskScore, riskLevel, 
                    riskCategory, assessmentDate, timestamp);
            
            if ("INVALID".equals(validationResult.get("status"))) {
                riskService.logInvalidRiskData(riskAssessmentId, 
                        (String) validationResult.get("reason"), timestamp);
                log.warn("Invalid risk scoring data: {}", validationResult.get("reason"));
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 2: Analyze risk score changes and trends
            Map<String, Object> riskAnalysis = riskService.analyzeRiskScoreChange(
                    merchantId, currentRiskScore, previousRiskScore, riskLevel, 
                    previousRiskLevel, riskCategory, timestamp);
            
            String scoreChangeSignificance = (String) riskAnalysis.get("significance"); // MINOR, MODERATE, SIGNIFICANT, CRITICAL
            String trendDirection = (String) riskAnalysis.get("trendDirection"); // IMPROVING, STABLE, DETERIORATING
            
            // Step 3: Process based on event type
            switch (eventType) {
                case "RISK_SCORE_UPDATED":
                    riskService.updateMerchantRiskScore(merchantId, riskAssessmentId, currentRiskScore,
                            riskLevel, riskCategory, chargebackRate, refundRate, fraudIncidents,
                            volumeDeviation, averageTicketDeviation, assessmentDate, timestamp);
                    break;
                    
                case "FRAUD_PATTERN_DETECTED":
                    riskService.processFraudPatternDetection(merchantId, riskAssessmentId,
                            suspiciousTransactions, anomalousPatterns, fraudIncidents,
                            velocityViolations, triggerReason, timestamp);
                    break;
                    
                case "RISK_THRESHOLD_EXCEEDED":
                    riskService.processRiskThresholdExceeded(merchantId, riskAssessmentId,
                            currentRiskScore, riskLevel, triggerReason, riskIndicators,
                            requiresManualReview, autoActionRequired, timestamp);
                    break;
                    
                default:
                    riskService.processGenericRiskEvent(merchantId, riskAssessmentId, 
                            eventType, event, timestamp);
            }
            
            // Step 4: Implement risk-based actions
            if (autoActionRequired) {
                Map<String, Object> autoActions = riskService.executeAutoRiskActions(
                        merchantId, currentRiskScore, riskLevel, riskCategory, 
                        scoreChangeSignificance, timestamp);
                
                log.info("Auto risk actions executed: {}", autoActions.get("actionsPerformed"));
            }
            
            // Step 5: Update processing limits based on risk level
            if ("SIGNIFICANT".equals(scoreChangeSignificance) || "CRITICAL".equals(scoreChangeSignificance)) {
                limitService.adjustProcessingLimitsForRisk(merchantId, riskLevel, 
                        currentRiskScore, chargebackRate, refundRate, timestamp);
            }
            
            // Step 6: Handle high-risk scenarios
            if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
                riskService.implementHighRiskProtocols(merchantId, riskAssessmentId,
                        riskLevel, triggerReason, riskIndicators, timestamp);
                
                // Enhanced monitoring
                riskService.enableEnhancedMonitoring(merchantId, riskLevel, riskCategory, timestamp);
            }
            
            // Step 7: Compliance integration
            if (complianceViolations > 0 || "COMPLIANCE".equals(riskCategory)) {
                complianceService.integrateRiskWithCompliance(merchantId, riskAssessmentId,
                        complianceViolations, riskLevel, currentRiskScore, timestamp);
            }
            
            // Step 8: Generate risk reports and alerts
            if (requiresManualReview || "CRITICAL".equals(riskLevel)) {
                riskService.generateRiskReviewCase(merchantId, riskAssessmentId,
                        currentRiskScore, riskLevel, triggerReason, riskIndicators, timestamp);
            }
            
            // Step 9: Update merchant tier and status
            if ("SIGNIFICANT".equals(scoreChangeSignificance)) {
                riskService.reviewMerchantTierAssignment(merchantId, currentRiskScore,
                        riskLevel, chargebackRate, fraudIncidents, timestamp);
            }
            
            // Step 10: Send risk notifications
            notificationService.sendRiskScoringNotification(merchantId, riskAssessmentId,
                    eventType, currentRiskScore, previousRiskScore, riskLevel,
                    scoreChangeSignificance, requiresManualReview, timestamp);
            
            // Step 11: Historical risk tracking
            riskService.updateRiskHistory(merchantId, riskAssessmentId, currentRiskScore,
                    riskLevel, riskCategory, riskAnalysis, timestamp);
            
            // Step 12: Audit logging
            auditService.auditFinancialEvent(
                    "MERCHANT_RISK_SCORING_EVENT_PROCESSED",
                    merchantId.toString(),
                    String.format("Merchant risk scoring event processed - Type: %s, Score: %d, Level: %s, Change: %s", 
                            eventType, currentRiskScore, riskLevel, scoreChangeSignificance),
                    Map.of(
                            "merchantId", merchantId.toString(),
                            "riskAssessmentId", riskAssessmentId.toString(),
                            "eventType", eventType,
                            "currentRiskScore", currentRiskScore.toString(),
                            "previousRiskScore", previousRiskScore.toString(),
                            "riskLevel", riskLevel,
                            "previousRiskLevel", previousRiskLevel,
                            "riskCategory", riskCategory,
                            "chargebackRate", chargebackRate.toString(),
                            "refundRate", refundRate.toString(),
                            "fraudIncidents", fraudIncidents.toString(),
                            "scoreChangeSignificance", scoreChangeSignificance,
                            "trendDirection", trendDirection,
                            "requiresManualReview", requiresManualReview.toString(),
                            "autoActionRequired", autoActionRequired.toString(),
                            "triggerReason", triggerReason != null ? triggerReason : "N/A"
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed merchant risk scoring event - MerchantId: {}, Score: {}, Level: {}", 
                    merchantId, currentRiskScore, riskLevel);
            
        } catch (Exception e) {
            log.error("Merchant risk scoring event processing failed - MerchantId: {}, RiskAssessmentId: {}, Error: {}", 
                    merchantId, riskAssessmentId, e.getMessage(), e);
            throw new MerchantProcessingException("Merchant risk scoring event processing failed", e);
        }
    }
}