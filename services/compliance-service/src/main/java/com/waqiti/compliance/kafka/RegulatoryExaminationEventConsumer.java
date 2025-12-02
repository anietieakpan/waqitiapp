package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.ComplianceAuditService;
import com.waqiti.compliance.service.CaseManagementService;
import com.waqiti.compliance.service.RegulatoryCaseService;
import com.waqiti.compliance.entity.RegulatoryFiling;
import com.waqiti.compliance.entity.ComplianceAlert;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Critical Event Consumer #184: Regulatory Examination Event Consumer
 * Processes FDIC/OCC exam response and regulatory inquiry management
 * Implements 12-step zero-tolerance processing for secure regulatory examination workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegulatoryExaminationEventConsumer extends BaseKafkaConsumer {

    private final RegulatoryReportingService regulatoryReportingService;
    private final ComplianceAuditService auditService;
    private final CaseManagementService caseManagementService;
    private final RegulatoryCaseService regulatoryCaseService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "regulatory-examination-events", groupId = "regulatory-examination-group")
    @CircuitBreaker(name = "regulatory-examination-consumer")
    @Retry(name = "regulatory-examination-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleRegulatoryExaminationEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "regulatory-examination-event");
        
        try {
            log.info("Step 1: Processing regulatory examination event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String examinationId = eventData.path("examinationId").asText();
            String regulatoryAgency = eventData.path("regulatoryAgency").asText(); // FDIC, OCC, FED, CFPB
            String examinationType = eventData.path("examinationType").asText(); // SAFETY_SOUNDNESS, BSA_AML, CONSUMER
            String requestType = eventData.path("requestType").asText(); // INFORMATION_REQUEST, FINDING_RESPONSE, MRA
            LocalDateTime requestDate = LocalDateTime.parse(eventData.path("requestDate").asText());
            LocalDateTime responseDeadline = LocalDateTime.parse(eventData.path("responseDeadline").asText());
            List<String> requestedDocuments = objectMapper.convertValue(
                eventData.path("requestedDocuments"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            List<String> complianceAreas = objectMapper.convertValue(
                eventData.path("complianceAreas"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            String priority = eventData.path("priority").asText(); // URGENT, HIGH, MEDIUM, LOW
            String examinerId = eventData.path("examinerId").asText();
            String responsibleOfficerId = eventData.path("responsibleOfficerId").asText();
            Map<String, Object> requestDetails = objectMapper.convertValue(
                eventData.path("requestDetails"), 
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted examination details: examinationId={}, agency={}, type={}, deadline={}", 
                    examinationId, regulatoryAgency, examinationType, responseDeadline);
            
            // Step 3: Validate regulatory examination jurisdiction and authority
            regulatoryReportingService.validateRegulatoryJurisdiction(
                regulatoryAgency, examinationType, complianceAreas, timestamp);
            
            log.info("Step 3: Validated regulatory examination jurisdiction and authority");
            
            // Step 4: Create regulatory case and assign responsibility
            String caseId = regulatoryCaseService.createRegulatoryExaminationCase(
                examinationId, regulatoryAgency, examinationType, requestType, 
                requestDate, responseDeadline, responsibleOfficerId, timestamp);
            
            log.info("Step 4: Created regulatory examination case: caseId={}", caseId);
            
            // Step 5: Analyze document requirements and compliance scope
            Map<String, Object> documentAnalysis = regulatoryCaseService.analyzeDocumentRequirements(
                caseId, requestedDocuments, complianceAreas, requestDetails, timestamp);
            
            log.info("Step 5: Completed document requirements analysis");
            
            // Step 6: Identify data sources and responsible departments
            Map<String, Object> dataSourceMapping = regulatoryCaseService.identifyDataSources(
                caseId, requestedDocuments, complianceAreas, timestamp);
            
            log.info("Step 6: Identified data sources and responsible departments");
            
            // Step 7: Initiate document collection and preparation process
            regulatoryCaseService.initiateDocumentCollection(
                caseId, examinationId, dataSourceMapping, responseDeadline, timestamp);
            
            log.info("Step 7: Initiated document collection and preparation process");
            
            // Step 8: Schedule compliance review and quality assurance
            regulatoryCaseService.scheduleComplianceReview(
                caseId, complianceAreas, responsibleOfficerId, responseDeadline, timestamp);
            
            log.info("Step 8: Scheduled compliance review and quality assurance");
            
            // Step 9: Generate examination response timeline and milestones
            Map<String, Object> responseTimeline = regulatoryCaseService.generateResponseTimeline(
                caseId, requestDate, responseDeadline, documentAnalysis, timestamp);
            
            log.info("Step 9: Generated examination response timeline");
            
            // Step 10: Notify senior management and stakeholders
            regulatoryCaseService.notifyStakeholders(
                caseId, examinationId, regulatoryAgency, examinationType, 
                priority, responseDeadline, responsibleOfficerId, timestamp);
            
            // Escalate urgent requests
            if ("URGENT".equals(priority) || "HIGH".equals(priority)) {
                regulatoryCaseService.escalateToSeniorManagement(
                    caseId, regulatoryAgency, examinationType, responseDeadline, timestamp);
                
                log.info("Step 10: Escalated urgent examination request to senior management");
            }
            
            // Step 11: Establish monitoring and progress tracking
            regulatoryCaseService.establishProgressTracking(
                caseId, responseTimeline, responsibleOfficerId, timestamp);
            
            log.info("Step 11: Established monitoring and progress tracking");
            
            // Step 12: Log regulatory examination for audit trail and compliance record
            auditService.logRegulatoryExaminationEvent(
                examinationId, caseId, regulatoryAgency, examinationType, 
                requestType, requestDate, responseDeadline, priority, 
                responsibleOfficerId, timestamp);
            
            regulatoryReportingService.generateExaminationResponseReports(
                caseId, documentAnalysis, responseTimeline, examinationId, timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed regulatory examination event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing regulatory examination event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("examinationId") || 
            !eventData.has("regulatoryAgency") || !eventData.has("examinationType") ||
            !eventData.has("requestType") || !eventData.has("requestDate") ||
            !eventData.has("responseDeadline") || !eventData.has("requestedDocuments") ||
            !eventData.has("complianceAreas") || !eventData.has("priority") ||
            !eventData.has("responsibleOfficerId") || !eventData.has("timestamp")) {
            throw new IllegalArgumentException("Invalid regulatory examination event structure");
        }
    }
}