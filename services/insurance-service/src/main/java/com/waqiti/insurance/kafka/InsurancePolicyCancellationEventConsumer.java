package com.waqiti.insurance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.insurance.service.PolicyCancellationService;
import com.waqiti.insurance.service.RefundCalculationService;
import com.waqiti.insurance.entity.PolicyCancellation;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Critical Event Consumer #302: Insurance Policy Cancellation Event Consumer
 * Processes policy termination with refund calculations and regulatory compliance
 * Implements 12-step zero-tolerance processing for policy cancellations
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsurancePolicyCancellationEventConsumer extends BaseKafkaConsumer {

    private final PolicyCancellationService policyCancellationService;
    private final RefundCalculationService refundCalculationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "insurance-policy-cancellation-events", groupId = "insurance-policy-cancellation-group")
    @CircuitBreaker(name = "insurance-policy-cancellation-consumer")
    @Retry(name = "insurance-policy-cancellation-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleInsurancePolicyCancellationEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "insurance-policy-cancellation-event");
        
        try {
            log.info("Step 1: Processing insurance policy cancellation event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String cancellationId = eventData.path("cancellationId").asText();
            String policyId = eventData.path("policyId").asText();
            String customerId = eventData.path("customerId").asText();
            String cancellationReason = eventData.path("cancellationReason").asText();
            LocalDate cancellationDate = LocalDate.parse(eventData.path("cancellationDate").asText());
            LocalDate effectiveDate = LocalDate.parse(eventData.path("effectiveDate").asText());
            String initiatedBy = eventData.path("initiatedBy").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted cancellation details: cancellationId={}, policyId={}, reason={}", 
                    cancellationId, policyId, cancellationReason);
            
            // Step 3: Validate policy eligibility for cancellation
            boolean eligibleForCancellation = policyCancellationService.validateCancellationEligibility(
                    policyId, cancellationReason, initiatedBy, timestamp);
            
            if (!eligibleForCancellation) {
                log.error("Step 3: Policy not eligible for cancellation: policyId={}", policyId);
                policyCancellationService.rejectCancellation(cancellationId, "NOT_ELIGIBLE", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Policy eligible for cancellation");
            
            // Step 4: Check for outstanding claims
            boolean hasOutstandingClaims = policyCancellationService.checkOutstandingClaims(policyId, timestamp);
            
            if (hasOutstandingClaims) {
                log.warn("Step 4: Outstanding claims exist for policyId={}", policyId);
                policyCancellationService.setCancellationStatus(cancellationId, "PENDING_CLAIMS_RESOLUTION", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: No outstanding claims found");
            
            // Step 5: Calculate refund amount
            BigDecimal refundAmount = refundCalculationService.calculateRefund(
                    policyId, cancellationDate, effectiveDate, cancellationReason, timestamp);
            log.info("Step 5: Calculated refund amount: {}", refundAmount);
            
            // Step 6: Process policy cancellation
            PolicyCancellation cancellation = policyCancellationService.processCancellation(
                    cancellationId, policyId, customerId, cancellationReason, 
                    cancellationDate, effectiveDate, initiatedBy, refundAmount, timestamp);
            log.info("Step 6: Processed policy cancellation");
            
            // Step 7: Update policy status
            policyCancellationService.updatePolicyStatus(policyId, "CANCELLED", effectiveDate, timestamp);
            log.info("Step 7: Updated policy status to CANCELLED");
            
            // Step 8: Process refund if applicable
            if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                String refundId = refundCalculationService.processRefund(
                        policyId, customerId, refundAmount, timestamp);
                log.info("Step 8: Processed refund: refundId={}", refundId);
            } else {
                log.info("Step 8: No refund required");
            }
            
            // Step 9: Update commission adjustments
            policyCancellationService.adjustCommissions(policyId, cancellationDate, refundAmount, timestamp);
            log.info("Step 9: Adjusted commissions for cancellation");
            
            // Step 10: Notify regulatory authorities
            policyCancellationService.notifyRegulatoryAuthorities(policyId, cancellationReason, timestamp);
            log.info("Step 10: Notified regulatory authorities");
            
            // Step 11: Archive cancellation records
            policyCancellationService.archiveCancellation(cancellationId, eventData.toString(), timestamp);
            log.info("Step 11: Archived cancellation records");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed insurance policy cancellation: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing insurance policy cancellation event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("cancellationId") || 
            !eventData.has("policyId") || !eventData.has("cancellationReason")) {
            throw new IllegalArgumentException("Invalid insurance policy cancellation event structure");
        }
    }
}