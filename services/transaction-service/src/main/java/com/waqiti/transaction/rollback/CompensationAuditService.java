package com.waqiti.transaction.rollback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise-grade Compensation Audit Service
 * 
 * Maintains comprehensive audit trail for all compensation actions
 * during transaction rollbacks for compliance and forensics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompensationAuditService {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, CompensationAuditEntry> auditCache = new ConcurrentHashMap<>();

    /**
     * Record successful compensation action
     */
    @Transactional
    public void recordCompensation(UUID transactionId, String actionId, 
                                  String serviceType, String status) {
        log.info("Recording compensation audit: transactionId={}, actionId={}, service={}, status={}", 
                transactionId, actionId, serviceType, status);

        String sql = """
            INSERT INTO compensation_audit (
                id, transaction_id, action_id, service_type, 
                status, executed_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        UUID auditId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(sql,
            auditId,
            transactionId,
            actionId,
            serviceType,
            status,
            now,
            now
        );

        // Update cache
        String cacheKey = buildCacheKey(transactionId, actionId, serviceType);
        auditCache.put(cacheKey, CompensationAuditEntry.builder()
            .id(auditId)
            .transactionId(transactionId)
            .actionId(actionId)
            .serviceType(serviceType)
            .status(status)
            .executedAt(now)
            .build());

        log.debug("Compensation audit recorded: {}", auditId);
    }

    /**
     * Record failed compensation action
     */
    @Transactional
    public void recordCompensationFailure(UUID transactionId, String actionId, 
                                         String serviceType, String errorMessage) {
        log.error("Recording compensation failure: transactionId={}, actionId={}, service={}, error={}", 
                transactionId, actionId, serviceType, errorMessage);

        String sql = """
            INSERT INTO compensation_audit (
                id, transaction_id, action_id, service_type, 
                status, error_message, executed_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        UUID auditId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(sql,
            auditId,
            transactionId,
            actionId,
            serviceType,
            "FAILED",
            errorMessage,
            now,
            now
        );

        // Send alert for failed compensation
        sendCompensationFailureAlert(transactionId, actionId, serviceType, errorMessage);

        log.error("Compensation failure recorded: {}", auditId);
    }

    /**
     * Check if compensation has already been applied (idempotency check)
     */
    public boolean isCompensationApplied(UUID transactionId, String actionId, String serviceType) {
        // Check cache first
        String cacheKey = buildCacheKey(transactionId, actionId, serviceType);
        if (auditCache.containsKey(cacheKey)) {
            CompensationAuditEntry entry = auditCache.get(cacheKey);
            return "COMPLETED".equals(entry.getStatus()) || 
                   "ALREADY_COMPLETED".equals(entry.getStatus());
        }

        // Check database
        String sql = """
            SELECT COUNT(*) FROM compensation_audit 
            WHERE transaction_id = ? 
            AND action_id = ? 
            AND service_type = ?
            AND status IN ('COMPLETED', 'ALREADY_COMPLETED')
            """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, 
            transactionId, actionId, serviceType);

        boolean isApplied = count != null && count > 0;

        // Update cache if found
        if (isApplied) {
            auditCache.put(cacheKey, CompensationAuditEntry.builder()
                .transactionId(transactionId)
                .actionId(actionId)
                .serviceType(serviceType)
                .status("ALREADY_COMPLETED")
                .build());
        }

        return isApplied;
    }

    /**
     * Get compensation history for a transaction
     */
    public List<CompensationAuditEntry> getCompensationHistory(UUID transactionId) {
        String sql = """
            SELECT id, transaction_id, action_id, service_type, 
                   status, error_message, executed_at, created_at
            FROM compensation_audit
            WHERE transaction_id = ?
            ORDER BY created_at DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> 
            CompensationAuditEntry.builder()
                .id(UUID.fromString(rs.getString("id")))
                .transactionId(UUID.fromString(rs.getString("transaction_id")))
                .actionId(rs.getString("action_id"))
                .serviceType(rs.getString("service_type"))
                .status(rs.getString("status"))
                .errorMessage(rs.getString("error_message"))
                .executedAt(rs.getTimestamp("executed_at").toLocalDateTime())
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build(),
            transactionId
        );
    }

    /**
     * Get failed compensations requiring manual intervention
     */
    public List<CompensationAuditEntry> getFailedCompensations(LocalDateTime since) {
        String sql = """
            SELECT id, transaction_id, action_id, service_type, 
                   status, error_message, executed_at, created_at
            FROM compensation_audit
            WHERE status = 'FAILED'
            AND created_at >= ?
            AND manual_intervention_required = true
            ORDER BY created_at DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> 
            CompensationAuditEntry.builder()
                .id(UUID.fromString(rs.getString("id")))
                .transactionId(UUID.fromString(rs.getString("transaction_id")))
                .actionId(rs.getString("action_id"))
                .serviceType(rs.getString("service_type"))
                .status(rs.getString("status"))
                .errorMessage(rs.getString("error_message"))
                .executedAt(rs.getTimestamp("executed_at").toLocalDateTime())
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build(),
            since
        );
    }

    /**
     * Mark compensation as requiring manual intervention
     */
    @Transactional
    public void markForManualIntervention(UUID transactionId, String actionId, String reason) {
        String sql = """
            UPDATE compensation_audit
            SET manual_intervention_required = true,
                manual_intervention_reason = ?,
                updated_at = ?
            WHERE transaction_id = ?
            AND action_id = ?
            """;

        jdbcTemplate.update(sql, reason, LocalDateTime.now(), transactionId, actionId);

        log.warn("Compensation marked for manual intervention: transaction={}, action={}, reason={}", 
                transactionId, actionId, reason);
    }

    /**
     * Generate compensation audit report
     */
    public CompensationAuditReport generateAuditReport(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Generating compensation audit report from {} to {}", startDate, endDate);

        // Get statistics
        String statsSql = """
            SELECT 
                COUNT(*) as total_compensations,
                COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as successful,
                COUNT(CASE WHEN status = 'FAILED' THEN 1 END) as failed,
                COUNT(CASE WHEN status = 'IN_PROGRESS' THEN 1 END) as in_progress,
                COUNT(DISTINCT transaction_id) as unique_transactions,
                COUNT(DISTINCT service_type) as services_involved
            FROM compensation_audit
            WHERE created_at BETWEEN ? AND ?
            """;

        Map<String, Object> stats = jdbcTemplate.queryForMap(statsSql, startDate, endDate);

        // Get failure breakdown
        String failureSql = """
            SELECT service_type, COUNT(*) as failure_count
            FROM compensation_audit
            WHERE status = 'FAILED'
            AND created_at BETWEEN ? AND ?
            GROUP BY service_type
            ORDER BY failure_count DESC
            """;

        List<Map<String, Object>> failureBreakdown = jdbcTemplate.queryForList(failureSql, startDate, endDate);

        return CompensationAuditReport.builder()
            .startDate(startDate)
            .endDate(endDate)
            .totalCompensations(((Number) stats.get("total_compensations")).longValue())
            .successfulCompensations(((Number) stats.get("successful")).longValue())
            .failedCompensations(((Number) stats.get("failed")).longValue())
            .inProgressCompensations(((Number) stats.get("in_progress")).longValue())
            .uniqueTransactions(((Number) stats.get("unique_transactions")).longValue())
            .servicesInvolved(((Number) stats.get("services_involved")).intValue())
            .failureBreakdown(failureBreakdown)
            .generatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Clean up old audit records (compliance retention)
     */
    @Transactional
    public int cleanupOldAuditRecords(int retentionDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        
        String sql = """
            DELETE FROM compensation_audit
            WHERE created_at < ?
            AND status = 'COMPLETED'
            AND manual_intervention_required = false
            """;

        int deleted = jdbcTemplate.update(sql, cutoffDate);
        
        log.info("Cleaned up {} old compensation audit records older than {} days", 
                deleted, retentionDays);
        
        return deleted;
    }

    private String buildCacheKey(UUID transactionId, String actionId, String serviceType) {
        return String.format("%s:%s:%s", transactionId, actionId, serviceType);
    }

    private void sendCompensationFailureAlert(UUID transactionId, String actionId, 
                                             String serviceType, String errorMessage) {
        // Integration with alerting service
        log.error("ALERT: Compensation failure - Transaction: {}, Action: {}, Service: {}, Error: {}", 
                transactionId, actionId, serviceType, errorMessage);
        
        // In production, this would integrate with PagerDuty, Slack, etc.
    }

    // Internal DTOs
    @lombok.Builder
    @lombok.Data
    public static class CompensationAuditEntry {
        private UUID id;
        private UUID transactionId;
        private String actionId;
        private String serviceType;
        private String status;
        private String errorMessage;
        private LocalDateTime executedAt;
        private LocalDateTime createdAt;
        private boolean manualInterventionRequired;
        private String manualInterventionReason;
    }

    @lombok.Builder
    @lombok.Data
    public static class CompensationAuditReport {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private Long totalCompensations;
        private Long successfulCompensations;
        private Long failedCompensations;
        private Long inProgressCompensations;
        private Long uniqueTransactions;
        private Integer servicesInvolved;
        private List<Map<String, Object>> failureBreakdown;
        private LocalDateTime generatedAt;
    }
}