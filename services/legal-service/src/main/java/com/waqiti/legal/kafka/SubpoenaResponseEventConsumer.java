package com.waqiti.legal.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.legal.service.SubpoenaProcessingService;
import com.waqiti.legal.service.RecordsManagementService;
import com.waqiti.legal.domain.Subpoena;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Critical Event Consumer #176: Subpoena Response Event Consumer
 * Processes legal subpoenas with Right to Financial Privacy Act compliance
 * Implements 12-step zero-tolerance processing for court-ordered document production
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubpoenaResponseEventConsumer extends BaseKafkaConsumer {

    private final SubpoenaProcessingService subpoenaProcessingService;
    private final RecordsManagementService recordsManagementService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "subpoena-response-events", groupId = "subpoena-response-group")
    @CircuitBreaker(name = "subpoena-response-consumer")
    @Retry(name = "subpoena-response-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleSubpoenaResponseEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "subpoena-response-event");
        
        try {
            log.info("Step 1: Processing subpoena response event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String subpoenaId = eventData.path("subpoenaId").asText();
            String customerId = eventData.path("customerId").asText();
            String caseNumber = eventData.path("caseNumber").asText();
            String issuingCourt = eventData.path("issuingCourt").asText();
            LocalDate issuanceDate = LocalDate.parse(eventData.path("issuanceDate").asText());
            LocalDate responseDeadline = LocalDate.parse(eventData.path("responseDeadline").asText());
            String subpoenaType = eventData.path("subpoenaType").asText();
            String requestedRecords = eventData.path("requestedRecords").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted subpoena details: caseNumber={}, type={}, deadline={}", 
                    caseNumber, subpoenaType, responseDeadline);
            
            Subpoena subpoena = subpoenaProcessingService.createSubpoenaRecord(
                    subpoenaId, customerId, caseNumber, issuingCourt, 
                    issuanceDate, responseDeadline, subpoenaType, requestedRecords, timestamp);
            
            log.info("Step 3: Created subpoena tracking record");
            
            boolean validSubpoena = subpoenaProcessingService.validateSubpoena(
                    subpoenaId, issuingCourt, timestamp);
            
            if (!validSubpoena) {
                log.error("Step 4: Invalid subpoena, consulting legal counsel");
                subpoenaProcessingService.escalateToLegalCounsel(subpoenaId, timestamp);
                ack.acknowledge();
                return;
            }
            
            log.info("Step 4: Subpoena validated");
            
            boolean customerNotificationRequired = subpoenaProcessingService
                    .checkCustomerNotificationRequirement(subpoenaType, timestamp);
            
            if (customerNotificationRequired) {
                subpoenaProcessingService.notifyCustomer(customerId, subpoenaId, 
                        caseNumber, timestamp);
                log.info("Step 5: Sent RFPA customer notification");
            } else {
                log.info("Step 5: Customer notification not required (law enforcement exception)");
            }
            
            List<String> recordIds = recordsManagementService.gatherRequestedRecords(
                    customerId, requestedRecords, timestamp);
            
            log.info("Step 6: Gathered {} requested records", recordIds.size());
            
            recordsManagementService.redactPrivilegedInformation(recordIds, timestamp);
            log.info("Step 7: Redacted privileged and non-relevant information");
            
            String productionBatesNumbers = recordsManagementService.prepareDocumentProduction(
                    recordIds, subpoenaId, timestamp);
            
            log.info("Step 8: Prepared document production with Bates numbering: {}", 
                    productionBatesNumbers);
            
            subpoenaProcessingService.certifyRecords(subpoenaId, recordIds.size(), timestamp);
            log.info("Step 9: Certified business records authenticity");
            
            subpoenaProcessingService.submitToIssuingParty(subpoenaId, issuingCourt, 
                    productionBatesNumbers, timestamp);
            log.info("Step 10: Submitted records to court/issuing party");
            
            subpoenaProcessingService.fileComplianceCertificate(subpoenaId, caseNumber, 
                    responseDeadline, timestamp);
            log.info("Step 11: Filed certificate of compliance");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed subpoena response: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing subpoena response event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("subpoenaId") || 
            !eventData.has("customerId") || !eventData.has("caseNumber")) {
            throw new IllegalArgumentException("Invalid subpoena response event structure");
        }
    }
}