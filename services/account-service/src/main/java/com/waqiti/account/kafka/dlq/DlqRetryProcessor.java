package com.waqiti.account.kafka.dlq;

import com.waqiti.account.entity.DlqRetryRecord;
import com.waqiti.account.entity.DlqRetryRecord.RetryStatus;
import com.waqiti.account.entity.ManualReviewRecord;
import com.waqiti.account.entity.PermanentFailureRecord;
import com.waqiti.account.repository.DlqRetryRepository;
import com.waqiti.account.repository.ManualReviewRepository;
import com.waqiti.account.repository.PermanentFailureRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scheduled processor for DLQ retry queue
 *
 * <p>Processes pending retries with exponential backoff strategy:</p>
 * <ul>
 *   <li>Runs every 10 seconds (configurable)</li>
 *   <li>Processes up to 100 retries per batch (configurable)</li>
 *   <li>Invokes handler-specific recovery logic</li>
 *   <li>Escalates to manual review after max retries</li>
 *   <li>Records permanent failures for compliance audit</li>
 * </ul>
 *
 * <h3>Retry Flow:</h3>
 * <pre>
 * 1. Query pending retries (status=PENDING, nextRetryAt <= now)
 * 2. Mark as RETRYING to prevent duplicate processing
 * 3. Invoke handler.attemptRecovery()
 * 4. On success: Mark SUCCESS, schedule for deletion
 * 5. On failure:
 *    - If attempts < max: Increment retry with exponential backoff
 *    - If attempts >= max: Escalate to manual review
 * </pre>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class DlqRetryProcessor {

    @Autowired
    private DlqRetryRepository retryRepository;

    @Autowired
    private ManualReviewRepository manualReviewRepository;

    @Autowired
    private PermanentFailureRepository permanentFailureRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired(required = false)
    private DlqAlertService alertService;

    @Autowired(required = false)
    private DlqMetricsService metricsService;

    @Value("${dlq.retry.batch-size:100}")
    private int batchSize;

    @Value("${dlq.retry.success-retention-hours:24}")
    private int successRetentionHours;

    @Value("${dlq.retry.backoff-multiplier:2}")
    private int backoffMultiplier;

    @Value("${dlq.retry.initial-backoff-ms:5000}")
    private long initialBackoffMs;

    // Handler cache (handlerName -> BaseDlqHandler instance)
    private final Map<String, BaseDlqHandler<?>> handlerCache = new ConcurrentHashMap<>();

    // Metrics
    private Counter retriesProcessed;
    private Counter retriesSucceeded;
    private Counter retriesFailed;
    private Counter retriesEscalated;
    private Timer retryProcessingTime;

    @PostConstruct
    public void init() {
        initializeMetrics();
        log.info("DlqRetryProcessor initialized - batchSize={}, successRetention={}h",
            batchSize, successRetentionHours);
    }

    /**
     * Process pending retries every 10 seconds
     */
    @Scheduled(fixedDelayString = "${dlq.retry.polling-interval-ms:10000}")
    @Transactional
    public void processPendingRetries() {
        try {
            log.debug("Processing pending DLQ retries - batchSize={}", batchSize);

            // Query pending retries
            List<DlqRetryRecord> pendingRetries = retryRepository.findPendingRetries(
                LocalDateTime.now());

            if (pendingRetries.isEmpty()) {
                log.debug("No pending retries found");
                return;
            }

            log.info("Found {} pending retries to process", pendingRetries.size());

            // Process up to batch size
            int processed = 0;
            for (DlqRetryRecord retry : pendingRetries) {
                if (processed >= batchSize) {
                    log.info("Batch size limit reached ({}), remaining retries will process next cycle",
                        batchSize);
                    break;
                }

                processRetry(retry);
                processed++;
            }

            log.info("Processed {} retries in this cycle", processed);

        } catch (Exception e) {
            log.error("Critical error in DLQ retry processor", e);

            // Alert on processor failure
            if (alertService != null) {
                alertService.sendCriticalAlert(
                    "DLQ Retry Processor Failure",
                    "Scheduled retry processor failed: " + e.getMessage(),
                    Map.of("error", e.getClass().getSimpleName()));
            }
        }
    }

    /**
     * Process single retry record
     */
    @Transactional
    public void processRetry(DlqRetryRecord retry) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.info("[Retry-{}] Processing retry - attempt={}/{}, handler={}, correlationId={}",
                retry.getId(), retry.getRetryAttempt() + 1, retry.getMaxRetryAttempts(),
                retry.getHandlerName(), retry.getCorrelationId());

            // Mark as RETRYING to prevent duplicate processing
            retry.setStatus(RetryStatus.RETRYING);
            retryRepository.save(retry);

            // Get handler instance
            BaseDlqHandler<?> handler = getHandler(retry.getHandlerName());
            if (handler == null) {
                log.error("[Retry-{}] Handler not found: {}", retry.getId(), retry.getHandlerName());
                escalateToManualReview(retry, "Handler not found: " + retry.getHandlerName());
                return;
            }

            // Attempt recovery
            boolean success;
            try {
                success = handler.attemptRecovery(retry);
            } catch (Exception e) {
                log.error("[Retry-{}] Recovery attempt threw exception", retry.getId(), e);
                success = false;
            }

            if (success) {
                handleSuccessfulRecovery(retry, sample);
            } else {
                handleFailedRecovery(retry, sample);
            }

            retriesProcessed.increment();

        } catch (Exception e) {
            log.error("[Retry-{}] Critical error processing retry", retry.getId(), e);

            // Mark as failed and escalate
            try {
                retry.setStatus(RetryStatus.FAILED);
                retryRepository.save(retry);
                escalateToManualReview(retry, "Critical error in retry processor: " + e.getMessage());
            } catch (Exception saveError) {
                log.error("[Retry-{}] Failed to save error state", retry.getId(), saveError);
            }
        } finally {
            sample.stop(retryProcessingTime);
        }
    }

    /**
     * Handle successful recovery
     */
    private void handleSuccessfulRecovery(DlqRetryRecord retry, Timer.Sample sample) {
        log.info("[Retry-{}] Recovery successful - attempt={}/{}, handler={}",
            retry.getId(), retry.getRetryAttempt() + 1, retry.getMaxRetryAttempts(),
            retry.getHandlerName());

        // Mark as SUCCESS
        retry.markSuccess();
        retryRepository.save(retry);

        retriesSucceeded.increment();

        // Record metrics
        if (metricsService != null) {
            long durationMs = (long) sample.stop(Timer.builder("dlq.retry.recovery.duration")
                .tag("handler", retry.getHandlerName())
                .register(meterRegistry)) / 1_000_000;

            metricsService.recordRecoverySuccess(
                retry.getHandlerName(),
                retry.getRetryAttempt() + 1,
                durationMs);
        }

        // Alert if recovery took multiple attempts
        if (retry.getRetryAttempt() >= 2 && alertService != null) {
            alertService.sendInfoNotification(
                "DLQ Recovery Success After Multiple Attempts",
                String.format("Message recovered after %d attempts - handler=%s",
                    retry.getRetryAttempt() + 1, retry.getHandlerName()),
                Map.of("handler", retry.getHandlerName(),
                       "attempts", String.valueOf(retry.getRetryAttempt() + 1),
                       "correlationId", retry.getCorrelationId()));
        }
    }

    /**
     * Handle failed recovery
     */
    private void handleFailedRecovery(DlqRetryRecord retry, Timer.Sample sample) {
        int nextAttempt = retry.getRetryAttempt() + 1;

        log.warn("[Retry-{}] Recovery failed - attempt={}/{}, handler={}",
            retry.getId(), nextAttempt, retry.getMaxRetryAttempts(), retry.getHandlerName());

        // Check if max retries exceeded
        if (nextAttempt >= retry.getMaxRetryAttempts()) {
            log.error("[Retry-{}] Max retries exceeded, escalating to manual review",
                retry.getId());

            retry.markFailed("Max retry attempts exceeded");
            retryRepository.save(retry);

            escalateToManualReview(retry, "Max retry attempts exceeded");

            retriesFailed.increment();
            retriesEscalated.increment();

            // Record metrics
            if (metricsService != null) {
                metricsService.recordRecoveryFailure(
                    retry.getHandlerName(),
                    retry.getMaxRetryAttempts(),
                    "Max retries exceeded");
            }

        } else {
            // Schedule next retry with exponential backoff
            long nextBackoffMs = calculateBackoff(nextAttempt);
            retry.incrementRetryAttempt(nextBackoffMs);
            retry.setStatus(RetryStatus.PENDING);
            retryRepository.save(retry);

            log.info("[Retry-{}] Scheduled next retry - attempt={}/{}, backoff={}ms, nextRetry={}",
                retry.getId(), nextAttempt + 1, retry.getMaxRetryAttempts(),
                nextBackoffMs, retry.getNextRetryAt());

            // Alert if on final retry
            if (nextAttempt == retry.getMaxRetryAttempts() - 1 && alertService != null) {
                alertService.sendWarningAlert(
                    "DLQ Retry - Final Attempt",
                    String.format("Message on final retry attempt - handler=%s",
                        retry.getHandlerName()),
                    Map.of("handler", retry.getHandlerName(),
                           "attempt", String.valueOf(nextAttempt + 1),
                           "correlationId", retry.getCorrelationId()));
            }
        }
    }

    /**
     * Escalate to manual review queue
     */
    private void escalateToManualReview(DlqRetryRecord retry, String reason) {
        log.warn("[Retry-{}] Escalating to manual review - reason={}", retry.getId(), reason);

        try {
            ManualReviewRecord reviewRecord = ManualReviewRecord.builder()
                .originalTopic(retry.getOriginalTopic())
                .originalPartition(retry.getOriginalPartition())
                .originalOffset(retry.getOriginalOffset())
                .originalKey(retry.getOriginalKey())
                .payload(retry.getPayload())
                .exceptionMessage(retry.getExceptionMessage())
                .exceptionClass(retry.getExceptionClass())
                .exceptionStackTrace(retry.getExceptionStackTrace())
                .failedAt(retry.getFailedAt())
                .retryAttempts(retry.getRetryAttempt())
                .reviewReason(reason)
                .priority(ManualReviewRecord.ReviewPriority.HIGH)
                .status(ManualReviewRecord.ReviewStatus.PENDING)
                .handlerName(retry.getHandlerName())
                .correlationId(retry.getCorrelationId())
                .build();

            manualReviewRepository.save(reviewRecord);

            log.info("[Retry-{}] Escalated to manual review - reviewId={}, priority=HIGH",
                retry.getId(), reviewRecord.getId());

            // Alert on escalation
            if (alertService != null) {
                alertService.sendHighPriorityAlert(
                    "DLQ Escalated to Manual Review",
                    String.format("Retry failed after %d attempts - %s",
                        retry.getMaxRetryAttempts(), reason),
                    Map.of("handler", retry.getHandlerName(),
                           "topic", retry.getOriginalTopic(),
                           "correlationId", retry.getCorrelationId(),
                           "reviewId", reviewRecord.getId().toString()));
            }

        } catch (Exception e) {
            log.error("[Retry-{}] Failed to escalate to manual review", retry.getId(), e);

            // Last resort: create permanent failure
            createPermanentFailure(retry, "Failed to escalate: " + e.getMessage());
        }
    }

    /**
     * Create permanent failure record (last resort)
     */
    private void createPermanentFailure(DlqRetryRecord retry, String reason) {
        log.error("[Retry-{}] Creating permanent failure - reason={}", retry.getId(), reason);

        try {
            PermanentFailureRecord failureRecord = PermanentFailureRecord.builder()
                .originalTopic(retry.getOriginalTopic())
                .originalPartition(retry.getOriginalPartition())
                .originalOffset(retry.getOriginalOffset())
                .originalKey(retry.getOriginalKey())
                .payload(retry.getPayload())
                .failureReason(reason)
                .failureCategory(PermanentFailureRecord.FailureCategory.MAX_RETRIES_EXCEEDED)
                .exceptionMessage(retry.getExceptionMessage())
                .exceptionClass(retry.getExceptionClass())
                .exceptionStackTrace(retry.getExceptionStackTrace())
                .failedAt(retry.getFailedAt())
                .retryAttempts(retry.getRetryAttempt())
                .handlerName(retry.getHandlerName())
                .correlationId(retry.getCorrelationId())
                .businessImpact(PermanentFailureRecord.BusinessImpact.HIGH)
                .impactDescription("Could not recover after max retries and manual review escalation failed")
                .build();

            permanentFailureRepository.save(failureRecord);

            log.error("[Retry-{}] Permanent failure recorded - failureId={}",
                retry.getId(), failureRecord.getId());

            // Critical alert
            if (alertService != null) {
                alertService.sendCriticalAlert(
                    "DLQ Permanent Failure",
                    "Message permanently failed - could not escalate to manual review: " + reason,
                    Map.of("handler", retry.getHandlerName(),
                           "topic", retry.getOriginalTopic(),
                           "correlationId", retry.getCorrelationId(),
                           "failureId", failureRecord.getId().toString()));
            }

        } catch (Exception e) {
            log.error("[Retry-{}] Failed to create permanent failure record", retry.getId(), e);
        }
    }

    /**
     * Calculate exponential backoff delay
     */
    private long calculateBackoff(int retryAttempt) {
        return initialBackoffMs * (long) Math.pow(backoffMultiplier, retryAttempt);
    }

    /**
     * Get or cache handler instance
     */
    @SuppressWarnings("unchecked")
    private BaseDlqHandler<?> getHandler(String handlerName) {
        return handlerCache.computeIfAbsent(handlerName, name -> {
            try {
                // Try to find bean by name pattern
                String beanName = name + "DlqHandler";
                if (applicationContext.containsBean(beanName)) {
                    Object bean = applicationContext.getBean(beanName);
                    if (bean instanceof BaseDlqHandler) {
                        log.info("Cached DLQ handler: {}", beanName);
                        return (BaseDlqHandler<?>) bean;
                    }
                }

                log.warn("Handler not found in application context: {}", handlerName);
                return null;

            } catch (Exception e) {
                log.error("Error loading handler: {}", handlerName, e);
                return null;
            }
        });
    }

    /**
     * Cleanup successful retries older than retention period
     */
    @Scheduled(cron = "${dlq.retry.cleanup-cron:0 0 2 * * ?}")  // Daily at 2 AM
    @Transactional
    public void cleanupSuccessfulRetries() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusHours(successRetentionHours);

            log.info("Cleaning up successful retries older than {}", cutoffDate);

            int deleted = retryRepository.deleteByUpdatedAtBeforeAndStatusIn(
                cutoffDate,
                List.of(RetryStatus.SUCCESS, RetryStatus.CANCELLED));

            log.info("Deleted {} old retry records", deleted);

        } catch (Exception e) {
            log.error("Error during retry cleanup", e);
        }
    }

    /**
     * Mark SLA-breached manual reviews
     */
    @Scheduled(fixedDelayString = "${dlq.manual-review.sla-check-interval-ms:60000}")  // Every 1 minute
    @Transactional
    public void checkManualReviewSlas() {
        try {
            int breached = manualReviewRepository.markSlaBreached(LocalDateTime.now());

            if (breached > 0) {
                log.warn("Marked {} manual reviews as SLA breached", breached);

                // Alert on SLA breaches
                if (alertService != null) {
                    alertService.sendHighPriorityAlert(
                        "DLQ Manual Review SLA Breached",
                        String.format("%d manual reviews have breached their SLA", breached),
                        Map.of("count", String.valueOf(breached)));
                }
            }

        } catch (Exception e) {
            log.error("Error checking manual review SLAs", e);
        }
    }

    /**
     * Initialize metrics counters
     */
    private void initializeMetrics() {
        retriesProcessed = Counter.builder("dlq.retries.processed")
            .description("Total retries processed")
            .register(meterRegistry);

        retriesSucceeded = Counter.builder("dlq.retries.succeeded")
            .description("Retries that succeeded")
            .register(meterRegistry);

        retriesFailed = Counter.builder("dlq.retries.failed")
            .description("Retries that failed permanently")
            .register(meterRegistry);

        retriesEscalated = Counter.builder("dlq.retries.escalated")
            .description("Retries escalated to manual review")
            .register(meterRegistry);

        retryProcessingTime = Timer.builder("dlq.retry.processing.time")
            .description("Retry processing duration")
            .register(meterRegistry);
    }
}
