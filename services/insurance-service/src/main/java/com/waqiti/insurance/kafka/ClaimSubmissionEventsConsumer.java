package com.waqiti.insurance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.insurance.service.ClaimProcessingService;
import com.waqiti.insurance.service.ClaimValidationService;
import com.waqiti.insurance.service.FraudDetectionService;
import com.waqiti.insurance.service.ClaimAdjustmentService;
import com.waqiti.insurance.service.AuditService;
import com.waqiti.insurance.entity.InsuranceClaim;
import com.waqiti.insurance.entity.ClaimValidation;
import com.waqiti.insurance.entity.FraudAssessment;
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
 * Critical Event Consumer #13: Claim Submission Events Consumer
 * Processes insurance claim submissions, validation, and fraud detection
 * Implements 12-step zero-tolerance processing for insurance claims
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimSubmissionEventsConsumer extends BaseKafkaConsumer {

    private final ClaimProcessingService claimProcessingService;
    private final ClaimValidationService validationService;
    private final FraudDetectionService fraudDetectionService;
    private final ClaimAdjustmentService adjustmentService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "claim-submission-events", 
        groupId = "claim-submission-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @CircuitBreaker(name = "claim-submission-consumer")
    @Retry(name = "claim-submission-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleClaimSubmissionEvent(
            ConsumerRecord<String, String> record, 
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "claim-submission-event");
        MDC.put("partition", String.valueOf(partition));
        
        try {
            log.info("Step 1: Processing claim submission event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String claimId = eventData.path("claimId").asText();
            String policyId = eventData.path("policyId").asText();
            String policyholderCustomerId = eventData.path("policyholderCustomerId").asText();
            String claimType = eventData.path("claimType").asText(); // MEDICAL, ACCIDENT, PROPERTY, LIABILITY
            BigDecimal claimedAmount = new BigDecimal(eventData.path("claimedAmount").asText());
            LocalDate incidentDate = LocalDate.parse(eventData.path("incidentDate").asText());
            LocalDateTime submissionDate = LocalDateTime.parse(eventData.path("submissionDate").asText());
            String incidentDescription = eventData.path("incidentDescription").asText();
            String incidentLocation = eventData.path("incidentLocation").asText();
            List<String> documentIds = objectMapper.convertValue(eventData.path("documentIds"), List.class);
            String priority = eventData.path("priority").asText(); // URGENT, HIGH, NORMAL, LOW
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted claim details: claimId={}, policyId={}, type={}, amount={}, priority={}", 
                    claimId, policyId, claimType, claimedAmount, priority);
            
            // Step 3: Claim registration and initial validation
            log.info("Step 3: Registering claim and performing initial validation");
            InsuranceClaim claim = claimProcessingService.createClaim(eventData);
            
            claimProcessingService.validateClaimType(claimType);
            claimProcessingService.validatePolicyStatus(policyId);
            claimProcessingService.validatePolicyholder(policyholderCustomerId, policyId);
            claimProcessingService.validateSubmissionTimeliness(incidentDate, submissionDate);
            
            if (!claimProcessingService.isValidClaimAmount(claimedAmount)) {
                throw new IllegalStateException("Invalid claim amount: " + claimedAmount);
            }
            
            claimProcessingService.assignClaimNumber(claim);
            claimProcessingService.setClaimPriority(claim, priority);
            
            // Step 4: Policy coverage validation and verification
            log.info("Step 4: Validating policy coverage and verifying claim eligibility");
            ClaimValidation validation = validationService.createClaimValidation(claim);
            
            validationService.validateCoverageEligibility(claim, validation);
            validationService.checkPolicyLimits(claim, validation);
            validationService.validateDeductibles(claim, validation);
            validationService.checkExclusionsAndLimitations(claim, validation);
            
            boolean coverageValid = validationService.verifyCoverage(claim, claimType);
            if (!coverageValid) {
                validationService.handleCoverageRejection(claim, validation);
                return;
            }
            
            validationService.calculateCoverageAmount(claim, validation);
            
            // Step 5: Documentation collection and verification
            log.info("Step 5: Collecting and verifying claim documentation");
            validationService.validateRequiredDocuments(claim, documentIds);
            validationService.verifyDocumentAuthenticity(documentIds);
            validationService.extractDocumentData(claim, documentIds);
            validationService.crossReferenceDocuments(claim, documentIds);
            
            if (validationService.hasIncompleteDocumentation(claim)) {
                validationService.requestAdditionalDocuments(claim);
                claimProcessingService.updateClaimStatus(claim, "PENDING_DOCUMENTS");
                return;
            }
            
            validationService.archiveDocuments(claim, documentIds);
            
            // Step 6: Fraud detection and risk assessment
            log.info("Step 6: Conducting fraud detection and risk assessment");
            FraudAssessment fraudAssessment = fraudDetectionService.createFraudAssessment(claim);
            
            fraudDetectionService.analyzeFraudIndicators(claim, fraudAssessment);
            fraudDetectionService.checkClaimHistory(policyholderCustomerId, fraudAssessment);
            fraudDetectionService.assessIncidentCircumstances(claim, fraudAssessment);
            fraudDetectionService.analyzeSubmissionPatterns(claim, fraudAssessment);
            
            int fraudRiskScore = fraudDetectionService.calculateFraudRiskScore(fraudAssessment);
            if (fraudRiskScore >= 75) {
                fraudDetectionService.flagForInvestigation(claim, fraudAssessment);
                claimProcessingService.updateClaimStatus(claim, "UNDER_INVESTIGATION");
                return;
            }
            
            fraudDetectionService.updateFraudMetrics(fraudAssessment);
            
            // Step 7: Medical review and assessment (for medical claims)
            log.info("Step 7: Conducting medical review and assessment for medical claims");
            if ("MEDICAL".equals(claimType)) {
                validationService.validateMedicalNecessity(claim);
                validationService.reviewMedicalRecords(claim);
                validationService.assessTreatmentAppropriately(claim);
                validationService.verifyProviderCredentials(claim);
                
                if (validationService.requiresMedicalReview(claim)) {
                    validationService.scheduleMedicalReview(claim);
                    claimProcessingService.updateClaimStatus(claim, "MEDICAL_REVIEW");
                }
            }
            
            // Step 8: Claim adjustment and settlement calculation
            log.info("Step 8: Performing claim adjustment and calculating settlement");
            adjustmentService.assessClaimDamages(claim);
            adjustmentService.calculateSettlementAmount(claim);
            adjustmentService.applyDeductibles(claim);
            adjustmentService.considerPolicyLimits(claim);
            
            BigDecimal settlementAmount = adjustmentService.determineSettlement(claim);
            adjustmentService.validateSettlementCalculation(claim, settlementAmount);
            
            if (adjustmentService.requiresAdjusterReview(claim, settlementAmount)) {
                adjustmentService.assignClaimsAdjuster(claim);
                claimProcessingService.updateClaimStatus(claim, "ADJUSTER_REVIEW");
            }
            
            // Step 9: Settlement approval and authorization
            log.info("Step 9: Processing settlement approval and authorization");
            if (settlementAmount.compareTo(adjustmentService.getAutoApprovalLimit()) <= 0) {
                adjustmentService.autoApproveSettlement(claim, settlementAmount);
                claimProcessingService.updateClaimStatus(claim, "APPROVED");
            } else {
                adjustmentService.requireManagerialApproval(claim, settlementAmount);
                claimProcessingService.updateClaimStatus(claim, "PENDING_APPROVAL");
                return;
            }
            
            adjustmentService.generateSettlementDocuments(claim, settlementAmount);
            
            // Step 10: Payment processing and disbursement
            log.info("Step 10: Processing payment and disbursing settlement");
            if ("APPROVED".equals(claim.getStatus())) {
                claimProcessingService.initiatePayment(claim, settlementAmount);
                claimProcessingService.validatePaymentMethod(claim);
                claimProcessingService.processSettlementPayment(claim);
                
                claimProcessingService.generatePaymentConfirmation(claim);
                claimProcessingService.updateClaimStatus(claim, "PAID");
            }
            
            claimProcessingService.updateClaimHistory(claim);
            
            // Step 11: Customer communication and case closure
            log.info("Step 11: Managing customer communications and case closure");
            claimProcessingService.notifyCustomerOfDecision(claim);
            claimProcessingService.generateClaimSummary(claim);
            claimProcessingService.updateCustomerHistory(claim);
            claimProcessingService.provideFeedbackOpportunity(claim);
            
            if ("PAID".equals(claim.getStatus())) {
                claimProcessingService.closeClaim(claim);
                claimProcessingService.generateClosureDocuments(claim);
            }
            
            // Step 12: Audit trail and regulatory reporting
            log.info("Step 12: Completing audit trail and regulatory compliance");
            auditService.logInsuranceClaim(claim);
            auditService.logClaimValidation(validation);
            auditService.logFraudAssessment(fraudAssessment);
            
            claimProcessingService.updateClaimMetrics(claim);
            validationService.updateValidationStatistics(validation);
            fraudDetectionService.updateFraudStatistics(fraudAssessment);
            
            auditService.generateClaimReport(claim);
            auditService.updateRegulatoryReporting(claim);
            
            claimProcessingService.archiveClaimDocuments(claim);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed claim submission: claimId={}, eventId={}, status={}", 
                    claimId, eventId, claim.getStatus());
            
        } catch (Exception e) {
            log.error("Error processing claim submission event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("claimId") || 
            !eventData.has("policyId") || !eventData.has("policyholderCustomerId") ||
            !eventData.has("claimType") || !eventData.has("claimedAmount") ||
            !eventData.has("incidentDate") || !eventData.has("submissionDate") ||
            !eventData.has("incidentDescription") || !eventData.has("incidentLocation") ||
            !eventData.has("documentIds") || !eventData.has("priority") ||
            !eventData.has("timestamp")) {
            throw new IllegalArgumentException("Invalid claim submission event structure");
        }
    }
}