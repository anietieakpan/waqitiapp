package com.waqiti.common.audit.controller;

import com.waqiti.common.audit.AuditEventType;
import com.waqiti.common.audit.AuditCoverageAnalyzer;
import com.waqiti.common.audit.domain.AuditLog;
import com.waqiti.common.audit.domain.AuditLog.EventCategory;
import com.waqiti.common.audit.domain.AuditLog.Severity;
import com.waqiti.common.audit.domain.AuditLog.OperationResult;
import com.waqiti.common.audit.service.AuditSearchService;
import com.waqiti.common.audit.dto.AuditSearchRequest;
import com.waqiti.common.audit.dto.AuditSearchResponse;
import com.waqiti.common.audit.dto.AuditSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for audit log search and retrieval
 * 
 * Provides comprehensive search capabilities for audit logs with role-based
 * access control, compliance filtering, and forensic investigation support.
 * 
 * SECURITY:
 * - Role-based access control for sensitive audit data
 * - Data masking for unauthorized users
 * - Rate limiting to prevent abuse
 * - Audit logging of search activities
 * 
 * COMPLIANCE:
 * - PCI DSS: Restricted access to cardholder data audit logs
 * - GDPR: Personal data access tracking and consent validation
 * - SOX: Financial audit trail access controls
 * - SOC 2: Security event monitoring and alerting
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditSearchController {
    
    private final AuditSearchService auditSearchService;
    private final AuditCoverageAnalyzer auditCoverageAnalyzer;
    
    /**
     * Search audit logs with filters
     * 
     * @param searchRequest Search criteria and filters
     * @param pageable Pagination parameters
     * @return Paginated audit log results
     */
    @PostMapping("/search")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('AUDITOR') or hasRole('SECURITY_ANALYST')")
    public ResponseEntity<AuditSearchResponse> searchAuditLogs(
            @Valid @RequestBody AuditSearchRequest searchRequest,
            Pageable pageable) {
        
        log.info("Audit log search requested by user: {} with criteria: {}", 
                getCurrentUsername(), searchRequest);
        
        AuditSearchResponse response = auditSearchService.searchAuditLogs(searchRequest, pageable);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get audit logs by user ID
     * 
     * @param userId User ID to search for
     * @param startDate Start date for search (optional)
     * @param endDate End date for search (optional)
     * @param pageable Pagination parameters
     * @return User's audit log timeline
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('AUDITOR') or " +
                 "(hasRole('USER') and #userId == authentication.name)")
    public ResponseEntity<Page<AuditLog>> getUserAuditLogs(
            @PathVariable String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {
        
        log.info("User audit logs requested for user: {} by: {}", userId, getCurrentUsername());
        
        Page<AuditLog> auditLogs = auditSearchService.getUserAuditLogs(userId, startDate, endDate, pageable);
        
        return ResponseEntity.ok(auditLogs);
    }
    
    /**
     * Get audit log timeline for a session
     * 
     * @param sessionId Session ID to search for
     * @return Session audit log timeline
     */
    @GetMapping("/session/{sessionId}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('SECURITY_ANALYST')")
    public ResponseEntity<List<AuditLog>> getSessionAuditLogs(@PathVariable String sessionId) {
        
        log.info("Session audit logs requested for session: {} by: {}", sessionId, getCurrentUsername());
        
        List<AuditLog> auditLogs = auditSearchService.getSessionAuditLogs(sessionId);
        
        return ResponseEntity.ok(auditLogs);
    }
    
    /**
     * Get audit logs by correlation ID
     * 
     * @param correlationId Correlation ID to search for
     * @return Related audit logs
     */
    @GetMapping("/correlation/{correlationId}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('SECURITY_ANALYST')")
    public ResponseEntity<List<AuditLog>> getCorrelatedAuditLogs(@PathVariable String correlationId) {
        
        log.info("Correlated audit logs requested for correlation: {} by: {}", 
                correlationId, getCurrentUsername());
        
        List<AuditLog> auditLogs = auditSearchService.getCorrelatedAuditLogs(correlationId);
        
        return ResponseEntity.ok(auditLogs);
    }
    
    /**
     * Get financial transaction audit trail
     * 
     * @param transactionId Transaction ID to search for
     * @return Complete audit trail for the transaction
     */
    @GetMapping("/transaction/{transactionId}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('FINANCIAL_AUDITOR')")
    public ResponseEntity<List<AuditLog>> getTransactionAuditTrail(@PathVariable String transactionId) {
        
        log.info("Transaction audit trail requested for transaction: {} by: {}", 
                transactionId, getCurrentUsername());
        
        List<AuditLog> auditLogs = auditSearchService.getFinancialTransactionAuditTrail(transactionId);
        
        return ResponseEntity.ok(auditLogs);
    }
    
    /**
     * Get audit logs by event type
     * 
     * @param eventType Event type to search for
     * @param startDate Start date for search (optional)
     * @param endDate End date for search (optional)
     * @param pageable Pagination parameters
     * @return Audit logs of specified event type
     */
    @GetMapping("/event-type/{eventType}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('AUDITOR')")
    public ResponseEntity<Page<AuditLog>> getAuditLogsByEventType(
            @PathVariable AuditEventType eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {
        
        log.info("Audit logs by event type requested: {} by: {}", eventType, getCurrentUsername());
        
        Page<AuditLog> auditLogs = auditSearchService.getAuditLogsByEventType(eventType, startDate, endDate, pageable);
        
        return ResponseEntity.ok(auditLogs);
    }
    
    /**
     * Get audit logs by event category
     * 
     * @param category Event category to search for
     * @param pageable Pagination parameters
     * @return Audit logs of specified category
     */
    @GetMapping("/category/{category}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('AUDITOR')")
    public ResponseEntity<Page<AuditLog>> getAuditLogsByCategory(
            @PathVariable EventCategory category,
            Pageable pageable) {
        
        log.info("Audit logs by category requested: {} by: {}", category, getCurrentUsername());
        
        Page<AuditLog> auditLogs = auditSearchService.getAuditLogsByCategory(category, pageable);
        
        return ResponseEntity.ok(auditLogs);
    }
    
    /**
     * Get audit logs by severity level
     * 
     * @param severity Severity level to search for
     * @param pageable Pagination parameters
     * @return Audit logs of specified severity
     */
    @GetMapping("/severity/{severity}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('SECURITY_ANALYST')")
    public ResponseEntity<Page<AuditLog>> getAuditLogsBySeverity(
            @PathVariable Severity severity,
            Pageable pageable) {
        
        log.info("Audit logs by severity requested: {} by: {}", severity, getCurrentUsername());
        
        Page<AuditLog> auditLogs = auditSearchService.getAuditLogsBySeverity(severity, pageable);
        
        return ResponseEntity.ok(auditLogs);
    }
    
    /**
     * Get failed operations
     * 
     * @param startDate Start date for search (optional)
     * @param pageable Pagination parameters
     * @return Failed operations audit logs
     */
    @GetMapping("/failures")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('OPERATIONS_MANAGER')")
    public ResponseEntity<Page<AuditLog>> getFailedOperations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            Pageable pageable) {
        
        log.info("Failed operations requested by: {}", getCurrentUsername());
        
        Page<AuditLog> auditLogs = auditSearchService.getFailedOperations(startDate, pageable);
        
        return ResponseEntity.ok(auditLogs);
    }
    
    /**
     * Get suspicious activities
     * 
     * @param startDate Start date for search (optional)
     * @param riskThreshold Minimum risk score threshold (optional)
     * @return Suspicious activity audit logs
     */
    @GetMapping("/suspicious-activities")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('FRAUD_ANALYST') or hasRole('SECURITY_ANALYST')")
    public ResponseEntity<List<AuditLog>> getSuspiciousActivities(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false, defaultValue = "50") Integer riskThreshold) {
        
        log.info("Suspicious activities requested by: {} with risk threshold: {}", 
                getCurrentUsername(), riskThreshold);
        
        List<AuditLog> auditLogs = auditSearchService.getSuspiciousActivities(startDate, riskThreshold);
        
        return ResponseEntity.ok(auditLogs);
    }
    
    /**
     * Get events requiring investigation
     * 
     * @return Audit logs flagged for investigation
     */
    @GetMapping("/investigation-required")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('SECURITY_ANALYST')")
    public ResponseEntity<List<AuditLog>> getEventsRequiringInvestigation() {
        
        log.info("Events requiring investigation requested by: {}", getCurrentUsername());
        
        List<AuditLog> auditLogs = auditSearchService.getEventsRequiringInvestigation();
        
        return ResponseEntity.ok(auditLogs);
    }
    
    /**
     * Get PCI DSS relevant audit logs
     * 
     * @param startDate Start date for search
     * @param endDate End date for search
     * @param pageable Pagination parameters
     * @return PCI DSS relevant audit logs
     */
    @GetMapping("/pci-relevant")
    @PreAuthorize("hasRole('PCI_COMPLIANCE_OFFICER')")
    public ResponseEntity<Page<AuditLog>> getPciRelevantLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {
        
        log.info("PCI relevant logs requested by: {} for period: {} to {}", 
                getCurrentUsername(), startDate, endDate);
        
        Page<AuditLog> auditLogs = auditSearchService.getPciRelevantLogs(startDate, endDate, pageable);
        
        return ResponseEntity.ok(auditLogs);
    }
    
    /**
     * Get GDPR relevant audit logs
     * 
     * @param startDate Start date for search
     * @param endDate End date for search
     * @param pageable Pagination parameters
     * @return GDPR relevant audit logs
     */
    @GetMapping("/gdpr-relevant")
    @PreAuthorize("hasRole('DATA_PROTECTION_OFFICER')")
    public ResponseEntity<Page<AuditLog>> getGdprRelevantLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {
        
        log.info("GDPR relevant logs requested by: {} for period: {} to {}", 
                getCurrentUsername(), startDate, endDate);
        
        Page<AuditLog> auditLogs = auditSearchService.getGdprRelevantLogs(startDate, endDate, pageable);
        
        return ResponseEntity.ok(auditLogs);
    }
    
    /**
     * Get SOX relevant audit logs
     * 
     * @param startDate Start date for search
     * @param endDate End date for search
     * @param pageable Pagination parameters
     * @return SOX relevant audit logs
     */
    @GetMapping("/sox-relevant")
    @PreAuthorize("hasRole('FINANCIAL_AUDITOR')")
    public ResponseEntity<Page<AuditLog>> getSoxRelevantLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {
        
        log.info("SOX relevant logs requested by: {} for period: {} to {}", 
                getCurrentUsername(), startDate, endDate);
        
        Page<AuditLog> auditLogs = auditSearchService.getSoxRelevantLogs(startDate, endDate, pageable);
        
        return ResponseEntity.ok(auditLogs);
    }
    
    /**
     * Get audit log statistics and summary
     * 
     * @param startDate Start date for statistics
     * @param endDate End date for statistics
     * @return Audit log summary and statistics
     */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('AUDITOR')")
    public ResponseEntity<AuditSummaryResponse> getAuditSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        log.info("Audit summary requested by: {} for period: {} to {}", 
                getCurrentUsername(), startDate, endDate);
        
        AuditSummaryResponse summary = auditSearchService.getAuditSummary(startDate, endDate);
        
        return ResponseEntity.ok(summary);
    }
    
    /**
     * Get audit log by ID
     * 
     * @param id Audit log ID
     * @return Specific audit log
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('AUDITOR')")
    public ResponseEntity<AuditLog> getAuditLogById(@PathVariable UUID id) {
        
        log.info("Audit log requested by ID: {} by: {}", id, getCurrentUsername());
        
        AuditLog auditLog = auditSearchService.getAuditLogById(id);
        
        return ResponseEntity.ok(auditLog);
    }
    
    /**
     * Perform full-text search on audit logs
     * 
     * @param query Search query text
     * @param pageable Pagination parameters
     * @return Audit logs matching search query
     */
    @GetMapping("/search-text")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('AUDITOR')")
    public ResponseEntity<Page<AuditLog>> searchAuditLogsByText(
            @RequestParam String query,
            Pageable pageable) {
        
        log.info("Text search requested for query: '{}' by: {}", query, getCurrentUsername());
        
        Page<AuditLog> auditLogs = auditSearchService.searchAuditLogsByText(query, pageable);
        
        return ResponseEntity.ok(auditLogs);
    }
    
    /**
     * Get audit logs for IP address (security investigation)
     * 
     * @param ipAddress IP address to search for
     * @param startDate Start date for search (optional)
     * @param pageable Pagination parameters
     * @return Audit logs from specified IP address
     */
    @GetMapping("/ip/{ipAddress}")
    @PreAuthorize("hasRole('SECURITY_ANALYST') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<Page<AuditLog>> getAuditLogsByIpAddress(
            @PathVariable String ipAddress,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            Pageable pageable) {
        
        log.info("Audit logs by IP address requested: {} by: {}", ipAddress, getCurrentUsername());
        
        Page<AuditLog> auditLogs = auditSearchService.getAuditLogsByIpAddress(ipAddress, startDate, pageable);
        
        return ResponseEntity.ok(auditLogs);
    }
    
    /**
     * Get audit log volume metrics
     * 
     * @return Audit log volume by time periods
     */
    @GetMapping("/metrics/volume")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('OPERATIONS_MANAGER')")
    public ResponseEntity<?> getAuditLogVolumeMetrics() {
        
        log.info("Audit log volume metrics requested by: {}", getCurrentUsername());
        
        Object metrics = auditSearchService.getAuditLogVolumeMetrics();
        
        return ResponseEntity.ok(metrics);
    }
    
    /**
     * Get top users by audit activity
     * 
     * @param startDate Start date for analysis
     * @param limit Number of top users to return
     * @return Top users by audit activity
     */
    @GetMapping("/metrics/top-users")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<?> getTopUsersByActivity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("Top users by activity requested by: {} for period since: {}", 
                getCurrentUsername(), startDate);
        
        Object topUsers = auditSearchService.getTopUsersByActivity(startDate, limit);
        
        return ResponseEntity.ok(topUsers);
    }
    
    /**
     * Export audit logs to CSV
     * 
     * @param searchRequest Search criteria for export
     * @return CSV file with audit logs
     */
    @PostMapping("/export/csv")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<byte[]> exportAuditLogsToCsv(@Valid @RequestBody AuditSearchRequest searchRequest) {
        
        log.info("Audit logs export to CSV requested by: {} with criteria: {}", 
                getCurrentUsername(), searchRequest);
        
        byte[] csvData = auditSearchService.exportAuditLogsToCsv(searchRequest);
        
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=audit-logs.csv")
                .body(csvData);
    }
    
    /**
     * Export audit logs to JSON
     * 
     * @param searchRequest Search criteria for export
     * @return JSON file with audit logs
     */
    @PostMapping("/export/json")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<byte[]> exportAuditLogsToJson(@Valid @RequestBody AuditSearchRequest searchRequest) {
        
        log.info("Audit logs export to JSON requested by: {} with criteria: {}", 
                getCurrentUsername(), searchRequest);
        
        byte[] jsonData = auditSearchService.exportAuditLogsToJson(searchRequest);
        
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .header("Content-Disposition", "attachment; filename=audit-logs.json")
                .body(jsonData);
    }
    
    /**
     * Validate audit log integrity (tamper detection)
     * 
     * @param startSequence Start sequence number for validation
     * @param endSequence End sequence number for validation
     * @return Integrity validation results
     */
    @GetMapping("/integrity/validate")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('SECURITY_ANALYST')")
    public ResponseEntity<?> validateAuditLogIntegrity(
            @RequestParam Long startSequence,
            @RequestParam Long endSequence) {
        
        log.info("Audit log integrity validation requested by: {} for sequences: {} to {}", 
                getCurrentUsername(), startSequence, endSequence);
        
        Object validationResult = auditSearchService.validateAuditLogIntegrity(startSequence, endSequence);
        
        return ResponseEntity.ok(validationResult);
    }
    
    /**
     * Get audit coverage analysis report
     * 
     * @return Comprehensive audit coverage analysis
     */
    @GetMapping("/coverage/analysis")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<AuditCoverageAnalyzer.AuditCoverageReport> getAuditCoverageAnalysis() {
        
        log.info("Audit coverage analysis requested by: {}", getCurrentUsername());
        
        AuditCoverageAnalyzer.AuditCoverageReport coverageReport = auditCoverageAnalyzer.generateCoverageReport();
        
        return ResponseEntity.ok(coverageReport);
    }
    
    /**
     * Get current username from security context
     */
    private String getCurrentUsername() {
        // Implementation would extract from Spring Security context
        return "current_user";
    }
}