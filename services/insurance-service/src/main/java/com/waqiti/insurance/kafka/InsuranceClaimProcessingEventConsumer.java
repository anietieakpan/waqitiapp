package com.waqiti.insurance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.insurance.service.ClaimAdjudicationService;
import com.waqiti.insurance.service.PayoutService;
import com.waqiti.insurance.entity.ClaimAdjudication;
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
 * Critical Event Consumer #300: Insurance Claim Processing Event Consumer
 * Processes claim adjudication with regulatory compliance and payout processing
 * Implements 12-step zero-tolerance processing for claim settlements
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsuranceClaimProcessingEventConsumer extends BaseKafkaConsumer {

    private final ClaimAdjudicationService claimAdjudicationService;
    private final PayoutService payoutService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "insurance-claim-processing-events", groupId = "insurance-claim-processing-group")
    @CircuitBreaker(name = "insurance-claim-processing-consumer")
    @Retry(name = "insurance-claim-processing-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleInsuranceClaimProcessingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "insurance-claim-processing-event");
        
        try {
            log.info("Step 1: Processing insurance claim processing event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String claimId = eventData.path("claimId").asText();
            String adjusterId = eventData.path("adjusterId").asText();
            String adjudicationDecision = eventData.path("adjudicationDecision").asText();
            BigDecimal approvedAmount = new BigDecimal(eventData.path("approvedAmount").asText());
            String reasonCode = eventData.path("reasonCode").asText();
            String assessmentNotes = eventData.path("assessmentNotes").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted adjudication details: claimId={}, decision={}, amount={}", 
                    claimId, adjudicationDecision, approvedAmount);
            
            // Step 3: Validate adjuster authorization
            boolean adjusterAuthorized = claimAdjudicationService.validateAdjusterAuthorization(
                    adjusterId, approvedAmount, timestamp);
            
            if (!adjusterAuthorized) {
                log.error("Step 3: Adjuster not authorized for amount: adjusterId={}, amount={}", 
                        adjusterId, approvedAmount);
                claimAdjudicationService.escalateToSupervisor(claimId, adjusterId, approvedAmount, timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Adjuster authorization validated");
            
            // Step 4: Regulatory compliance review
            boolean complianceApproved = claimAdjudicationService.performComplianceReview(
                    claimId, adjudicationDecision, approvedAmount, reasonCode, timestamp);
            
            if (!complianceApproved) {
                log.error("Step 4: Compliance review failed for claimId={}", claimId);
                claimAdjudicationService.setClaimStatus(claimId, "COMPLIANCE_REVIEW_REQUIRED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: Compliance review approved");
            
            // Step 5: Process adjudication decision
            ClaimAdjudication adjudication = claimAdjudicationService.processAdjudication(
                    claimId, adjusterId, adjudicationDecision, approvedAmount, 
                    reasonCode, assessmentNotes, timestamp);
            log.info("Step 5: Processed adjudication decision");
            
            if ("APPROVED".equals(adjudicationDecision)) {
                // Step 6: Calculate final payout amount
                BigDecimal finalAmount = payoutService.calculateFinalPayout(
                        claimId, approvedAmount, timestamp);
                log.info("Step 6: Calculated final payout amount: {}", finalAmount);
                
                // Step 7: Reserve funds for payout
                boolean reservedFunds = payoutService.reserveFunds(claimId, finalAmount, timestamp);
                
                if (!reservedFunds) {
                    log.error("Step 7: Failed to reserve funds for claimId={}", claimId);
                    claimAdjudicationService.setClaimStatus(claimId, "FUNDING_ISSUE", timestamp);
                    ack.acknowledge();
                    return;
                }
                log.info("Step 7: Reserved funds for payout");
                
                // Step 8: Process payout
                String payoutId = payoutService.processPayout(claimId, finalAmount, timestamp);
                log.info("Step 8: Processed payout: payoutId={}", payoutId);
                
                // Step 9: Update claim status to paid
                claimAdjudicationService.setClaimStatus(claimId, "PAID", timestamp);
                log.info("Step 9: Updated claim status to PAID");
                
            } else if ("DENIED".equals(adjudicationDecision)) {
                // Step 6-9: Handle denial
                claimAdjudicationService.setClaimStatus(claimId, "DENIED", timestamp);
                claimAdjudicationService.sendDenialNotification(claimId, reasonCode, timestamp);
                log.info("Step 6-9: Processed claim denial");
            }
            
            // Step 10: Update actuarial data
            claimAdjudicationService.updateActuarialData(claimId, adjudicationDecision, approvedAmount, timestamp);
            log.info("Step 10: Updated actuarial data");
            
            // Step 11: Archive adjudication records
            claimAdjudicationService.archiveAdjudication(claimId, eventData.toString(), timestamp);
            log.info("Step 11: Archived adjudication records");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed insurance claim processing: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing insurance claim processing event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("claimId") || 
            !eventData.has("adjusterId") || !eventData.has("adjudicationDecision")) {
            throw new IllegalArgumentException("Invalid insurance claim processing event structure");
        }
    }
}