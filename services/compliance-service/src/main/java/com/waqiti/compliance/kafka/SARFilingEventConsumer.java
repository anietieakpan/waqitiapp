package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.validation.NullSafetyUtils;
import com.waqiti.compliance.service.SARProcessingService;
import com.waqiti.compliance.service.FINCENIntegrationService;
import com.waqiti.compliance.service.ComplianceAuditService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.entity.SARFiling;
import com.waqiti.compliance.entity.SuspiciousActivity;
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
import java.util.Map;
import java.util.UUID;

/**
 * Critical Event Consumer #112: SAR Filing Event Consumer
 * Processes Suspicious Activity Report generation with FinCEN compliance
 * Implements 12-step zero-tolerance processing for secure SAR filing workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SARFilingEventConsumer extends BaseKafkaConsumer {

    private final SARProcessingService sarProcessingService;
    private final FINCENIntegrationService fincenService;
    private final ComplianceAuditService auditService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "sar-filing-events", groupId = "sar-filing-group")
    @CircuitBreaker(name = "sar-filing-consumer")
    @Retry(name = "sar-filing-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleSARFilingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "sar-filing-event");
        
        try {
            log.info("Step 1: Processing SAR filing event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String sarId = eventData.path("sarId").asText();
            String customerId = eventData.path("customerId").asText();
            String suspiciousActivityType = eventData.path("suspiciousActivityType").asText();
            List<String> involvedTransactions = objectMapper.convertValue(
                eventData.path("involvedTransactions"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            // SAFETY FIX: Safe parsing with validation to prevent NumberFormatException
            BigDecimal totalAmount = NullSafetyUtils.safeParseBigDecimal(
                eventData.path("totalAmount").asText(),
                BigDecimal.ZERO
            );
            String currency = eventData.path("currency").asText();
            LocalDateTime activityStartDate = LocalDateTime.parse(eventData.path("activityStartDate").asText());
            LocalDateTime activityEndDate = LocalDateTime.parse(eventData.path("activityEndDate").asText());
            String filingReason = eventData.path("filingReason").asText();
            String filingOfficerId = eventData.path("filingOfficerId").asText();
            String priority = eventData.path("priority").asText(); // HIGH, MEDIUM, LOW
            Map<String, Object> suspiciousIndicators = objectMapper.convertValue(
                eventData.path("suspiciousIndicators"), 
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );
            List<String> supportingDocuments = objectMapper.convertValue(
                eventData.path("supportingDocuments"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted SAR filing details: sarId={}, customer={}, activityType={}, amount={} {}", 
                    sarId, customerId, suspiciousActivityType, totalAmount, currency);
            
            // Step 3: Validate SAR filing requirements and jurisdiction
            sarProcessingService.validateSARFilingRequirements(
                customerId, suspiciousActivityType, totalAmount, currency, 
                activityStartDate, activityEndDate, timestamp);
            
            log.info("Step 3: Validated SAR filing requirements and jurisdiction");
            
            // Step 4: Conduct comprehensive investigation and evidence gathering
            SuspiciousActivity investigation = sarProcessingService.conductSuspiciousActivityInvestigation(
                sarId, customerId, involvedTransactions, suspiciousActivityType, 
                suspiciousIndicators, activityStartDate, activityEndDate, timestamp);
            
            log.info("Step 4: Completed suspicious activity investigation: investigationId={}", 
                    investigation.getInvestigationId());
            
            // Step 5: Analyze transaction patterns and behavioral anomalies
            Map<String, Object> patternAnalysis = sarProcessingService.analyzeTransactionPatterns(
                involvedTransactions, customerId, activityStartDate, activityEndDate, timestamp);
            
            log.info("Step 5: Completed transaction pattern analysis");
            
            // Step 6: Determine SAR filing obligation and regulatory requirements
            boolean filingRequired = sarProcessingService.determineSARFilingObligation(
                suspiciousActivityType, totalAmount, currency, patternAnalysis, timestamp);
            
            if (!filingRequired) {
                log.warn("Step 6: SAR filing not required based on analysis: sarId={}", sarId);
                sarProcessingService.documentFilingDecision(sarId, "NOT_REQUIRED", timestamp);
                ack.acknowledge();
                return;
            }
            
            // Step 7: Generate SAR filing documentation with required forms
            SARFiling sarFiling = sarProcessingService.generateSARFiling(
                sarId, customerId, suspiciousActivityType, involvedTransactions, 
                totalAmount, currency, activityStartDate, activityEndDate, 
                filingReason, investigation, patternAnalysis, 
                suspiciousIndicators, filingOfficerId, timestamp);
            
            log.info("Step 7: Generated SAR filing documentation: filingId={}", 
                    sarFiling.getFilingId());
            
            // Step 8: Validate SAR completeness and regulatory compliance
            sarProcessingService.validateSARCompleteness(sarFiling, timestamp);
            
            // Step 9: Submit SAR to FinCEN through secure channels
            String fincenSubmissionId = fincenService.submitSARToFinCEN(
                sarFiling, supportingDocuments, priority, timestamp);
            
            log.info("Step 9: Submitted SAR to FinCEN: submissionId={}", fincenSubmissionId);
            
            // Step 10: Monitor submission status and handle responses
            fincenService.monitorSARSubmissionStatus(
                sarId, fincenSubmissionId, timestamp);
            
            sarProcessingService.updateSARStatus(sarId, "SUBMITTED", fincenSubmissionId, timestamp);
            
            // Step 11: Generate internal notifications and alerts
            sarProcessingService.sendSARFilingNotifications(
                sarId, filingOfficerId, sarFiling, fincenSubmissionId, timestamp);
            
            // Notify senior management for high-priority filings
            if ("HIGH".equals(priority)) {
                sarProcessingService.notifySeniorManagement(sarId, sarFiling, timestamp);
                
                log.info("Step 11: Notified senior management for high-priority SAR filing");
            }
            
            // Step 12: Log SAR filing for audit trail and regulatory examination
            auditService.logSARFilingEvent(
                sarId, customerId, suspiciousActivityType, involvedTransactions, 
                totalAmount, currency, filingReason, filingOfficerId, 
                fincenSubmissionId, sarFiling.getStatus(), timestamp);
            
            regulatoryReportingService.generateSARFilingReports(
                sarFiling, fincenSubmissionId, timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed SAR filing event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing SAR filing event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("sarId") || 
            !eventData.has("customerId") || !eventData.has("suspiciousActivityType") ||
            !eventData.has("involvedTransactions") || !eventData.has("totalAmount") ||
            !eventData.has("currency") || !eventData.has("activityStartDate") ||
            !eventData.has("activityEndDate") || !eventData.has("filingReason") ||
            !eventData.has("filingOfficerId") || !eventData.has("priority") ||
            !eventData.has("suspiciousIndicators")) {
            throw new IllegalArgumentException("Invalid SAR filing event structure");
        }
    }
}