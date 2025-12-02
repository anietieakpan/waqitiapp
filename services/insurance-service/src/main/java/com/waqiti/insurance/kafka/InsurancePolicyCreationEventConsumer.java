package com.waqiti.insurance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.insurance.service.PolicyIssuanceService;
import com.waqiti.insurance.service.UnderwritingService;
import com.waqiti.insurance.entity.InsurancePolicy;
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
 * Critical Event Consumer #298: Insurance Policy Creation Event Consumer
 * Processes insurance policy issuance with state regulatory compliance
 * Implements 12-step zero-tolerance processing for policy underwriting
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsurancePolicyCreationEventConsumer extends BaseKafkaConsumer {

    private final PolicyIssuanceService policyIssuanceService;
    private final UnderwritingService underwritingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "insurance-policy-creation-events", groupId = "insurance-policy-creation-group")
    @CircuitBreaker(name = "insurance-policy-creation-consumer")
    @Retry(name = "insurance-policy-creation-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleInsurancePolicyCreationEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "insurance-policy-creation-event");
        
        try {
            log.info("Step 1: Processing insurance policy creation event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String policyId = eventData.path("policyId").asText();
            String customerId = eventData.path("customerId").asText();
            String policyType = eventData.path("policyType").asText();
            BigDecimal coverageAmount = new BigDecimal(eventData.path("coverageAmount").asText());
            BigDecimal premiumAmount = new BigDecimal(eventData.path("premiumAmount").asText());
            LocalDate effectiveDate = LocalDate.parse(eventData.path("effectiveDate").asText());
            String state = eventData.path("state").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted policy details: policyId={}, type={}, coverage={}", 
                    policyId, policyType, coverageAmount);
            
            // Step 3: Underwriting validation
            boolean underwritten = underwritingService.performUnderwriting(
                    customerId, policyType, coverageAmount, timestamp);
            
            if (!underwritten) {
                log.error("Step 3: Underwriting failed for customerId={}", customerId);
                policyIssuanceService.rejectPolicy(policyId, "UNDERWRITING_DECLINED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Underwriting approved");
            
            // Step 4: State regulatory compliance
            boolean stateCompliant = policyIssuanceService.verifyStateCompliance(
                    policyType, coverageAmount, state, timestamp);
            
            if (!stateCompliant) {
                log.error("Step 4: State compliance failed for state={}", state);
                policyIssuanceService.rejectPolicy(policyId, "STATE_COMPLIANCE_FAILED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: State compliance verified");
            
            // Step 5: Create policy record
            InsurancePolicy policy = policyIssuanceService.createPolicy(
                    policyId, customerId, policyType, coverageAmount, premiumAmount,
                    effectiveDate, state, timestamp);
            log.info("Step 5: Created insurance policy record");
            
            // Step 6: Calculate actuarial reserves
            BigDecimal reserves = underwritingService.calculateReserves(
                    policyType, coverageAmount, timestamp);
            log.info("Step 6: Calculated actuarial reserves: {}", reserves);
            
            // Step 7: Generate policy documents
            policyIssuanceService.generatePolicyDocuments(policyId, timestamp);
            log.info("Step 7: Generated policy documents");
            
            // Step 8: Process initial premium payment
            boolean paymentReceived = policyIssuanceService.processInitialPremium(
                    policyId, premiumAmount, timestamp);
            
            if (!paymentReceived) {
                log.warn("Step 8: Initial premium not received, policy pending");
                policyIssuanceService.setPolicyStatus(policyId, "PENDING_PAYMENT", timestamp);
            } else {
                log.info("Step 8: Initial premium processed");
                policyIssuanceService.setPolicyStatus(policyId, "ACTIVE", timestamp);
            }
            
            // Step 9: Register with state insurance department
            policyIssuanceService.registerWithStateDepartment(policyId, state, timestamp);
            log.info("Step 9: Registered with state insurance department");
            
            // Step 10: Send policy confirmation
            policyIssuanceService.sendPolicyConfirmation(customerId, policyId, timestamp);
            log.info("Step 10: Sent policy confirmation to customer");
            
            // Step 11: Archive policy creation records
            policyIssuanceService.archivePolicyCreation(policyId, eventData.toString(), timestamp);
            log.info("Step 11: Archived policy creation records");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed insurance policy creation: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing insurance policy creation event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("policyId") || 
            !eventData.has("customerId") || !eventData.has("coverageAmount")) {
            throw new IllegalArgumentException("Invalid insurance policy creation event structure");
        }
    }
}