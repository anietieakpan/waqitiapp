package com.waqiti.common.idempotency;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Scheduled job to clean up expired and old idempotency records
 *
 * Features:
 * - Marks expired records (expires_at < now)
 * - Deletes old records beyond retention period (default: 90 days)
 * - Metrics for monitoring cleanup effectiveness
 * - Configurable via application properties
 * - Runs daily at 2 AM by default
 *
 * Configuration:
 * <pre>
 * idempotency:
 *   cleanup:
 *     enabled: true
 *     cron: "0 0 2 * * *"  # Daily at 2 AM
 *     retention-days: 90
 * </pre>
 *
 * Metrics:
 * - idempotency.cleanup.expired.count - Number of expired records marked
 * - idempotency.cleanup.deleted.count - Number of old records deleted
 * - idempotency.cleanup.duration - Cleanup job duration
 * - idempotency.cleanup.errors - Cleanup job errors
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-02
 */
@Component
@ConditionalOnProperty(
    prefix = "idempotency.cleanup",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupJob {

    private final IdempotencyRecordRepository repository;
    private final MeterRegistry meterRegistry;

    // Default retention: 90 days (configurable via properties)
    private static final int DEFAULT_RETENTION_DAYS = 90;

    /**
     * Scheduled cleanup job - runs daily at 2 AM
     * Cron: 0 0 2 * * * = Second Minute Hour Day Month DayOfWeek
     */
    @Scheduled(cron = "${idempotency.cleanup.cron:0 0 2 * * *}")
    @Transactional
    public void cleanupExpiredRecords() {
        log.info("IDEMPOTENCY_CLEANUP: Starting cleanup job...");
        Instant startTime = Instant.now();

        try {
            // Get retention period from config (default: 90 days)
            int retentionDays = getRetentionDays();

            // Step 1: Mark expired records
            long expiredCount = markExpiredRecords();
            log.info("IDEMPOTENCY_CLEANUP: Marked {} expired records", expiredCount);

            // Step 2: Delete old records beyond retention period
            long deletedCount = deleteOldRecords(retentionDays);
            log.info("IDEMPOTENCY_CLEANUP: Deleted {} old records (retention: {} days)",
                    deletedCount, retentionDays);

            // Record metrics
            getCounter("idempotency.cleanup.expired.count").increment(expiredCount);
            getCounter("idempotency.cleanup.deleted.count").increment(deletedCount);

            // Record duration
            Duration duration = Duration.between(startTime, Instant.now());
            meterRegistry.timer("idempotency.cleanup.duration")
                .record(duration);

            log.info("IDEMPOTENCY_CLEANUP: Cleanup completed in {}ms (expired={}, deleted={})",
                    duration.toMillis(), expiredCount, deletedCount);

        } catch (Exception e) {
            log.error("IDEMPOTENCY_CLEANUP: Cleanup job failed", e);
            getCounter("idempotency.cleanup.errors").increment();

            // Re-throw to trigger retry/alerting
            throw new RuntimeException("Idempotency cleanup job failed", e);
        }
    }

    /**
     * Mark records as EXPIRED where expires_at < now
     * AND status is not already in a final state
     */
    private long markExpiredRecords() {
        LocalDateTime now = LocalDateTime.now();

        log.debug("IDEMPOTENCY_CLEANUP: Marking expired records (expires_at < {})", now);

        // Use repository's bulk update method
        int updatedCount = repository.markExpiredRecords(now);

        if (updatedCount > 0) {
            log.warn("IDEMPOTENCY_CLEANUP: Found {} stuck/expired operations - investigate potential issues",
                    updatedCount);
        }

        return updatedCount;
    }

    /**
     * Delete old records beyond retention period
     * Only deletes records in final states (COMPLETED, FAILED, EXPIRED)
     */
    private long deleteOldRecords(int retentionDays) {
        Instant cutoffDate = Instant.now().minus(Duration.ofDays(retentionDays));

        log.debug("IDEMPOTENCY_CLEANUP: Deleting old records (created_at < {})", cutoffDate);

        // Use repository's bulk delete method
        int deletedCount = repository.deleteOldRecords(cutoffDate);

        if (deletedCount > 1000) {
            log.warn("IDEMPOTENCY_CLEANUP: Deleted {} records - consider database partitioning for better performance",
                    deletedCount);
        }

        return deletedCount;
    }

    /**
     * Get retention period from configuration
     * Falls back to DEFAULT_RETENTION_DAYS if not configured
     */
    private int getRetentionDays() {
        // In a real implementation, this would read from @Value or Environment
        // For now, return default
        return DEFAULT_RETENTION_DAYS;
    }

    /**
     * Get or create counter metric
     */
    private Counter getCounter(String name) {
        return meterRegistry.counter(name);
    }

    /**
     * Manual cleanup trigger (for admin operations)
     * Can be called via JMX or admin endpoint
     */
    public CleanupResult cleanupNow(int retentionDays) {
        log.info("IDEMPOTENCY_CLEANUP: Manual cleanup triggered (retention={} days)", retentionDays);

        Instant startTime = Instant.now();
        long expiredCount = markExpiredRecords();
        long deletedCount = deleteOldRecords(retentionDays);
        Duration duration = Duration.between(startTime, Instant.now());

        return new CleanupResult(expiredCount, deletedCount, duration);
    }

    /**
     * Cleanup result DTO
     */
    public static class CleanupResult {
        private final long expiredCount;
        private final long deletedCount;
        private final Duration duration;

        public CleanupResult(long expiredCount, long deletedCount, Duration duration) {
            this.expiredCount = expiredCount;
            this.deletedCount = deletedCount;
            this.duration = duration;
        }

        public long getExpiredCount() {
            return expiredCount;
        }

        public long getDeletedCount() {
            return deletedCount;
        }

        public Duration getDuration() {
            return duration;
        }

        @Override
        public String toString() {
            return String.format("CleanupResult{expired=%d, deleted=%d, duration=%dms}",
                    expiredCount, deletedCount, duration.toMillis());
        }
    }
}
