package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.compliance.service.RegEComplianceService;
import com.waqiti.compliance.service.DisputeManagementService;
import com.waqiti.compliance.entity.RegEDispute;
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
 * Critical Event Consumer #173: Reg E Compliance Event Consumer
 * Processes electronic fund transfer error resolution with Reg E compliance
 * Implements 12-step zero-tolerance processing for consumer dispute handling
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegEComplianceEventConsumer extends BaseKafkaConsumer {

    private final RegEComplianceService regEComplianceService;
    private final DisputeManagementService disputeManagementService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "reg-e-compliance-events", groupId = "reg-e-compliance-group")
    @CircuitBreaker(name = "reg-e-compliance-consumer")
    @Retry(name = "reg-e-compliance-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleRegEComplianceEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "reg-e-compliance-event");
        
        try {
            log.info("Step 1: Processing Reg E compliance event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String disputeId = eventData.path("disputeId").asText();
            String accountId = eventData.path("accountId").asText();
            String transactionId = eventData.path("transactionId").asText();
            BigDecimal disputedAmount = new BigDecimal(eventData.path("disputedAmount").asText());
            String disputeReason = eventData.path("disputeReason").asText();
            LocalDate disputeDate = LocalDate.parse(eventData.path("disputeDate").asText());
            LocalDate transactionDate = LocalDate.parse(eventData.path("transactionDate").asText());
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted Reg E dispute details: disputeId={}, amount={}, reason={}", 
                    disputeId, disputedAmount, disputeReason);
            
            boolean timelyNotification = regEComplianceService.verifyTimelyNotification(
                    transactionDate, disputeDate);
            
            if (!timelyNotification) {
                log.warn("Step 3: Dispute notification exceeds 60-day Reg E requirement");
            } else {
                log.info("Step 3: Timely notification verified within 60 days");
            }
            
            RegEDispute dispute = disputeManagementService.createDisputeRecord(
                    disputeId, accountId, transactionId, disputedAmount, 
                    disputeReason, disputeDate, timestamp);
            
            log.info("Step 4: Created Reg E dispute record");
            
            regEComplianceService.sendProvisionalCreditNotice(accountId, disputeId, 
                    disputedAmount, timestamp);
            log.info("Step 5: Sent 10-day provisional credit notice");
            
            if (timelyNotification && disputedAmount.compareTo(BigDecimal.valueOf(50)) > 0) {
                regEComplianceService.issueProvisionalCredit(accountId, disputedAmount, 
                        disputeId, timestamp);
                log.info("Step 6: Issued provisional credit per Reg E");
            } else {
                log.info("Step 6: Provisional credit not required");
            }
            
            disputeManagementService.investigateTransaction(transactionId, disputeReason, 
                    timestamp);
            log.info("Step 7: Initiated transaction investigation");
            
            boolean investigationComplete = disputeManagementService.completeInvestigation(
                    disputeId, 45);
            
            if (investigationComplete) {
                log.info("Step 8: Investigation completed within 45-day requirement");
                
                String resolution = disputeManagementService.determineResolution(
                        disputeId, timestamp);
                log.info("Step 9: Determined dispute resolution: {}", resolution);
                
                if ("ERROR_CONFIRMED".equals(resolution)) {
                    regEComplianceService.makePermanentAdjustment(accountId, disputedAmount, 
                            disputeId, timestamp);
                    log.info("Step 10: Made permanent credit adjustment");
                } else if ("NO_ERROR".equals(resolution)) {
                    regEComplianceService.reverseProvisionalCredit(accountId, disputedAmount, 
                            disputeId, timestamp);
                    log.info("Step 10: Reversed provisional credit, no error found");
                }
                
                regEComplianceService.sendResolutionNotice(accountId, disputeId, 
                        resolution, timestamp);
                log.info("Step 11: Sent dispute resolution notice");
                
            } else {
                log.warn("Step 8-11: Investigation pending, extending deadline");
            }
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed Reg E compliance: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing Reg E compliance event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("disputeId") || 
            !eventData.has("accountId") || !eventData.has("transactionId")) {
            throw new IllegalArgumentException("Invalid Reg E compliance event structure");
        }
    }
}