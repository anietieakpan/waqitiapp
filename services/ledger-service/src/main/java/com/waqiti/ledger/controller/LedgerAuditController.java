package com.waqiti.ledger.controller;

import com.waqiti.ledger.dto.LedgerAuditTrailResponse;
import com.waqiti.ledger.service.AuditTrailService;
import com.waqiti.ledger.service.LedgerAuditService;
import com.waqiti.common.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for comprehensive ledger audit trail operations.
 * Provides enterprise-grade audit tracking, compliance reporting, and integrity verification.
 * 
 * @author Waqiti Ledger Team
 * @since 2.0.0
 */
@RestController
@RequestMapping("/api/v1/ledger/audit")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Ledger Audit Trail", description = "Comprehensive audit trail and compliance operations")
public class LedgerAuditController {

    private final AuditTrailService auditTrailService;
    private final LedgerAuditService ledgerAuditService;

    /**
     * Get comprehensive audit trail for a specific ledger entity
     */
    @GetMapping("/trail/{entityType}/{entityId}")
    @Operation(summary = "Get entity audit trail", 
               description = "Retrieves complete audit trail for a specific ledger entity")
    @ApiResponse(responseCode = "200", description = "Audit trail retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'COMPLIANCE_OFFICER')")
    public ResponseEntity<List<LedgerAuditTrailResponse>> getEntityAuditTrail(
            @PathVariable @NotBlank String entityType,
            @PathVariable @NotBlank String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        log.info("Audit trail request for entity: {} ID: {} by user: {}", 
                entityType, entityId, SecurityUtils.getCurrentUserId());

        try {
            AuditTrailService.AuditQuery query = AuditTrailService.AuditQuery.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .startTime(startDate)
                    .endTime(endDate)
                    .build();

            List<LedgerAuditTrailResponse> auditTrail = auditTrailService.queryAuditTrail(query);
            return ResponseEntity.ok(auditTrail);

        } catch (Exception e) {
            log.error("Failed to retrieve audit trail for entity: {} {}", entityType, entityId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get user activity audit trail
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user audit trail", 
               description = "Retrieves audit trail for all activities by a specific user")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR') or (#userId == authentication.name)")
    public ResponseEntity<List<LedgerAuditTrailResponse>> getUserAuditTrail(
            @PathVariable @NotBlank String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "100") @Positive int limit) {

        log.info("User audit trail request for user: {} by: {}", userId, SecurityUtils.getCurrentUserId());

        try {
            AuditTrailService.AuditQuery query = AuditTrailService.AuditQuery.builder()
                    .performedBy(userId)
                    .startTime(startDate)
                    .endTime(endDate)
                    .build();

            List<LedgerAuditTrailResponse> auditTrail = auditTrailService.queryAuditTrail(query);
            
            // Limit results for performance
            if (auditTrail.size() > limit) {
                auditTrail = auditTrail.subList(0, limit);
            }

            return ResponseEntity.ok(auditTrail);

        } catch (Exception e) {
            log.error("Failed to retrieve user audit trail for: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Search audit trail with advanced filters
     */
    @PostMapping("/search")
    @Operation(summary = "Advanced audit trail search", 
               description = "Searches audit trail with multiple filters and pagination")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'COMPLIANCE_OFFICER')")
    public ResponseEntity<Page<LedgerAuditTrailResponse>> searchAuditTrail(
            @RequestBody @Validated AuditSearchRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.info("Advanced audit search by user: {}", SecurityUtils.getCurrentUserId());

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<LedgerAuditTrailResponse> results = ledgerAuditService.searchAuditTrail(request, pageable);
            
            return ResponseEntity.ok(results);

        } catch (Exception e) {
            log.error("Failed to perform audit trail search", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Verify audit trail integrity
     */
    @PostMapping("/verify")
    @Operation(summary = "Verify audit trail integrity", 
               description = "Performs cryptographic verification of audit trail integrity")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'SECURITY_OFFICER')")
    public ResponseEntity<AuditTrailService.VerificationResult> verifyAuditIntegrity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {

        log.info("Audit integrity verification requested by: {} for period: {} to {}", 
                SecurityUtils.getCurrentUserId(), startDate, endDate);

        try {
            AuditTrailService.VerificationResult result = auditTrailService.verifyAuditTrailIntegrity(startDate, endDate);

            // Log verification attempt
            ledgerAuditService.logAuditVerification(
                    SecurityUtils.getCurrentUserId(),
                    startDate,
                    endDate,
                    result.isValid(),
                    result.getViolations().size(),
                    SecurityUtils.getClientIp(request)
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Failed to verify audit trail integrity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generate compliance audit report
     */
    @PostMapping("/compliance-report")
    @Operation(summary = "Generate compliance audit report", 
               description = "Generates comprehensive compliance audit report")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'AUDITOR')")
    public ResponseEntity<byte[]> generateComplianceReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "PDF") String format,
            @RequestParam(defaultValue = "COMPREHENSIVE") String reportType) {

        log.info("Compliance report generation by: {} for period: {} to {}", 
                SecurityUtils.getCurrentUserId(), startDate, endDate);

        try {
            byte[] reportData = ledgerAuditService.generateComplianceReport(
                    startDate, endDate, format, reportType);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", 
                    "ledger-compliance-report-" + startDate.toLocalDate() + "-to-" + endDate.toLocalDate() + "." + format.toLowerCase());
            headers.setContentLength(reportData.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(reportData);

        } catch (Exception e) {
            log.error("Failed to generate compliance report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get audit statistics and metrics
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get audit statistics", 
               description = "Retrieves comprehensive audit trail statistics and metrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'COMPLIANCE_OFFICER')")
    public ResponseEntity<AuditStatisticsResponse> getAuditStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        log.info("Audit statistics request by: {} for period: {} to {}", 
                SecurityUtils.getCurrentUserId(), startDate, endDate);

        try {
            AuditStatisticsResponse statistics = ledgerAuditService.getAuditStatistics(startDate, endDate);
            return ResponseEntity.ok(statistics);

        } catch (Exception e) {
            log.error("Failed to retrieve audit statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get failed operations for analysis
     */
    @GetMapping("/failures")
    @Operation(summary = "Get failed operations", 
               description = "Retrieves audit trail of failed operations for analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'SUPPORT')")
    public ResponseEntity<List<LedgerAuditTrailResponse>> getFailedOperations(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "100") @Positive int limit) {

        log.info("Failed operations request by: {} for period: {} to {}", 
                SecurityUtils.getCurrentUserId(), startDate, endDate);

        try {
            List<LedgerAuditTrailResponse> failures = ledgerAuditService.getFailedOperations(startDate, endDate, limit);
            return ResponseEntity.ok(failures);

        } catch (Exception e) {
            log.error("Failed to retrieve failed operations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get audit trail by correlation ID (for distributed tracing)
     */
    @GetMapping("/correlation/{correlationId}")
    @Operation(summary = "Get audit trail by correlation ID", 
               description = "Retrieves audit trail for distributed transaction correlation")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'SUPPORT')")
    public ResponseEntity<List<LedgerAuditTrailResponse>> getAuditByCorrelation(
            @PathVariable @NotBlank String correlationId) {

        log.info("Correlation audit trail request for: {} by: {}", correlationId, SecurityUtils.getCurrentUserId());

        try {
            List<LedgerAuditTrailResponse> auditTrail = ledgerAuditService.getAuditByCorrelationId(correlationId);
            return ResponseEntity.ok(auditTrail);

        } catch (Exception e) {
            log.error("Failed to retrieve audit trail for correlation: {}", correlationId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get real-time audit feed
     */
    @GetMapping("/live-feed")
    @Operation(summary = "Get real-time audit feed", 
               description = "Retrieves recent audit events for real-time monitoring")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'SECURITY_OFFICER')")
    public ResponseEntity<List<LedgerAuditTrailResponse>> getLiveAuditFeed(
            @RequestParam(defaultValue = "50") @Positive int limit,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action) {

        log.info("Live audit feed request by: {}", SecurityUtils.getCurrentUserId());

        try {
            List<LedgerAuditTrailResponse> liveEvents = ledgerAuditService.getLiveAuditFeed(limit, entityType, action);
            return ResponseEntity.ok(liveEvents);

        } catch (Exception e) {
            log.error("Failed to retrieve live audit feed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get audit trail summary for specific time periods
     */
    @GetMapping("/summary")
    @Operation(summary = "Get audit trail summary", 
               description = "Retrieves summarized audit statistics for dashboard views")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'COMPLIANCE_OFFICER', 'MANAGER')")
    public ResponseEntity<AuditSummaryResponse> getAuditSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "DAILY") String groupBy) {

        log.info("Audit summary request by: {} for period: {} to {}", 
                SecurityUtils.getCurrentUserId(), startDate, endDate);

        try {
            AuditSummaryResponse summary = ledgerAuditService.getAuditSummary(startDate, endDate, groupBy);
            return ResponseEntity.ok(summary);

        } catch (Exception e) {
            log.error("Failed to retrieve audit summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Export audit trail data
     */
    @PostMapping("/export")
    @Operation(summary = "Export audit trail data", 
               description = "Exports audit trail data in various formats for external analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'AUDITOR')")
    public ResponseEntity<byte[]> exportAuditTrail(
            @RequestBody @Validated AuditExportRequest request) {

        log.info("Audit trail export by: {} format: {}", SecurityUtils.getCurrentUserId(), request.getFormat());

        try {
            byte[] exportData = ledgerAuditService.exportAuditTrail(request);

            HttpHeaders headers = new HttpHeaders();
            String contentType = getContentTypeForFormat(request.getFormat());
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentDispositionFormData("attachment", 
                    "audit-export-" + System.currentTimeMillis() + "." + request.getFormat().toLowerCase());
            headers.setContentLength(exportData.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(exportData);

        } catch (Exception e) {
            log.error("Failed to export audit trail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Detect anomalous audit patterns
     */
    @GetMapping("/anomalies")
    @Operation(summary = "Detect audit anomalies", 
               description = "Detects anomalous patterns in audit trail for security monitoring")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECURITY_OFFICER', 'AUDITOR')")
    public ResponseEntity<List<AuditAnomalyResponse>> detectAuditAnomalies(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "MEDIUM") String sensitivity) {

        log.info("Audit anomaly detection by: {} for period: {} to {}", 
                SecurityUtils.getCurrentUserId(), startDate, endDate);

        try {
            List<AuditAnomalyResponse> anomalies = ledgerAuditService.detectAnomalies(startDate, endDate, sensitivity);
            return ResponseEntity.ok(anomalies);

        } catch (Exception e) {
            log.error("Failed to detect audit anomalies", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check for audit system
     */
    @GetMapping("/health")
    @Operation(summary = "Audit system health check", 
               description = "Checks the health and status of the audit trail system")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
    public ResponseEntity<AuditHealthResponse> getAuditHealth() {
        try {
            AuditHealthResponse health = ledgerAuditService.getAuditSystemHealth();
            return ResponseEntity.ok(health);

        } catch (Exception e) {
            log.error("Failed to check audit system health", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Helper method to determine content type for export format
     */
    private String getContentTypeForFormat(String format) {
        return switch (format.toUpperCase()) {
            case "CSV" -> "text/csv";
            case "JSON" -> "application/json";
            case "XML" -> "application/xml";
            case "PDF" -> "application/pdf";
            case "EXCEL" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }

    /**
     * Audit search request DTO
     */
    public static class AuditSearchRequest {
        private String entityType;
        private String entityId;
        private String action;
        private String performedBy;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private Boolean successful;
        private String ipAddress;
        private String correlationId;
        private Map<String, String> metadata;

        // Getters and setters
        public String getEntityType() { return entityType; }
        public void setEntityType(String entityType) { this.entityType = entityType; }
        public String getEntityId() { return entityId; }
        public void setEntityId(String entityId) { this.entityId = entityId; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getPerformedBy() { return performedBy; }
        public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }
        public LocalDateTime getStartDate() { return startDate; }
        public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
        public LocalDateTime getEndDate() { return endDate; }
        public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
        public Boolean getSuccessful() { return successful; }
        public void setSuccessful(Boolean successful) { this.successful = successful; }
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    }

    /**
     * Audit export request DTO
     */
    public static class AuditExportRequest {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String format;
        private List<String> entityTypes;
        private List<String> actions;
        private Boolean includeMetadata;
        private String compression;

        // Getters and setters
        public LocalDateTime getStartDate() { return startDate; }
        public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
        public LocalDateTime getEndDate() { return endDate; }
        public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public List<String> getEntityTypes() { return entityTypes; }
        public void setEntityTypes(List<String> entityTypes) { this.entityTypes = entityTypes; }
        public List<String> getActions() { return actions; }
        public void setActions(List<String> actions) { this.actions = actions; }
        public Boolean getIncludeMetadata() { return includeMetadata; }
        public void setIncludeMetadata(Boolean includeMetadata) { this.includeMetadata = includeMetadata; }
        public String getCompression() { return compression; }
        public void setCompression(String compression) { this.compression = compression; }
    }

    /**
     * Audit statistics response DTO
     */
    public static class AuditStatisticsResponse {
        private long totalEntries;
        private long successfulOperations;
        private long failedOperations;
        private double successRate;
        private Map<String, Long> operationsByType;
        private Map<String, Long> operationsByUser;
        private Map<String, Long> operationsByEntity;
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;

        // Getters and setters
        public long getTotalEntries() { return totalEntries; }
        public void setTotalEntries(long totalEntries) { this.totalEntries = totalEntries; }
        public long getSuccessfulOperations() { return successfulOperations; }
        public void setSuccessfulOperations(long successfulOperations) { this.successfulOperations = successfulOperations; }
        public long getFailedOperations() { return failedOperations; }
        public void setFailedOperations(long failedOperations) { this.failedOperations = failedOperations; }
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        public Map<String, Long> getOperationsByType() { return operationsByType; }
        public void setOperationsByType(Map<String, Long> operationsByType) { this.operationsByType = operationsByType; }
        public Map<String, Long> getOperationsByUser() { return operationsByUser; }
        public void setOperationsByUser(Map<String, Long> operationsByUser) { this.operationsByUser = operationsByUser; }
        public Map<String, Long> getOperationsByEntity() { return operationsByEntity; }
        public void setOperationsByEntity(Map<String, Long> operationsByEntity) { this.operationsByEntity = operationsByEntity; }
        public LocalDateTime getPeriodStart() { return periodStart; }
        public void setPeriodStart(LocalDateTime periodStart) { this.periodStart = periodStart; }
        public LocalDateTime getPeriodEnd() { return periodEnd; }
        public void setPeriodEnd(LocalDateTime periodEnd) { this.periodEnd = periodEnd; }
    }

    /**
     * Audit summary response DTO
     */
    public static class AuditSummaryResponse {
        private List<AuditPeriodSummary> periods;
        private AuditStatisticsResponse overallStats;
        private List<String> topUsers;
        private List<String> topActions;

        // Getters and setters
        public List<AuditPeriodSummary> getPeriods() { return periods; }
        public void setPeriods(List<AuditPeriodSummary> periods) { this.periods = periods; }
        public AuditStatisticsResponse getOverallStats() { return overallStats; }
        public void setOverallStats(AuditStatisticsResponse overallStats) { this.overallStats = overallStats; }
        public List<String> getTopUsers() { return topUsers; }
        public void setTopUsers(List<String> topUsers) { this.topUsers = topUsers; }
        public List<String> getTopActions() { return topActions; }
        public void setTopActions(List<String> topActions) { this.topActions = topActions; }
    }

    /**
     * Audit period summary DTO
     */
    public static class AuditPeriodSummary {
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
        private long totalOperations;
        private double successRate;
        private String mostActiveUser;
        private String mostCommonAction;

        // Getters and setters
        public LocalDateTime getPeriodStart() { return periodStart; }
        public void setPeriodStart(LocalDateTime periodStart) { this.periodStart = periodStart; }
        public LocalDateTime getPeriodEnd() { return periodEnd; }
        public void setPeriodEnd(LocalDateTime periodEnd) { this.periodEnd = periodEnd; }
        public long getTotalOperations() { return totalOperations; }
        public void setTotalOperations(long totalOperations) { this.totalOperations = totalOperations; }
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        public String getMostActiveUser() { return mostActiveUser; }
        public void setMostActiveUser(String mostActiveUser) { this.mostActiveUser = mostActiveUser; }
        public String getMostCommonAction() { return mostCommonAction; }
        public void setMostCommonAction(String mostCommonAction) { this.mostCommonAction = mostCommonAction; }
    }

    /**
     * Audit anomaly response DTO
     */
    public static class AuditAnomalyResponse {
        private String anomalyType;
        private String description;
        private double riskScore;
        private LocalDateTime detectedAt;
        private Map<String, Object> evidence;
        private String recommendedAction;

        // Getters and setters
        public String getAnomalyType() { return anomalyType; }
        public void setAnomalyType(String anomalyType) { this.anomalyType = anomalyType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public double getRiskScore() { return riskScore; }
        public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
        public LocalDateTime getDetectedAt() { return detectedAt; }
        public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }
        public Map<String, Object> getEvidence() { return evidence; }
        public void setEvidence(Map<String, Object> evidence) { this.evidence = evidence; }
        public String getRecommendedAction() { return recommendedAction; }
        public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }
    }

    /**
     * Audit health response DTO
     */
    public static class AuditHealthResponse {
        private String status;
        private LocalDateTime lastAuditEntry;
        private long auditBacklog;
        private double integrityScore;
        private List<String> healthIssues;
        private Map<String, String> systemMetrics;

        // Getters and setters
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getLastAuditEntry() { return lastAuditEntry; }
        public void setLastAuditEntry(LocalDateTime lastAuditEntry) { this.lastAuditEntry = lastAuditEntry; }
        public long getAuditBacklog() { return auditBacklog; }
        public void setAuditBacklog(long auditBacklog) { this.auditBacklog = auditBacklog; }
        public double getIntegrityScore() { return integrityScore; }
        public void setIntegrityScore(double integrityScore) { this.integrityScore = integrityScore; }
        public List<String> getHealthIssues() { return healthIssues; }
        public void setHealthIssues(List<String> healthIssues) { this.healthIssues = healthIssues; }
        public Map<String, String> getSystemMetrics() { return systemMetrics; }
        public void setSystemMetrics(Map<String, String> systemMetrics) { this.systemMetrics = systemMetrics; }
    }
}