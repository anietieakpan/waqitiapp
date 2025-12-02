package com.waqiti.common.database;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.waqiti.common.database.SqlSafetyUtils.*;

/**
 * Database Archival Service
 * 
 * Manages automated database archival, cleanup, and compliance procedures
 * for the Waqiti financial application. Handles long-term data retention
 * according to regulatory requirements and business policies.
 */
@Service
@Slf4j
public class DatabaseArchivalService {

    private final JdbcTemplate jdbcTemplate;
    private final Executor asyncExecutor;

    // Configuration
    @Value("${database.archival.enabled:true}")
    private boolean archivalEnabled;

    @Value("${database.archival.transaction-retention-years:7}")
    private int transactionRetentionYears;

    @Value("${database.archival.audit-retention-years:3}")
    private int auditRetentionYears;

    @Value("${database.archival.cleanup-enabled:true}")
    private boolean cleanupEnabled;

    @Value("${database.archival.batch-size:10000}")
    private int batchSize;

    @Value("${database.archival.dry-run:false}")
    private boolean dryRun;

    public DatabaseArchivalService(JdbcTemplate jdbcTemplate, Executor asyncExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.asyncExecutor = asyncExecutor;
    }

    // =====================================================================
    // SCHEDULED ARCHIVAL OPERATIONS
    // =====================================================================

    /**
     * Automated daily maintenance - runs every day at 2 AM
     */
    @Scheduled(cron = "${database.archival.maintenance-cron:0 0 2 * * *}")
    public void performDailyMaintenance() {
        if (!archivalEnabled) {
            log.info("Database archival is disabled, skipping maintenance");
            return;
        }

        log.info("Starting automated database archival maintenance");
        
        try {
            CompletableFuture<Void> maintenanceFuture = CompletableFuture.runAsync(() -> {
                try {
                    Map<String, Object> results = runAutomatedMaintenance();
                    log.info("Automated maintenance completed successfully: {}", results);
                    
                    // Send notification if configured
                    sendMaintenanceNotification(results, true);
                    
                } catch (Exception e) {
                    log.error("Automated maintenance failed", e);
                    sendMaintenanceNotification(Collections.singletonMap("error", e.getMessage()), false);
                }
            }, asyncExecutor);

            // Don't block the scheduler, but log completion
            maintenanceFuture.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Maintenance future completed with error", throwable);
                }
            });

        } catch (Exception e) {
            log.error("Failed to start automated maintenance", e);
        }
    }

    /**
     * Weekly comprehensive archival check - runs every Sunday at 3 AM
     */
    @Scheduled(cron = "${database.archival.weekly-cron:0 0 3 * * SUN}")
    public void performWeeklyArchival() {
        if (!archivalEnabled) {
            return;
        }

        log.info("Starting weekly comprehensive archival process");
        
        CompletableFuture.runAsync(() -> {
            try {
                // Archive transactions
                int archivedTransactions = archiveOldTransactions();
                
                // Archive audit events
                int archivedAudits = archiveOldAuditEvents();
                
                // Generate compliance report
                Map<String, Object> complianceReport = generateComplianceReport();
                
                log.info("Weekly archival completed - Transactions: {}, Audits: {}", 
                        archivedTransactions, archivedAudits);
                
                // Store or send compliance report
                handleComplianceReport(complianceReport);
                
            } catch (Exception e) {
                log.error("Weekly archival failed", e);
            }
        }, asyncExecutor);
    }

    /**
     * Monthly cleanup of expired archives - runs first day of month at 4 AM
     */
    @Scheduled(cron = "${database.archival.cleanup-cron:0 0 4 1 * *}")
    public void performMonthlyCleanup() {
        if (!archivalEnabled || !cleanupEnabled) {
            return;
        }

        log.info("Starting monthly cleanup of expired archives");
        
        CompletableFuture.runAsync(() -> {
            try {
                int cleanedUp = cleanupExpiredArchives();
                log.info("Monthly cleanup completed - {} items cleaned", cleanedUp);
                
                // Update storage metrics
                updateStorageMetrics();
                
            } catch (Exception e) {
                log.error("Monthly cleanup failed", e);
            }
        }, asyncExecutor);
    }

    // =====================================================================
    // ARCHIVAL OPERATIONS
    // =====================================================================

    /**
     * Archive old transaction partitions
     */
    public int archiveOldTransactions() {
        if (dryRun) {
            log.info("DRY RUN: Would archive transactions older than {} years", transactionRetentionYears);
            return simulateTransactionArchival();
        }

        LocalDate retentionDate = LocalDate.now().minusYears(transactionRetentionYears);
        
        String sql = "SELECT archive.archive_transaction_partitions(?)";
        
        try {
            Integer result = jdbcTemplate.queryForObject(sql, Integer.class, retentionDate);
            log.info("Archived {} transaction partitions older than {}", result, retentionDate);
            return result != null ? result : 0;
            
        } catch (Exception e) {
            log.error("Failed to archive transaction partitions", e);
            throw new ArchivalException("Transaction archival failed", e);
        }
    }

    /**
     * Archive old audit events
     */
    public int archiveOldAuditEvents() {
        if (dryRun) {
            log.info("DRY RUN: Would archive audit events older than {} years", auditRetentionYears);
            return simulateAuditArchival();
        }

        LocalDate retentionDate = LocalDate.now().minusYears(auditRetentionYears);
        
        String sql = "SELECT archive.archive_audit_events(?)";
        
        try {
            Integer result = jdbcTemplate.queryForObject(sql, Integer.class, retentionDate);
            log.info("Archived {} audit event partitions older than {}", result, retentionDate);
            return result != null ? result : 0;
            
        } catch (Exception e) {
            log.error("Failed to archive audit events", e);
            throw new ArchivalException("Audit archival failed", e);
        }
    }

    /**
     * Clean up expired archived data
     */
    public int cleanupExpiredArchives() {
        if (dryRun) {
            log.info("DRY RUN: Would cleanup expired archives");
            return simulateCleanup();
        }

        String sql = "SELECT archive.cleanup_expired_archives()";
        
        try {
            Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
            log.info("Cleaned up {} expired archive items", result);
            return result != null ? result : 0;
            
        } catch (Exception e) {
            log.error("Failed to cleanup expired archives", e);
            throw new ArchivalException("Archive cleanup failed", e);
        }
    }

    /**
     * Run complete automated maintenance cycle
     */
    public Map<String, Object> runAutomatedMaintenance() {
        String sql = "SELECT * FROM archive.run_automated_maintenance()";
        
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            
            Map<String, Object> summary = new HashMap<>();
            summary.put("execution_time", LocalDateTime.now());
            summary.put("tasks_executed", results.size());
            summary.put("task_details", results);
            
            int totalProcessed = results.stream()
                .mapToInt(row -> (Integer) row.getOrDefault("items_processed", 0))
                .sum();
            summary.put("total_items_processed", totalProcessed);
            
            return summary;
            
        } catch (Exception e) {
            log.error("Automated maintenance failed", e);
            throw new ArchivalException("Automated maintenance failed", e);
        }
    }

    // =====================================================================
    // COMPLIANCE AND REPORTING
    // =====================================================================

    /**
     * Generate comprehensive compliance report
     */
    public Map<String, Object> generateComplianceReport() {
        LocalDate reportStart = LocalDate.now().minusYears(1);
        LocalDate reportEnd = LocalDate.now();
        
        String sql = "SELECT * FROM archive.generate_compliance_report(?, ?)";
        
        try {
            List<Map<String, Object>> complianceData = jdbcTemplate.queryForList(sql, reportStart, reportEnd);
            
            Map<String, Object> report = new HashMap<>();
            report.put("report_period_start", reportStart);
            report.put("report_period_end", reportEnd);
            report.put("generated_at", LocalDateTime.now());
            report.put("compliance_data", complianceData);
            
            // Calculate summary statistics
            Map<String, Object> summary = calculateComplianceSummary(complianceData);
            report.put("summary", summary);
            
            return report;
            
        } catch (Exception e) {
            log.error("Failed to generate compliance report", e);
            throw new ArchivalException("Compliance report generation failed", e);
        }
    }

    /**
     * Get archival job status and history
     */
    public List<Map<String, Object>> getArchivalJobHistory(int limitJobs) {
        String sql = """
            SELECT job_id, job_name, job_type, status, table_name,
                   records_processed, progress_percentage,
                   started_at, completed_at, error_message,
                   created_at
            FROM archive.archival_jobs
            ORDER BY created_at DESC
            LIMIT ?
            """;
        
        try {
            return jdbcTemplate.queryForList(sql, limitJobs);
        } catch (Exception e) {
            log.error("Failed to retrieve archival job history", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get current archival statistics
     */
    public Map<String, Object> getArchivalStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // Get archival metadata summary
            String metadataSql = """
                SELECT 
                    COUNT(*) as total_archives,
                    SUM(records_archived) as total_records_archived,
                    SUM(data_size_mb) as total_size_mb,
                    COUNT(DISTINCT table_name) as tables_with_archives,
                    MIN(archive_date) as oldest_archive,
                    MAX(archive_date) as newest_archive
                FROM archive.archival_metadata
                """;
            
            List<Map<String, Object>> metadataResults = jdbcTemplate.queryForList(metadataSql);
            if (!metadataResults.isEmpty()) {
                stats.putAll(metadataResults.get(0));
            }
            
            // Get retention policy compliance
            String complianceSql = """
                SELECT 
                    COUNT(*) FILTER (WHERE retention_expires_at > CURRENT_TIMESTAMP) as compliant_archives,
                    COUNT(*) FILTER (WHERE retention_expires_at <= CURRENT_TIMESTAMP) as expired_archives
                FROM archive.archival_metadata
                """;
            
            List<Map<String, Object>> complianceResults = jdbcTemplate.queryForList(complianceSql);
            if (!complianceResults.isEmpty()) {
                stats.putAll(complianceResults.get(0));
            }
            
            // Get job statistics
            String jobSql = """
                SELECT 
                    COUNT(*) as total_jobs,
                    COUNT(*) FILTER (WHERE status = 'COMPLETED') as successful_jobs,
                    COUNT(*) FILTER (WHERE status = 'FAILED') as failed_jobs,
                    COUNT(*) FILTER (WHERE status = 'RUNNING') as running_jobs
                FROM archive.archival_jobs
                WHERE created_at >= CURRENT_DATE - INTERVAL '30 days'
                """;
            
            List<Map<String, Object>> jobResults = jdbcTemplate.queryForList(jobSql);
            if (!jobResults.isEmpty()) {
                stats.putAll(jobResults.get(0));
            }
            
            stats.put("generated_at", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Failed to get archival statistics", e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }

    // =====================================================================
    // DATA RESTORATION
    // =====================================================================

    /**
     * Restore archived data for compliance or investigation purposes
     */
    public List<Map<String, Object>> restoreArchivedData(
            String tablePattern,
            LocalDate startDate,
            LocalDate endDate,
            String restoreSchema) {
        
        if (dryRun) {
            log.info("DRY RUN: Would restore data for pattern {} from {} to {}", 
                    tablePattern, startDate, endDate);
            return Collections.emptyList();
        }
        
        String sql = "SELECT * FROM archive.restore_archived_data(?, ?, ?, ?)";
        
        try {
            List<Map<String, Object>> restored = jdbcTemplate.queryForList(sql, 
                tablePattern, startDate, endDate, restoreSchema);
            
            log.info("Restored {} archived tables to schema {}", restored.size(), restoreSchema);
            return restored;
            
        } catch (Exception e) {
            log.error("Failed to restore archived data", e);
            throw new ArchivalException("Data restoration failed", e);
        }
    }

    /**
     * Create emergency backup before archival
     */
    public boolean createEmergencyBackup(String tableName) {
        if (dryRun) {
            log.info("DRY RUN: Would create emergency backup for {}", tableName);
            return true;
        }
        
        try {
            // Validate table name to prevent SQL injection
            if (!isSafeSqlIdentifier(tableName)) {
                throw new IllegalArgumentException("Invalid table name: " + tableName);
            }
            
            String backupTableName = tableName + "_emergency_backup_" + 
                                   LocalDate.now().toString().replace("-", "");
            
            // Use safe SQL utilities for proper escaping
            String sql = "CREATE TABLE " + quoteTableName(backupTableName) + 
                        " AS SELECT * FROM " + quoteTableName(tableName);
            
            jdbcTemplate.execute(sql);
            log.info("Created emergency backup table: {}", backupTableName);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to create emergency backup for {}", tableName, e);
            return false;
        }
    }

    // =====================================================================
    // PRIVATE HELPER METHODS
    // =====================================================================

    private int simulateTransactionArchival() {
        // Simulate archival by counting partitions that would be archived
        LocalDate retentionDate = LocalDate.now().minusYears(transactionRetentionYears);
        
        String sql = """
            SELECT COUNT(*) FROM pg_tables 
            WHERE tablename LIKE 'transactions_y%m%' 
            AND schemaname = 'public'
            """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count / 2 : 0; // Simulate that half would be archived
    }

    private int simulateAuditArchival() {
        LocalDate retentionDate = LocalDate.now().minusYears(auditRetentionYears);
        
        String sql = """
            SELECT COUNT(*) FROM pg_tables 
            WHERE tablename LIKE 'audit_events_w%' 
            AND schemaname = 'public'
            """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count / 3 : 0; // Simulate that third would be archived
    }

    private int simulateCleanup() {
        String sql = """
            SELECT COUNT(*) FROM archive.archival_metadata 
            WHERE retention_expires_at <= CURRENT_TIMESTAMP
            """;
        
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0; // Archive schema might not exist in dev/test
        }
    }

    private Map<String, Object> calculateComplianceSummary(List<Map<String, Object>> complianceData) {
        Map<String, Object> summary = new HashMap<>();
        
        long totalRecords = complianceData.stream()
            .mapToLong(row -> (Long) row.getOrDefault("total_records_archived", 0L))
            .sum();
        
        double totalSizeMb = complianceData.stream()
            .mapToDouble(row -> ((Number) row.getOrDefault("total_size_mb", 0)).doubleValue())
            .sum();
        
        long compliantTables = complianceData.stream()
            .filter(row -> "COMPLIANT".equals(row.get("retention_status")))
            .count();
        
        summary.put("total_records_archived", totalRecords);
        summary.put("total_size_mb", totalSizeMb);
        summary.put("total_tables", complianceData.size());
        summary.put("compliant_tables", compliantTables);
        summary.put("compliance_percentage", 
                   complianceData.isEmpty() ? 0 : (double) compliantTables / complianceData.size() * 100);
        
        return summary;
    }

    private void sendMaintenanceNotification(Map<String, Object> results, boolean success) {
        // Implementation would send notifications via email, Slack, etc.
        // For now, just log
        if (success) {
            log.info("Maintenance notification: Success - {}", results);
        } else {
            log.error("Maintenance notification: Failure - {}", results);
        }
    }

    private void handleComplianceReport(Map<String, Object> report) {
        // Implementation would store report in compliance system or send to stakeholders
        log.info("Compliance report generated with {} items", 
                ((List<?>) report.getOrDefault("compliance_data", Collections.emptyList())).size());
    }

    private void updateStorageMetrics() {
        // Implementation would update monitoring metrics for storage usage
        Map<String, Object> stats = getArchivalStatistics();
        log.info("Updated storage metrics: {}", stats);
    }

    // =====================================================================
    // EXCEPTION HANDLING
    // =====================================================================

    public static class ArchivalException extends RuntimeException {
        public ArchivalException(String message) {
            super(message);
        }
        
        public ArchivalException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // =====================================================================
    // CONFIGURATION METHODS
    // =====================================================================

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
        log.info("Archival service dry run mode: {}", dryRun);
    }

    public boolean isArchivalEnabled() {
        return archivalEnabled;
    }

    public boolean isCleanupEnabled() {
        return cleanupEnabled;
    }

    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("archival_enabled", archivalEnabled);
        config.put("cleanup_enabled", cleanupEnabled);
        config.put("transaction_retention_years", transactionRetentionYears);
        config.put("audit_retention_years", auditRetentionYears);
        config.put("batch_size", batchSize);
        config.put("dry_run", dryRun);
        return config;
    }
}