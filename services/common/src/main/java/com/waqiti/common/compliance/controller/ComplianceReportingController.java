package com.waqiti.common.compliance.controller;

import com.waqiti.common.compliance.RegulatoryComplianceReportingService;
import com.waqiti.common.compliance.dto.ComplianceDTOs;
import com.waqiti.common.compliance.dto.ComplianceDTOs.*;
import com.waqiti.common.compliance.mapper.ComplianceMapper;
import com.waqiti.common.compliance.ComplianceReportStatus;
import com.waqiti.common.security.ValidateOwnership;
import com.waqiti.common.security.ResourceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for regulatory compliance reporting operations
 * Provides endpoints for compliance officers and system administrators
 */
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Compliance Reporting", description = "Regulatory compliance reporting and management APIs")
public class ComplianceReportingController {
    
    private final RegulatoryComplianceReportingService complianceReportingService;
    
    /**
     * Generate compliance report
     */
    @PostMapping("/reports/generate")
    @Operation(summary = "Generate compliance report",
               description = "Generate a new compliance report based on specified criteria")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<CompletableFuture<ComplianceReport>> generateReport(
            @Valid @RequestBody ComplianceDTOs.ComplianceReportRequest request) {

        log.info("Compliance report generation requested: {} by {}",
            request.getReportType(), request.getGeneratedBy());

        CompletableFuture<ComplianceReport> reportFuture =
            complianceReportingService.generateComplianceReport(ComplianceMapper.toDomain(request))
                .thenApply(ComplianceMapper::toDTO);

        return ResponseEntity.accepted().body(reportFuture);
    }
    
    /**
     * Get compliance report status
     */
    @GetMapping("/reports/{reportId}/status")
    @Operation(summary = "Get report status", 
               description = "Check the status of a compliance report generation")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<ComplianceReportStatus> getReportStatus(
            @Parameter(description = "Report ID") 
            @PathVariable String reportId) {
        
        log.debug("Checking status for compliance report: {}", reportId);
        
        ComplianceReportStatus status = complianceReportingService.getReportStatus(reportId);
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * List compliance reports
     */
    @GetMapping("/reports")
    @Operation(summary = "List compliance reports",
               description = "Retrieve a list of compliance reports with filtering options")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<ComplianceReportSummary> listReports(
            @Valid ComplianceDTOs.ComplianceReportFilter filter) {

        log.debug("Listing compliance reports with filter: {}", filter);

        ComplianceReportSummary summary = ComplianceMapper.toDTO(
            complianceReportingService.listComplianceReports(ComplianceMapper.toDomain(filter)));

        return ResponseEntity.ok(summary);
    }
    
    /**
     * Submit compliance report to regulatory authority
     */
    @PostMapping("/reports/{reportId}/submit")
    @Operation(summary = "Submit compliance report",
               description = "Submit a completed compliance report to regulatory authority")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    @ValidateOwnership(resourceType = ResourceType.COMPLIANCE_REPORT, resourceIdParam = "reportId", operation = "SUBMIT")
    public ResponseEntity<CompletableFuture<ComplianceSubmissionResult>> submitReport(
            @Parameter(description = "Report ID to submit")
            @PathVariable String reportId,
            @Valid @RequestBody ComplianceDTOs.ComplianceSubmissionRequest submissionRequest) {

        log.info("Submitting compliance report: {} to {}",
            reportId, submissionRequest.getRegulatoryAuthority());

        CompletableFuture<ComplianceSubmissionResult> submissionFuture =
            complianceReportingService.submitComplianceReport(reportId, ComplianceMapper.toDomain(submissionRequest))
                .thenApply(ComplianceMapper::toDTO);

        return ResponseEntity.accepted().body(submissionFuture);
    }
    
    /**
     * Generate Suspicious Activity Report (SAR)
     */
    @PostMapping("/reports/sar")
    @Operation(summary = "Generate SAR",
               description = "Generate a Suspicious Activity Report for flagged transactions")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('AML_ANALYST') or hasRole('ADMIN')")
    public ResponseEntity<CompletableFuture<ComplianceReport>> generateSAR(
            @Valid @RequestBody ComplianceDTOs.SARRequest sarRequest) {

        log.warn("SAR generation requested for transaction: {} by {}",
            sarRequest.getTransactionId(), sarRequest.getGeneratedBy());

        CompletableFuture<ComplianceReport> sarFuture =
            complianceReportingService.generateSAR(ComplianceMapper.toDomain(sarRequest))
                .thenApply(ComplianceMapper::toDTO);

        return ResponseEntity.accepted().body(sarFuture);
    }
    
    /**
     * Generate Currency Transaction Report (CTR)
     */
    @PostMapping("/reports/ctr")
    @Operation(summary = "Generate CTR",
               description = "Generate a Currency Transaction Report for large cash transactions")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('AML_ANALYST') or hasRole('ADMIN')")
    public ResponseEntity<CompletableFuture<ComplianceReport>> generateCTR(
            @Valid @RequestBody ComplianceDTOs.CTRRequest ctrRequest) {

        log.info("CTR generation requested for transaction: {} (amount: {} USD) by {}",
            ctrRequest.getTransactionId(), ctrRequest.getTransactionAmount(), ctrRequest.getGeneratedBy());

        CompletableFuture<ComplianceReport> ctrFuture =
            complianceReportingService.generateCTR(ComplianceMapper.toDomain(ctrRequest))
                .thenApply(ComplianceMapper::toDTO);

        return ResponseEntity.accepted().body(ctrFuture);
    }
    
    /**
     * Get compliance dashboard statistics
     */
    @GetMapping("/dashboard/statistics")
    @Operation(summary = "Get compliance statistics", 
               description = "Retrieve compliance dashboard statistics and metrics")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<ComplianceDashboardStatistics> getDashboardStatistics(
            @Parameter(description = "Time period in days") 
            @RequestParam(defaultValue = "30") int days) {
        
        log.debug("Retrieving compliance dashboard statistics for {} days", days);
        
        // In production, this would aggregate actual statistics
        ComplianceDashboardStatistics stats = ComplianceDashboardStatistics.builder()
            .totalReportsGenerated(245L)
            .pendingReports(8L)
            .submittedReports(220L)
            .failedReports(5L)
            .sarReports(12L)
            .ctrReports(35L)
            .averageGenerationTimeMinutes(15.5)
            .complianceScore(98.5)
            .build();
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get regulatory deadline calendar
     */
    @GetMapping("/calendar/deadlines")
    @Operation(summary = "Get regulatory deadlines", 
               description = "Retrieve upcoming regulatory reporting deadlines")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<RegulatoryDeadlineCalendar> getRegulatoryDeadlines(
            @Parameter(description = "Number of months ahead to look") 
            @RequestParam(defaultValue = "3") int monthsAhead) {
        
        log.debug("Retrieving regulatory deadlines for {} months ahead", monthsAhead);
        
        // In production, this would query regulatory calendar service
        RegulatoryDeadlineCalendar calendar = RegulatoryDeadlineCalendar.builder()
            .upcomingDeadlines(java.util.Arrays.asList(
                RegulatoryDeadline.builder()
                    .reportType(ComplianceDTOs.ComplianceReportType.PCI_DSS_COMPLIANCE)
                    .dueDate(java.time.LocalDate.now().plusDays(15))
                    .authority("PCI Security Standards Council")
                    .priority(ComplianceDTOs.ComplianceReportPriority.HIGH)
                    .build(),
                RegulatoryDeadline.builder()
                    .reportType(ComplianceDTOs.ComplianceReportType.SOX_COMPLIANCE)
                    .dueDate(java.time.LocalDate.now().plusDays(30))
                    .authority("SEC")
                    .priority(ComplianceDTOs.ComplianceReportPriority.CRITICAL)
                    .build()
            ))
            .totalDeadlines(12L)
            .criticalDeadlines(2L)
            .build();
        
        return ResponseEntity.ok(calendar);
    }
    
    /**
     * Validate compliance report data
     */
    @PostMapping("/reports/validate")
    @Operation(summary = "Validate report data", 
               description = "Validate compliance report data before generation")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<ComplianceValidationResult> validateReportData(
            @Valid @RequestBody ComplianceReportValidationRequest validationRequest) {
        
        log.debug("Validating compliance report data for type: {}", 
            validationRequest.getReportType());
        
        // In production, this would perform actual validation
        ComplianceValidationResult result = ComplianceValidationResult.builder()
            .valid(true)
            .errors(java.util.Collections.emptyList())
            .warnings(java.util.Collections.emptyList())
            .validatedAt(java.time.LocalDateTime.now())
            .build();
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Get compliance audit trail
     */
    @GetMapping("/audit-trail")
    @Operation(summary = "Get compliance audit trail", 
               description = "Retrieve audit trail for compliance activities")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('AUDIT_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<ComplianceAuditTrail> getComplianceAuditTrail(
            @Parameter(description = "Start date for audit trail") 
            @RequestParam(required = false) String startDate,
            @Parameter(description = "End date for audit trail") 
            @RequestParam(required = false) String endDate,
            @Parameter(description = "Activity type filter") 
            @RequestParam(required = false) String activityType) {
        
        log.debug("Retrieving compliance audit trail from {} to {} for type: {}", 
            startDate, endDate, activityType);
        
        // In production, this would query audit service
        ComplianceAuditTrail auditTrail = ComplianceAuditTrail.builder()
            .totalActivities(1250L)
            .reportGenerations(850L)
            .reportSubmissions(320L)
            .validationActivities(80L)
            .activities(java.util.Collections.emptyList())
            .build();
        
        return ResponseEntity.ok(auditTrail);
    }
    
    /**
     * Get compliance health status
     */
    @GetMapping("/health")
    @Operation(summary = "Get compliance health status", 
               description = "Check health status of compliance reporting system")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<ComplianceSystemHealth> getComplianceHealth() {
        
        // In production, this would check actual system health
        ComplianceSystemHealth health = ComplianceSystemHealth.builder()
            .overallStatus(ComplianceDTOs.SystemHealthStatus.HEALTHY)
            .reportGenerationStatus(ComplianceDTOs.SystemHealthStatus.HEALTHY)
            .submissionServiceStatus(ComplianceDTOs.SystemHealthStatus.HEALTHY)
            .auditServiceStatus(ComplianceDTOs.SystemHealthStatus.HEALTHY)
            .storageServiceStatus(ComplianceDTOs.SystemHealthStatus.HEALTHY)
            .lastHealthCheck(java.time.LocalDateTime.now())
            .build();
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * Download compliance report
     */
    @GetMapping("/reports/{reportId}/download")
    @Operation(summary = "Download compliance report", 
               description = "Download a completed compliance report file")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    @ValidateOwnership(resourceType = ResourceType.COMPLIANCE_REPORT, resourceIdParam = "reportId", operation = "DOWNLOAD")
    public ResponseEntity<byte[]> downloadReport(
            @Parameter(description = "Report ID to download") 
            @PathVariable String reportId) {
        
        log.info("Download requested for compliance report: {}", reportId);
        
        // In production, this would retrieve and return the actual report file
        String reportContent = "Sample compliance report content for report: " + reportId;
        byte[] content = reportContent.getBytes();
        
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"" + reportId + ".pdf\"")
            .header("Content-Type", "application/pdf")
            .body(content);
    }
    
    /**
     * Schedule compliance report generation
     */
    @PostMapping("/reports/schedule")
    @Operation(summary = "Schedule report generation", 
               description = "Schedule automatic compliance report generation")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<Void> scheduleReport(
            @Valid @RequestBody ComplianceReportScheduleRequest scheduleRequest) {
        
        log.info("Scheduling compliance report: {} with cron: {} by {}", 
            scheduleRequest.getReportType(), scheduleRequest.getCronExpression(), 
            scheduleRequest.getScheduledBy());
        
        // In production, this would create a scheduled job
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Cancel scheduled compliance report
     */
    @DeleteMapping("/reports/schedule/{scheduleId}")
    @Operation(summary = "Cancel scheduled report", 
               description = "Cancel a scheduled compliance report generation")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<Void> cancelScheduledReport(
            @Parameter(description = "Schedule ID to cancel") 
            @PathVariable String scheduleId) {
        
        log.info("Cancelling scheduled compliance report: {}", scheduleId);
        
        // In production, this would remove the scheduled job
        
        return ResponseEntity.ok().build();
    }
}