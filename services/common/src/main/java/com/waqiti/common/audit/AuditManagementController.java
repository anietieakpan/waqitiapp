package com.waqiti.common.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.waqiti.common.audit.dto.AuditSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.waqiti.common.audit.dto.AuditSummary;
import java.util.UUID;

/**
 * Administrative controller for audit log management and compliance reporting
 * Provides secure access to audit trails with proper authorization controls
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Audit Management", description = "Administrative audit log management operations")
@SecurityRequirement(name = "bearerAuth")
public class AuditManagementController {

    private final ComprehensiveAuditService auditService;
    private final AuditEventRepository auditEventRepository;
    private final AuditReportService auditReportService;
    private final SecureAuditLogger secureAuditLogger;

    /**
     * Search audit events with comprehensive filtering
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER') or hasAuthority('AUDIT_READ')")
    @Operation(summary = "Search audit events", description = "Search audit events with filtering and pagination")
    public ResponseEntity<Page<com.waqiti.common.events.model.AuditEvent>> searchAuditEvents(
            @Parameter(description = "User ID filter") @RequestParam(required = false) String userId,
            @Parameter(description = "Event type filter") @RequestParam(required = false) String eventType,
            @Parameter(description = "Start date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "Severity filter") @RequestParam(required = false) String severity,
            @Parameter(description = "Success filter") @RequestParam(required = false) Boolean success,
            @Parameter(description = "Resource type filter") @RequestParam(required = false) String resourceType,
            @Parameter(description = "IP address filter") @RequestParam(required = false) String ipAddress,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "timestamp") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "DESC") String sortDir) {

        // Log the audit search request
        secureAuditLogger.logDataAccess(
            SecureAuditLogger.DataAccessType.READ,
            "AUDIT_LOGS",
            "search_request",
            true
        );

        try {
            // Create search criteria
            AuditSearchCriteria criteria = AuditSearchCriteria.builder()
                    .userId(userId)
                    .eventType(eventType)
                    .startDate(startDate)
                    .endDate(endDate)
                    .severity(severity)
                    .success(success)
                    .resourceType(resourceType)
                    .ipAddress(ipAddress)
                    .build();

            // Create pageable
            Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            // Execute search
            Page<com.waqiti.common.events.model.AuditEvent> results = auditEventRepository.searchAuditEvents(criteria, pageable);

            log.info("Audit search completed - {} results found", results.getTotalElements());

            return ResponseEntity.ok(results);

        } catch (Exception e) {
            log.error("Error searching audit events", e);
            throw new AuditException("Failed to search audit events", e);
        }
    }

    /**
     * Get audit trail for a specific entity
     */
    @GetMapping("/trail/{entityType}/{entityId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER') or hasAuthority('AUDIT_READ')")
    @Operation(summary = "Get entity audit trail", description = "Get complete audit trail for a specific entity")
    public ResponseEntity<List<AuditRecord>> getEntityAuditTrail(
            @Parameter(description = "Entity type") @PathVariable String entityType,
            @Parameter(description = "Entity ID") @PathVariable String entityId,
            @Parameter(description = "Limit results") @RequestParam(defaultValue = "100") int limit) {

        // Log the audit trail access
        secureAuditLogger.logDataAccess(
            SecureAuditLogger.DataAccessType.READ,
            "AUDIT_TRAIL",
            entityType + ":" + entityId,
            true
        );

        try {
            List<AuditRecord> auditTrail = auditService.getAuditTrail(entityType, entityId)
                    .stream()
                    .limit(limit)
                    .collect(java.util.stream.Collectors.toList());

            log.info("Retrieved audit trail for {}/{} - {} records", entityType, entityId, auditTrail.size());

            return ResponseEntity.ok(auditTrail);

        } catch (Exception e) {
            log.error("Error retrieving audit trail for {}/{}", entityType, entityId, e);
            throw new AuditException("Failed to retrieve audit trail", e);
        }
    }

    /**
     * Get user activity summary
     */
    @GetMapping("/user/{userId}/summary")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER') or hasAuthority('AUDIT_READ')")
    @Operation(summary = "Get user activity summary", description = "Get summarized activity for a specific user")
    public ResponseEntity<AuditSummary> getUserActivitySummary(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Start date") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        // Log the user activity summary request
        secureAuditLogger.logDataAccess(
            SecureAuditLogger.DataAccessType.READ,
            "USER_ACTIVITY_SUMMARY",
            userId,
            true
        );

        try {
            ComprehensiveAuditService.AuditSummary internalSummary = auditService.getUserActivitySummary(userId, startDate, endDate);

            // Convert internal summary to DTO
            com.waqiti.common.audit.dto.AuditSummary summary = com.waqiti.common.audit.dto.AuditSummary.builder()
                .periodStart(internalSummary.getPeriodStart())
                .periodEnd(internalSummary.getPeriodEnd())
                .totalEvents(internalSummary.getTotalEvents())
                .securityEvents(internalSummary.getSecurityEvents())
                .dataAccessEvents(internalSummary.getDataAccessEvents())
                .configurationChanges(internalSummary.getConfigurationChanges())
                .failedAttempts(internalSummary.getFailedAttempts())
                .eventsByCategory(internalSummary.getEventsByCategory())
                .eventsByUser(internalSummary.getEventsByUser())
                .riskLevel(internalSummary.getRiskLevel())
                .build();

            log.info("Retrieved activity summary for user {} from {} to {}", userId, startDate, endDate);

            return ResponseEntity.ok(summary);

        } catch (Exception e) {
            log.error("Error retrieving user activity summary for {}", userId, e);
            throw new AuditException("Failed to retrieve user activity summary", e);
        }
    }

    /**
     * Generate compliance report
     */
    @PostMapping("/reports/compliance")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    @Operation(summary = "Generate compliance report", description = "Generate comprehensive compliance audit report")
    public ResponseEntity<AuditComplianceReport> generateComplianceReport(
            @RequestBody ComplianceReportRequest request) {

        // Log the compliance report generation
        secureAuditLogger.logComplianceEvent(
            SecureAuditLogger.ComplianceEventType.REGULATORY_REPORT,
            null,
            request.getComplianceType(),
            Map.of("reportType", "AUDIT_COMPLIANCE", "period", request.getReportPeriod())
        );

        try {
            AuditComplianceReport report = auditReportService.generateComplianceReport(request);

            log.info("Compliance report generated - Type: {}, Period: {}, Records: {}",
                    request.getComplianceType(), request.getReportPeriod(), report.getTotalRecords());

            return ResponseEntity.ok(report);

        } catch (Exception e) {
            log.error("Error generating compliance report", e);
            throw new AuditException("Failed to generate compliance report", e);
        }
    }

    /**
     * Export audit data for external analysis
     */
    @PostMapping("/export")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    @Operation(summary = "Export audit data", description = "Export audit data in specified format for analysis")
    public ResponseEntity<AuditExportResult> exportAuditData(
            @RequestBody AuditExportRequest request) {

        // Log the audit data export
        secureAuditLogger.logDataAccess(
            SecureAuditLogger.DataAccessType.EXPORT,
            "AUDIT_DATA",
            "bulk_export",
            true
        );

        try {
            AuditExportResult result = auditReportService.exportAuditData(request);

            log.warn("Audit data exported - Format: {}, Records: {}, File: {}",
                    request.getFormat(), result.getTotalRecords(), result.getExportFileName());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error exporting audit data", e);
            throw new AuditException("Failed to export audit data", e);
        }
    }

    /**
     * Verify audit log integrity
     */
    @PostMapping("/verify-integrity")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Verify audit integrity", description = "Verify the integrity of audit logs using hash chains")
    public ResponseEntity<AuditIntegrityReport> verifyAuditIntegrity(
            @Parameter(description = "Start sequence") @RequestParam(required = false) Long startSequence,
            @Parameter(description = "End sequence") @RequestParam(required = false) Long endSequence) {

        // Log the integrity verification
        secureAuditLogger.logSecurityEvent(
            SecureAuditLogger.SecurityEventType.SECURITY_ALERT,
            "Audit log integrity verification initiated"
        );

        try {
            AuditIntegrityReport report = auditReportService.verifyAuditIntegrityForController(startSequence, endSequence);

            if (report.isIntegrityViolationDetected()) {
                log.error("SECURITY ALERT: Audit log integrity violation detected - {} violations found",
                        report.getViolationCount());
                
                // Generate security alert
                secureAuditLogger.logSecurityEvent(
                    SecureAuditLogger.SecurityEventType.SECURITY_ALERT,
                    "Audit log integrity violation detected: " + report.getViolationCount() + " violations"
                );
            } else {
                log.info("Audit log integrity verification completed - No violations detected");
            }

            return ResponseEntity.ok(report);

        } catch (Exception e) {
            log.error("Error verifying audit integrity", e);
            throw new AuditException("Failed to verify audit integrity", e);
        }
    }

    /**
     * Archive old audit events
     */
    @PostMapping("/archive")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Archive audit events", description = "Archive audit events older than specified retention period")
    public ResponseEntity<AuditArchiveResult> archiveAuditEvents(
            @Parameter(description = "Archive older than days") @RequestParam(defaultValue = "2555") int olderThanDays,
            @Parameter(description = "Dry run mode") @RequestParam(defaultValue = "false") boolean dryRun) {

        // Log the archival operation
        secureAuditLogger.logDataAccess(
            SecureAuditLogger.DataAccessType.UPDATE,
            "AUDIT_ARCHIVE",
            "archive_operation",
            true
        );

        try {
            AuditArchiveResult result = auditReportService.archiveAuditEventsForController(olderThanDays, dryRun);

            if (!dryRun) {
                log.warn("Audit events archived - {} events moved to archive", result.getArchivedCount());
            } else {
                log.info("Audit archive dry run completed - {} events would be archived", result.getArchivedCount());
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error archiving audit events", e);
            throw new AuditException("Failed to archive audit events", e);
        }
    }

    /**
     * Get audit statistics dashboard
     */
    @GetMapping("/dashboard/statistics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER') or hasAuthority('AUDIT_READ')")
    @Operation(summary = "Get audit statistics", description = "Get audit statistics for dashboard display")
    public ResponseEntity<AuditDashboardStatistics> getAuditStatistics(
            @Parameter(description = "Time period in days") @RequestParam(defaultValue = "30") int days) {

        try {
            AuditDashboardStatistics stats = auditReportService.getAuditStatistics(days);

            log.debug("Audit statistics retrieved for {} days period", days);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error retrieving audit statistics", e);
            throw new AuditException("Failed to retrieve audit statistics", e);
        }
    }

    /**
     * Get high-risk audit events
     */
    @GetMapping("/high-risk")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_OFFICER') or hasAuthority('AUDIT_READ')")
    @Operation(summary = "Get high-risk events", description = "Get audit events with high risk scores requiring attention")
    public ResponseEntity<List<ComprehensiveAuditService.ComprehensiveAuditRecord>> getHighRiskEvents(
            @Parameter(description = "Minimum risk score") @RequestParam(defaultValue = "7") int minRiskScore,
            @Parameter(description = "Hours back to search") @RequestParam(defaultValue = "24") int hoursBack,
            @Parameter(description = "Maximum results") @RequestParam(defaultValue = "100") int limit) {

        // Log the high-risk events query
        secureAuditLogger.logSecurityEvent(
            SecureAuditLogger.SecurityEventType.SECURITY_ALERT,
            "High-risk audit events query executed"
        );

        try {
            LocalDateTime startTime = LocalDateTime.now().minusHours(hoursBack);

            List<ComprehensiveAuditService.ComprehensiveAuditRecord> highRiskEvents =
                    auditReportService.getHighRiskEventsForController(minRiskScore, startTime, limit);

            log.info("Retrieved {} high-risk audit events (score >= {}) from last {} hours",
                    highRiskEvents.size(), minRiskScore, hoursBack);

            if (!highRiskEvents.isEmpty()) {
                // Generate security alert for high number of high-risk events
                if (highRiskEvents.size() > 10) {
                    secureAuditLogger.logSecurityEvent(
                        SecureAuditLogger.SecurityEventType.SECURITY_ALERT,
                        "High number of high-risk events detected: " + highRiskEvents.size()
                    );
                }
            }

            return ResponseEntity.ok(highRiskEvents);

        } catch (Exception e) {
            log.error("Error retrieving high-risk audit events", e);
            throw new AuditException("Failed to retrieve high-risk audit events", e);
        }
    }

    // Request/Response DTOs for audit management

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ComplianceReportRequest {
        private String complianceType; // PCI_DSS, SOX, GDPR, etc.
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String reportPeriod; // DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY
        private List<String> eventTypes;
        private String format; // PDF, CSV, JSON
        private boolean includeDetails;
    }

    @lombok.Data
    @lombok.Builder
    public static class AuditExportRequest {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private List<String> eventTypes;
        private List<String> userIds;
        private String format; // CSV, JSON, XML
        private boolean sanitizeData;
        private String compressionType; // ZIP, GZIP, NONE
        private String criteria; // Search criteria filter
    }

    @lombok.Data
    @lombok.Builder
    public static class AuditComplianceReport {
        private String reportId;
        private String complianceType;
        private LocalDateTime generatedAt;
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
        private long totalRecords;
        private long complianceViolations;
        private Map<String, Long> eventTypeCounts;
        private List<String> findings;
        private String downloadUrl;
    }

    @lombok.Data
    @lombok.Builder
    public static class AuditExportResult {
        private String exportId;
        private String exportFileName;
        private long totalRecords;
        private long fileSizeBytes;
        private String downloadUrl;
        private LocalDateTime expiresAt;
        private String checksum;
    }

    @lombok.Data
    @lombok.Builder
    public static class AuditIntegrityReport {
        private String reportId;
        private LocalDateTime verifiedAt;
        private Long startSequence;
        private Long endSequence;
        private long totalRecordsVerified;
        private boolean integrityViolationDetected;
        private int violationCount;
        private List<String> violationDetails;
        private String overallStatus;
    }

    @lombok.Data
    @lombok.Builder
    public static class AuditArchiveResult {
        private long candidateCount;
        private long archivedCount;
        private long failedCount;
        private boolean dryRun;
        private LocalDateTime executedAt;
        private String archiveLocation;
        private List<String> errors;
    }

    @lombok.Data
    @lombok.Builder
    public static class AuditDashboardStatistics {
        private LocalDateTime generatedAt;
        private int periodDays;
        private long totalEvents;
        private long securityEvents;
        private long failedEvents;
        private long highRiskEvents;
        private Map<String, Long> eventTypeDistribution;
        private Map<String, Long> dailyEventCounts;
        private List<String> topUsers;
        private List<String> topActions;
        private double averageRiskScore;
    }
}