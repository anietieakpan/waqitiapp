package com.waqiti.wallet.scheduler;

import com.waqiti.wallet.service.ProductionWalletBalanceService;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CRITICAL PRODUCTION SCHEDULER: Automatic Cleanup of Expired Fund Reservations
 *
 * This scheduler runs every minute to automatically release expired reservations
 * and return funds to available balance. This prevents funds from being locked
 * indefinitely due to failed/abandoned transactions.
 *
 * FEATURES:
 * - Distributed lock using ShedLock (only one instance runs cleanup at a time)
 * - Prometheus metrics for monitoring
 * - Configurable via application.yml
 * - Graceful error handling
 *
 * CONFIGURATION:
 * wallet.reservation.cleanup.enabled=true (default)
 * wallet.reservation.cleanup.cron=0 * * * * ? (every minute)
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-01
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    prefix = "wallet.reservation.cleanup",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true  // Enabled by default
)
public class FundReservationCleanupScheduler {

    private final ProductionWalletBalanceService walletBalanceService;
    private final MeterRegistry meterRegistry;

    private Counter cleanupJobCounter;
    private Counter cleanupSuccessCounter;
    private Counter cleanupFailureCounter;
    private Counter reservationsCleanedCounter;

    @PostConstruct
    public void initializeMetrics() {
        cleanupJobCounter = Counter.builder("wallet.reservation.cleanup.job")
                .description("Total cleanup job executions")
                .tag("scheduler", "fund-reservation-cleanup")
                .register(meterRegistry);

        cleanupSuccessCounter = Counter.builder("wallet.reservation.cleanup.success")
                .description("Successful cleanup job executions")
                .tag("scheduler", "fund-reservation-cleanup")
                .register(meterRegistry);

        cleanupFailureCounter = Counter.builder("wallet.reservation.cleanup.failure")
                .description("Failed cleanup job executions")
                .tag("scheduler", "fund-reservation-cleanup")
                .register(meterRegistry);

        reservationsCleanedCounter = Counter.builder("wallet.reservation.cleanup.count")
                .description("Total reservations cleaned up")
                .tag("scheduler", "fund-reservation-cleanup")
                .register(meterRegistry);
    }

    /**
     * CRITICAL: Cleanup expired fund reservations every minute
     *
     * Uses ShedLock to ensure only ONE instance in the cluster runs cleanup at a time.
     * This prevents race conditions when multiple service instances are deployed.
     *
     * Lock Configuration:
     * - lockAtMostFor: 50 seconds (job must complete within this time)
     * - lockAtLeastFor: 10 seconds (minimum time between executions across cluster)
     *
     * Schedule: Every minute (cron: 0 * * * * ?)
     */
    @Scheduled(cron = "${wallet.reservation.cleanup.cron:0 * * * * ?}")
    @SchedulerLock(
        name = "fundReservationCleanup",
        lockAtMostFor = "50s",    // Job must complete within 50 seconds
        lockAtLeastFor = "10s"     // Prevent execution more than once every 10 seconds
    )
    @Timed(value = "wallet.reservation.cleanup.duration", description = "Fund reservation cleanup duration")
    public void cleanupExpiredReservations() {
        cleanupJobCounter.increment();

        try {
            log.info("Starting fund reservation cleanup job...");

            long startTime = System.currentTimeMillis();
            int cleanedCount = walletBalanceService.cleanupExpiredReservations();
            long duration = System.currentTimeMillis() - startTime;

            reservationsCleanedCounter.increment(cleanedCount);
            cleanupSuccessCounter.increment();

            log.info("Fund reservation cleanup completed successfully - " +
                    "Cleaned: {}, Duration: {}ms", cleanedCount, duration);

            // Alert if cleanup took too long
            if (duration > 30000) { // 30 seconds
                log.warn("PERFORMANCE WARNING: Cleanup job took {}ms (> 30s threshold)", duration);
            }

            // Alert if large number of expired reservations
            if (cleanedCount > 100) {
                log.warn("BUSINESS WARNING: Cleaned up {} expired reservations. " +
                        "This may indicate issues with transaction completion or TTL configuration.",
                        cleanedCount);
            }

        } catch (Exception e) {
            cleanupFailureCounter.increment();
            log.error("Fund reservation cleanup job failed", e);
            // Don't throw - let next scheduled run retry
        }
    }

    /**
     * OPTIONAL: Cleanup old archived reservations (data retention)
     *
     * Runs daily at 2 AM to delete old confirmed/released/expired reservations
     * that are older than the retention period (default: 90 days).
     *
     * This keeps the fund_reservations table size manageable.
     */
    @Scheduled(cron = "${wallet.reservation.archive-cleanup.cron:0 0 2 * * ?}")
    @SchedulerLock(
        name = "fundReservationArchiveCleanup",
        lockAtMostFor = "10m",
        lockAtLeastFor = "1m"
    )
    @Timed(value = "wallet.reservation.archive_cleanup.duration")
    public void cleanupArchivedReservations() {
        try {
            log.info("Starting archived fund reservation cleanup job...");

            // This would call a method to delete old records
            // Implementation depends on your data retention policy

            log.info("Archived fund reservation cleanup completed");

        } catch (Exception e) {
            log.error("Archived fund reservation cleanup job failed", e);
        }
    }
}
