package com.waqiti.insurance.consumer;

import com.waqiti.common.events.InsuranceClaimSubmittedEvent;
import com.waqiti.insurance.service.ClaimProcessingService;
import com.waqiti.insurance.service.FraudInvestigationService;
import com.waqiti.insurance.service.PolicyVerificationService;
import com.waqiti.insurance.service.NotificationService;
import com.waqiti.insurance.service.ActuarialService;
import com.waqiti.insurance.repository.ProcessedEventRepository;
import com.waqiti.insurance.repository.InsuranceClaimRepository;
import com.waqiti.insurance.model.ProcessedEvent;
import com.waqiti.insurance.model.InsuranceClaim;
import com.waqiti.insurance.model.ClaimStatus;
import com.waqiti.insurance.model.InvestigationLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Consumer for InsuranceClaimSubmittedEvent - Critical for insurance processing
 * Handles claim validation, fraud detection, policy verification, and automated adjudication
 * ZERO TOLERANCE: All insurance claims must follow proper adjudication and fraud detection procedures
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InsuranceClaimSubmittedEventConsumer {
    
    private final ClaimProcessingService claimProcessingService;
    private final FraudInvestigationService fraudInvestigationService;
    private final PolicyVerificationService policyVerificationService;
    private final NotificationService notificationService;
    private final ActuarialService actuarialService;
    private final ProcessedEventRepository processedEventRepository;
    private final InsuranceClaimRepository insuranceClaimRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal LARGE_CLAIM_THRESHOLD = new BigDecimal("25000");
    private static final BigDecimal AUTOMATED_APPROVAL_LIMIT = new BigDecimal("5000");
    private static final BigDecimal SUSPICIOUS_AMOUNT_THRESHOLD = new BigDecimal("50000");
    
    @KafkaListener(
        topics = "insurance.claim.submitted",
        groupId = "insurance-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for claim processing
    public void handleInsuranceClaimSubmitted(InsuranceClaimSubmittedEvent event) {
        log.info("Processing insurance claim: {} - Policy: {} - Amount: ${} - Type: {}", 
            event.getClaimId(), event.getPolicyId(), event.getClaimAmount(), event.getClaimType());
        
        // IDEMPOTENCY CHECK - Prevent duplicate claim processing
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Insurance claim already processed: {}", event.getEventId());
            return;
        }
        
        try {
            // Get or create claim record
            InsuranceClaim claim = getOrCreateClaim(event);
            
            // STEP 1: Verify policy validity and coverage
            verifyPolicyAndCoverage(claim, event);
            
            // STEP 2: Perform initial fraud screening
            performInitialFraudScreening(claim, event);
            
            // STEP 3: Validate claim documentation and evidence
            validateClaimDocumentation(claim, event);
            
            // STEP 4: Execute automated claim assessment
            executeAutomatedClaimAssessment(claim, event);
            
            // STEP 5: Determine investigation level and requirements
            determineInvestigationLevel(claim, event);
            
            // STEP 6: Process medical/technical reviews if required
            processMedicalTechnicalReviews(claim, event);
            
            // STEP 7: Calculate actuarial reserves and impact
            calculateActuarialReserves(claim, event);
            
            // STEP 8: Execute automated adjudication engine
            executeAutomatedAdjudication(claim, event);
            
            // STEP 9: Generate regulatory compliance reports
            generateRegulatoryCompliance(claim, event);
            
            // STEP 10: Process reinsurance notifications if applicable
            processReinsuranceNotifications(claim, event);
            
            // STEP 11: Send stakeholder notifications
            sendStakeholderNotifications(claim, event);
            
            // STEP 12: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("InsuranceClaimSubmittedEvent")
                .processedAt(Instant.now())
                .claimId(event.getClaimId())
                .policyId(event.getPolicyId())
                .claimantId(event.getClaimantId())
                .claimAmount(event.getClaimAmount())
                .claimType(event.getClaimType())
                .claimStatus(claim.getStatus())
                .fraudScore(claim.getFraudScore())
                .investigationLevel(claim.getInvestigationLevel())
                .approvedAmount(claim.getApprovedAmount())
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully processed insurance claim: {} - Status: {}, Approved: ${}, Investigation: {}", 
                event.getClaimId(), claim.getStatus(), claim.getApprovedAmount(), claim.getInvestigationLevel());
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process insurance claim: {}", 
                event.getClaimId(), e);
                
            // Create manual intervention record
            createManualInterventionRecord(event, e);
            
            throw new RuntimeException("Insurance claim processing failed", e);
        }
    }
    
    private InsuranceClaim getOrCreateClaim(InsuranceClaimSubmittedEvent event) {
        return insuranceClaimRepository.findById(event.getClaimId())
            .orElseGet(() -> createNewClaim(event));
    }
    
    private InsuranceClaim createNewClaim(InsuranceClaimSubmittedEvent event) {
        InsuranceClaim claim = InsuranceClaim.builder()
            .id(event.getClaimId())
            .policyId(event.getPolicyId())
            .claimantId(event.getClaimantId())
            .claimAmount(event.getClaimAmount())
            .claimType(event.getClaimType())
            .incidentDate(event.getIncidentDate())
            .reportedDate(event.getReportedDate())
            .submissionDate(LocalDateTime.now())
            .description(event.getDescription())
            .status(ClaimStatus.SUBMITTED)
            .fraudFlags(new ArrayList<>())
            .investigationNotes(new ArrayList<>())
            .build();
        
        return insuranceClaimRepository.save(claim);
    }
    
    private void verifyPolicyAndCoverage(InsuranceClaim claim, InsuranceClaimSubmittedEvent event) {
        // Verify policy is active and in good standing
        Map<String, Object> policyStatus = policyVerificationService.verifyPolicyStatus(
            event.getPolicyId(),
            event.getIncidentDate()
        );
        
        claim.setPolicyVerificationData(policyStatus);
        
        boolean policyActive = (Boolean) policyStatus.get("active");
        boolean premiumsCurrent = (Boolean) policyStatus.get("premiumsCurrent");
        
        if (!policyActive) {
            claim.setStatus(ClaimStatus.DENIED);
            claim.setDenialReason("POLICY_INACTIVE");
            claim.setDenialDate(LocalDateTime.now());
            
            insuranceClaimRepository.save(claim);
            
            log.warn("Claim {} denied - policy inactive: {}", event.getClaimId(), event.getPolicyId());
            throw new RuntimeException("Policy is not active on incident date");
        }
        
        if (!premiumsCurrent) {
            claim.addFraudFlag("PREMIUMS_IN_ARREARS");
            claim.setRequiresManualReview(true);
        }
        
        // Verify coverage for claim type and amount
        Map<String, Object> coverageVerification = policyVerificationService.verifyCoverage(
            event.getPolicyId(),
            event.getClaimType(),
            event.getClaimAmount(),
            event.getIncidentDate()
        );
        
        claim.setCoverageVerificationData(coverageVerification);
        
        boolean coverageValid = (Boolean) coverageVerification.get("coverageValid");
        BigDecimal maxCoverage = (BigDecimal) coverageVerification.get("maxCoverageAmount");
        BigDecimal deductible = (BigDecimal) coverageVerification.get("deductible");
        
        if (!coverageValid) {
            claim.setStatus(ClaimStatus.DENIED);
            claim.setDenialReason("NO_COVERAGE");
            claim.setDenialDate(LocalDateTime.now());
            
            insuranceClaimRepository.save(claim);
            
            log.warn("Claim {} denied - no coverage for type: {}", event.getClaimId(), event.getClaimType());
            throw new RuntimeException("No coverage exists for this claim type");
        }
        
        claim.setMaxCoverageAmount(maxCoverage);
        claim.setDeductible(deductible);
        
        // Check if claim amount exceeds coverage limits
        if (event.getClaimAmount().compareTo(maxCoverage) > 0) {
            claim.addFraudFlag("EXCEEDS_COVERAGE_LIMIT");
            claim.setAdjustedAmount(maxCoverage.subtract(deductible));
        } else {
            claim.setAdjustedAmount(event.getClaimAmount().subtract(deductible));
        }
        
        // Check waiting periods and exclusions
        List<String> exclusions = policyVerificationService.checkExclusions(
            event.getPolicyId(),
            event.getClaimType(),
            event.getIncidentDate(),
            event.getDescription()
        );
        
        if (!exclusions.isEmpty()) {
            claim.setStatus(ClaimStatus.DENIED);
            claim.setDenialReason("POLICY_EXCLUSION");
            claim.setExclusionReasons(exclusions);
            
            insuranceClaimRepository.save(claim);
            
            log.warn("Claim {} denied - policy exclusions: {}", event.getClaimId(), exclusions);
            throw new RuntimeException("Claim falls under policy exclusions");
        }
        
        insuranceClaimRepository.save(claim);
        
        log.info("Policy verification completed for claim {}: Valid coverage: ${}, Deductible: ${}", 
            event.getClaimId(), maxCoverage, deductible);
    }
    
    private void performInitialFraudScreening(InsuranceClaim claim, InsuranceClaimSubmittedEvent event) {
        // Calculate fraud risk score
        double fraudScore = fraudInvestigationService.calculateFraudScore(
            event.getClaimId(),
            event.getClaimantId(),
            event.getPolicyId(),
            event.getClaimAmount(),
            event.getClaimType(),
            event.getIncidentDate(),
            event.getReportedDate(),
            event.getDescription()
        );
        
        claim.setFraudScore(fraudScore);
        
        // Check for suspicious patterns
        if (fraudScore > 0.75) {
            claim.addFraudFlag("HIGH_FRAUD_SCORE");
            claim.setRequiresInvestigation(true);
        }
        
        // Check claim amount patterns
        if (event.getClaimAmount().compareTo(SUSPICIOUS_AMOUNT_THRESHOLD) > 0) {
            claim.addFraudFlag("LARGE_CLAIM_AMOUNT");
            claim.setRequiresInvestigation(true);
        }
        
        // Check timing patterns (claim reported too quickly or too late)
        long reportingDelayHours = java.time.Duration.between(
            event.getIncidentDate().atZone(java.time.ZoneOffset.UTC).toInstant(),
            event.getReportedDate().atZone(java.time.ZoneOffset.UTC).toInstant()
        ).toHours();
        
        if (reportingDelayHours < 1) {
            claim.addFraudFlag("IMMEDIATE_REPORTING");
        } else if (reportingDelayHours > 720) { // 30 days
            claim.addFraudFlag("DELAYED_REPORTING");
        }
        
        // Check claimant history
        Map<String, Object> claimantHistory = fraudInvestigationService.analyzeClaimantHistory(
            event.getClaimantId(),
            LocalDateTime.now().minusYears(5)
        );
        
        claim.setClaimantHistoryData(claimantHistory);
        
        int priorClaimsCount = (Integer) claimantHistory.get("priorClaimsCount");
        BigDecimal priorClaimsAmount = (BigDecimal) claimantHistory.get("priorClaimsAmount");
        
        if (priorClaimsCount > 3) {
            claim.addFraudFlag("FREQUENT_CLAIMANT");
        }
        
        if (priorClaimsAmount.compareTo(new BigDecimal("100000")) > 0) {
            claim.addFraudFlag("HIGH_VALUE_CLAIMANT");
        }
        
        // Check for duplicate claims
        boolean duplicateClaim = fraudInvestigationService.checkDuplicateClaims(
            event.getClaimantId(),
            event.getIncidentDate(),
            event.getClaimAmount(),
            event.getDescription()
        );
        
        if (duplicateClaim) {
            claim.addFraudFlag("POTENTIAL_DUPLICATE");
            claim.setRequiresInvestigation(true);
        }
        
        insuranceClaimRepository.save(claim);
        
        log.info("Initial fraud screening completed for claim {}: Score: {}, Flags: {}", 
            event.getClaimId(), fraudScore, claim.getFraudFlags().size());
    }
    
    private void validateClaimDocumentation(InsuranceClaim claim, InsuranceClaimSubmittedEvent event) {
        List<String> documentationIssues = new ArrayList<>();
        
        // Validate required documents based on claim type
        List<String> requiredDocuments = claimProcessingService.getRequiredDocuments(
            event.getClaimType(),
            event.getClaimAmount()
        );
        
        List<String> providedDocuments = event.getProvidedDocuments();
        
        for (String requiredDoc : requiredDocuments) {
            if (!providedDocuments.contains(requiredDoc)) {
                documentationIssues.add("MISSING_" + requiredDoc);
            }
        }
        
        claim.setDocumentationIssues(documentationIssues);
        
        if (!documentationIssues.isEmpty()) {
            claim.setStatus(ClaimStatus.PENDING_DOCUMENTATION);
            claim.setRequiresCustomerAction(true);
            
            log.info("Claim {} pending documentation: {}", event.getClaimId(), documentationIssues);
        }
        
        // Validate document authenticity using AI/ML models
        for (String document : providedDocuments) {
            double authenticityScore = claimProcessingService.validateDocumentAuthenticity(
                document,
                event.getClaimType()
            );
            
            if (authenticityScore < 0.7) {
                claim.addFraudFlag("QUESTIONABLE_DOCUMENT_" + document);
                claim.setRequiresInvestigation(true);
            }
        }
        
        // Check for consistent information across documents
        boolean consistentInfo = claimProcessingService.validateDocumentConsistency(
            providedDocuments,
            event.getClaimAmount(),
            event.getIncidentDate(),
            event.getDescription()
        );
        
        if (!consistentInfo) {
            claim.addFraudFlag("INCONSISTENT_DOCUMENTATION");
            claim.setRequiresManualReview(true);
        }
        
        insuranceClaimRepository.save(claim);
        
        log.info("Documentation validation completed for claim {}: Issues: {}", 
            event.getClaimId(), documentationIssues.size());
    }
    
    private void executeAutomatedClaimAssessment(InsuranceClaim claim, InsuranceClaimSubmittedEvent event) {
        // Skip automated assessment if investigation required
        if (claim.isRequiresInvestigation()) {
            claim.setStatus(ClaimStatus.UNDER_INVESTIGATION);
            insuranceClaimRepository.save(claim);
            return;
        }
        
        // Skip if documentation is incomplete
        if (!claim.getDocumentationIssues().isEmpty()) {
            return; // Status already set to PENDING_DOCUMENTATION
        }
        
        // Run automated assessment engine
        Map<String, Object> assessmentResult = claimProcessingService.runAutomatedAssessment(
            event.getClaimId(),
            event.getClaimType(),
            event.getClaimAmount(),
            claim.getAdjustedAmount(),
            claim.getFraudScore(),
            claim.getFraudFlags(),
            event.getProvidedDocuments()
        );
        
        claim.setAssessmentData(assessmentResult);
        
        String assessmentDecision = (String) assessmentResult.get("decision");
        BigDecimal assessedAmount = (BigDecimal) assessmentResult.get("assessedAmount");
        double confidence = (Double) assessmentResult.get("confidence");
        
        claim.setAssessedAmount(assessedAmount);
        claim.setAssessmentConfidence(confidence);
        
        // Apply decision based on assessment
        switch (assessmentDecision) {
            case "APPROVE" -> {
                if (assessedAmount.compareTo(AUTOMATED_APPROVAL_LIMIT) <= 0 && confidence > 0.9) {
                    claim.setStatus(ClaimStatus.APPROVED);
                    claim.setApprovedAmount(assessedAmount);
                    claim.setApprovalDate(LocalDateTime.now());
                    claim.setApprovedBy("AUTOMATED_SYSTEM");
                } else {
                    claim.setStatus(ClaimStatus.PENDING_MANUAL_REVIEW);
                    claim.setRequiresManualReview(true);
                }
            }
            case "DENY" -> {
                claim.setStatus(ClaimStatus.DENIED);
                claim.setDenialReason("AUTOMATED_ASSESSMENT");
                claim.setDenialDate(LocalDateTime.now());
            }
            case "INVESTIGATE" -> {
                claim.setStatus(ClaimStatus.UNDER_INVESTIGATION);
                claim.setRequiresInvestigation(true);
            }
            default -> {
                claim.setStatus(ClaimStatus.PENDING_MANUAL_REVIEW);
                claim.setRequiresManualReview(true);
            }
        }
        
        insuranceClaimRepository.save(claim);
        
        log.info("Automated assessment completed for claim {}: Decision: {}, Amount: ${}, Confidence: {}", 
            event.getClaimId(), assessmentDecision, assessedAmount, confidence);
    }
    
    private void determineInvestigationLevel(InsuranceClaim claim, InsuranceClaimSubmittedEvent event) {
        if (!claim.isRequiresInvestigation()) {
            claim.setInvestigationLevel(InvestigationLevel.NONE);
            insuranceClaimRepository.save(claim);
            return;
        }
        
        // Determine investigation level based on risk factors
        InvestigationLevel level = fraudInvestigationService.determineInvestigationLevel(
            claim.getFraudScore(),
            claim.getFraudFlags(),
            event.getClaimAmount(),
            claim.getClaimantHistoryData()
        );
        
        claim.setInvestigationLevel(level);
        
        // Assign investigation resources
        switch (level) {
            case BASIC -> {
                String investigatorId = fraudInvestigationService.assignBasicInvestigator(event.getClaimId());
                claim.setAssignedInvestigatorId(investigatorId);
            }
            case ENHANCED -> {
                String investigatorId = fraudInvestigationService.assignEnhancedInvestigator(event.getClaimId());
                claim.setAssignedInvestigatorId(investigatorId);
                
                // Schedule field investigation if high value
                if (event.getClaimAmount().compareTo(LARGE_CLAIM_THRESHOLD) > 0) {
                    String fieldInvestigationId = fraudInvestigationService.scheduleFieldInvestigation(
                        event.getClaimId(),
                        event.getIncidentLocation()
                    );
                    claim.setFieldInvestigationId(fieldInvestigationId);
                }
            }
            case SPECIAL_INVESTIGATION -> {
                String siuInvestigatorId = fraudInvestigationService.assignSIUInvestigator(event.getClaimId());
                claim.setAssignedInvestigatorId(siuInvestigatorId);
                
                // Notify law enforcement if criminal fraud suspected
                if (claim.getFraudScore() > 0.9) {
                    fraudInvestigationService.notifyLawEnforcement(
                        event.getClaimId(),
                        event.getClaimantId(),
                        event.getClaimAmount()
                    );
                    claim.setLawEnforcementNotified(true);
                }
            }
        }
        
        insuranceClaimRepository.save(claim);
        
        log.info("Investigation level determined for claim {}: Level: {}, Investigator: {}", 
            event.getClaimId(), level, claim.getAssignedInvestigatorId());
    }
    
    private void processMedicalTechnicalReviews(InsuranceClaim claim, InsuranceClaimSubmittedEvent event) {
        // Skip if not applicable to claim type
        if (!requiresMedicalTechnicalReview(event.getClaimType())) {
            return;
        }
        
        // Schedule medical review for health/disability claims
        if (event.getClaimType().toUpperCase().contains("MEDICAL") || 
            event.getClaimType().toUpperCase().contains("DISABILITY")) {
            
            String medicalReviewId = claimProcessingService.scheduleMedicalReview(
                event.getClaimId(),
                event.getClaimType(),
                event.getClaimAmount(),
                event.getProvidedDocuments()
            );
            
            claim.setMedicalReviewId(medicalReviewId);
            claim.setStatus(ClaimStatus.PENDING_MEDICAL_REVIEW);
        }
        
        // Schedule technical review for property/auto claims
        if (event.getClaimType().toUpperCase().contains("PROPERTY") || 
            event.getClaimType().toUpperCase().contains("AUTO")) {
            
            String technicalReviewId = claimProcessingService.scheduleTechnicalReview(
                event.getClaimId(),
                event.getClaimType(),
                event.getClaimAmount(),
                event.getIncidentLocation()
            );
            
            claim.setTechnicalReviewId(technicalReviewId);
            claim.setStatus(ClaimStatus.PENDING_TECHNICAL_REVIEW);
        }
        
        insuranceClaimRepository.save(claim);
        
        log.info("Medical/Technical reviews scheduled for claim {}: Medical: {}, Technical: {}", 
            event.getClaimId(), claim.getMedicalReviewId(), claim.getTechnicalReviewId());
    }
    
    private void calculateActuarialReserves(InsuranceClaim claim, InsuranceClaimSubmittedEvent event) {
        // Calculate initial reserve amount
        BigDecimal initialReserve = actuarialService.calculateInitialReserve(
            event.getClaimType(),
            event.getClaimAmount(),
            claim.getFraudScore(),
            claim.getAssessmentConfidence()
        );
        
        claim.setInitialReserve(initialReserve);
        
        // Calculate ultimate reserve estimate
        BigDecimal ultimateReserve = actuarialService.calculateUltimateReserve(
            event.getClaimId(),
            event.getClaimType(),
            initialReserve,
            claim.getClaimantHistoryData()
        );
        
        claim.setUltimateReserve(ultimateReserve);
        
        // Update company loss reserves
        actuarialService.updateLossReserves(
            event.getClaimType(),
            initialReserve,
            ultimateReserve
        );
        
        // Check impact on profitability
        Map<String, Object> profitabilityImpact = actuarialService.analyzeProfitabilityImpact(
            event.getPolicyId(),
            ultimateReserve,
            LocalDateTime.now().getYear()
        );
        
        claim.setProfitabilityImpact(profitabilityImpact);
        
        insuranceClaimRepository.save(claim);
        
        log.info("Actuarial reserves calculated for claim {}: Initial: ${}, Ultimate: ${}", 
            event.getClaimId(), initialReserve, ultimateReserve);
    }
    
    private void executeAutomatedAdjudication(InsuranceClaim claim, InsuranceClaimSubmittedEvent event) {
        // Skip if manual review or investigation required
        if (claim.isRequiresManualReview() || claim.isRequiresInvestigation()) {
            return;
        }
        
        // Skip if pending other reviews
        if (claim.getStatus() == ClaimStatus.PENDING_DOCUMENTATION ||
            claim.getStatus() == ClaimStatus.PENDING_MEDICAL_REVIEW ||
            claim.getStatus() == ClaimStatus.PENDING_TECHNICAL_REVIEW) {
            return;
        }
        
        // Execute final adjudication
        Map<String, Object> adjudicationResult = claimProcessingService.executeAdjudication(
            event.getClaimId(),
            claim.getAssessedAmount(),
            claim.getDeductible(),
            claim.getMaxCoverageAmount(),
            claim.getFraudFlags()
        );
        
        claim.setAdjudicationData(adjudicationResult);
        
        String finalDecision = (String) adjudicationResult.get("decision");
        BigDecimal finalAmount = (BigDecimal) adjudicationResult.get("finalAmount");
        
        switch (finalDecision) {
            case "APPROVE" -> {
                claim.setStatus(ClaimStatus.APPROVED);
                claim.setApprovedAmount(finalAmount);
                claim.setApprovalDate(LocalDateTime.now());
                claim.setApprovedBy("AUTOMATED_ADJUDICATION");
            }
            case "PARTIAL_APPROVE" -> {
                claim.setStatus(ClaimStatus.PARTIALLY_APPROVED);
                claim.setApprovedAmount(finalAmount);
                claim.setApprovalDate(LocalDateTime.now());
                claim.setPartialApprovalReason((String) adjudicationResult.get("reason"));
            }
            case "DENY" -> {
                claim.setStatus(ClaimStatus.DENIED);
                claim.setDenialReason((String) adjudicationResult.get("reason"));
                claim.setDenialDate(LocalDateTime.now());
            }
        }
        
        insuranceClaimRepository.save(claim);
        
        log.info("Automated adjudication completed for claim {}: Decision: {}, Amount: ${}", 
            event.getClaimId(), finalDecision, finalAmount);
    }
    
    private void generateRegulatoryCompliance(InsuranceClaim claim, InsuranceClaimSubmittedEvent event) {
        List<String> complianceReports = new ArrayList<>();
        
        // Generate state insurance regulator notification for large claims
        if (event.getClaimAmount().compareTo(new BigDecimal("100000")) > 0) {
            String regulatorNotificationId = claimProcessingService.generateRegulatoryNotification(
                event.getClaimId(),
                event.getPolicyId(),
                event.getClaimAmount(),
                claim.getStatus(),
                "STATE_REGULATOR"
            );
            
            claim.setRegulatoryNotificationId(regulatorNotificationId);
            complianceReports.add("STATE_REGULATOR_NOTIFICATION");
        }
        
        // Generate fraud reporting if applicable
        if (claim.isLawEnforcementNotified()) {
            String fraudReportId = claimProcessingService.generateFraudReport(
                event.getClaimId(),
                claim.getFraudScore(),
                claim.getFraudFlags(),
                event.getClaimAmount()
            );
            
            claim.setFraudReportId(fraudReportId);
            complianceReports.add("FRAUD_REPORT");
        }
        
        // Generate statistical reporting
        String statisticalReportId = claimProcessingService.generateStatisticalReport(
            event.getClaimId(),
            event.getClaimType(),
            event.getClaimAmount(),
            claim.getStatus(),
            LocalDateTime.now().getYear()
        );
        
        claim.setStatisticalReportId(statisticalReportId);
        complianceReports.add("STATISTICAL_REPORT");
        
        claim.setComplianceReports(complianceReports);
        insuranceClaimRepository.save(claim);
        
        log.info("Regulatory compliance completed for claim {}: Reports: {}", 
            event.getClaimId(), complianceReports);
    }
    
    private void processReinsuranceNotifications(InsuranceClaim claim, InsuranceClaimSubmittedEvent event) {
        // Check if claim exceeds reinsurance retention limit
        BigDecimal retentionLimit = claimProcessingService.getReinsuranceRetentionLimit(
            event.getPolicyId(),
            event.getClaimType()
        );
        
        if (event.getClaimAmount().compareTo(retentionLimit) > 0) {
            String reinsuranceNotificationId = claimProcessingService.notifyReinsurer(
                event.getClaimId(),
                event.getPolicyId(),
                event.getClaimAmount(),
                retentionLimit,
                claim.getStatus()
            );
            
            claim.setReinsuranceNotificationId(reinsuranceNotificationId);
            claim.setReinsuranceInvolved(true);
            
            log.info("Reinsurance notification sent for claim {}: Notification ID: {}, Amount over retention: ${}", 
                event.getClaimId(), reinsuranceNotificationId, event.getClaimAmount().subtract(retentionLimit));
        }
        
        insuranceClaimRepository.save(claim);
    }
    
    private void sendStakeholderNotifications(InsuranceClaim claim, InsuranceClaimSubmittedEvent event) {
        // Send claimant notification
        notificationService.sendClaimReceiptConfirmation(
            event.getClaimantId(),
            event.getClaimId(),
            event.getClaimAmount(),
            claim.getStatus()
        );
        
        // Send status-specific notifications
        switch (claim.getStatus()) {
            case APPROVED -> {
                notificationService.sendClaimApprovalNotification(
                    event.getClaimantId(),
                    event.getClaimId(),
                    claim.getApprovedAmount(),
                    generatePaymentInstructions(claim)
                );
            }
            case DENIED -> {
                notificationService.sendClaimDenialNotification(
                    event.getClaimantId(),
                    event.getClaimId(),
                    claim.getDenialReason(),
                    generateAppealInstructions(claim)
                );
            }
            case UNDER_INVESTIGATION -> {
                notificationService.sendInvestigationNotification(
                    event.getClaimantId(),
                    event.getClaimId(),
                    claim.getInvestigationLevel(),
                    claim.getAssignedInvestigatorId()
                );
            }
            case PENDING_DOCUMENTATION -> {
                notificationService.sendDocumentationRequestNotification(
                    event.getClaimantId(),
                    event.getClaimId(),
                    claim.getDocumentationIssues()
                );
            }
        }
        
        // Send large claim alerts
        if (event.getClaimAmount().compareTo(LARGE_CLAIM_THRESHOLD) > 0) {
            notificationService.sendLargeClaimAlert(
                event.getClaimId(),
                event.getClaimAmount(),
                claim.getStatus(),
                claim.getFraudScore()
            );
        }
        
        log.info("Stakeholder notifications sent for claim {}: Status: {}", 
            event.getClaimId(), claim.getStatus());
    }
    
    private boolean requiresMedicalTechnicalReview(String claimType) {
        return claimType.toUpperCase().contains("MEDICAL") ||
               claimType.toUpperCase().contains("DISABILITY") ||
               claimType.toUpperCase().contains("PROPERTY") ||
               claimType.toUpperCase().contains("AUTO");
    }
    
    private String generatePaymentInstructions(InsuranceClaim claim) {
        return String.format("Payment of $%.2f will be processed within 5-7 business days.", 
            claim.getApprovedAmount());
    }
    
    private String generateAppealInstructions(InsuranceClaim claim) {
        return "You have the right to appeal this decision within 60 days. Please contact customer service for appeal procedures.";
    }
    
    private void createManualInterventionRecord(InsuranceClaimSubmittedEvent event, Exception exception) {
        manualInterventionService.createTask(
            "INSURANCE_CLAIM_PROCESSING_FAILED",
            String.format(
                "Failed to process insurance claim. " +
                "Claim ID: %s, Policy ID: %s, Claimant ID: %s, Amount: $%.2f, Type: %s. " +
                "Claimant may not have received claim status notification. " +
                "Exception: %s. Manual intervention required.",
                event.getClaimId(),
                event.getPolicyId(),
                event.getClaimantId(),
                event.getClaimAmount(),
                event.getClaimType(),
                exception.getMessage()
            ),
            "CRITICAL",
            event,
            exception
        );
    }
}