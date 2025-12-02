package com.waqiti.audit.controller;

import com.waqiti.audit.dto.*;
import com.waqiti.audit.service.AuditService;
import com.waqiti.audit.service.ComplianceReportingService;
import com.waqiti.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Audit Management", description = "Audit trail and compliance reporting operations")
@Validated
public class AuditController {

    private final AuditService auditService;
    private final ComplianceReportingService complianceReportingService;

    @PostMapping("/events")
    @Operation(summary = "Create audit event")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AuditEventResponse>> createAuditEvent(
            @Valid @RequestBody CreateAuditEventRequest request) {
        log.info("Creating audit event: {}", request.getEventType());
        
        AuditEventResponse response = auditService.createAuditEvent(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/events")
    @Operation(summary = "Search audit events")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<Page<AuditEventResponse>>> searchAuditEvents(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String severity,
            Pageable pageable) {
        
        AuditSearchRequest searchRequest = AuditSearchRequest.builder()
                .entityType(entityType)
                .entityId(entityId)
                .eventType(eventType)
                .userId(userId)
                .startDate(startDate)
                .endDate(endDate)
                .severity(severity)
                .build();
        
        Page<AuditEventResponse> events = auditService.searchAuditEvents(searchRequest, pageable);
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @GetMapping("/events/{eventId}")
    @Operation(summary = "Get audit event details")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<AuditEventDetailResponse>> getAuditEventDetails(
            @PathVariable UUID eventId) {
        
        AuditEventDetailResponse event = auditService.getAuditEventDetails(eventId);
        return ResponseEntity.ok(ApiResponse.success(event));
    }

    @GetMapping("/trail/{entityId}")
    @Operation(summary = "Get audit trail for entity")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<List<AuditEventResponse>>> getAuditTrail(
            @PathVariable String entityId,
            @RequestParam(required = false) String entityType) {
        
        List<AuditEventResponse> trail = auditService.getAuditTrail(entityId, entityType);
        return ResponseEntity.ok(ApiResponse.success(trail));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify audit trail integrity")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<AuditVerificationResponse>> verifyAuditTrail(
            @Valid @RequestBody AuditVerificationRequest request) {
        log.info("Verifying audit trail for: {}", request);
        
        AuditVerificationResponse verification = auditService.verifyAuditTrail(request);
        return ResponseEntity.ok(ApiResponse.success(verification));
    }

    @GetMapping("/statistics")
    @Operation(summary = "Get audit statistics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<AuditStatisticsResponse>> getAuditStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        AuditStatisticsResponse statistics = auditService.getAuditStatistics(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(statistics));
    }

    // Compliance Reporting Endpoints
    @PostMapping("/compliance/reports")
    @Operation(summary = "Generate compliance report")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<ApiResponse<ComplianceReportResponse>> generateComplianceReport(
            @Valid @RequestBody GenerateComplianceReportRequest request) {
        log.info("Generating compliance report: {}", request.getReportType());
        
        ComplianceReportResponse report = complianceReportingService.generateReport(request);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/compliance/reports")
    @Operation(summary = "Get compliance reports")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<ApiResponse<Page<ComplianceReportResponse>>> getComplianceReports(
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        
        Page<ComplianceReportResponse> reports = complianceReportingService.getReports(reportType, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(reports));
    }

    @GetMapping("/compliance/reports/{reportId}")
    @Operation(summary = "Get compliance report details")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<ApiResponse<ComplianceReportDetailResponse>> getComplianceReportDetails(
            @PathVariable UUID reportId) {
        
        ComplianceReportDetailResponse report = complianceReportingService.getReportDetails(reportId);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/compliance/reports/{reportId}/download")
    @Operation(summary = "Download compliance report")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<byte[]> downloadComplianceReport(@PathVariable UUID reportId) {
        
        byte[] reportData = complianceReportingService.downloadReport(reportId);
        
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=compliance-report-" + reportId + ".pdf")
                .body(reportData);
    }

    @PostMapping("/compliance/sar")
    @Operation(summary = "File Suspicious Activity Report")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<ApiResponse<SuspiciousActivityReportResponse>> fileSAR(
            @Valid @RequestBody FileSARRequest request) {
        log.info("Filing SAR for transaction: {}", request.getTransactionId());
        
        SuspiciousActivityReportResponse sar = complianceReportingService.fileSAR(request);
        return ResponseEntity.ok(ApiResponse.success(sar));
    }

    @GetMapping("/compliance/sar")
    @Operation(summary = "Get SARs")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<ApiResponse<Page<SuspiciousActivityReportResponse>>> getSARs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {
        
        Page<SuspiciousActivityReportResponse> sars = complianceReportingService.getSARs(status, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(sars));
    }

    @PostMapping("/retention/archive")
    @Operation(summary = "Archive old audit records")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ArchiveResponse>> archiveOldRecords(
            @Valid @RequestBody ArchiveRequest request) {
        log.info("Archiving audit records older than: {}", request.getArchiveDate());
        
        ArchiveResponse response = auditService.archiveOldRecords(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("Audit service is healthy"));
    }
}