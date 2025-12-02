package com.waqiti.monitoring.kafka;

import com.waqiti.common.events.DeadlockRecoveryEvent;
import com.waqiti.monitoring.service.DatabaseRecoveryService;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.monitoring.service.InfrastructureMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class DeadlockRecoveryEventsConsumer {

    private final DatabaseRecoveryService recoveryService;
    private final AlertingService alertingService;
    private final InfrastructureMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("deadlock_recovery_processed_total")
            .description("Total number of successfully processed deadlock recovery events")
            .register(meterRegistry);
        errorCounter = Counter.builder("deadlock_recovery_errors_total")
            .description("Total number of deadlock recovery processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("deadlock_recovery_processing_duration")
            .description("Time taken to process deadlock recovery events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"deadlock-recovery-events", "database-deadlock-recovery", "transaction-deadlock-recovery"},
        groupId = "deadlock-recovery-group",
        containerFactory = "criticalInfrastructureKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "deadlock-recovery", fallbackMethod = "handleDeadlockRecoveryEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleDeadlockRecoveryEvent(
            @Payload DeadlockRecoveryEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("deadlock-recovery-%s-p%d-o%d", event.getDeadlockId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getDeadlockId(), event.getRecoveryAction(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing deadlock recovery: deadlockId={}, action={}, database={}, affectedTables={}",
                event.getDeadlockId(), event.getRecoveryAction(), event.getDatabaseName(), event.getAffectedTables());

            cleanExpiredEntries();

            switch (event.getRecoveryAction()) {
                case AUTOMATIC_ROLLBACK:
                    handleAutomaticRollback(event, correlationId);
                    break;

                case TRANSACTION_RETRY:
                    handleTransactionRetry(event, correlationId);
                    break;

                case LOCK_TIMEOUT_ADJUSTMENT:
                    handleLockTimeoutAdjustment(event, correlationId);
                    break;

                case ISOLATION_LEVEL_CHANGE:
                    handleIsolationLevelChange(event, correlationId);
                    break;

                case QUERY_OPTIMIZATION:
                    handleQueryOptimization(event, correlationId);
                    break;

                case INDEX_ANALYSIS:
                    handleIndexAnalysis(event, correlationId);
                    break;

                case CONNECTION_POOL_RESET:
                    handleConnectionPoolReset(event, correlationId);
                    break;

                case MANUAL_INTERVENTION_REQUIRED:
                    handleManualInterventionRequired(event, correlationId);
                    break;

                case RECOVERY_COMPLETED:
                    handleRecoveryCompleted(event, correlationId);
                    break;

                default:
                    log.warn("Unknown deadlock recovery action: {}", event.getRecoveryAction());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logInfrastructureEvent("DEADLOCK_RECOVERY_PROCESSED", event.getDeadlockId(),
                Map.of("recoveryAction", event.getRecoveryAction(), "databaseName", event.getDatabaseName(),
                    "affectedTables", event.getAffectedTables(), "recoveryTimeMs", event.getRecoveryTimeMs(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process deadlock recovery event: {}", e.getMessage(), e);

            kafkaTemplate.send("deadlock-recovery-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleDeadlockRecoveryEventFallback(
            DeadlockRecoveryEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("deadlock-recovery-fallback-%s-p%d-o%d", event.getDeadlockId(), partition, offset);

        log.error("Circuit breaker fallback for deadlock recovery: deadlockId={}, error={}",
            event.getDeadlockId(), ex.getMessage());

        kafkaTemplate.send("deadlock-recovery-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendOperationalAlert(
                "Deadlock Recovery Processing Failure",
                String.format("Deadlock recovery processing failed for %s: %s", event.getDeadlockId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltDeadlockRecoveryEvent(
            @Payload DeadlockRecoveryEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-deadlock-recovery-%s-%d", event.getDeadlockId(), System.currentTimeMillis());

        log.error("DLT handler - Deadlock recovery failed: deadlockId={}, topic={}, error={}",
            event.getDeadlockId(), topic, exceptionMessage);

        auditService.logInfrastructureEvent("DEADLOCK_RECOVERY_DLT_EVENT", event.getDeadlockId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "recoveryAction", event.getRecoveryAction(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Deadlock Recovery DLT Event",
                String.format("Deadlock recovery %s sent to DLT: %s", event.getDeadlockId(), exceptionMessage),
                Map.of("deadlockId", event.getDeadlockId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }
        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void handleAutomaticRollback(DeadlockRecoveryEvent event, String correlationId) {
        log.info("Processing automatic rollback: deadlockId={}, affectedTransactions={}",
            event.getDeadlockId(), event.getAffectedTransactions());

        recoveryService.executeAutomaticRollback(
            event.getDeadlockId(), event.getAffectedTransactions(), event.getDatabaseName());

        alertingService.sendInfoAlert(
            "Deadlock Automatic Rollback",
            String.format("Automatic rollback executed for deadlock %s (%d transactions)",
                event.getDeadlockId(), event.getAffectedTransactions().size()),
            correlationId
        );

        metricsService.recordDeadlockRollback(event.getDeadlockId(), event.getAffectedTransactions().size());
    }

    private void handleTransactionRetry(DeadlockRecoveryEvent event, String correlationId) {
        log.info("Processing transaction retry: deadlockId={}, retryCount={}, maxRetries={}",
            event.getDeadlockId(), event.getRetryCount(), event.getMaxRetries());

        recoveryService.retryDeadlockedTransactions(
            event.getDeadlockId(), event.getAffectedTransactions(), event.getRetryCount());

        if (event.getRetryCount() >= event.getMaxRetries()) {
            alertingService.sendMediumPriorityAlert(
                "Deadlock Retry Limit Reached",
                String.format("Transaction retry limit reached for deadlock %s (%d/%d)",
                    event.getDeadlockId(), event.getRetryCount(), event.getMaxRetries()),
                correlationId
            );
        }

        metricsService.recordDeadlockRetry(event.getDeadlockId(), event.getRetryCount());
    }

    private void handleLockTimeoutAdjustment(DeadlockRecoveryEvent event, String correlationId) {
        log.info("Adjusting lock timeout: deadlockId={}, newTimeout={}ms, previousTimeout={}ms",
            event.getDeadlockId(), event.getNewLockTimeoutMs(), event.getPreviousLockTimeoutMs());

        recoveryService.adjustLockTimeout(
            event.getDatabaseName(), event.getNewLockTimeoutMs(), event.getDeadlockId());

        alertingService.sendInfoAlert(
            "Lock Timeout Adjusted",
            String.format("Lock timeout adjusted for deadlock %s: %dms -> %dms",
                event.getDeadlockId(), event.getPreviousLockTimeoutMs(), event.getNewLockTimeoutMs()),
            correlationId
        );

        metricsService.recordLockTimeoutAdjustment(event.getDeadlockId(), event.getNewLockTimeoutMs());
    }

    private void handleIsolationLevelChange(DeadlockRecoveryEvent event, String correlationId) {
        log.info("Changing isolation level: deadlockId={}, newLevel={}, previousLevel={}",
            event.getDeadlockId(), event.getNewIsolationLevel(), event.getPreviousIsolationLevel());

        recoveryService.changeIsolationLevel(
            event.getDatabaseName(), event.getNewIsolationLevel(), event.getDeadlockId());

        alertingService.sendInfoAlert(
            "Isolation Level Changed",
            String.format("Isolation level changed for deadlock %s: %s -> %s",
                event.getDeadlockId(), event.getPreviousIsolationLevel(), event.getNewIsolationLevel()),
            correlationId
        );

        metricsService.recordIsolationLevelChange(event.getDeadlockId(), event.getNewIsolationLevel());
    }

    private void handleQueryOptimization(DeadlockRecoveryEvent event, String correlationId) {
        log.info("Optimizing queries for deadlock prevention: deadlockId={}, problematicQueries={}",
            event.getDeadlockId(), event.getProblematicQueries());

        recoveryService.optimizeProblematicQueries(
            event.getDatabaseName(), event.getProblematicQueries(), event.getDeadlockId());

        alertingService.sendInfoAlert(
            "Query Optimization for Deadlock Prevention",
            String.format("Optimizing %d queries to prevent deadlock recurrence (%s)",
                event.getProblematicQueries().size(), event.getDeadlockId()),
            correlationId
        );

        metricsService.recordQueryOptimization(event.getDeadlockId(), event.getProblematicQueries().size());
    }

    private void handleIndexAnalysis(DeadlockRecoveryEvent event, String correlationId) {
        log.info("Analyzing indexes for deadlock prevention: deadlockId={}, affectedTables={}",
            event.getDeadlockId(), event.getAffectedTables());

        recoveryService.analyzeIndexes(
            event.getDatabaseName(), event.getAffectedTables(), event.getDeadlockId());

        alertingService.sendInfoAlert(
            "Index Analysis for Deadlock Prevention",
            String.format("Analyzing indexes on tables: %s (deadlock: %s)",
                String.join(", ", event.getAffectedTables()), event.getDeadlockId()),
            correlationId
        );

        metricsService.recordIndexAnalysis(event.getDeadlockId(), event.getAffectedTables().size());
    }

    private void handleConnectionPoolReset(DeadlockRecoveryEvent event, String correlationId) {
        log.warn("Resetting connection pool for deadlock recovery: deadlockId={}, database={}",
            event.getDeadlockId(), event.getDatabaseName());

        recoveryService.resetConnectionPool(event.getDatabaseName(), event.getDeadlockId());

        alertingService.sendMediumPriorityAlert(
            "Connection Pool Reset for Deadlock Recovery",
            String.format("Connection pool reset for database %s due to deadlock %s",
                event.getDatabaseName(), event.getDeadlockId()),
            correlationId
        );

        metricsService.recordConnectionPoolReset(event.getDeadlockId());
    }

    private void handleManualInterventionRequired(DeadlockRecoveryEvent event, String correlationId) {
        log.error("Manual intervention required for deadlock: deadlockId={}, reason={}",
            event.getDeadlockId(), event.getManualInterventionReason());

        recoveryService.escalateToManualIntervention(
            event.getDeadlockId(), event.getManualInterventionReason(), event.getDatabaseName());

        alertingService.sendHighPriorityAlert(
            "Manual Intervention Required - Deadlock Recovery",
            String.format("Deadlock %s requires manual intervention: %s",
                event.getDeadlockId(), event.getManualInterventionReason()),
            correlationId
        );

        metricsService.recordManualInterventionRequired(event.getDeadlockId());

        // Escalate to DBA team
        notificationService.escalateToDBA(
            "Deadlock Manual Intervention Required",
            String.format("Deadlock %s in database %s requires manual intervention: %s",
                event.getDeadlockId(), event.getDatabaseName(), event.getManualInterventionReason()),
            Map.of("deadlockId", event.getDeadlockId(), "database", event.getDatabaseName(), "correlationId", correlationId)
        );
    }

    private void handleRecoveryCompleted(DeadlockRecoveryEvent event, String correlationId) {
        log.info("Deadlock recovery completed: deadlockId={}, recoveryMethod={}, totalTime={}ms",
            event.getDeadlockId(), event.getRecoveryMethod(), event.getTotalRecoveryTimeMs());

        recoveryService.markRecoveryCompleted(
            event.getDeadlockId(), event.getRecoveryMethod(), event.getTotalRecoveryTimeMs());

        alertingService.sendInfoAlert(
            "Deadlock Recovery Completed",
            String.format("Deadlock %s recovered using %s (total time: %dms)",
                event.getDeadlockId(), event.getRecoveryMethod(), event.getTotalRecoveryTimeMs()),
            correlationId
        );

        metricsService.recordRecoveryCompleted(event.getDeadlockId(), event.getTotalRecoveryTimeMs());

        // Clear deadlock tracking
        recoveryService.clearDeadlockTracking(event.getDeadlockId());
    }
}