package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.compliance.service.DataRetentionService;
import com.waqiti.compliance.service.RecordsArchivalService;
import com.waqiti.compliance.entity.RetentionPolicy;
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
 * Critical Event Consumer #177: Data Retention Policy Event Consumer
 * Processes data retention and deletion with SOX, GDPR, and banking law compliance
 * Implements 12-step zero-tolerance processing for regulatory record-keeping
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionPolicyEventConsumer extends BaseKafkaConsumer {

    private final DataRetentionService dataRetentionService;
    private final RecordsArchivalService recordsArchivalService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "data-retention-policy-events", groupId = "data-retention-policy-group")
    @CircuitBreaker(name = "data-retention-policy-consumer")
    @Retry(name = "data-retention-policy-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleDataRetentionPolicyEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "data-retention-policy-event");
        
        try {
            log.info("Step 1: Processing data retention policy event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String policyId = eventData.path("policyId").asText();
            String dataCategory = eventData.path("dataCategory").asText();
            String action = eventData.path("action").asText();
            int retentionYears = eventData.path("retentionYears").asInt();
            LocalDate cutoffDate = LocalDate.parse(eventData.path("cutoffDate").asText());
            String regulatoryBasis = eventData.path("regulatoryBasis").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted retention policy details: category={}, action={}, years={}", 
                    dataCategory, action, retentionYears);
            
            RetentionPolicy policy = dataRetentionService.validateRetentionPolicy(
                    policyId, dataCategory, retentionYears, regulatoryBasis, timestamp);
            
            log.info("Step 3: Validated retention policy against regulatory requirements");
            
            List<String> recordIds = dataRetentionService.identifyAffectedRecords(
                    dataCategory, cutoffDate, timestamp);
            
            log.info("Step 4: Identified {} records subject to retention policy", recordIds.size());
            
            boolean legalHoldActive = dataRetentionService.checkLegalHolds(
                    recordIds, timestamp);
            
            if (legalHoldActive) {
                log.warn("Step 5: LEGAL HOLD detected, suspending deletion");
                dataRetentionService.deferRetentionAction(policyId, timestamp);
                ack.acknowledge();
                return;
            }
            
            log.info("Step 5: No legal holds, proceeding with retention action");
            
            if ("ARCHIVE".equals(action)) {
                recordsArchivalService.moveToArchivalStorage(recordIds, dataCategory, timestamp);
                log.info("Step 6: Moved {} records to cold storage", recordIds.size());
            } else if ("DELETE".equals(action)) {
                boolean soxCompliant = dataRetentionService.verifySOXRetentionMet(
                        dataCategory, cutoffDate, timestamp);
                
                if (!soxCompliant) {
                    log.error("Step 6: SOX retention period not met, cancelling deletion");
                    dataRetentionService.cancelRetentionAction(policyId, timestamp);
                    ack.acknowledge();
                    return;
                }
                
                log.info("Step 6: SOX retention period verified");
                
                dataRetentionService.executeSecureDeletion(recordIds, dataCategory, timestamp);
                log.info("Step 7: Executed secure deletion for {} records", recordIds.size());
            } else {
                log.info("Step 6-7: Unknown action type: {}", action);
            }
            
            if ("DELETE".equals(action)) {
                boolean gdprCompliant = dataRetentionService.verifyGDPRCompliance(
                        recordIds, timestamp);
                log.info("Step 8: GDPR right-to-erasure compliance: {}", gdprCompliant);
            } else {
                log.info("Step 8: Archival action, GDPR erasure not applicable");
            }
            
            dataRetentionService.generateRetentionCertificate(policyId, recordIds.size(), 
                    action, timestamp);
            log.info("Step 9: Generated retention compliance certificate");
            
            dataRetentionService.updateRetentionAuditLog(policyId, dataCategory, 
                    recordIds.size(), action, timestamp);
            log.info("Step 10: Updated retention audit log");
            
            dataRetentionService.notifyComplianceOfficer(policyId, dataCategory, 
                    recordIds.size(), action, timestamp);
            log.info("Step 11: Notified compliance officer");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed data retention policy: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing data retention policy event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("policyId") || 
            !eventData.has("dataCategory") || !eventData.has("action")) {
            throw new IllegalArgumentException("Invalid data retention policy event structure");
        }
    }
}