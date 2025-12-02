package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.ComplianceReportingService;
import com.waqiti.compliance.service.RegulatoryFilingService;
import com.waqiti.compliance.service.ReportValidationService;
import com.waqiti.compliance.service.AuditService;
import com.waqiti.compliance.entity.RegulatoryReport;
import com.waqiti.compliance.entity.ComplianceFiling;
import com.waqiti.compliance.entity.ReportValidation;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

/**
 * Critical Event Consumer #9: Regulatory Report Events Consumer
 * Processes regulatory report generation, compliance filings, and supervisory reporting
 * Implements 12-step zero-tolerance processing for regulatory compliance
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegulatoryReportEventsConsumer extends BaseKafkaConsumer {

    private final RegulatoryReportingService regulatoryReportingService;
    private final ComplianceReportingService complianceReportingService;
    private final RegulatoryFilingService filingService;
    private final ReportValidationService validationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "regulatory-report-events", 
        groupId = "regulatory-report-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @CircuitBreaker(name = "regulatory-report-consumer")
    @Retry(name = "regulatory-report-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleRegulatoryReportEvent(
            ConsumerRecord<String, String> record, 
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "regulatory-report-event");
        MDC.put("partition", String.valueOf(partition));
        
        try {
            log.info("Step 1: Processing regulatory report event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String reportType = eventData.path("reportType").asText(); // CTR, SAR, FBAR, CALL_REPORT, BASEL_III
            String reportingPeriod = eventData.path("reportingPeriod").asText();
            LocalDateTime reportingDate = LocalDateTime.parse(eventData.path("reportingDate").asText());
            LocalDateTime dueDate = LocalDateTime.parse(eventData.path("dueDate").asText());
            String regulatoryAgency = eventData.path("regulatoryAgency").asText(); // FINCEN, OCC, FDIC, FED
            String reportingJurisdiction = eventData.path("reportingJurisdiction").asText();
            String reportFormat = eventData.path("reportFormat").asText(); // XML, PDF, CSV, XBRL
            boolean emergencyFiling = eventData.path("emergencyFiling").asBoolean();
            String triggerEvent = eventData.path("triggerEvent").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted report details: type={}, period={}, agency={}, due={}, emergency={}", 
                    reportType, reportingPeriod, regulatoryAgency, dueDate, emergencyFiling);
            
            // Step 3: Regulatory report validation and compliance check
            log.info("Step 3: Validating regulatory report requirements and compliance obligations");
            RegulatoryReport report = regulatoryReportingService.createRegulatoryReport(eventData);
            
            regulatoryReportingService.validateReportType(reportType);
            regulatoryReportingService.validateRegulatoryAgency(regulatoryAgency);
            regulatoryReportingService.validateReportingJurisdiction(reportingJurisdiction);
            regulatoryReportingService.validateReportingPeriod(reportingPeriod, reportType);
            
            if (!regulatoryReportingService.isValidReportFormat(reportFormat, reportType)) {
                throw new IllegalStateException("Invalid report format for type: " + reportType);
            }
            
            regulatoryReportingService.checkReportingDeadlines(report, dueDate);
            
            // Step 4: Data collection and aggregation
            log.info("Step 4: Collecting and aggregating data for regulatory report");
            complianceReportingService.collectReportData(report);
            complianceReportingService.aggregateTransactionData(report, reportingPeriod);
            complianceReportingService.validateDataCompleteness(report);
            complianceReportingService.reconcileDataSources(report);
            
            if ("CTR".equals(reportType)) {
                complianceReportingService.collectCTRData(report, reportingPeriod);
            } else if ("SAR".equals(reportType)) {
                complianceReportingService.collectSARData(report, reportingPeriod);
            } else if ("CALL_REPORT".equals(reportType)) {
                complianceReportingService.collectCallReportData(report, reportingPeriod);
            }
            
            complianceReportingService.validateDataAccuracy(report);
            
            // Step 5: Report generation and formatting
            log.info("Step 5: Generating report content and applying regulatory formatting");
            String reportContent = regulatoryReportingService.generateReportContent(report);
            regulatoryReportingService.applyRegulatoryFormatting(report, reportFormat);
            regulatoryReportingService.validateReportStructure(report);
            
            if ("XML".equals(reportFormat)) {
                regulatoryReportingService.generateXMLReport(report);
                regulatoryReportingService.validateXMLSchema(report);
            } else if ("XBRL".equals(reportFormat)) {
                regulatoryReportingService.generateXBRLReport(report);
                regulatoryReportingService.validateXBRLTaxonomy(report);
            }
            
            regulatoryReportingService.addDigitalSignature(report);
            
            // Step 6: Data validation and quality assurance
            log.info("Step 6: Performing comprehensive data validation and quality assurance");
            ReportValidation validation = validationService.createReportValidation(report);
            
            validationService.validateBusinessRules(report, validation);
            validationService.performDataConsistencyChecks(report, validation);
            validationService.validateCalculations(report, validation);
            validationService.checkRegulatoryCompliance(report, validation);
            
            boolean validationPassed = validationService.performFullValidation(validation);
            if (!validationPassed) {
                validationService.generateValidationReport(validation);
                regulatoryReportingService.flagValidationErrors(report);
                return;
            }
            
            validationService.certifyDataQuality(validation);
            
            // Step 7: Emergency filing and priority processing
            log.info("Step 7: Processing emergency filings and priority submissions");
            if (emergencyFiling) {
                filingService.processEmergencyFiling(report);
                filingService.notifyRegulatoryAgency(report);
                filingService.escalateToCompliance(report);
                
                regulatoryReportingService.generateEmergencyNotification(report, triggerEvent);
                regulatoryReportingService.implementUrgentProcedures(report);
            }
            
            filingService.setPriority(report, emergencyFiling);
            
            // Step 8: Regulatory submission and filing
            log.info("Step 8: Submitting report to regulatory agencies and managing filings");
            ComplianceFiling filing = filingService.createComplianceFiling(report);
            
            filingService.submitToRegulatoryAgency(filing, regulatoryAgency);
            filingService.trackSubmissionStatus(filing);
            filingService.generateSubmissionReceipt(filing);
            
            if (filingService.requiresMultipleAgencies(reportType)) {
                filingService.submitToMultipleAgencies(filing);
            }
            
            filingService.updateFilingMetrics(filing);
            filingService.scheduleFollowUp(filing);
            
            // Step 9: Confirmation and acknowledgment processing
            log.info("Step 9: Processing regulatory confirmations and acknowledgments");
            String submissionId = filingService.getSubmissionId(filing);
            filingService.waitForAcknowledgment(filing, submissionId);
            
            boolean acknowledged = filingService.processAcknowledgment(filing);
            if (acknowledged) {
                filingService.updateFilingStatus(filing, "ACKNOWLEDGED");
                regulatoryReportingService.notifyStakeholders(report);
            } else {
                filingService.handleRejection(filing);
                filingService.initiateResubmission(filing);
            }
            
            filingService.archiveSubmissionDocuments(filing);
            
            // Step 10: Compliance monitoring and follow-up
            log.info("Step 10: Implementing compliance monitoring and follow-up procedures");
            regulatoryReportingService.schedulePeriodicReporting(reportType, reportingPeriod);
            regulatoryReportingService.updateComplianceCalendar(reportType, dueDate);
            regulatoryReportingService.monitorRegulatoryChanges(reportType);
            
            complianceReportingService.trackReportingMetrics(report);
            complianceReportingService.updateComplianceScore(report);
            
            if (regulatoryReportingService.requiresOngoingMonitoring(reportType)) {
                regulatoryReportingService.scheduleMonitoringActivities(report);
            }
            
            // Step 11: Documentation and record keeping
            log.info("Step 11: Managing documentation and regulatory record keeping");
            regulatoryReportingService.archiveReportDocuments(report);
            regulatoryReportingService.maintainRegulatoryRecords(report);
            regulatoryReportingService.updateComplianceLibrary(report);
            
            auditService.createAuditTrail(report);
            auditService.documentComplianceActions(report);
            auditService.maintainRegulatoryEvidence(report);
            
            regulatoryReportingService.updateRetentionSchedule(report);
            
            // Step 12: Audit trail and performance metrics
            log.info("Step 12: Completing audit trail and updating performance metrics");
            auditService.logRegulatoryReport(report);
            auditService.logComplianceFiling(filing);
            auditService.logReportValidation(validation);
            
            regulatoryReportingService.updateReportingMetrics(report);
            complianceReportingService.updateComplianceStatistics(report);
            filingService.updateFilingStatistics(filing);
            
            auditService.generateRegulatoryReport(report);
            auditService.updateRegulatoryReporting(report);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed regulatory report: type={}, eventId={}, status={}", 
                    reportType, eventId, filing.getStatus());
            
        } catch (Exception e) {
            log.error("Error processing regulatory report event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("reportType") || 
            !eventData.has("reportingPeriod") || !eventData.has("reportingDate") ||
            !eventData.has("dueDate") || !eventData.has("regulatoryAgency") ||
            !eventData.has("reportingJurisdiction") || !eventData.has("reportFormat") ||
            !eventData.has("emergencyFiling") || !eventData.has("triggerEvent") ||
            !eventData.has("timestamp")) {
            throw new IllegalArgumentException("Invalid regulatory report event structure");
        }
    }
}