package com.waqiti.insurance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.insurance.service.ClaimProcessingService;
import com.waqiti.insurance.service.FraudDetectionService;
import com.waqiti.insurance.entity.InsuranceClaim;
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
import java.util.List;
import java.util.UUID;

/**
 * Critical Event Consumer #299: Insurance Claim Submission Event Consumer
 * Processes claim filing with fraud detection and regulatory compliance
 * Implements 12-step zero-tolerance processing for claim documentation
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsuranceClaimSubmissionEventConsumer extends BaseKafkaConsumer {

    private final ClaimProcessingService claimProcessingService;
    private final FraudDetectionService fraudDetectionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "insurance-claim-submission-events", groupId = "insurance-claim-submission-group")
    @CircuitBreaker(name = "insurance-claim-submission-consumer")
    @Retry(name = "insurance-claim-submission-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleInsuranceClaimSubmissionEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "insurance-claim-submission-event");
        
        try {
            log.info("Step 1: Processing insurance claim submission event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String claimId = eventData.path("claimId").asText();
            String policyId = eventData.path("policyId").asText();
            String customerId = eventData.path("customerId").asText();
            String claimType = eventData.path("claimType").asText();
            BigDecimal claimAmount = new BigDecimal(eventData.path("claimAmount").asText());
            String incidentDate = eventData.path("incidentDate").asText();
            List<String> documents = objectMapper.convertValue(
                    eventData.path("documents"), List.class);
            String description = eventData.path("description").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted claim details: claimId={}, type={}, amount={}", 
                    claimId, claimType, claimAmount);
            
            // Step 3: Validate policy coverage
            boolean coverageValid = claimProcessingService.validatePolicyCoverage(
                    policyId, claimType, claimAmount, timestamp);
            
            if (!coverageValid) {
                log.error("Step 3: Policy coverage invalid for claimId={}", claimId);
                claimProcessingService.rejectClaim(claimId, "COVERAGE_INVALID", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Policy coverage validated");
            
            // Step 4: Fraud detection screening
            double fraudScore = fraudDetectionService.calculateFraudScore(
                    customerId, claimType, claimAmount, incidentDate, description, timestamp);
            
            if (fraudScore > 0.8) {
                log.warn("Step 4: High fraud risk detected: score={}", fraudScore);
                claimProcessingService.flagForInvestigation(claimId, fraudScore, timestamp);
                claimProcessingService.setClaimStatus(claimId, "UNDER_INVESTIGATION", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: Fraud screening passed: score={}", fraudScore);
            
            // Step 5: Document validation
            boolean documentsValid = claimProcessingService.validateDocuments(
                    claimId, documents, claimType, timestamp);
            
            if (!documentsValid) {
                log.error("Step 5: Document validation failed for claimId={}", claimId);
                claimProcessingService.setClaimStatus(claimId, "PENDING_DOCUMENTS", timestamp);
                claimProcessingService.requestAdditionalDocuments(claimId, timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 5: Documents validated");
            
            // Step 6: Create claim record
            InsuranceClaim claim = claimProcessingService.createClaim(
                    claimId, policyId, customerId, claimType, claimAmount,
                    incidentDate, description, documents, timestamp);
            log.info("Step 6: Created insurance claim record");
            
            // Step 7: Assign claim adjuster
            String adjusterId = claimProcessingService.assignAdjuster(
                    claimId, claimType, claimAmount, timestamp);
            log.info("Step 7: Assigned claim adjuster: {}", adjusterId);
            
            // Step 8: Schedule initial assessment
            claimProcessingService.scheduleAssessment(claimId, adjusterId, timestamp);
            log.info("Step 8: Scheduled initial assessment");
            
            // Step 9: Update policy claim count
            claimProcessingService.updatePolicyClaimCount(policyId, timestamp);
            log.info("Step 9: Updated policy claim count");
            
            // Step 10: Notify stakeholders
            claimProcessingService.notifyClaimSubmission(customerId, adjusterId, claimId, timestamp);
            log.info("Step 10: Notified stakeholders of claim submission");
            
            // Step 11: Archive claim submission
            claimProcessingService.archiveClaimSubmission(claimId, eventData.toString(), timestamp);
            log.info("Step 11: Archived claim submission records");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed insurance claim submission: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing insurance claim submission event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("claimId") || 
            !eventData.has("policyId") || !eventData.has("claimAmount")) {
            throw new IllegalArgumentException("Invalid insurance claim submission event structure");
        }
    }
}