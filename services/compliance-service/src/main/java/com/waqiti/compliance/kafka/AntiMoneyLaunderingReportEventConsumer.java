package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.validation.NullSafetyUtils;
import com.waqiti.compliance.service.AMLTransactionMonitoringService;
import com.waqiti.compliance.service.FINCENIntegrationService;
import com.waqiti.compliance.service.ComplianceAuditService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.domain.SARFiling;
import com.waqiti.compliance.domain.SuspiciousActivity;
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
 * Critical Event Consumer #178: Anti-Money Laundering Report Event Consumer
 * Processes AML report generation with SAR filing and FinCEN submission
 * Implements 12-step zero-tolerance processing for secure AML reporting workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AntiMoneyLaunderingReportEventConsumer extends BaseKafkaConsumer {

    private final AMLTransactionMonitoringService amlMonitoringService;
    private final FINCENIntegrationService fincenService;
    private final ComplianceAuditService auditService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "anti-money-laundering-report-events", groupId = "anti-money-laundering-report-group")
    @CircuitBreaker(name = "anti-money-laundering-report-consumer")
    @Retry(name = "anti-money-laundering-report-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleAntiMoneyLaunderingReportEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "anti-money-laundering-report-event");
        
        try {
            log.info("Step 1: Processing AML report event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String reportId = eventData.path("reportId").asText();
            String customerId = eventData.path("customerId").asText();
            String reportType = eventData.path("reportType").asText(); // SAR, CTR, FBAR
            List<String> suspiciousTransactions = objectMapper.convertValue(
                eventData.path("suspiciousTransactions"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            // SAFETY FIX: Safe parsing with validation to prevent NumberFormatException
            BigDecimal totalAmount = NullSafetyUtils.safeParseBigDecimal(
                eventData.path("totalAmount").asText(),
                BigDecimal.ZERO
            );
            String currency = eventData.path("currency").asText();
            String suspiciousActivityType = eventData.path("suspiciousActivityType").asText();
            LocalDateTime detectionDate = LocalDateTime.parse(eventData.path("detectionDate").asText());
            LocalDateTime activityStartDate = LocalDateTime.parse(eventData.path("activityStartDate").asText());
            LocalDateTime activityEndDate = LocalDateTime.parse(eventData.path("activityEndDate").asText());
            String riskLevel = eventData.path("riskLevel").asText(); // HIGH, MEDIUM, LOW
            String analystId = eventData.path("analystId").asText();
            Map<String, Object> riskIndicators = objectMapper.convertValue(
                eventData.path("riskIndicators"), 
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted AML report details: reportId={}, customer={}, type={}, amount={} {}", 
                    reportId, customerId, reportType, totalAmount, currency);
            
            // Step 3: Validate AML reporting requirements and BSA compliance
            amlMonitoringService.validateAMLReportingRequirements(
                customerId, reportType, totalAmount, currency, 
                suspiciousActivityType, detectionDate, timestamp);
            
            log.info("Step 3: Validated AML reporting requirements and BSA compliance");
            
            // Step 4: Conduct comprehensive AML investigation and pattern analysis
            SuspiciousActivity investigation = amlMonitoringService.conductComprehensiveAMLInvestigation(
                reportId, customerId, suspiciousTransactions, suspiciousActivityType, 
                riskIndicators, activityStartDate, activityEndDate, timestamp);
            
            log.info("Step 4: Completed comprehensive AML investigation: investigationId={}", 
                    investigation.getInvestigationId());
            
            // Step 5: Analyze transaction patterns and ML typologies
            Map<String, Object> mlTypologyAnalysis = amlMonitoringService.analyzeMLTypologies(
                suspiciousTransactions, customerId, activityStartDate, activityEndDate, 
                riskIndicators, timestamp);
            
            log.info("Step 5: Completed ML typology analysis");
            
            // Step 6: Determine filing obligation based on BSA requirements
            String filingObligation = amlMonitoringService.determineBSAFilingObligation(
                reportType, suspiciousActivityType, totalAmount, currency, 
                mlTypologyAnalysis, riskLevel, timestamp);
            
            if ("NOT_REQUIRED".equals(filingObligation)) {
                log.warn("Step 6: AML filing not required based on analysis: reportId={}", reportId);
                amlMonitoringService.documentFilingDecision(reportId, "NOT_REQUIRED", timestamp);
                ack.acknowledge();
                return;
            }
            
            // Step 7: Generate regulatory filing documentation
            SARFiling amlFiling = amlMonitoringService.generateAMLRegulatorFiling(
                reportId, customerId, reportType, suspiciousTransactions, 
                totalAmount, currency, suspiciousActivityType, investigation, 
                mlTypologyAnalysis, riskIndicators, analystId, timestamp);
            
            log.info("Step 7: Generated AML regulatory filing: filingId={}", 
                    amlFiling.getFilingId());
            
            // Step 8: Validate filing completeness and regulatory compliance
            amlMonitoringService.validateFilingCompleteness(amlFiling, reportType, timestamp);
            
            // Step 9: Submit filing to FinCEN through secure BSA E-Filing system
            String fincenSubmissionId = fincenService.submitAMLFilingToFinCEN(
                amlFiling, reportType, riskLevel, timestamp);
            
            log.info("Step 9: Submitted AML filing to FinCEN: submissionId={}", fincenSubmissionId);
            
            // Step 10: Monitor submission status and handle regulatory responses
            fincenService.monitorAMLSubmissionStatus(
                reportId, fincenSubmissionId, reportType, timestamp);
            
            amlMonitoringService.updateFilingStatus(reportId, "SUBMITTED", fincenSubmissionId, timestamp);
            
            // Step 11: Generate notifications and regulatory alerts
            amlMonitoringService.sendAMLFilingNotifications(
                reportId, analystId, amlFiling, fincenSubmissionId, timestamp);
            
            // Notify MLRO for high-risk cases
            if ("HIGH".equals(riskLevel)) {
                amlMonitoringService.notifyMoneyLaunderingReportingOfficer(
                    reportId, amlFiling, investigation, timestamp);
                
                log.info("Step 11: Notified MLRO for high-risk AML filing");
            }
            
            // Step 12: Log AML filing for audit trail and regulatory examination
            auditService.logAMLFilingEvent(
                reportId, customerId, reportType, suspiciousTransactions, 
                totalAmount, currency, suspiciousActivityType, analystId, 
                fincenSubmissionId, amlFiling.getStatus(), timestamp);
            
            regulatoryReportingService.generateAMLFilingReports(
                amlFiling, fincenSubmissionId, reportType, timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed AML report event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing AML report event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("reportId") || 
            !eventData.has("customerId") || !eventData.has("reportType") ||
            !eventData.has("suspiciousTransactions") || !eventData.has("totalAmount") ||
            !eventData.has("currency") || !eventData.has("suspiciousActivityType") ||
            !eventData.has("detectionDate") || !eventData.has("activityStartDate") ||
            !eventData.has("activityEndDate") || !eventData.has("riskLevel") ||
            !eventData.has("analystId") || !eventData.has("riskIndicators")) {
            throw new IllegalArgumentException("Invalid AML report event structure");
        }
    }
}