package com.waqiti.common.retention;

import com.waqiti.common.retention.model.RetentionResult;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Comprehensive Data Retention Policy Service
 *
 * COMPLIANCE:
 * - BSA/FinCEN: 5-year retention for financial records
 * - GDPR: Right to erasure after legal retention period
 * - PCI-DSS: Secure deletion of cardholder data
 * - SOX: 7-year retention for audit records
 *
 * FEATURES:
 * - Automated retention policy enforcement
 * - Configurable retention periods per data type
 * - Secure deletion with audit trail
 * - Legal hold support
 * - GDPR right-to-erasure integration
 * - Batch processing with rate limiting
 * - Dry-run mode for testing
 *
 * ARCHITECTURE:
 * - Scheduled jobs for automated cleanup
 * - Batch processing to avoid database overload
 * - Tombstone records for compliance audit
 * - Integration with audit service for full traceability
 *
 * @author Waqiti Compliance Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataRetentionService {

    private final JdbcTemplate jdbcTemplate;
    private final DataRetentionAuditService auditService;
    private final DataRetentionConfigRepository configRepository;

    @Value("${data-retention.enabled:true}")
    private boolean retentionEnabled;

    @Value("${data-retention.dry-run:false}")
    private boolean dryRunMode;

    @Value("${data-retention.batch-size:100}")
    private int batchSize;

    @Value("${data-retention.rate-limit-ms:1000}")
    private int rateLimitMs;

    // Track retention stats
    private final Map<String, RetentionStats> retentionStats = new ConcurrentHashMap<>();

    /**
     * Scheduled job: Daily retention policy enforcement
     * Runs at 2 AM daily to minimize production impact
     */
    @Scheduled(cron = "${data-retention.schedule:0 0 2 * * *}")
    public void enforceRetentionPolicies() {
        if (!retentionEnabled) {
            log.info("Data retention enforcement is disabled");
            return;
        }

        log.info("Starting data retention policy enforcement (dry-run={})", dryRunMode);

        try {
            // Get all active retention policies
            List<DataRetentionPolicy> policies = configRepository.findAllActivePolicies();

            log.info("Found {} active retention policies to enforce", policies.size());

            for (DataRetentionPolicy policy : policies) {
                try {
                    enforcePolicy(policy);
                } catch (Exception e) {
                    log.error("Failed to enforce retention policy: {}", policy.getDataType(), e);
                    auditService.auditRetentionFailure(policy, e);
                }
            }

            // Generate summary report
            generateRetentionReport();

            log.info("Data retention policy enforcement completed");

        } catch (Exception e) {
            log.error("Critical error in retention policy enforcement", e);
            auditService.auditSystemFailure("RETENTION_ENFORCEMENT_FAILED", e);
        }
    }

    /**
     * Enforce a specific retention policy
     */
    @Transactional
    public RetentionResult enforcePolicy(DataRetentionPolicy policy) {
        log.info("Enforcing retention policy: {} (retention={} days)",
            policy.getDataType(), policy.getRetentionDays());

        Instant cutoffDate = Instant.now().minus(policy.getRetentionDays(), ChronoUnit.DAYS);

        RetentionResult result = RetentionResult.builder()
            .policyId(policy.getId())
            .dataType(policy.getDataType())
            .cutoffDate(cutoffDate)
            .startTime(Instant.now())
            .dryRun(dryRunMode)
            .build();

        try {
            // Step 1: Identify records eligible for deletion
            List<String> eligibleRecordIds = identifyEligibleRecords(policy, cutoffDate);
            result.setEligibleRecordCount(eligibleRecordIds.size());

            if (eligibleRecordIds.isEmpty()) {
                log.info("No records eligible for retention policy: {}", policy.getDataType());
                result.setSuccess(true);
                return result;
            }

            log.info("Found {} records eligible for deletion: {}",
                eligibleRecordIds.size(), policy.getDataType());

            // Step 2: Filter out records under legal hold
            List<String> recordsUnderHold = filterLegalHolds(eligibleRecordIds);
            result.setLegalHoldCount(recordsUnderHold.size());

            List<String> recordsToDelete = new ArrayList<>(eligibleRecordIds);
            recordsToDelete.removeAll(recordsUnderHold);
            result.setDeletableRecordCount(recordsToDelete.size());

            if (recordsUnderHold.size() > 0) {
                log.warn("{} records under legal hold cannot be deleted: {}",
                    recordsUnderHold.size(), policy.getDataType());
            }

            if (recordsToDelete.isEmpty()) {
                log.info("All eligible records are under legal hold: {}", policy.getDataType());
                result.setSuccess(true);
                return result;
            }

            // Step 3: Perform deletion in batches
            if (!dryRunMode) {
                int deletedCount = performBatchDeletion(policy, recordsToDelete);
                result.setDeletedRecordCount(deletedCount);

                // Step 4: Create tombstone records for audit compliance
                createTombstoneRecords(policy, recordsToDelete);

                log.info("Successfully deleted {} records for policy: {}",
                    deletedCount, policy.getDataType());
            } else {
                log.info("DRY RUN: Would delete {} records for policy: {}",
                    recordsToDelete.size(), policy.getDataType());
                result.setDeletedRecordCount(0);
            }

            result.setSuccess(true);
            result.setEndTime(Instant.now());

            // Audit the retention enforcement
            auditService.auditRetentionEnforcement(result);

            // Update stats
            updateRetentionStats(policy.getDataType(), result);

        } catch (Exception e) {
            log.error("Failed to enforce retention policy: {}", policy.getDataType(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(Instant.now());
            throw e;
        }

        return result;
    }

    /**
     * Identify records eligible for deletion based on retention policy
     */
    private List<String> identifyEligibleRecords(DataRetentionPolicy policy, Instant cutoffDate) {
        String sql = buildEligibilityQuery(policy, cutoffDate);

        log.debug("Executing eligibility query for {}: {}", policy.getDataType(), sql);

        return jdbcTemplate.queryForList(sql, String.class);
    }

    /**
     * Build SQL query to identify eligible records
     */
    private String buildEligibilityQuery(DataRetentionPolicy policy, Instant cutoffDate) {
        // Convert Instant to LocalDateTime for SQL
        LocalDateTime cutoffDateTime = LocalDateTime.ofInstant(cutoffDate, ZoneId.systemDefault());
        String cutoffString = cutoffDateTime.toString().replace('T', ' ');

        switch (policy.getDataType()) {
            case "KYC_RECORDS":
                return String.format(
                    "SELECT id FROM kyc_verifications " +
                    "WHERE created_at < TIMESTAMP '%s' " +
                    "AND status IN ('COMPLETED', 'EXPIRED') " +
                    "AND NOT EXISTS (SELECT 1 FROM legal_holds WHERE entity_type='KYC' AND entity_id=kyc_verifications.id) " +
                    "LIMIT 10000",
                    cutoffString
                );

            case "AML_RECORDS":
                return String.format(
                    "SELECT id FROM aml_screenings " +
                    "WHERE created_at < TIMESTAMP '%s' " +
                    "AND status = 'COMPLETED' " +
                    "AND NOT EXISTS (SELECT 1 FROM legal_holds WHERE entity_type='AML' AND entity_id=aml_screenings.id) " +
                    "LIMIT 10000",
                    cutoffString
                );

            case "TRANSACTION_RECORDS":
                return String.format(
                    "SELECT id FROM transactions " +
                    "WHERE created_at < TIMESTAMP '%s' " +
                    "AND status IN ('COMPLETED', 'CANCELLED') " +
                    "AND NOT EXISTS (SELECT 1 FROM legal_holds WHERE entity_type='TRANSACTION' AND entity_id=transactions.id) " +
                    "LIMIT 10000",
                    cutoffString
                );

            case "AUDIT_LOGS":
                return String.format(
                    "SELECT id FROM audit_log " +
                    "WHERE created_at < TIMESTAMP '%s' " +
                    "AND action NOT IN ('ADMIN_ACTION', 'CRITICAL_OPERATION') " +
                    "LIMIT 10000",
                    cutoffString
                );

            case "SESSION_DATA":
                return String.format(
                    "SELECT id FROM user_sessions " +
                    "WHERE last_activity < TIMESTAMP '%s' " +
                    "AND status = 'EXPIRED' " +
                    "LIMIT 10000",
                    cutoffString
                );

            case "NOTIFICATION_HISTORY":
                return String.format(
                    "SELECT id FROM notifications " +
                    "WHERE sent_at < TIMESTAMP '%s' " +
                    "AND status = 'DELIVERED' " +
                    "LIMIT 10000",
                    cutoffString
                );

            case "PAYMENT_TOKENS":
                // PCI-DSS: Delete tokenized payment data after retention period
                return String.format(
                    "SELECT id FROM payment_tokens " +
                    "WHERE created_at < TIMESTAMP '%s' " +
                    "AND last_used < TIMESTAMP '%s' " +
                    "AND NOT EXISTS (SELECT 1 FROM active_subscriptions WHERE payment_token_id=payment_tokens.id) " +
                    "LIMIT 10000",
                    cutoffString, cutoffString
                );

            case "DEVICE_FINGERPRINTS":
                return String.format(
                    "SELECT id FROM device_fingerprints " +
                    "WHERE last_seen < TIMESTAMP '%s' " +
                    "LIMIT 10000",
                    cutoffString
                );

            default:
                throw new IllegalArgumentException("Unknown data type: " + policy.getDataType());
        }
    }

    /**
     * Filter out records under legal hold
     */
    private List<String> filterLegalHolds(List<String> recordIds) {
        if (recordIds.isEmpty()) {
            return Collections.emptyList();
        }

        String sql = String.format(
            "SELECT entity_id FROM legal_holds " +
            "WHERE entity_id IN (%s) " +
            "AND status = 'ACTIVE' " +
            "AND (expiry_date IS NULL OR expiry_date > NOW())",
            recordIds.stream().map(id -> "'" + id + "'").collect(Collectors.joining(","))
        );

        return jdbcTemplate.queryForList(sql, String.class);
    }

    /**
     * Perform batch deletion with rate limiting
     */
    private int performBatchDeletion(DataRetentionPolicy policy, List<String> recordIds) {
        int totalDeleted = 0;

        // Process in batches
        for (int i = 0; i < recordIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, recordIds.size());
            List<String> batch = recordIds.subList(i, end);

            try {
                int deleted = deleteBatch(policy, batch);
                totalDeleted += deleted;

                log.debug("Deleted batch {}/{} ({} records) for policy: {}",
                    (i/batchSize + 1), (recordIds.size()/batchSize + 1), deleted, policy.getDataType());

                // Rate limiting to avoid database overload
                if (i + batchSize < recordIds.size()) {
                    Thread.sleep(rateLimitMs);
                }

            } catch (Exception e) {
                log.error("Failed to delete batch for policy: {}", policy.getDataType(), e);
                // Continue with next batch
            }
        }

        return totalDeleted;
    }

    /**
     * Delete a batch of records
     */
    private int deleteBatch(DataRetentionPolicy policy, List<String> recordIds) {
        String tableName = policy.getTableName();
        String idList = recordIds.stream()
            .map(id -> "'" + id + "'")
            .collect(Collectors.joining(","));

        // Use soft delete if configured
        if (policy.isUseSoftDelete()) {
            String sql = String.format(
                "UPDATE %s SET deleted_at = NOW(), deleted_by = 'DATA_RETENTION_SERVICE' " +
                "WHERE id IN (%s)",
                tableName, idList
            );
            return jdbcTemplate.update(sql);
        } else {
            // Hard delete
            String sql = String.format(
                "DELETE FROM %s WHERE id IN (%s)",
                tableName, idList
            );
            return jdbcTemplate.update(sql);
        }
    }

    /**
     * Create tombstone records for audit compliance
     * Tombstones record that data was deleted and why
     */
    private void createTombstoneRecords(DataRetentionPolicy policy, List<String> recordIds) {
        String sql = "INSERT INTO data_retention_tombstones " +
                    "(id, entity_type, entity_id, deleted_at, deleted_by, retention_policy_id, reason) " +
                    "VALUES (?, ?, ?, NOW(), 'DATA_RETENTION_SERVICE', ?, ?)";

        List<Object[]> batchArgs = recordIds.stream()
            .map(recordId -> new Object[]{
                UUID.randomUUID().toString(),
                policy.getDataType(),
                recordId,
                policy.getId(),
                String.format("Deleted per retention policy: %d days", policy.getRetentionDays())
            })
            .collect(Collectors.toList());

        jdbcTemplate.batchUpdate(sql, batchArgs);

        log.info("Created {} tombstone records for policy: {}", recordIds.size(), policy.getDataType());
    }

    /**
     * Update retention statistics
     */
    private void updateRetentionStats(String dataType, RetentionResult result) {
        retentionStats.compute(dataType, (key, stats) -> {
            if (stats == null) {
                stats = new RetentionStats();
                stats.setDataType(dataType);
            }
            stats.incrementTotalRuns();
            stats.addDeletedRecords(result.getDeletedRecordCount());
            stats.setLastRunTime(result.getEndTime());
            return stats;
        });
    }

    /**
     * Generate retention enforcement report
     */
    private void generateRetentionReport() {
        log.info("=================================================");
        log.info("DATA RETENTION ENFORCEMENT REPORT");
        log.info("=================================================");
        log.info("Mode: {}", dryRunMode ? "DRY RUN" : "PRODUCTION");
        log.info("Total Policies Enforced: {}", retentionStats.size());

        retentionStats.values().forEach(stats -> {
            log.info("  {} - Runs: {}, Deleted: {}, Last Run: {}",
                stats.getDataType(),
                stats.getTotalRuns(),
                stats.getTotalDeletedRecords(),
                stats.getLastRunTime()
            );
        });

        log.info("=================================================");
    }

    /**
     * Manually trigger retention policy enforcement (for testing/admin)
     */
    public RetentionResult triggerRetentionPolicy(String dataType) {
        log.info("Manually triggering retention policy for: {}", dataType);

        DataRetentionPolicy policy = configRepository.findByDataType(dataType)
            .orElseThrow(() -> new IllegalArgumentException("No policy found for data type: " + dataType));

        return enforcePolicy(policy);
    }

    /**
     * Get retention statistics
     */
    public Map<String, RetentionStats> getRetentionStats() {
        return new HashMap<>(retentionStats);
    }

    /**
     * Check if a record can be deleted (not under legal hold)
     */
    public boolean canDeleteRecord(String entityType, String entityId) {
        String sql = "SELECT COUNT(*) FROM legal_holds " +
                    "WHERE entity_type = ? AND entity_id = ? " +
                    "AND status = 'ACTIVE' " +
                    "AND (expiry_date IS NULL OR expiry_date > NOW())";

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, entityType, entityId);
        return count != null && count == 0;
    }

    /**
     * Place legal hold on record
     */
    @Transactional
    public void placeLegalHold(String entityType, String entityId, String reason, Instant expiryDate) {
        log.warn("Placing legal hold: type={}, id={}, reason={}", entityType, entityId, reason);

        String sql = "INSERT INTO legal_holds " +
                    "(id, entity_type, entity_id, reason, placed_at, placed_by, status, expiry_date) " +
                    "VALUES (?, ?, ?, ?, NOW(), ?, 'ACTIVE', ?)";

        jdbcTemplate.update(sql,
            UUID.randomUUID().toString(),
            entityType,
            entityId,
            reason,
            "SYSTEM",
            expiryDate
        );

        auditService.auditLegalHold(entityType, entityId, reason);
    }

    /**
     * Release legal hold
     */
    @Transactional
    public void releaseLegalHold(String entityType, String entityId, String reason) {
        log.info("Releasing legal hold: type={}, id={}, reason={}", entityType, entityId, reason);

        String sql = "UPDATE legal_holds " +
                    "SET status = 'RELEASED', released_at = NOW(), released_by = ?, release_reason = ? " +
                    "WHERE entity_type = ? AND entity_id = ? AND status = 'ACTIVE'";

        jdbcTemplate.update(sql, "SYSTEM", reason, entityType, entityId);

        auditService.auditLegalHoldRelease(entityType, entityId, reason);
    }

    // ========================================================================
    // Data Classes
    // ========================================================================

    @Data
    public static class RetentionStats {
        private String dataType;
        private AtomicInteger totalRuns = new AtomicInteger(0);
        private AtomicInteger totalDeletedRecords = new AtomicInteger(0);
        private Instant lastRunTime;

        public void incrementTotalRuns() {
            totalRuns.incrementAndGet();
        }

        public void addDeletedRecords(int count) {
            totalDeletedRecords.addAndGet(count);
        }

        public int getTotalRuns() {
            return totalRuns.get();
        }

        public int getTotalDeletedRecords() {
            return totalDeletedRecords.get();
        }
    }
}
