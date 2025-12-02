package com.waqiti.insurance.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.insurance.service.InsurTechClaimService;
import com.waqiti.insurance.service.ClaimProcessingService;
import com.waqiti.insurance.service.InsuranceComplianceService;
import com.waqiti.insurance.entity.InsurTechClaim;
import com.waqiti.insurance.entity.ClaimStatus;
import com.waqiti.insurance.entity.ClaimType;
import com.waqiti.insurance.entity.InsuranceProduct;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Critical Event Consumer #72: InsurTech Claim Event Consumer
 * Processes insurance technology claims with full regulatory compliance and automated processing
 * Implements 12-step zero-tolerance processing for digital insurance claims and payouts
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsurTechClaimEventConsumer extends BaseKafkaConsumer {

    private final InsurTechClaimService insurTechClaimService;
    private final ClaimProcessingService claimProcessingService;
    private final InsuranceComplianceService insuranceComplianceService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "insurtech-claim-events", groupId = "insurtech-claim-group")
    @CircuitBreaker(name = "insurtech-claim-consumer")
    @Retry(name = "insurtech-claim-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleInsurTechClaimEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "insurtech-claim-event");
        MDC.put("partition", String.valueOf(record.partition()));
        MDC.put("offset", String.valueOf(record.offset()));
        
        try {
            log.info("Step 1: Processing InsurTech claim event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            // Step 2: Parse and validate event structure
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            // Step 3: Extract and validate event details
            String eventId = eventData.path("eventId").asText();
            String eventType = eventData.path("eventType").asText();
            String claimId = eventData.path("claimId").asText();
            String policyId = eventData.path("policyId").asText();
            String policyHolderId = eventData.path("policyHolderId").asText();
            String insurerId = eventData.path("insurerId").asText();
            String claimType = eventData.path("claimType").asText();
            BigDecimal claimAmount = new BigDecimal(eventData.path("claimAmount").asText());
            String currency = eventData.path("currency").asText();
            LocalDateTime incidentDate = LocalDateTime.parse(eventData.path("incidentDate").asText());
            String incidentLocation = eventData.path("incidentLocation").asText();
            String incidentDescription = eventData.path("incidentDescription").asText();
            String evidenceUrls = eventData.path("evidenceUrls").asText();
            String automatedAssessment = eventData.path("automatedAssessment").asText();
            BigDecimal policyLimit = new BigDecimal(eventData.path("policyLimit").asText());
            BigDecimal deductible = new BigDecimal(eventData.path("deductible").asText());
            String jurisdiction = eventData.path("jurisdiction").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            MDC.put("eventId", eventId);
            MDC.put("claimId", claimId);
            MDC.put("eventType", eventType);
            MDC.put("claimType", claimType);
            MDC.put("policyHolderId", policyHolderId);
            
            log.info("Step 2: Extracted InsurTech claim details: eventId={}, type={}, claimId={}, amount={}, claimType={}", 
                    eventId, eventType, claimId, claimAmount, claimType);
            
            // Step 4: Validate insurance policy and coverage
            validatePolicyCoverage(eventId, policyId, policyHolderId, claimType, claimAmount, policyLimit, deductible);
            
            // Step 5: Validate claim eligibility and fraud detection
            validateClaimEligibility(eventId, claimId, policyId, incidentDate, claimAmount, automatedAssessment);
            
            // Step 6: Check idempotency to prevent duplicate processing
            if (insurTechClaimService.isClaimEventProcessed(eventId, claimId)) {
                log.warn("Step 6: InsurTech claim event already processed: eventId={}, claimId={}", eventId, claimId);
                ack.acknowledge();
                return;
            }
            
            // Step 7: Validate regulatory compliance and reporting requirements
            validateRegulatoryCompliance(policyId, claimType, claimAmount, jurisdiction, insurerId);
            
            // Step 8: Process InsurTech claim based on type and automation rules
            processInsurTechClaim(eventId, eventType, claimId, policyId, policyHolderId, insurerId, claimType,
                    claimAmount, currency, incidentDate, incidentLocation, incidentDescription, evidenceUrls,
                    automatedAssessment, policyLimit, deductible, jurisdiction, timestamp);
            
            // Step 9: Execute automated claim processing and assessment
            executeAutomatedProcessing(eventId, claimId, automatedAssessment, claimAmount, evidenceUrls, timestamp);
            
            // Step 10: Update claim status and generate compliance records
            updateClaimStatusAndCompliance(eventId, claimId, policyId, claimAmount, claimType, jurisdiction, timestamp);
            
            // Step 11: Publish claim processing and payout events
            publishClaimProcessingEvents(eventId, claimId, eventType, claimAmount, claimType, timestamp);
            
            // Step 12: Complete processing and acknowledge
            insurTechClaimService.markClaimEventProcessed(eventId, claimId);
            ack.acknowledge();
            
            log.info("Step 12: Successfully processed InsurTech claim event: eventId={}, claimId={}", 
                    eventId, claimId);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse InsurTech claim event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (Exception e) {
            log.error("Error processing InsurTech claim event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        log.info("Step 2a: Validating InsurTech claim event structure");
        
        if (!eventData.has("eventId") || eventData.path("eventId").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty eventId in InsurTech claim event");
        }
        
        if (!eventData.has("eventType") || eventData.path("eventType").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty eventType in InsurTech claim event");
        }
        
        if (!eventData.has("claimId") || eventData.path("claimId").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty claimId in InsurTech claim event");
        }
        
        if (!eventData.has("policyId") || eventData.path("policyId").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty policyId in InsurTech claim event");
        }
        
        if (!eventData.has("claimAmount") || eventData.path("claimAmount").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty claimAmount in InsurTech claim event");
        }
        
        if (!eventData.has("claimType") || eventData.path("claimType").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty claimType in InsurTech claim event");
        }
        
        if (!eventData.has("incidentDate") || eventData.path("incidentDate").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty incidentDate in InsurTech claim event");
        }
        
        log.info("Step 2b: InsurTech claim event structure validation successful");
    }

    private void validatePolicyCoverage(String eventId, String policyId, String policyHolderId, String claimType,
                                      BigDecimal claimAmount, BigDecimal policyLimit, BigDecimal deductible) {
        log.info("Step 4: Validating policy coverage for eventId={}", eventId);
        
        try {
            // Validate policy exists and is active
            InsuranceProduct policy = insurTechClaimService.getInsurancePolicy(policyId);
            if (policy == null) {
                throw new IllegalArgumentException("Insurance policy not found: " + policyId);
            }
            
            if (!policy.isActive()) {
                throw new IllegalArgumentException("Insurance policy is not active: " + policyId);
            }
            
            // Validate policy holder
            if (!policy.getPolicyHolderId().equals(policyHolderId)) {
                throw new SecurityException("Policy holder mismatch: " + policyHolderId + " vs " + policy.getPolicyHolderId());
            }
            
            // Validate claim type is covered
            if (!insurTechClaimService.isClaimTypeCovered(policyId, claimType)) {
                throw new IllegalArgumentException("Claim type not covered by policy: " + claimType);
            }
            
            // Validate claim amount within policy limits
            if (claimAmount.compareTo(policyLimit) > 0) {
                throw new IllegalArgumentException("Claim amount exceeds policy limit: " + claimAmount + " > " + policyLimit);
            }
            
            // Validate deductible requirements
            if (claimAmount.compareTo(deductible) <= 0) {
                throw new IllegalArgumentException("Claim amount does not exceed deductible: " + claimAmount + " <= " + deductible);
            }
            
            // Check policy premiums are current
            if (!insurTechClaimService.arePremiumsCurrent(policyId)) {
                throw new IllegalArgumentException("Policy premiums are not current: " + policyId);
            }
            
            log.info("Step 4: Policy coverage validation successful for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 4: Policy coverage validation failed for eventId={}: {}", eventId, e.getMessage(), e);
            throw new SecurityException("Policy coverage validation failed: " + e.getMessage(), e);
        }
    }

    private void validateClaimEligibility(String eventId, String claimId, String policyId, LocalDateTime incidentDate,
                                        BigDecimal claimAmount, String automatedAssessment) {
        log.info("Step 5: Validating claim eligibility for eventId={}", eventId);
        
        try {
            // Validate incident date is within policy period
            if (!insurTechClaimService.isIncidentWithinPolicyPeriod(policyId, incidentDate)) {
                throw new IllegalArgumentException("Incident date outside policy period: " + incidentDate);
            }
            
            // Check for duplicate claims
            if (insurTechClaimService.isDuplicateClaim(policyId, incidentDate, claimAmount)) {
                throw new SecurityException("Duplicate claim detected for policy: " + policyId);
            }
            
            // Validate automated fraud assessment
            if (!claimProcessingService.passesFraudDetection(claimId, automatedAssessment)) {
                throw new SecurityException("Claim flagged by fraud detection: " + claimId);
            }
            
            // Check claim reporting timeline
            if (!insurTechClaimService.isClaimReportedWithinTimeline(policyId, incidentDate)) {
                throw new IllegalArgumentException("Claim not reported within required timeline: " + policyId);
            }
            
            // Validate claim amount reasonableness
            if (!claimProcessingService.isClaimAmountReasonable(claimId, claimAmount, automatedAssessment)) {
                throw new IllegalArgumentException("Claim amount appears unreasonable: " + claimAmount);
            }
            
            log.info("Step 5: Claim eligibility validation successful for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 5: Claim eligibility validation failed for eventId={}: {}", eventId, e.getMessage(), e);
            throw new SecurityException("Claim eligibility validation failed: " + e.getMessage(), e);
        }
    }

    private void validateRegulatoryCompliance(String policyId, String claimType, BigDecimal claimAmount,
                                            String jurisdiction, String insurerId) {
        log.info("Step 7: Validating regulatory compliance for policyId={}", policyId);
        
        try {
            // Validate insurer is licensed in jurisdiction
            if (!insuranceComplianceService.isLicensedInJurisdiction(insurerId, jurisdiction)) {
                throw new SecurityException("Insurer not licensed in jurisdiction: " + insurerId + " in " + jurisdiction);
            }
            
            // Check regulatory reporting requirements
            if (!insuranceComplianceService.meetsReportingRequirements(claimType, claimAmount, jurisdiction)) {
                throw new SecurityException("Claim does not meet regulatory reporting requirements");
            }
            
            // Validate claim processing timeline compliance
            if (!insuranceComplianceService.meetsProcessingTimelineRequirements(claimType, jurisdiction)) {
                throw new SecurityException("Claim processing timeline requirements not met");
            }
            
            // Check solvency and reserve requirements
            if (!insuranceComplianceService.meetsSolvencyRequirements(insurerId, claimAmount)) {
                throw new SecurityException("Insurer solvency requirements not met for claim amount");
            }
            
            // Validate consumer protection compliance
            if (!insuranceComplianceService.meetsConsumerProtectionRequirements(claimType, jurisdiction)) {
                throw new SecurityException("Consumer protection requirements not met");
            }
            
            log.info("Step 7: Regulatory compliance validation successful for policyId={}", policyId);
            
        } catch (Exception e) {
            log.error("Step 7: Regulatory compliance validation failed for policyId={}: {}", policyId, e.getMessage(), e);
            throw new SecurityException("Regulatory compliance validation failed: " + e.getMessage(), e);
        }
    }

    private void processInsurTechClaim(String eventId, String eventType, String claimId, String policyId,
                                     String policyHolderId, String insurerId, String claimType, BigDecimal claimAmount,
                                     String currency, LocalDateTime incidentDate, String incidentLocation,
                                     String incidentDescription, String evidenceUrls, String automatedAssessment,
                                     BigDecimal policyLimit, BigDecimal deductible, String jurisdiction,
                                     LocalDateTime timestamp) {
        log.info("Step 8: Processing InsurTech claim: eventId={}, type={}", eventId, eventType);
        
        try {
            InsurTechClaim claim = InsurTechClaim.builder()
                    .id(UUID.randomUUID())
                    .eventId(eventId)
                    .claimId(claimId)
                    .policyId(policyId)
                    .policyHolderId(policyHolderId)
                    .insurerId(insurerId)
                    .type(ClaimType.valueOf(claimType.toUpperCase()))
                    .claimAmount(claimAmount)
                    .currency(currency)
                    .incidentDate(incidentDate)
                    .incidentLocation(incidentLocation)
                    .incidentDescription(incidentDescription)
                    .evidenceUrls(evidenceUrls)
                    .automatedAssessment(automatedAssessment)
                    .policyLimit(policyLimit)
                    .deductible(deductible)
                    .jurisdiction(jurisdiction)
                    .status(ClaimStatus.PROCESSING)
                    .timestamp(timestamp)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            // Process based on event type
            switch (eventType.toUpperCase()) {
                case "CLAIM_SUBMITTED":
                    claimProcessingService.processClaimSubmission(claim);
                    break;
                case "AUTOMATED_APPROVAL":
                    claimProcessingService.processAutomatedApproval(claim);
                    break;
                case "MANUAL_REVIEW_REQUIRED":
                    claimProcessingService.processManualReviewRequired(claim);
                    break;
                case "CLAIM_INVESTIGATION":
                    claimProcessingService.processClaimInvestigation(claim);
                    break;
                case "CLAIM_APPROVED":
                    claimProcessingService.processClaimApproval(claim);
                    break;
                case "CLAIM_DENIED":
                    claimProcessingService.processClaimDenial(claim);
                    break;
                case "PAYOUT_INITIATED":
                    claimProcessingService.processPayoutInitiation(claim);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported InsurTech claim event type: " + eventType);
            }
            
            // Save claim record
            insurTechClaimService.saveInsurTechClaim(claim);
            
            log.info("Step 8: InsurTech claim processed successfully: eventId={}, claimId={}", eventId, claimId);
            
        } catch (Exception e) {
            log.error("Step 8: InsurTech claim processing failed for eventId={}: {}", eventId, e.getMessage(), e);
            throw new IllegalStateException("InsurTech claim processing failed: " + e.getMessage(), e);
        }
    }

    private void executeAutomatedProcessing(String eventId, String claimId, String automatedAssessment,
                                          BigDecimal claimAmount, String evidenceUrls, LocalDateTime timestamp) {
        log.info("Step 9: Executing automated processing for eventId={}", eventId);
        
        try {
            // Execute AI-powered claim assessment
            claimProcessingService.executeAIAssessment(claimId, automatedAssessment, evidenceUrls, timestamp);
            
            // Process automated evidence verification
            claimProcessingService.processEvidenceVerification(claimId, evidenceUrls, timestamp);
            
            // Execute automated payout calculation
            BigDecimal payoutAmount = claimProcessingService.calculateAutomatedPayout(claimId, claimAmount, timestamp);
            
            // Process straight-through processing if eligible
            if (claimProcessingService.isEligibleForStraightThroughProcessing(claimId, automatedAssessment, claimAmount)) {
                claimProcessingService.executeStraightThroughProcessing(claimId, payoutAmount, timestamp);
            }
            
            log.info("Step 9: Automated processing executed successfully for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 9: Automated processing failed for eventId={}: {}", eventId, e.getMessage(), e);
            // Don't fail the transaction for automated processing issues, but log for manual review
        }
    }

    private void updateClaimStatusAndCompliance(String eventId, String claimId, String policyId, BigDecimal claimAmount,
                                              String claimType, String jurisdiction, LocalDateTime timestamp) {
        log.info("Step 10: Updating claim status and compliance for eventId={}", eventId);
        
        try {
            // Update claim processing status
            insurTechClaimService.updateClaimStatus(claimId, "PROCESSED", timestamp);
            
            // Record regulatory compliance data
            insuranceComplianceService.recordClaimCompliance(
                    claimId, policyId, claimAmount, claimType, jurisdiction, timestamp
            );
            
            // Update insurer reserves and solvency
            insuranceComplianceService.updateInsurerReserves(claimId, claimAmount, timestamp);
            
            // Generate regulatory reports if required
            if (claimAmount.compareTo(new BigDecimal("10000")) >= 0) {
                insuranceComplianceService.generateRegulatoryReport(claimId, claimAmount, claimType, jurisdiction);
            }
            
            log.info("Step 10: Claim status and compliance updated successfully for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 10: Claim status and compliance update failed for eventId={}: {}", eventId, e.getMessage(), e);
            // Don't fail the transaction for status update issues, but log for manual review
        }
    }

    private void publishClaimProcessingEvents(String eventId, String claimId, String eventType, BigDecimal claimAmount,
                                            String claimType, LocalDateTime timestamp) {
        log.info("Step 11: Publishing claim processing events for eventId={}", eventId);
        
        try {
            // Publish claim status update event
            insurTechClaimService.publishClaimStatusEvent(
                    eventId, claimId, eventType, "PROCESSED", timestamp
            );
            
            // Publish payout notification event
            insurTechClaimService.publishPayoutNotificationEvent(
                    eventId, claimId, claimAmount, timestamp
            );
            
            // Publish to regulatory monitoring systems
            insurTechClaimService.publishToRegulatoryMonitoring(
                    claimId, claimType, claimAmount, timestamp
            );
            
            // Publish analytics and metrics event
            insurTechClaimService.publishAnalyticsEvent(
                    claimId, eventType, claimAmount, claimType, timestamp
            );
            
            log.info("Step 11: Claim processing events published successfully for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 11: Claim processing event publishing failed for eventId={}: {}", eventId, e.getMessage(), e);
            // Don't fail the transaction for event publishing issues, but log for manual review
        }
    }
}