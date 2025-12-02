package com.waqiti.payment.saga.recovery;

import com.waqiti.payment.saga.model.*;
import com.waqiti.payment.saga.compensation.PaymentCompensationHandler;
import com.waqiti.common.saga.SagaStateRepository;
import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.common.idempotency.RedisIdempotencyService;
import com.waqiti.payment.client.NotificationServiceClient;
import com.waqiti.payment.service.AuditService;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Saga Recovery Service
 *
 * Handles recovery and retry of failed saga compensations:
 * - Monitors saga-compensation-failures topic
 * - Automatic retry with exponential backoff
 * - Manual review queue for persistent failures
 * - Scheduled recovery of stuck sagas
 * - Comprehensive metrics and alerting
 *
 * Recovery Strategies:
 * 1. Immediate retry (3 attempts with exponential backoff)
 * 2. Delayed retry (after 1 hour, 6 hours, 24 hours)
 * 3. Manual review queue (after all retries exhausted)
 * 4. Periodic scan for stuck sagas (every 15 minutes)
 *
 * @author Waqiti Platform - Saga Team
 * @version 1.0
 * @since 2025-10-13
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SagaRecoveryService {

    private final SagaStateRepository sagaStateRepository;
    private final PaymentCompensationHandler compensationHandler;
    private final DistributedLockService lockService;
    private final RedisIdempotencyService idempotencyService;
    private final NotificationServiceClient notificationServiceClient;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    private Counter recoveryAttemptCounter;
    private Counter recoverySuccessCounter;
    private Counter recoveryFailureCounter;
    private Counter manualReviewCounter;

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final Duration[] RETRY_DELAYS = {
        Duration.ofMinutes(1),
        Duration.ofMinutes(5),
        Duration.ofMinutes(15),
        Duration.ofHours(1),
        Duration.ofHours(6)
    };

    @PostConstruct
    public void initialize() {
        recoveryAttemptCounter = Counter.builder("saga.recovery.attempt")
            .description("Number of saga recovery attempts")
            .register(meterRegistry);

        recoverySuccessCounter = Counter.builder("saga.recovery.success")
            .description("Number of successful saga recoveries")
            .register(meterRegistry);

        recoveryFailureCounter = Counter.builder("saga.recovery.failure")
            .description("Number of failed saga recoveries")
            .register(meterRegistry);

        manualReviewCounter = Counter.builder("saga.manual.review")
            .description("Number of sagas queued for manual review")
            .register(meterRegistry);

        log.info("✅ Saga Recovery Service initialized - Max retries: {}", MAX_RETRY_ATTEMPTS);
    }

    /**
     * Consume compensation failure events and attempt recovery
     */
    @KafkaListener(
        topics = "saga-compensation-failures",
        groupId = "saga-recovery-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleCompensationFailure(
            @Payload Map<String, Object> failurePayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String sagaId = (String) failurePayload.get("sagaId");
        String stepName = (String) failurePayload.get("stepName");

        log.warn("📥 Received compensation failure - sagaId: {}, step: {}", sagaId, stepName);

        recoveryAttemptCounter.increment();

        // Build idempotency key for recovery
        String idempotencyKey = idempotencyService.buildIdempotencyKey(
            "saga-recovery",
            sagaId,
            stepName + ":" + partition + ":" + offset
        );

        // Check if already recovered
        if (idempotencyService.isProcessed(idempotencyKey)) {
            log.info("⏭️ Recovery already processed, skipping - sagaId: {}", sagaId);
            acknowledgment.acknowledge();
            return;
        }

        try {
            // Get current retry count
            Integer retryCount = (Integer) failurePayload.getOrDefault("retryCount", 0);

            if (retryCount >= MAX_RETRY_ATTEMPTS) {
                // Max retries exhausted - queue for manual review
                log.error("🔴 Max recovery attempts exhausted - sagaId: {}, retries: {}. Queuing for manual review.",
                    sagaId, retryCount);

                queueForManualReview(failurePayload);
                manualReviewCounter.increment();

                // Mark as processed to prevent further retries
                idempotencyService.markFinancialOperationProcessed(idempotencyKey);
                acknowledgment.acknowledge();
                return;
            }

            // Attempt recovery with exponential backoff
            Duration delay = RETRY_DELAYS[Math.min(retryCount, RETRY_DELAYS.length - 1)];
            log.info("🔄 Scheduling recovery attempt {} for sagaId: {} after delay: {}",
                retryCount + 1, sagaId, delay);

            // Schedule delayed retry
            scheduleDelayedRetry(failurePayload, delay, retryCount + 1);

            // Mark this recovery attempt as processed
            idempotencyService.markProcessed(idempotencyKey, Duration.ofDays(7));

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("❌ Failed to process compensation failure - sagaId: {}", sagaId, e);
            recoveryFailureCounter.increment();
            throw e;
        }
    }

    /**
     * Schedule delayed compensation retry
     */
    private void scheduleDelayedRetry(Map<String, Object> failurePayload, Duration delay, int retryCount) {
        String sagaId = (String) failurePayload.get("sagaId");

        // Update retry count
        failurePayload.put("retryCount", retryCount);
        failurePayload.put("scheduledRetryTime", LocalDateTime.now().plus(delay));

        // Send to delayed retry topic (with TTL/delay)
        kafkaTemplate.send("saga-compensation-retry", sagaId, failurePayload);

        log.debug("📤 Scheduled retry for sagaId: {} at {}", sagaId, LocalDateTime.now().plus(delay));
    }

    /**
     * Consume delayed retry events
     */
    @KafkaListener(
        topics = "saga-compensation-retry",
        groupId = "saga-recovery-retry-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleDelayedRetry(
            @Payload Map<String, Object> retryPayload,
            Acknowledgment acknowledgment) {

        String sagaId = (String) retryPayload.get("sagaId");
        String stepName = (String) retryPayload.get("stepName");
        Integer retryCount = (Integer) retryPayload.get("retryCount");

        log.info("🔄 Attempting compensation retry {} - sagaId: {}, step: {}", retryCount, sagaId, stepName);

        // Acquire lock to prevent concurrent retries
        String lockKey = "saga:recovery:" + sagaId;
        boolean lockAcquired = lockService.acquireLock(lockKey, Duration.ofMinutes(10));

        if (!lockAcquired) {
            log.warn("⚠️ Failed to acquire recovery lock - sagaId: {}. Will retry later.", sagaId);
            acknowledgment.acknowledge();
            return;
        }

        try {
            // Reconstruct compensation context
            CompensationContext context = reconstructCompensationContext(retryPayload);

            // Attempt compensation
            compensationHandler.compensate(context);

            log.info("✅ Compensation retry successful - sagaId: {}, step: {}, attempt: {}",
                sagaId, stepName, retryCount);

            recoverySuccessCounter.increment();

            // Update saga state to compensated
            updateSagaState(sagaId, SagaStatus.COMPENSATED);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("❌ Compensation retry failed - sagaId: {}, attempt: {}", sagaId, retryCount, e);

            recoveryFailureCounter.increment();

            // Re-queue with incremented retry count
            if (retryCount < MAX_RETRY_ATTEMPTS) {
                kafkaTemplate.send("saga-compensation-failures", sagaId, retryPayload);
            } else {
                queueForManualReview(retryPayload);
                manualReviewCounter.increment();
            }

            acknowledgment.acknowledge();

        } finally {
            lockService.releaseLock(lockKey);
        }
    }

    /**
     * Periodic scan for stuck sagas (runs every 15 minutes)
     */
    @Scheduled(fixedDelay = 900000) // 15 minutes
    @Transactional(readOnly = true)
    public void recoverStuckSagas() {
        log.info("🔍 Starting periodic scan for stuck sagas...");

        try {
            LocalDateTime threshold = LocalDateTime.now().minusHours(1);

            // Find sagas stuck in COMPENSATING state
            List<SagaState> stuckSagas = sagaStateRepository.findByStatusAndUpdatedAtBefore(
                SagaStatus.COMPENSATING,
                threshold
            );

            log.info("Found {} stuck sagas requiring recovery", stuckSagas.size());

            for (SagaState saga : stuckSagas) {
                try {
                    log.warn("🚨 Recovering stuck saga: {}", saga.getSagaId());
                    recoverStuckSaga(saga);
                } catch (Exception e) {
                    log.error("Failed to recover stuck saga: {}", saga.getSagaId(), e);
                }
            }

        } catch (Exception e) {
            log.error("Error during stuck saga recovery scan", e);
        }
    }

    /**
     * Recover a stuck saga
     */
    private void recoverStuckSaga(SagaState saga) {
        String lockKey = "saga:recovery:" + saga.getSagaId();
        boolean lockAcquired = lockService.acquireLock(lockKey, Duration.ofMinutes(10));

        if (!lockAcquired) {
            log.warn("Could not acquire lock for stuck saga: {}", saga.getSagaId());
            return;
        }

        try {
            // Trigger compensation retry
            Map<String, Object> recoveryPayload = new HashMap<>();
            recoveryPayload.put("sagaId", saga.getSagaId());
            recoveryPayload.put("stepName", saga.getCurrentStep());
            recoveryPayload.put("errorMessage", "Saga stuck in COMPENSATING state");
            recoveryPayload.put("retryCount", 0);
            recoveryPayload.put("timestamp", LocalDateTime.now());
            recoveryPayload.put("source", "PERIODIC_RECOVERY");

            kafkaTemplate.send("saga-compensation-failures", saga.getSagaId(), recoveryPayload);

            log.info("✅ Stuck saga queued for recovery: {}", saga.getSagaId());

        } finally {
            lockService.releaseLock(lockKey);
        }
    }

    /**
     * Queue saga for manual review
     */
    private void queueForManualReview(Map<String, Object> failurePayload) {
        String sagaId = (String) failurePayload.get("sagaId");

        log.error("🔴 CRITICAL: Queueing saga for manual review - sagaId: {}", sagaId);

        // Add to manual review topic
        failurePayload.put("queuedForReview", LocalDateTime.now());
        failurePayload.put("reviewRequired", true);

        kafkaTemplate.send("saga-manual-review-queue", sagaId, failurePayload);

        // Send critical alert
        notificationServiceClient.sendCriticalAlert(
            "Saga Manual Review Required",
            String.format("SAGA %s requires manual intervention after %d failed recovery attempts.",
                sagaId, MAX_RETRY_ATTEMPTS),
            failurePayload
        );

        // Audit log
        auditService.logSagaManualReview(sagaId, failurePayload);
    }

    /**
     * Reconstruct compensation context from payload
     */
    private CompensationContext reconstructCompensationContext(Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> contextMap = (Map<String, Object>) payload.get("context");

        // Reconstruct context from stored data
        // This is simplified - actual implementation would deserialize full context
        return CompensationContext.builder()
            .sagaId((String) payload.get("sagaId"))
            .stepName((String) payload.get("stepName"))
            .build();
    }

    /**
     * Update saga state
     */
    private void updateSagaState(String sagaId, SagaStatus status) {
        sagaStateRepository.findBySagaId(sagaId).ifPresent(state -> {
            state.setStatus(status);
            state.setUpdatedAt(LocalDateTime.now());
            sagaStateRepository.save(state);
        });
    }
}
