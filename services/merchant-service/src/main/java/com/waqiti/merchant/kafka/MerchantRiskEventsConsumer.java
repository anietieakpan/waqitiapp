package com.waqiti.merchant.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.merchant.service.MerchantRiskAssessmentService;
import com.waqiti.merchant.service.ChargebackPreventionService;
import com.waqiti.merchant.service.FraudDetectionService;
import com.waqiti.merchant.service.ComplianceMonitoringService;
import com.waqiti.merchant.service.AuditService;
import com.waqiti.merchant.entity.MerchantRiskProfile;
import com.waqiti.merchant.entity.RiskAlert;
import com.waqiti.merchant.entity.ComplianceCheck;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

/**
 * Critical Event Consumer #5: Merchant Risk Events Consumer
 * Processes merchant risk assessments, fraud detection, and compliance monitoring
 * Implements 12-step zero-tolerance processing for merchant risk management
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantRiskEventsConsumer extends BaseKafkaConsumer {

    private final MerchantRiskAssessmentService riskAssessmentService;
    private final ChargebackPreventionService chargebackPreventionService;
    private final FraudDetectionService fraudDetectionService;
    private final ComplianceMonitoringService complianceService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "merchant-risk-events", 
        groupId = "merchant-risk-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @CircuitBreaker(name = "merchant-risk-consumer")
    @Retry(name = "merchant-risk-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleMerchantRiskEvent(
            ConsumerRecord<String, String> record, 
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "merchant-risk-event");
        MDC.put("partition", String.valueOf(partition));
        
        try {
            log.info("Step 1: Processing merchant risk event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String merchantId = eventData.path("merchantId").asText();
            String riskEventType = eventData.path("riskEventType").asText(); // CHARGEBACK, FRAUD, HIGH_VOLUME, VELOCITY
            String transactionId = eventData.path("transactionId").asText();
            BigDecimal transactionAmount = new BigDecimal(eventData.path("transactionAmount").asText());
            String currency = eventData.path("currency").asText();
            String customerEmail = eventData.path("customerEmail").asText();
            String customerIpAddress = eventData.path("customerIpAddress").asText();
            String cardBin = eventData.path("cardBin").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            int severityLevel = eventData.path("severityLevel").asInt(); // 1-10
            
            log.info("Step 2: Extracted risk details: merchantId={}, type={}, amount={} {}, severity={}", 
                    merchantId, riskEventType, transactionAmount, currency, severityLevel);
            
            // Step 3: Merchant risk profile assessment and validation
            log.info("Step 3: Assessing merchant risk profile and validating risk parameters");
            MerchantRiskProfile riskProfile = riskAssessmentService.getMerchantRiskProfile(merchantId);
            
            riskAssessmentService.validateRiskEventType(riskEventType);
            riskAssessmentService.updateRiskProfile(riskProfile, eventData);
            riskAssessmentService.calculateCurrentRiskScore(riskProfile);
            
            if (!riskAssessmentService.isWithinRiskTolerances(riskProfile)) {
                riskAssessmentService.escalateRiskProfile(riskProfile);
            }
            
            riskAssessmentService.validateMerchantStatus(merchantId);
            
            // Step 4: Transaction-level risk analysis
            log.info("Step 4: Conducting comprehensive transaction-level risk analysis");
            RiskAlert riskAlert = riskAssessmentService.createRiskAlert(eventData);
            
            riskAssessmentService.analyzeTransactionPattern(merchantId, transactionAmount, timestamp);
            riskAssessmentService.assessVelocityRisk(merchantId, transactionAmount, timestamp);
            riskAssessmentService.checkTransactionLimits(merchantId, transactionAmount);
            
            boolean highRiskTransaction = riskAssessmentService.isHighRiskTransaction(
                transactionAmount, customerIpAddress, cardBin);
            
            if (highRiskTransaction) {
                riskAssessmentService.flagHighRiskTransaction(riskAlert);
            }
            
            // Step 5: Chargeback risk assessment and prevention
            log.info("Step 5: Evaluating chargeback risk and implementing prevention measures");
            if ("CHARGEBACK".equals(riskEventType)) {
                chargebackPreventionService.analyzeChargebackRisk(merchantId, transactionId);
                chargebackPreventionService.updateChargebackRatio(merchantId);
                chargebackPreventionService.assessChargebackThresholds(merchantId);
                
                if (chargebackPreventionService.exceedsChargebackThresholds(merchantId)) {
                    chargebackPreventionService.implementPreventionMeasures(merchantId);
                    riskAssessmentService.elevateRiskLevel(riskProfile);
                }
                
                chargebackPreventionService.analyzeChargebackPatterns(merchantId);
            }
            
            // Step 6: Fraud pattern detection and analysis
            log.info("Step 6: Detecting fraud patterns and conducting security analysis");
            fraudDetectionService.analyzeTransactionFraud(transactionId, customerEmail, customerIpAddress);
            fraudDetectionService.checkFraudLists(customerEmail, customerIpAddress);
            fraudDetectionService.analyzeBehavioralPatterns(merchantId, customerEmail);
            
            boolean fraudDetected = fraudDetectionService.detectFraudPatterns(merchantId, eventData);
            if (fraudDetected) {
                fraudDetectionService.flagFraudulentActivity(riskAlert);
                riskAssessmentService.implementFraudPreventionMeasures(merchantId);
            }
            
            fraudDetectionService.updateFraudMetrics(merchantId, fraudDetected);
            
            // Step 7: Velocity and volume monitoring
            log.info("Step 7: Monitoring transaction velocity and volume patterns");
            if ("HIGH_VOLUME".equals(riskEventType) || "VELOCITY".equals(riskEventType)) {
                riskAssessmentService.analyzeVolumeSpike(merchantId, transactionAmount, timestamp);
                riskAssessmentService.checkVelocityThresholds(merchantId, timestamp);
                riskAssessmentService.assessUnusualActivity(merchantId, eventData);
                
                if (riskAssessmentService.exceedsVelocityLimits(merchantId)) {
                    riskAssessmentService.implementVelocityControls(merchantId);
                    riskAssessmentService.notifyRiskTeam(riskAlert);
                }
            }
            
            riskAssessmentService.updateVelocityMetrics(merchantId, transactionAmount);
            
            // Step 8: Compliance and regulatory risk assessment
            log.info("Step 8: Evaluating compliance risks and regulatory requirements");
            ComplianceCheck complianceCheck = complianceService.createComplianceCheck(merchantId);
            
            complianceService.assessAMLRisk(merchantId, transactionAmount, customerEmail);
            complianceService.checkSanctionLists(customerEmail, customerIpAddress);
            complianceService.validateKYCRequirements(merchantId);
            
            if (complianceService.requiresEnhancedDueDiligence(merchantId, transactionAmount)) {
                complianceService.initiateEDDProcess(merchantId);
                riskAssessmentService.requireManualReview(riskProfile);
            }
            
            complianceService.updateComplianceMetrics(complianceCheck);
            
            // Step 9: Risk scoring and decision making
            log.info("Step 9: Calculating comprehensive risk scores and making decisions");
            int transactionRiskScore = riskAssessmentService.calculateTransactionRiskScore(eventData);
            int merchantRiskScore = riskAssessmentService.calculateMerchantRiskScore(riskProfile);
            int overallRiskScore = riskAssessmentService.calculateOverallRiskScore(
                transactionRiskScore, merchantRiskScore, severityLevel);
            
            riskAlert.setTransactionRiskScore(transactionRiskScore);
            riskAlert.setMerchantRiskScore(merchantRiskScore);
            riskAlert.setOverallRiskScore(overallRiskScore);
            
            riskAssessmentService.makeRiskDecision(riskAlert, overallRiskScore);
            
            // Step 10: Risk mitigation and control implementation
            log.info("Step 10: Implementing risk mitigation measures and controls");
            if (overallRiskScore >= 80) {
                riskAssessmentService.implementHighRiskControls(merchantId);
                riskAssessmentService.requireStepUpAuthentication(merchantId);
                riskAssessmentService.enableEnhancedMonitoring(merchantId);
            } else if (overallRiskScore >= 60) {
                riskAssessmentService.implementMediumRiskControls(merchantId);
                riskAssessmentService.increaseMonitoringFrequency(merchantId);
            }
            
            riskAssessmentService.updateRiskControls(riskProfile, overallRiskScore);
            riskAssessmentService.adjustTransactionLimits(merchantId, overallRiskScore);
            
            // Step 11: Alert generation and escalation
            log.info("Step 11: Generating risk alerts and managing escalation procedures");
            if (overallRiskScore >= 70) {
                riskAssessmentService.generateRiskAlert(riskAlert);
                riskAssessmentService.notifyRiskManagement(riskAlert);
                
                if (severityLevel >= 8) {
                    riskAssessmentService.escalateToCompliance(riskAlert);
                    riskAssessmentService.notifyRegulators(riskAlert);
                }
            }
            
            riskAssessmentService.updateAlertStatus(riskAlert);
            riskAssessmentService.trackAlertResolution(riskAlert);
            
            // Step 12: Audit trail and monitoring updates
            log.info("Step 12: Completing audit trail and updating monitoring systems");
            auditService.logMerchantRiskEvent(riskAlert);
            auditService.logRiskProfile(riskProfile);
            auditService.logComplianceCheck(complianceCheck);
            
            riskAssessmentService.updateRiskMetrics(riskProfile);
            chargebackPreventionService.updateChargebackMetrics(merchantId);
            fraudDetectionService.updateFraudStatistics(merchantId);
            
            auditService.generateRiskReport(riskAlert);
            auditService.updateRegulatoryReporting(riskAlert);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed merchant risk event: merchantId={}, eventId={}, riskScore={}", 
                    merchantId, eventId, overallRiskScore);
            
        } catch (Exception e) {
            log.error("Error processing merchant risk event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("merchantId") || 
            !eventData.has("riskEventType") || !eventData.has("transactionId") ||
            !eventData.has("transactionAmount") || !eventData.has("currency") ||
            !eventData.has("customerEmail") || !eventData.has("customerIpAddress") ||
            !eventData.has("cardBin") || !eventData.has("timestamp") ||
            !eventData.has("severityLevel")) {
            throw new IllegalArgumentException("Invalid merchant risk event structure");
        }
    }
}