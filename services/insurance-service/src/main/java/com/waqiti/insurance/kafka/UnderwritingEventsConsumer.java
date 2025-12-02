package com.waqiti.insurance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.insurance.service.UnderwritingDecisionService;
import com.waqiti.insurance.service.RiskEvaluationService;
import com.waqiti.insurance.service.ActuarialAnalysisService;
import com.waqiti.insurance.service.UnderwritingComplianceService;
import com.waqiti.insurance.service.AuditService;
import com.waqiti.insurance.entity.UnderwritingDecision;
import com.waqiti.insurance.entity.RiskEvaluation;
import com.waqiti.insurance.entity.ActuarialAnalysis;
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

/**
 * Critical Event Consumer #14: Underwriting Events Consumer
 * Processes underwriting decisions, risk evaluations, and actuarial analysis
 * Implements 12-step zero-tolerance processing for insurance underwriting
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnderwritingEventsConsumer extends BaseKafkaConsumer {

    private final UnderwritingDecisionService decisionService;
    private final RiskEvaluationService riskEvaluationService;
    private final ActuarialAnalysisService actuarialService;
    private final UnderwritingComplianceService complianceService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "underwriting-events", 
        groupId = "underwriting-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @CircuitBreaker(name = "underwriting-consumer")
    @Retry(name = "underwriting-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleUnderwritingEvent(
            ConsumerRecord<String, String> record, 
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "underwriting-event");
        MDC.put("partition", String.valueOf(partition));
        
        try {
            log.info("Step 1: Processing underwriting event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String applicationId = eventData.path("applicationId").asText();
            String applicantId = eventData.path("applicantId").asText();
            String policyType = eventData.path("policyType").asText();
            BigDecimal requestedCoverage = new BigDecimal(eventData.path("requestedCoverage").asText());
            String riskClassification = eventData.path("riskClassification").asText();
            int age = eventData.path("age").asInt();
            String occupation = eventData.path("occupation").asText();
            String medicalHistory = eventData.path("medicalHistory").asText();
            String financialStatus = eventData.path("financialStatus").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted underwriting details: applicationId={}, coverage={}, risk={}, age={}", 
                    applicationId, requestedCoverage, riskClassification, age);
            
            // Step 3: Application review and initial assessment
            log.info("Step 3: Reviewing application and conducting initial assessment");
            UnderwritingDecision decision = decisionService.createUnderwritingDecision(eventData);
            
            decisionService.validateApplication(applicationId, decision);
            decisionService.verifyApplicantInformation(applicantId, decision);
            decisionService.assessApplicationCompleteness(decision);
            decisionService.checkPreviousApplications(applicantId, decision);
            
            if (!decisionService.isValidPolicyType(policyType)) {
                throw new IllegalStateException("Invalid policy type for underwriting: " + policyType);
            }
            
            decisionService.initializeUnderwritingProcess(decision);
            
            // Step 4: Risk evaluation and scoring
            log.info("Step 4: Conducting comprehensive risk evaluation and scoring");
            RiskEvaluation riskEval = riskEvaluationService.createRiskEvaluation(decision);
            
            riskEvaluationService.evaluateHealthRisks(age, medicalHistory, riskEval);
            riskEvaluationService.assessOccupationalHazards(occupation, riskEval);
            riskEvaluationService.analyzeFinancialStability(financialStatus, riskEval);
            riskEvaluationService.evaluateLifestyleFactors(applicantId, riskEval);
            
            int overallRiskScore = riskEvaluationService.calculateOverallRiskScore(riskEval);
            riskEvaluationService.classifyRiskLevel(riskEval, overallRiskScore);
            
            // Step 5: Medical underwriting and health assessment
            log.info("Step 5: Performing medical underwriting and health assessment");
            if (riskEvaluationService.requiresMedicalExam(riskEval)) {
                riskEvaluationService.scheduleMedicalExamination(decision);
                riskEvaluationService.orderMedicalRecords(applicantId, decision);
            }
            
            riskEvaluationService.analyzeHealthQuestionnaire(medicalHistory, riskEval);
            riskEvaluationService.assessPreexistingConditions(riskEval);
            riskEvaluationService.evaluateFamilyMedicalHistory(applicantId, riskEval);
            
            // Step 6: Financial underwriting and capacity analysis
            log.info("Step 6: Conducting financial underwriting and capacity analysis");
            actuarialService.createActuarialAnalysis(decision);
            actuarialService.verifyIncomeAndAssets(applicantId);
            actuarialService.assessInsurabilityLimits(requestedCoverage, applicantId);
            actuarialService.analyzePremiumAffordability(requestedCoverage, applicantId);
            
            boolean financiallyQualified = actuarialService.determineFinancialQualification(decision);
            if (!financiallyQualified) {
                decisionService.recommendCoverageReduction(decision);
            }
            
            // Step 7: Actuarial analysis and premium calculation
            log.info("Step 7: Performing actuarial analysis and calculating premiums");
            ActuarialAnalysis analysis = actuarialService.performActuarialAnalysis(decision, riskEval);
            
            actuarialService.calculateMortalityRates(analysis);
            actuarialService.assessMorbidityFactors(analysis);
            actuarialService.calculateBasePremium(analysis);
            actuarialService.applyRiskLoadings(analysis, overallRiskScore);
            
            BigDecimal finalPremium = actuarialService.calculateFinalPremium(analysis);
            actuarialService.validatePremiumCalculation(analysis, finalPremium);
            
            // Step 8: Underwriting decision and approval process
            log.info("Step 8: Making underwriting decision and processing approval");
            String underwritingDecision = decisionService.makeUnderwritingDecision(decision, riskEval, analysis);
            
            if ("DECLINED".equals(underwritingDecision)) {
                decisionService.processDeclineDecision(decision);
                decisionService.generateDeclineNotification(decision);
            } else if ("COUNTEROFFER".equals(underwritingDecision)) {
                decisionService.prepareCounteroffer(decision, finalPremium);
                decisionService.adjustCoverageTerms(decision);
            } else if ("APPROVED".equals(underwritingDecision)) {
                decisionService.processApprovalDecision(decision);
                decisionService.finalizePolicyTerms(decision, finalPremium);
            }
            
            // Step 9: Compliance validation and regulatory requirements
            log.info("Step 9: Validating compliance and regulatory requirements");
            complianceService.validateUnderwritingCompliance(decision);
            complianceService.checkDiscriminationGuidelines(decision);
            complianceService.validateInsurabilityRules(decision);
            complianceService.ensureRegulatoryCompliance(decision);
            
            if (complianceService.requiresComplianceReview(decision)) {
                complianceService.scheduleComplianceReview(decision);
            }
            
            // Step 10: Documentation and notification
            log.info("Step 10: Generating documentation and processing notifications");
            decisionService.generateUnderwritingReport(decision);
            decisionService.documentDecisionRationale(decision);
            decisionService.prepareApplicantNotification(decision);
            decisionService.updateApplicationStatus(decision);
            
            decisionService.notifyApplicant(decision);
            decisionService.informSalesTeam(decision);
            
            // Step 11: Quality assurance and peer review
            log.info("Step 11: Conducting quality assurance and peer review");
            if (decisionService.requiresPeerReview(decision)) {
                decisionService.assignPeerReviewer(decision);
                decisionService.scheduleQualityReview(decision);
            }
            
            decisionService.validateDecisionAccuracy(decision);
            decisionService.assessDecisionConsistency(decision);
            
            // Step 12: Audit trail and performance metrics
            log.info("Step 12: Completing audit trail and updating performance metrics");
            auditService.logUnderwritingDecision(decision);
            auditService.logRiskEvaluation(riskEval);
            auditService.logActuarialAnalysis(analysis);
            
            decisionService.updateUnderwritingMetrics(decision);
            riskEvaluationService.updateRiskStatistics(riskEval);
            actuarialService.updateActuarialMetrics(analysis);
            
            auditService.generateUnderwritingReport(decision);
            auditService.updateRegulatoryReporting(decision);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed underwriting event: applicationId={}, eventId={}, decision={}", 
                    applicationId, eventId, underwritingDecision);
            
        } catch (Exception e) {
            log.error("Error processing underwriting event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("applicationId") || 
            !eventData.has("applicantId") || !eventData.has("policyType") ||
            !eventData.has("requestedCoverage") || !eventData.has("riskClassification") ||
            !eventData.has("age") || !eventData.has("occupation") ||
            !eventData.has("medicalHistory") || !eventData.has("financialStatus") ||
            !eventData.has("timestamp")) {
            throw new IllegalArgumentException("Invalid underwriting event structure");
        }
    }
}