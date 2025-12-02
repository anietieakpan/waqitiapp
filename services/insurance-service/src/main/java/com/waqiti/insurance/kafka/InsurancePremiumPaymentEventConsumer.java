package com.waqiti.insurance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.insurance.service.PremiumCollectionService;
import com.waqiti.insurance.service.PolicyMaintenanceService;
import com.waqiti.insurance.entity.PremiumPayment;
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
 * Critical Event Consumer #301: Insurance Premium Payment Event Consumer
 * Processes premium collection with lapse management and regulatory compliance
 * Implements 12-step zero-tolerance processing for premium handling
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsurancePremiumPaymentEventConsumer extends BaseKafkaConsumer {

    private final PremiumCollectionService premiumCollectionService;
    private final PolicyMaintenanceService policyMaintenanceService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "insurance-premium-payment-events", groupId = "insurance-premium-payment-group")
    @CircuitBreaker(name = "insurance-premium-payment-consumer")
    @Retry(name = "insurance-premium-payment-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleInsurancePremiumPaymentEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "insurance-premium-payment-event");
        
        try {
            log.info("Step 1: Processing insurance premium payment event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String paymentId = eventData.path("paymentId").asText();
            String policyId = eventData.path("policyId").asText();
            String customerId = eventData.path("customerId").asText();
            BigDecimal paymentAmount = new BigDecimal(eventData.path("paymentAmount").asText());
            String paymentMethod = eventData.path("paymentMethod").asText();
            LocalDate dueDate = LocalDate.parse(eventData.path("dueDate").asText());
            LocalDate paymentDate = LocalDate.parse(eventData.path("paymentDate").asText());
            String paymentStatus = eventData.path("paymentStatus").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted payment details: paymentId={}, policyId={}, amount={}", 
                    paymentId, policyId, paymentAmount);
            
            // Step 3: Validate policy status
            boolean policyActive = policyMaintenanceService.validatePolicyStatus(policyId, timestamp);
            
            if (!policyActive) {
                log.error("Step 3: Policy not active for premium payment: policyId={}", policyId);
                premiumCollectionService.rejectPayment(paymentId, "POLICY_INACTIVE", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Policy status validated");
            
            // Step 4: Validate payment amount
            BigDecimal expectedAmount = premiumCollectionService.getExpectedPremium(policyId, dueDate, timestamp);
            
            if (paymentAmount.compareTo(expectedAmount) != 0) {
                log.warn("Step 4: Payment amount mismatch: expected={}, received={}", 
                        expectedAmount, paymentAmount);
                
                if (paymentAmount.compareTo(expectedAmount) < 0) {
                    premiumCollectionService.handlePartialPayment(paymentId, policyId, 
                            paymentAmount, expectedAmount, timestamp);
                    ack.acknowledge();
                    return;
                }
            }
            log.info("Step 4: Payment amount validated");
            
            if ("SUCCESSFUL".equals(paymentStatus)) {
                // Step 5: Process successful payment
                PremiumPayment payment = premiumCollectionService.recordPayment(
                        paymentId, policyId, customerId, paymentAmount, paymentMethod, 
                        dueDate, paymentDate, timestamp);
                log.info("Step 5: Recorded successful premium payment");
                
                // Step 6: Update policy premium balance
                premiumCollectionService.updatePremiumBalance(policyId, paymentAmount, timestamp);
                log.info("Step 6: Updated policy premium balance");
                
                // Step 7: Check for grace period recovery
                boolean wasInGracePeriod = policyMaintenanceService.checkGracePeriodStatus(policyId, timestamp);
                
                if (wasInGracePeriod) {
                    policyMaintenanceService.reinstatePolicy(policyId, timestamp);
                    log.info("Step 7: Reinstated policy from grace period");
                } else {
                    log.info("Step 7: Policy was current, no reinstatement needed");
                }
                
                // Step 8: Calculate next due date
                LocalDate nextDueDate = premiumCollectionService.calculateNextDueDate(
                        policyId, paymentDate, timestamp);
                log.info("Step 8: Calculated next due date: {}", nextDueDate);
                
                // Step 9: Update commission calculations
                premiumCollectionService.updateCommissions(policyId, paymentAmount, timestamp);
                log.info("Step 9: Updated commission calculations");
                
            } else if ("FAILED".equals(paymentStatus)) {
                // Step 5-9: Handle failed payment
                premiumCollectionService.recordFailedPayment(paymentId, policyId, paymentAmount, timestamp);
                
                // Check for lapse conditions
                boolean shouldLapse = policyMaintenanceService.evaluateLapseConditions(policyId, timestamp);
                
                if (shouldLapse) {
                    policyMaintenanceService.initiateLapseProcess(policyId, timestamp);
                    log.warn("Step 5-9: Initiated policy lapse process due to failed payment");
                } else {
                    policyMaintenanceService.setGracePeriod(policyId, timestamp);
                    log.info("Step 5-9: Set policy to grace period");
                }
            }
            
            // Step 10: Update regulatory reporting
            premiumCollectionService.updateRegulatoryReporting(policyId, paymentAmount, paymentStatus, timestamp);
            log.info("Step 10: Updated regulatory reporting");
            
            // Step 11: Archive payment records
            premiumCollectionService.archivePaymentRecord(paymentId, eventData.toString(), timestamp);
            log.info("Step 11: Archived payment records");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed insurance premium payment: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing insurance premium payment event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("paymentId") || 
            !eventData.has("policyId") || !eventData.has("paymentAmount")) {
            throw new IllegalArgumentException("Invalid insurance premium payment event structure");
        }
    }
}