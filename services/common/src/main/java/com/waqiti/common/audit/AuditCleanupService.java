package com.waqiti.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for cleaning up old audit events based on retention policies
 * Ensures compliance with data retention requirements while maintaining performance
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditCleanupService {

    private final AuditEventRepository auditEventRepository;

    @Value("${audit.retention.days:2555}") // 7 years default
    private int retentionDays;

    @Value("${audit.retention.security.days:3650}") // 10 years for security events
    private int securityRetentionDays;

    @Value("${audit.retention.compliance.days:3650}") // 10 years for compliance events
    private int complianceRetentionDays;

    @Value("${audit.retention.transaction.days:2555}") // 7 years for transaction events
    private int transactionRetentionDays;

    @Value("${audit.cleanup.batch.size:1000}")
    private int batchSize;

    @Value("${audit.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Value("${audit.cleanup.dry.run:false}")
    private boolean dryRun;

    private final AtomicLong totalDeletedEvents = new AtomicLong(0);
    private final AtomicLong lastCleanupDuration = new AtomicLong(0);

    /**
     * Scheduled cleanup task - runs daily at 2 AM
     */
    @Scheduled(cron = "${audit.cleanup.cron:0 0 2 * * ?}")
    public void performScheduledCleanup() {
        if (!cleanupEnabled) {
            log.debug("Audit cleanup is disabled");
            return;
        }

        log.info("Starting scheduled audit cleanup task");
        long startTime = System.currentTimeMillis();

        try {
            CleanupResult result = performCleanup();
            long duration = System.currentTimeMillis() - startTime;
            lastCleanupDuration.set(duration);

            log.info("Scheduled audit cleanup completed in {} ms. Deleted {} events across {} categories",
                    duration, result.getTotalDeleted(), result.getCategoriesProcessed());

        } catch (Exception e) {
            log.error("Scheduled audit cleanup failed", e);
        }
    }

    /**
     * Perform comprehensive audit cleanup based on retention policies
     */
    @Transactional
    public CleanupResult performCleanup() {
        log.info("Starting audit cleanup with retention: {} days, dry run: {}", retentionDays, dryRun);

        CleanupResult result = new CleanupResult();
        long totalDeleted = 0;

        try {
            // Clean up general audit events
            long generalDeleted = cleanupGeneralAuditEvents();
            result.addCategoryResult("general", generalDeleted);
            totalDeleted += generalDeleted;

            // Clean up security events (longer retention)
            long securityDeleted = cleanupSecurityEvents();
            result.addCategoryResult("security", securityDeleted);
            totalDeleted += securityDeleted;

            // Clean up compliance events (longer retention)
            long complianceDeleted = cleanupComplianceEvents();
            result.addCategoryResult("compliance", complianceDeleted);
            totalDeleted += complianceDeleted;

            // Clean up transaction events
            long transactionDeleted = cleanupTransactionEvents();
            result.addCategoryResult("transaction", transactionDeleted);
            totalDeleted += transactionDeleted;

            // Update total statistics
            this.totalDeletedEvents.addAndGet(totalDeleted);
            result.setTotalDeleted(totalDeleted);
            result.setDryRun(dryRun);

            log.info("Audit cleanup completed. Total events processed for deletion: {}", totalDeleted);

        } catch (Exception e) {
            log.error("Error during audit cleanup", e);
            result.setError(e.getMessage());
        }

        return result;
    }

    /**
     * Clean up general audit events based on standard retention
     */
    private long cleanupGeneralAuditEvents() {
        Instant cutoffTime = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        log.info("Cleaning up general audit events older than {} (retention: {} days)", cutoffTime, retentionDays);

        if (dryRun) {
            long count = auditEventRepository.countByTimestampBefore(cutoffTime);
            log.info("DRY RUN: Would delete {} general audit events", count);
            return count;
        }

        try {
            // Use batch deletion to avoid memory issues
            return performBatchDeletion(cutoffTime, "general");
        } catch (Exception e) {
            log.error("Error cleaning up general audit events", e);
            return 0;
        }
    }

    /**
     * Clean up security events with extended retention
     */
    private long cleanupSecurityEvents() {
        Instant cutoffTime = Instant.now().minus(securityRetentionDays, ChronoUnit.DAYS);
        log.info("Cleaning up security events older than {} (retention: {} days)", cutoffTime, securityRetentionDays);

        if (dryRun) {
            Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE);
            long count = auditEventRepository.findSecurityEventsOrderByTimestampDesc(pageable)
                    .stream()
                    .mapToLong(event -> event.getTimestamp().isBefore(cutoffTime) ? 1 : 0)
                    .sum();
            log.info("DRY RUN: Would delete {} security events", count);
            return count;
        }

        // Security events should be handled more carefully
        return deleteSecurityEventsBatch(cutoffTime);
    }

    /**
     * Clean up compliance events with extended retention
     */
    private long cleanupComplianceEvents() {
        Instant cutoffTime = Instant.now().minus(complianceRetentionDays, ChronoUnit.DAYS);
        log.info("Cleaning up compliance events older than {} (retention: {} days)", cutoffTime, complianceRetentionDays);

        if (dryRun) {
            Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE);
            long count = auditEventRepository.findComplianceEventsOrderByTimestampDesc(pageable)
                    .stream()
                    .mapToLong(event -> event.getTimestamp().isBefore(cutoffTime) ? 1 : 0)
                    .sum();
            log.info("DRY RUN: Would delete {} compliance events", count);
            return count;
        }

        return deleteComplianceEventsBatch(cutoffTime);
    }

    /**
     * Clean up transaction events
     */
    private long cleanupTransactionEvents() {
        Instant cutoffTime = Instant.now().minus(transactionRetentionDays, ChronoUnit.DAYS);
        log.info("Cleaning up transaction events older than {} (retention: {} days)", cutoffTime, transactionRetentionDays);

        if (dryRun) {
            Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE);
            long count = auditEventRepository.findTransactionEventsOrderByTimestampDesc(pageable)
                    .stream()
                    .mapToLong(event -> event.getTimestamp().isBefore(cutoffTime) ? 1 : 0)
                    .sum();
            log.info("DRY RUN: Would delete {} transaction events", count);
            return count;
        }

        return deleteTransactionEventsBatch(cutoffTime);
    }

    /**
     * Perform batch deletion to handle large datasets efficiently
     */
    private long performBatchDeletion(Instant cutoffTime, String category) {
        long totalDeleted = 0;
        boolean hasMore = true;

        while (hasMore) {
            try {
                // Get a batch of old events
                Pageable pageable = PageRequest.of(0, batchSize);
                var oldEvents = auditEventRepository.findByTimestampBetweenOrderByTimestampDesc(
                        Instant.EPOCH, cutoffTime, pageable);

                if (oldEvents.isEmpty()) {
                    hasMore = false;
                } else {
                    // Delete the batch
                    auditEventRepository.deleteAll(oldEvents.getContent());
                    int batchDeleted = oldEvents.getContent().size();
                    totalDeleted += batchDeleted;

                    log.debug("Deleted batch of {} {} audit events", batchDeleted, category);

                    // Check if we've processed all events in this batch
                    hasMore = oldEvents.hasNext();
                }

                // Add delay to prevent overwhelming the database
                Thread.sleep(100);

            } catch (Exception e) {
                log.error("Error during batch deletion for category: {}", category, e);
                hasMore = false;
            }
        }

        return totalDeleted;
    }

    /**
     * Delete security events in batches with extra logging
     */
    private long deleteSecurityEventsBatch(Instant cutoffTime) {
        long totalDeleted = 0;
        
        try {
            Pageable pageable = PageRequest.of(0, batchSize);
            var securityEvents = auditEventRepository.findSecurityEventsOrderByTimestampDesc(pageable);
            
            var eventsToDelete = securityEvents.getContent().stream()
                    .filter(event -> event.getTimestamp().isBefore(cutoffTime))
                    .toList();

            if (!eventsToDelete.isEmpty()) {
                log.info("Deleting {} expired security events", eventsToDelete.size());
                auditEventRepository.deleteAll(eventsToDelete);
                totalDeleted = eventsToDelete.size();
            }

        } catch (Exception e) {
            log.error("Error deleting security events", e);
        }

        return totalDeleted;
    }

    /**
     * Delete compliance events in batches with extra logging
     */
    private long deleteComplianceEventsBatch(Instant cutoffTime) {
        long totalDeleted = 0;
        
        try {
            Pageable pageable = PageRequest.of(0, batchSize);
            var complianceEvents = auditEventRepository.findComplianceEventsOrderByTimestampDesc(pageable);
            
            var eventsToDelete = complianceEvents.getContent().stream()
                    .filter(event -> event.getTimestamp().isBefore(cutoffTime))
                    .toList();

            if (!eventsToDelete.isEmpty()) {
                log.info("Deleting {} expired compliance events", eventsToDelete.size());
                auditEventRepository.deleteAll(eventsToDelete);
                totalDeleted = eventsToDelete.size();
            }

        } catch (Exception e) {
            log.error("Error deleting compliance events", e);
        }

        return totalDeleted;
    }

    /**
     * Delete transaction events in batches
     */
    private long deleteTransactionEventsBatch(Instant cutoffTime) {
        long totalDeleted = 0;
        
        try {
            Pageable pageable = PageRequest.of(0, batchSize);
            var transactionEvents = auditEventRepository.findTransactionEventsOrderByTimestampDesc(pageable);
            
            var eventsToDelete = transactionEvents.getContent().stream()
                    .filter(event -> event.getTimestamp().isBefore(cutoffTime))
                    .toList();

            if (!eventsToDelete.isEmpty()) {
                log.info("Deleting {} expired transaction events", eventsToDelete.size());
                auditEventRepository.deleteAll(eventsToDelete);
                totalDeleted = eventsToDelete.size();
            }

        } catch (Exception e) {
            log.error("Error deleting transaction events", e);
        }

        return totalDeleted;
    }

    /**
     * Get cleanup statistics
     */
    public Map<String, Object> getCleanupStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cleanupEnabled", cleanupEnabled);
        stats.put("dryRun", dryRun);
        stats.put("retentionDays", retentionDays);
        stats.put("securityRetentionDays", securityRetentionDays);
        stats.put("complianceRetentionDays", complianceRetentionDays);
        stats.put("transactionRetentionDays", transactionRetentionDays);
        stats.put("batchSize", batchSize);
        stats.put("totalDeletedEvents", totalDeletedEvents.get());
        stats.put("lastCleanupDurationMs", lastCleanupDuration.get());
        
        // Calculate next scheduled cleanup
        stats.put("nextScheduledCleanup", "Daily at 2:00 AM");
        
        return stats;
    }

    /**
     * Manually trigger cleanup (for testing or administrative purposes)
     */
    public CompletableFuture<CleanupResult> triggerManualCleanup() {
        log.info("Manual audit cleanup triggered");
        return CompletableFuture.supplyAsync(this::performCleanup);
    }

    /**
     * Update cleanup configuration
     */
    public void updateCleanupConfiguration(int retentionDays, boolean enabled, boolean dryRun) {
        this.retentionDays = retentionDays;
        this.cleanupEnabled = enabled;
        this.dryRun = dryRun;
        
        log.info("Updated cleanup configuration: retention={} days, enabled={}, dryRun={}", 
                retentionDays, enabled, dryRun);
    }

    /**
     * Result class for cleanup operations
     */
    public static class CleanupResult {
        private long totalDeleted = 0;
        private final Map<String, Long> categoryResults = new HashMap<>();
        private boolean dryRun = false;
        private String error;
        private final Instant completedAt = Instant.now();

        public void addCategoryResult(String category, long deleted) {
            categoryResults.put(category, deleted);
        }

        // Getters and setters
        public long getTotalDeleted() { return totalDeleted; }
        public void setTotalDeleted(long totalDeleted) { this.totalDeleted = totalDeleted; }
        
        public Map<String, Long> getCategoryResults() { return categoryResults; }
        public int getCategoriesProcessed() { return categoryResults.size(); }
        
        public boolean isDryRun() { return dryRun; }
        public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public Instant getCompletedAt() { return completedAt; }
        
        public boolean hasError() { return error != null; }
    }
}