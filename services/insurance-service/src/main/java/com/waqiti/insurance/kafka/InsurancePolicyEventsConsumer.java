package com.waqiti.insurance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.insurance.service.InsurancePolicyService;
import com.waqiti.insurance.service.PolicyUnderwritingService;
import com.waqiti.insurance.service.RiskAssessmentService;
import com.waqiti.insurance.service.ActuarialService;
import com.waqiti.insurance.service.AuditService;
import com.waqiti.insurance.entity.InsurancePolicy;
import com.waqiti.insurance.entity.PolicyUnderwriting;
import com.waqiti.insurance.entity.RiskProfile;
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
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

/**
 * Critical Event Consumer #12: Insurance Policy Events Consumer
 * Processes insurance policy applications, renewals, and policy management
 * Implements 12-step zero-tolerance processing for insurance policy administration
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsurancePolicyEventsConsumer extends BaseKafkaConsumer {

    private final InsurancePolicyService policyService;
    private final PolicyUnderwritingService underwritingService;
    private final RiskAssessmentService riskAssessmentService;
    private final ActuarialService actuarialService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "insurance-policy-events", 
        groupId = "insurance-policy-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @CircuitBreaker(name = "insurance-policy-consumer")
    @Retry(name = "insurance-policy-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleInsurancePolicyEvent(
            ConsumerRecord<String, String> record, 
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "insurance-policy-event");
        MDC.put("partition", String.valueOf(partition));
        
        try {
            log.info("Step 1: Processing insurance policy event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String policyId = eventData.path("policyId").asText();
            String policyholderCustomerId = eventData.path("policyholderCustomerId").asText();
            String eventType = eventData.path("eventType").asText(); // APPLICATION, RENEWAL, MODIFICATION, CANCELLATION
            String policyType = eventData.path("policyType").asText(); // LIFE, HEALTH, AUTO, HOME, BUSINESS
            BigDecimal coverageAmount = new BigDecimal(eventData.path("coverageAmount").asText());
            BigDecimal premiumAmount = new BigDecimal(eventData.path("premiumAmount").asText());
            LocalDate policyStartDate = LocalDate.parse(eventData.path("policyStartDate").asText());
            LocalDate policyEndDate = LocalDate.parse(eventData.path("policyEndDate").asText());
            String paymentFrequency = eventData.path("paymentFrequency").asText(); // MONTHLY, QUARTERLY, ANNUALLY
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            String riskCategory = eventData.path("riskCategory").asText(); // LOW, MEDIUM, HIGH
            
            log.info("Step 2: Extracted policy details: policyId={}, type={}, coverage={}, premium={}, risk={}", 
                    policyId, policyType, coverageAmount, premiumAmount, riskCategory);
            
            // Step 3: Policy application validation and eligibility
            log.info("Step 3: Validating policy application and checking eligibility");
            InsurancePolicy policy = policyService.createOrUpdatePolicy(eventData);
            
            policyService.validatePolicyType(policyType);
            policyService.validateCustomerEligibility(policyholderCustomerId, policyType);
            policyService.validateCoverageAmount(coverageAmount, policyType);
            policyService.validatePolicyDates(policyStartDate, policyEndDate);
            
            if (!policyService.isValidEventType(eventType)) {
                throw new IllegalStateException("Invalid policy event type: " + eventType);
            }
            
            policyService.checkExistingPolicies(policyholderCustomerId, policyType);
            
            // Step 4: Risk assessment and profiling
            log.info("Step 4: Conducting comprehensive risk assessment and profiling");
            RiskProfile riskProfile = riskAssessmentService.createRiskProfile(policy);
            
            riskAssessmentService.assessCustomerRisk(policyholderCustomerId, riskProfile);
            riskAssessmentService.evaluateRiskFactors(policy, riskProfile);
            riskAssessmentService.analyzeHistoricalClaims(policyholderCustomerId, riskProfile);
            riskAssessmentService.assessGeographicRisk(policy, riskProfile);
            
            if ("HIGH".equals(riskCategory)) {
                riskAssessmentService.requireAdditionalDocumentation(policy);
                riskAssessmentService.scheduleRiskEvaluation(policy);
            }
            
            riskAssessmentService.calculateRiskScore(riskProfile);
            
            // Step 5: Underwriting process and evaluation
            log.info("Step 5: Conducting underwriting evaluation and decision making");
            PolicyUnderwriting underwriting = underwritingService.createUnderwriting(policy);
            
            underwritingService.evaluateUnderwritingGuidelines(policy, underwriting);
            underwritingService.assessMedicalHistory(policyholderCustomerId, underwriting);
            underwritingService.validateFinancialInformation(policyholderCustomerId, underwriting);
            underwritingService.analyzeOccupationalRisk(policyholderCustomerId, underwriting);
            
            String underwritingDecision = underwritingService.makeUnderwritingDecision(underwriting);
            if (!"APPROVED".equals(underwritingDecision)) {
                underwritingService.handleDeclineOrCounteroffer(underwriting);
                return;
            }
            
            underwritingService.finalizeUnderwriting(underwriting);
            
            // Step 6: Actuarial analysis and premium calculation
            log.info("Step 6: Performing actuarial analysis and calculating premiums");
            actuarialService.performActuarialAnalysis(policy, riskProfile);
            actuarialService.calculateBasePremium(policy, riskProfile);
            actuarialService.applyRiskAdjustments(policy, riskProfile);
            actuarialService.calculateLoadingFactors(policy);
            
            BigDecimal calculatedPremium = actuarialService.calculateFinalPremium(policy, riskProfile);
            actuarialService.validatePremiumCalculation(calculatedPremium, premiumAmount);
            
            if (actuarialService.significantPremiumDeviation(calculatedPremium, premiumAmount)) {
                actuarialService.escalateToActuary(policy);
            }
            
            // Step 7: Policy issuance and documentation
            log.info("Step 7: Processing policy issuance and generating documentation");
            if ("APPLICATION".equals(eventType) && "APPROVED".equals(underwritingDecision)) {
                policyService.issuePolicy(policy);
                policyService.generatePolicyDocuments(policy);
                policyService.createPolicySchedule(policy);
                policyService.establishBeneficiaries(policy);
                
                policyService.activatePolicy(policy);
                policyService.sendPolicyConfirmation(policy);
            }
            
            // Step 8: Policy renewal and modification processing
            log.info("Step 8: Processing policy renewals and modifications");
            if ("RENEWAL".equals(eventType)) {
                policyService.processRenewal(policy);
                policyService.updateRenewalTerms(policy);
                policyService.recalculateRenewalPremium(policy, riskProfile);
                policyService.generateRenewalDocuments(policy);
            } else if ("MODIFICATION".equals(eventType)) {
                policyService.processModification(policy, eventData);
                policyService.validateModificationRequest(policy, eventData);
                policyService.updatePolicyTerms(policy, eventData);
                policyService.recalculatePremium(policy);
            }
            
            // Step 9: Premium billing and payment setup
            log.info("Step 9: Setting up premium billing and payment schedules");
            policyService.setupPremiumBilling(policy, paymentFrequency);
            policyService.calculatePaymentSchedule(policy, premiumAmount, paymentFrequency);
            policyService.configureAutomaticPayments(policy);
            policyService.generateBillingStatements(policy);
            
            policyService.validatePaymentMethods(policyholderCustomerId);
            policyService.scheduleFirstPremiumPayment(policy);
            
            // Step 10: Compliance and regulatory requirements
            log.info("Step 10: Ensuring compliance with insurance regulations");
            policyService.validateRegulatoryCompliance(policy);
            policyService.checkStateLicensingRequirements(policy);
            policyService.validateInsuranceLaws(policy, policyType);
            policyService.generateRegulatoryFilings(policy);
            
            if (policyService.requiresStateReporting(policy)) {
                policyService.submitStateReports(policy);
            }
            
            policyService.updateComplianceMetrics(policy);
            
            // Step 11: Customer communication and service setup
            log.info("Step 11: Establishing customer communications and service channels");
            policyService.sendWelcomePackage(policy);
            policyService.setupCustomerPortalAccess(policy);
            policyService.scheduleCustomerCommunications(policy);
            policyService.providePolicyEducation(policy);
            
            policyService.assignCustomerServiceRepresentative(policy);
            policyService.configureNotificationPreferences(policy);
            
            // Step 12: Audit trail and record management
            log.info("Step 12: Completing audit trail and maintaining policy records");
            auditService.logInsurancePolicy(policy);
            auditService.logPolicyUnderwriting(underwriting);
            auditService.logRiskProfile(riskProfile);
            
            policyService.updatePolicyMetrics(policy);
            underwritingService.updateUnderwritingStatistics(underwriting);
            riskAssessmentService.updateRiskStatistics(riskProfile);
            
            auditService.generatePolicyReport(policy);
            auditService.updateRegulatoryReporting(policy);
            
            policyService.archivePolicyDocuments(policy);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed insurance policy event: policyId={}, eventId={}, type={}", 
                    policyId, eventId, eventType);
            
        } catch (Exception e) {
            log.error("Error processing insurance policy event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("policyId") || 
            !eventData.has("policyholderCustomerId") || !eventData.has("eventType") ||
            !eventData.has("policyType") || !eventData.has("coverageAmount") ||
            !eventData.has("premiumAmount") || !eventData.has("policyStartDate") ||
            !eventData.has("policyEndDate") || !eventData.has("paymentFrequency") ||
            !eventData.has("timestamp") || !eventData.has("riskCategory")) {
            throw new IllegalArgumentException("Invalid insurance policy event structure");
        }
    }
}