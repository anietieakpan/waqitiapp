package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.compliance.service.ComplianceAuditService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.ComplianceMetricsService;
import com.waqiti.compliance.service.CaseManagementService;
import com.waqiti.compliance.entity.ComplianceAuditEntry;
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
 * Critical Event Consumer #185: Compliance Audit Event Consumer
 * Processes internal compliance auditing and control effectiveness assessment
 * Implements 12-step zero-tolerance processing for secure compliance audit workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceAuditEventConsumer extends BaseKafkaConsumer {

    private final ComplianceAuditService auditService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final ComplianceMetricsService metricsService;
    private final CaseManagementService caseManagementService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "compliance-audit-events", groupId = "compliance-audit-group")
    @CircuitBreaker(name = "compliance-audit-consumer")
    @Retry(name = "compliance-audit-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleComplianceAuditEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "compliance-audit-event");
        
        try {
            log.info("Step 1: Processing compliance audit event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String auditId = eventData.path("auditId").asText();
            String auditType = eventData.path("auditType").asText(); // INTERNAL, EXTERNAL, REGULATORY
            String auditScope = eventData.path("auditScope").asText(); // BSA_AML, KYC, SANCTIONS, PRIVACY
            LocalDateTime auditStartDate = LocalDateTime.parse(eventData.path("auditStartDate").asText());
            LocalDateTime auditEndDate = LocalDateTime.parse(eventData.path("auditEndDate").asText());
            List<String> auditAreas = objectMapper.convertValue(
                eventData.path("auditAreas"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            List<String> controlsFramework = objectMapper.convertValue(
                eventData.path("controlsFramework"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            String auditFirm = eventData.path("auditFirm").asText();
            String leadAuditorId = eventData.path("leadAuditorId").asText();
            String complianceOfficerId = eventData.path("complianceOfficerId").asText();
            Map<String, Object> auditFindings = objectMapper.convertValue(
                eventData.path("auditFindings"), 
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );
            Map<String, Object> controlDeficiencies = objectMapper.convertValue(
                eventData.path("controlDeficiencies"), 
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );
            String overallRating = eventData.path("overallRating").asText(); // SATISFACTORY, NEEDS_IMPROVEMENT, UNSATISFACTORY
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted audit details: auditId={}, type={}, scope={}, rating={}", 
                    auditId, auditType, auditScope, overallRating);
            
            // Step 3: Validate audit scope and regulatory requirements
            auditService.validateAuditScopeAndRequirements(
                auditType, auditScope, auditAreas, controlsFramework, timestamp);
            
            log.info("Step 3: Validated audit scope and regulatory requirements");
            
            // Step 4: Analyze audit findings and control effectiveness
            Map<String, Object> findingsAnalysis = auditService.analyzeAuditFindings(
                auditId, auditFindings, controlDeficiencies, auditScope, timestamp);
            
            log.info("Step 4: Completed audit findings analysis: criticalFindings={}", 
                    findingsAnalysis.get("criticalFindingsCount"));
            
            // Step 5: Assess control deficiencies and risk impact
            Map<String, Object> riskAssessment = auditService.assessControlDeficiencyRisk(
                auditId, controlDeficiencies, auditAreas, timestamp);
            
            log.info("Step 5: Completed control deficiency risk assessment");
            
            // Step 6: Generate management response requirements
            Map<String, Object> managementResponse = auditService.generateManagementResponseRequirements(
                auditId, auditFindings, controlDeficiencies, overallRating, timestamp);
            
            log.info("Step 6: Generated management response requirements");
            
            // Step 7: Create corrective action plans and remediation timeline
            List<String> correctiveActionPlans = auditService.createCorrectiveActionPlans(
                auditId, auditFindings, controlDeficiencies, riskAssessment, timestamp);
            
            log.info("Step 7: Created {} corrective action plans", correctiveActionPlans.size());
            
            // Step 8: Update compliance metrics and control effectiveness ratings
            metricsService.updateComplianceMetricsFromAudit(
                auditId, auditScope, overallRating, findingsAnalysis, riskAssessment, timestamp);
            
            log.info("Step 8: Updated compliance metrics and control effectiveness ratings");
            
            // Step 9: Generate regulatory notifications for significant deficiencies
            if ("UNSATISFACTORY".equals(overallRating) || 
                (Integer) findingsAnalysis.get("criticalFindingsCount") > 0) {
                
                auditService.generateRegulatoryNotifications(
                    auditId, auditScope, auditFindings, controlDeficiencies, timestamp);
                
                log.info("Step 9: Generated regulatory notifications for significant deficiencies");
            } else {
                log.info("Step 9: No regulatory notifications required");
            }
            
            // Step 10: Schedule follow-up audits and monitoring activities
            auditService.scheduleFollowUpActivities(
                auditId, correctiveActionPlans, overallRating, auditScope, timestamp);
            
            log.info("Step 10: Scheduled follow-up audits and monitoring activities");
            
            // Step 11: Notify senior management and audit committee
            auditService.notifyAuditCommittee(
                auditId, auditScope, overallRating, findingsAnalysis, 
                managementResponse, complianceOfficerId, timestamp);
            
            // Escalate unsatisfactory ratings
            if ("UNSATISFACTORY".equals(overallRating)) {
                auditService.escalateToSeniorManagement(
                    auditId, auditScope, auditFindings, controlDeficiencies, timestamp);
                
                log.info("Step 11: Escalated unsatisfactory audit rating to senior management");
            }
            
            // Step 12: Log compliance audit for regulatory record and continuous monitoring
            ComplianceAuditEntry auditEntry = auditService.logComplianceAuditEvent(
                auditId, auditType, auditScope, auditStartDate, auditEndDate, 
                overallRating, findingsAnalysis, riskAssessment, 
                leadAuditorId, complianceOfficerId, timestamp);
            
            regulatoryReportingService.generateAuditComplianceReports(
                auditEntry, managementResponse, correctiveActionPlans, auditId, timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed compliance audit event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing compliance audit event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("auditId") || 
            !eventData.has("auditType") || !eventData.has("auditScope") ||
            !eventData.has("auditStartDate") || !eventData.has("auditEndDate") ||
            !eventData.has("auditAreas") || !eventData.has("controlsFramework") ||
            !eventData.has("leadAuditorId") || !eventData.has("auditFindings") ||
            !eventData.has("controlDeficiencies") || !eventData.has("overallRating") ||
            !eventData.has("timestamp")) {
            throw new IllegalArgumentException("Invalid compliance audit event structure");
        }
    }
}