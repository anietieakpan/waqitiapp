package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.ComplianceAlertService;
import com.waqiti.compliance.service.SARFilingService;
import com.waqiti.compliance.service.CTRFilingService;
import com.waqiti.compliance.service.FIUReportingService;
import com.waqiti.compliance.domain.RegulatoryReport;
import com.waqiti.compliance.domain.ComplianceAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL COMPLIANCE: Consumes regulatory fraud report events
 * 
 * This consumer processes regulatory fraud reports that must be filed
 * with various regulatory bodies including:
 * - SAR (Suspicious Activity Reports)
 * - CTR (Currency Transaction Reports)  
 * - FIU (Financial Intelligence Unit) reports
 * 
 * Events processed:
 * - regulatory-fraud-report: Fraud cases requiring regulatory filings
 * 
 * Compliance Impact: CRITICAL - Regulatory violation prevention
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RegulatoryFraudReportConsumer {

    private final RegulatoryReportingService regulatoryReportingService;
    private final ComplianceAlertService complianceAlertService;
    private final SARFilingService sarFilingService;
    private final CTRFilingService ctrFilingService;
    private final FIUReportingService fiuReportingService;
    private final ObjectMapper objectMapper;

    /**
     * Process regulatory fraud report requirements
     */
    @KafkaListener(topics = "regulatory-fraud-report", groupId = "compliance-regulatory-group")
    public void handleRegulatoryFraudReport(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        try {
            log.info("COMPLIANCE: Processing regulatory fraud report - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
            
            Map<String, Object> reportEvent = objectMapper.readValue(message, Map.class);
            
            String caseId = (String) reportEvent.get("caseId");
            List<String> reportTypes = (List<String>) reportEvent.get("reportTypes");
            LocalDateTime timestamp = LocalDateTime.parse((String) reportEvent.get("timestamp"));
            
            if (caseId == null || reportTypes == null || reportTypes.isEmpty()) {
                log.error("COMPLIANCE: Invalid regulatory report event - missing caseId or reportTypes");
                createComplianceAlert("INVALID_REGULATORY_REPORT", 
                    "Missing required fields in regulatory report event", "HIGH");
                return;
            }
            
            // Create regulatory report record
            RegulatoryReport report = createRegulatoryReport(caseId, reportTypes, timestamp);
            
            // Process each report type
            for (String reportType : reportTypes) {
                processReportType(report, reportType, reportEvent);
            }
            
            // Update compliance dashboard
            updateComplianceDashboard(report);
            
            // Send notifications to compliance team
            sendComplianceNotifications(report);
            
            log.info("COMPLIANCE: Successfully processed regulatory fraud report for case: {}", caseId);
            
        } catch (Exception e) {
            log.error("COMPLIANCE: CRITICAL - Failed to process regulatory fraud report", e);
            
            // Create critical compliance alert
            createComplianceAlert("REGULATORY_REPORT_FAILURE", 
                "Failed to process regulatory fraud report: " + e.getMessage(), "CRITICAL");
            
            // Re-throw to trigger Kafka retry
            throw new RuntimeException("COMPLIANCE FAILURE: Regulatory report processing failed", e);
        }
    }
    
    /**
     * Create regulatory report record
     */
    private RegulatoryReport createRegulatoryReport(String caseId, List<String> reportTypes, 
                                                   LocalDateTime timestamp) {
        RegulatoryReport report = RegulatoryReport.builder()
            .reportId(UUID.randomUUID().toString())
            .caseId(caseId)
            .reportTypes(reportTypes)
            .status("INITIATED")
            .priority("HIGH")
            .createdAt(timestamp)
            .deadline(calculateReportingDeadline(reportTypes))
            .assignedTo("COMPLIANCE_TEAM")
            .regulatoryBodies(determineRegulatoryBodies(reportTypes))
            .complianceOfficer("PRIMARY_COMPLIANCE_OFFICER")
            .escalationRequired(true)
            .autoSubmissionEnabled(false) // Manual review required for fraud reports
            .build();
            
        return regulatoryReportingService.createReport(report);
    }
    
    /**
     * Process specific report type
     */
    private void processReportType(RegulatoryReport report, String reportType, 
                                 Map<String, Object> reportEvent) {
        try {
            switch (reportType.toUpperCase()) {
                case "SAR":
                    processSARReport(report, reportEvent);
                    break;
                case "CTR":
                    processCTRReport(report, reportEvent);
                    break;
                case "FIU":
                    processFIUReport(report, reportEvent);
                    break;
                default:
                    log.warn("COMPLIANCE: Unknown report type: {}", reportType);
                    createComplianceAlert("UNKNOWN_REPORT_TYPE", 
                        "Unknown regulatory report type: " + reportType, "MEDIUM");
            }
        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to process {} report for case: {}", 
                reportType, report.getCaseId(), e);
            createComplianceAlert("REPORT_PROCESSING_FAILURE", 
                String.format("Failed to process %s report: %s", reportType, e.getMessage()), "HIGH");
        }
    }
    
    /**
     * Process SAR (Suspicious Activity Report) filing
     */
    private void processSARReport(RegulatoryReport report, Map<String, Object> reportEvent) {
        log.info("COMPLIANCE: Processing SAR filing for case: {}", report.getCaseId());
        
        // Create SAR with fraud case details
        String sarId = sarFilingService.createSAR(
            report.getCaseId(),
            "FRAUD_ACTIVITY",
            "Automated fraud detection triggered SAR filing",
            LocalDateTime.now().plusDays(14) // 14-day SAR deadline
        );
        
        // Update report with SAR reference
        regulatoryReportingService.addReportReference(report.getReportId(), "SAR", sarId);
        
        // Schedule compliance review
        regulatoryReportingService.scheduleComplianceReview(report.getReportId(), 
            LocalDateTime.now().plusDays(1));
        
        log.info("COMPLIANCE: SAR {} created for case: {}", sarId, report.getCaseId());
    }
    
    /**
     * Process CTR (Currency Transaction Report) filing
     */
    private void processCTRReport(RegulatoryReport report, Map<String, Object> reportEvent) {
        log.info("COMPLIANCE: Processing CTR filing for case: {}", report.getCaseId());
        
        // Create CTR for suspicious currency transactions
        String ctrId = ctrFilingService.createCTR(
            report.getCaseId(),
            "SUSPICIOUS_CURRENCY_ACTIVITY",
            LocalDateTime.now().plusDays(15) // 15-day CTR deadline
        );
        
        // Update report with CTR reference
        regulatoryReportingService.addReportReference(report.getReportId(), "CTR", ctrId);
        
        log.info("COMPLIANCE: CTR {} created for case: {}", ctrId, report.getCaseId());
    }
    
    /**
     * Process FIU (Financial Intelligence Unit) report
     */
    private void processFIUReport(RegulatoryReport report, Map<String, Object> reportEvent) {
        log.info("COMPLIANCE: Processing FIU report for case: {}", report.getCaseId());
        
        // Create FIU report for international cooperation
        String fiuId = fiuReportingService.createFIUReport(
            report.getCaseId(),
            "CROSS_BORDER_FRAUD_ACTIVITY",
            LocalDateTime.now().plusDays(7) // 7-day FIU deadline
        );
        
        // Update report with FIU reference
        regulatoryReportingService.addReportReference(report.getReportId(), "FIU", fiuId);
        
        log.info("COMPLIANCE: FIU report {} created for case: {}", fiuId, report.getCaseId());
    }
    
    /**
     * Calculate reporting deadline based on report types
     */
    private LocalDateTime calculateReportingDeadline(List<String> reportTypes) {
        // Use the shortest deadline to ensure compliance
        LocalDateTime shortest = LocalDateTime.now().plusDays(30); // Default 30 days
        
        for (String reportType : reportTypes) {
            switch (reportType.toUpperCase()) {
                case "FIU":
                    shortest = earliest(shortest, LocalDateTime.now().plusDays(7));
                    break;
                case "SAR":
                    shortest = earliest(shortest, LocalDateTime.now().plusDays(14));
                    break;
                case "CTR":
                    shortest = earliest(shortest, LocalDateTime.now().plusDays(15));
                    break;
            }
        }
        
        return shortest;
    }
    
    /**
     * Determine regulatory bodies involved
     */
    private List<String> determineRegulatoryBodies(List<String> reportTypes) {
        return reportTypes.stream()
            .map(this::mapToRegulatoryBody)
            .distinct()
            .toList();
    }
    
    private String mapToRegulatoryBody(String reportType) {
        return switch (reportType.toUpperCase()) {
            case "SAR" -> "FINCEN";
            case "CTR" -> "IRS";
            case "FIU" -> "FINANCIAL_INTELLIGENCE_UNIT";
            default -> "UNKNOWN";
        };
    }
    
    private LocalDateTime earliest(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
    
    /**
     * Create compliance alert
     */
    private void createComplianceAlert(String alertType, String message, String severity) {
        try {
            ComplianceAlert alert = ComplianceAlert.builder()
                .alertId(UUID.randomUUID().toString())
                .alertType(alertType)
                .message(message)
                .severity(severity)
                .createdAt(LocalDateTime.now())
                .requiresAction(true)
                .assignedTo("COMPLIANCE_TEAM")
                .build();
                
            complianceAlertService.createAlert(alert);
        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to create compliance alert", e);
        }
    }
    
    /**
     * Update compliance dashboard
     */
    private void updateComplianceDashboard(RegulatoryReport report) {
        try {
            regulatoryReportingService.updateDashboard("REGULATORY_FRAUD_REPORT", report);
        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to update compliance dashboard", e);
        }
    }
    
    /**
     * Send notifications to compliance team
     */
    private void sendComplianceNotifications(RegulatoryReport report) {
        try {
            regulatoryReportingService.sendNotification(
                "COMPLIANCE_TEAM",
                "Regulatory Fraud Report Filed",
                String.format("Regulatory reports have been initiated for fraud case %s. " +
                    "Report types: %s. Deadline: %s. Report ID: %s",
                    report.getCaseId(), report.getReportTypes(), 
                    report.getDeadline(), report.getReportId()),
                "HIGH"
            );
        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to send compliance notifications", e);
        }
    }
}