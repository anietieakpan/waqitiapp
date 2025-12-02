package com.waqiti.payment.kafka;

import com.waqiti.common.events.BatchJobAlertEvent;
import com.waqiti.payment.domain.BatchJobAlert;
import com.waqiti.payment.repository.BatchJobAlertRepository;
import com.waqiti.payment.service.BatchProcessingService;
import com.waqiti.payment.service.AlertService;
import com.waqiti.payment.metrics.PaymentMetricsService;
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

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class BatchJobAlertsConsumer {

    private final BatchJobAlertRepository batchJobAlertRepository;
    private final BatchProcessingService batchProcessingService;
    private final AlertService alertService;
    private final PaymentMetricsService metricsService;
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
        successCounter = Counter.builder("batch_job_alerts_processed_total")
            .description("Total number of successfully processed batch job alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("batch_job_alerts_errors_total")
            .description("Total number of batch job alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("batch_job_alerts_processing_duration")
            .description("Time taken to process batch job alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"batch-job-alerts", "batch-monitoring-alerts", "batch-performance-alerts"},
        groupId = "batch-job-alerts-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "batch-job-alerts", fallbackMethod = "handleBatchJobAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBatchJobAlertEvent(
            @Payload BatchJobAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("batch-alert-%s-p%d-o%d", event.getBatchId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getBatchId(), event.getAlertType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing batch job alert: batchId={}, type={}, severity={}",
                event.getBatchId(), event.getAlertType(), event.getSeverity());

            cleanExpiredEntries();

            switch (event.getAlertType()) {
                case BATCH_PERFORMANCE_DEGRADED:
                    processBatchPerformanceDegraded(event, correlationId);
                    break;

                case BATCH_STUCK:
                    processBatchStuck(event, correlationId);
                    break;

                case BATCH_HIGH_ERROR_RATE:
                    processBatchHighErrorRate(event, correlationId);
                    break;

                case BATCH_RESOURCE_EXHAUSTED:
                    processBatchResourceExhausted(event, correlationId);
                    break;

                case BATCH_TIMEOUT_WARNING:
                    processBatchTimeoutWarning(event, correlationId);
                    break;

                case BATCH_TIMEOUT_EXCEEDED:
                    processBatchTimeoutExceeded(event, correlationId);
                    break;

                case BATCH_MEMORY_LEAK_DETECTED:
                    processBatchMemoryLeakDetected(event, correlationId);
                    break;

                case BATCH_DEADLOCK_DETECTED:
                    processBatchDeadlockDetected(event, correlationId);
                    break;

                case BATCH_DEPENDENCY_FAILED:
                    processBatchDependencyFailed(event, correlationId);
                    break;

                case BATCH_DATA_INCONSISTENCY:
                    processBatchDataInconsistency(event, correlationId);
                    break;

                default:
                    log.warn("Unknown batch job alert type: {}", event.getAlertType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("BATCH_JOB_ALERT_EVENT_PROCESSED", event.getBatchId(),
                Map.of("alertType", event.getAlertType(), "severity", event.getSeverity(),
                    "alertMessage", event.getAlertMessage(), "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process batch job alert event: {}", e.getMessage(), e);

            kafkaTemplate.send("batch-job-alerts-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handleBatchJobAlertEventFallback(
            BatchJobAlertEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("batch-alert-fallback-%s-p%d-o%d", event.getBatchId(), partition, offset);

        log.error("Circuit breaker fallback triggered for batch job alert: batchId={}, error={}",
            event.getBatchId(), ex.getMessage());

        kafkaTemplate.send("batch-job-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBatchJobAlertEvent(
            @Payload BatchJobAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-batch-alert-%s-%d", event.getBatchId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Batch job alert permanently failed: batchId={}, error={}",
            event.getBatchId(), exceptionMessage);

        auditService.logPaymentEvent("BATCH_JOB_ALERT_DLT_EVENT", event.getBatchId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "alertType", event.getAlertType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) return false;
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

    private void processBatchPerformanceDegraded(BatchJobAlertEvent event, String correlationId) {
        BatchJobAlert alert = BatchJobAlert.builder()
            .batchId(event.getBatchId())
            .alertType("BATCH_PERFORMANCE_DEGRADED")
            .severity(event.getSeverity())
            .alertMessage(event.getAlertMessage())
            .expectedThroughput(event.getExpectedThroughput())
            .actualThroughput(event.getActualThroughput())
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        batchJobAlertRepository.save(alert);

        // Analyze performance degradation
        batchProcessingService.analyzePerformanceDegradation(
            event.getBatchId(),
            event.getExpectedThroughput(),
            event.getActualThroughput(),
            correlationId
        );

        // Send operational alert
        notificationService.sendOperationalAlert(
            "Batch Performance Degraded",
            String.format("Batch %s performance degraded: Expected %d/min, Actual %d/min",
                event.getBatchId(), event.getExpectedThroughput(), event.getActualThroughput()),
            event.getSeverity()
        );

        metricsService.recordBatchPerformanceAlert(event.getBatchId(), event.getActualThroughput(), event.getExpectedThroughput());
    }

    private void processBatchStuck(BatchJobAlertEvent event, String correlationId) {
        BatchJobAlert alert = BatchJobAlert.builder()
            .batchId(event.getBatchId())
            .alertType("BATCH_STUCK")
            .severity(event.getSeverity())
            .alertMessage(event.getAlertMessage())
            .stuckDuration(event.getStuckDuration())
            .lastProcessedItem(event.getLastProcessedItem())
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        batchJobAlertRepository.save(alert);

        // Handle stuck batch
        batchProcessingService.handleStuckBatch(
            event.getBatchId(),
            event.getStuckDuration(),
            event.getLastProcessedItem(),
            correlationId
        );

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Batch Job Stuck",
            String.format("Batch %s has been stuck for %d minutes at item %s",
                event.getBatchId(), event.getStuckDuration(), event.getLastProcessedItem()),
            Map.of("batchId", event.getBatchId(), "stuckDuration", event.getStuckDuration(), "correlationId", correlationId)
        );

        metricsService.recordBatchStuckAlert(event.getBatchId(), event.getStuckDuration());
    }

    private void processBatchHighErrorRate(BatchJobAlertEvent event, String correlationId) {
        BatchJobAlert alert = BatchJobAlert.builder()
            .batchId(event.getBatchId())
            .alertType("BATCH_HIGH_ERROR_RATE")
            .severity(event.getSeverity())
            .alertMessage(event.getAlertMessage())
            .errorRate(event.getErrorRate())
            .errorThreshold(event.getErrorThreshold())
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        batchJobAlertRepository.save(alert);

        // Handle high error rate
        batchProcessingService.handleHighErrorRate(
            event.getBatchId(),
            event.getErrorRate(),
            event.getErrorThreshold(),
            correlationId
        );

        // Send high priority alert
        notificationService.sendOperationalAlert(
            "Batch High Error Rate",
            String.format("Batch %s error rate exceeded threshold: %.2f%% (threshold: %.2f%%)",
                event.getBatchId(), event.getErrorRate(), event.getErrorThreshold()),
            "HIGH"
        );

        metricsService.recordBatchHighErrorRateAlert(event.getBatchId(), event.getErrorRate());
    }

    private void processBatchResourceExhausted(BatchJobAlertEvent event, String correlationId) {
        BatchJobAlert alert = BatchJobAlert.builder()
            .batchId(event.getBatchId())
            .alertType("BATCH_RESOURCE_EXHAUSTED")
            .severity(event.getSeverity())
            .alertMessage(event.getAlertMessage())
            .resourceType(event.getResourceType())
            .resourceUsage(event.getResourceUsage())
            .resourceLimit(event.getResourceLimit())
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        batchJobAlertRepository.save(alert);

        // Handle resource exhaustion
        batchProcessingService.handleResourceExhaustion(
            event.getBatchId(),
            event.getResourceType(),
            event.getResourceUsage(),
            event.getResourceLimit(),
            correlationId
        );

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Batch Resource Exhausted",
            String.format("Batch %s %s resource exhausted: %.2f%% (limit: %.2f%%)",
                event.getBatchId(), event.getResourceType(), event.getResourceUsage(), event.getResourceLimit()),
            Map.of("batchId", event.getBatchId(), "resourceType", event.getResourceType(), "correlationId", correlationId)
        );

        metricsService.recordBatchResourceExhaustedAlert(event.getBatchId(), event.getResourceType(), event.getResourceUsage());
    }

    private void processBatchTimeoutWarning(BatchJobAlertEvent event, String correlationId) {
        BatchJobAlert alert = BatchJobAlert.builder()
            .batchId(event.getBatchId())
            .alertType("BATCH_TIMEOUT_WARNING")
            .severity(event.getSeverity())
            .alertMessage(event.getAlertMessage())
            .timeoutThreshold(event.getTimeoutThreshold())
            .currentDuration(event.getCurrentDuration())
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        batchJobAlertRepository.save(alert);

        // Handle timeout warning
        batchProcessingService.handleTimeoutWarning(
            event.getBatchId(),
            event.getCurrentDuration(),
            event.getTimeoutThreshold(),
            correlationId
        );

        // Send warning notification
        notificationService.sendOperationalAlert(
            "Batch Timeout Warning",
            String.format("Batch %s approaching timeout: %d minutes (threshold: %d minutes)",
                event.getBatchId(), event.getCurrentDuration(), event.getTimeoutThreshold()),
            "MEDIUM"
        );

        metricsService.recordBatchTimeoutWarning(event.getBatchId(), event.getCurrentDuration());
    }

    private void processBatchTimeoutExceeded(BatchJobAlertEvent event, String correlationId) {
        BatchJobAlert alert = BatchJobAlert.builder()
            .batchId(event.getBatchId())
            .alertType("BATCH_TIMEOUT_EXCEEDED")
            .severity(event.getSeverity())
            .alertMessage(event.getAlertMessage())
            .timeoutThreshold(event.getTimeoutThreshold())
            .currentDuration(event.getCurrentDuration())
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        batchJobAlertRepository.save(alert);

        // Handle timeout exceeded
        batchProcessingService.handleTimeoutExceeded(
            event.getBatchId(),
            event.getCurrentDuration(),
            event.getTimeoutThreshold(),
            correlationId
        );

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Batch Timeout Exceeded",
            String.format("Batch %s exceeded timeout: %d minutes (threshold: %d minutes)",
                event.getBatchId(), event.getCurrentDuration(), event.getTimeoutThreshold()),
            Map.of("batchId", event.getBatchId(), "duration", event.getCurrentDuration(), "correlationId", correlationId)
        );

        metricsService.recordBatchTimeoutExceeded(event.getBatchId(), event.getCurrentDuration());
    }

    private void processBatchMemoryLeakDetected(BatchJobAlertEvent event, String correlationId) {
        BatchJobAlert alert = BatchJobAlert.builder()
            .batchId(event.getBatchId())
            .alertType("BATCH_MEMORY_LEAK_DETECTED")
            .severity(event.getSeverity())
            .alertMessage(event.getAlertMessage())
            .memoryUsage(event.getMemoryUsage())
            .memoryGrowthRate(event.getMemoryGrowthRate())
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        batchJobAlertRepository.save(alert);

        // Handle memory leak
        batchProcessingService.handleMemoryLeak(
            event.getBatchId(),
            event.getMemoryUsage(),
            event.getMemoryGrowthRate(),
            correlationId
        );

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Batch Memory Leak Detected",
            String.format("Memory leak detected in batch %s: %.2f%% usage, %.2f%% growth rate",
                event.getBatchId(), event.getMemoryUsage(), event.getMemoryGrowthRate()),
            Map.of("batchId", event.getBatchId(), "memoryUsage", event.getMemoryUsage(), "correlationId", correlationId)
        );

        metricsService.recordBatchMemoryLeakAlert(event.getBatchId(), event.getMemoryUsage(), event.getMemoryGrowthRate());
    }

    private void processBatchDeadlockDetected(BatchJobAlertEvent event, String correlationId) {
        BatchJobAlert alert = BatchJobAlert.builder()
            .batchId(event.getBatchId())
            .alertType("BATCH_DEADLOCK_DETECTED")
            .severity(event.getSeverity())
            .alertMessage(event.getAlertMessage())
            .deadlockDetails(event.getDeadlockDetails())
            .affectedThreads(event.getAffectedThreads())
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        batchJobAlertRepository.save(alert);

        // Handle deadlock
        batchProcessingService.handleDeadlock(
            event.getBatchId(),
            event.getDeadlockDetails(),
            event.getAffectedThreads(),
            correlationId
        );

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Batch Deadlock Detected",
            String.format("Deadlock detected in batch %s affecting %d threads",
                event.getBatchId(), event.getAffectedThreads()),
            Map.of("batchId", event.getBatchId(), "affectedThreads", event.getAffectedThreads(), "correlationId", correlationId)
        );

        metricsService.recordBatchDeadlockAlert(event.getBatchId(), event.getAffectedThreads());
    }

    private void processBatchDependencyFailed(BatchJobAlertEvent event, String correlationId) {
        BatchJobAlert alert = BatchJobAlert.builder()
            .batchId(event.getBatchId())
            .alertType("BATCH_DEPENDENCY_FAILED")
            .severity(event.getSeverity())
            .alertMessage(event.getAlertMessage())
            .failedDependency(event.getFailedDependency())
            .dependencyError(event.getDependencyError())
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        batchJobAlertRepository.save(alert);

        // Handle dependency failure
        batchProcessingService.handleDependencyFailure(
            event.getBatchId(),
            event.getFailedDependency(),
            event.getDependencyError(),
            correlationId
        );

        // Send high priority alert
        notificationService.sendOperationalAlert(
            "Batch Dependency Failed",
            String.format("Batch %s dependency failed: %s - %s",
                event.getBatchId(), event.getFailedDependency(), event.getDependencyError()),
            "HIGH"
        );

        metricsService.recordBatchDependencyFailureAlert(event.getBatchId(), event.getFailedDependency());
    }

    private void processBatchDataInconsistency(BatchJobAlertEvent event, String correlationId) {
        BatchJobAlert alert = BatchJobAlert.builder()
            .batchId(event.getBatchId())
            .alertType("BATCH_DATA_INCONSISTENCY")
            .severity(event.getSeverity())
            .alertMessage(event.getAlertMessage())
            .inconsistencyType(event.getInconsistencyType())
            .affectedRecords(event.getAffectedRecords())
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        batchJobAlertRepository.save(alert);

        // Handle data inconsistency
        batchProcessingService.handleDataInconsistency(
            event.getBatchId(),
            event.getInconsistencyType(),
            event.getAffectedRecords(),
            correlationId
        );

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Batch Data Inconsistency",
            String.format("Data inconsistency detected in batch %s: %s affecting %d records",
                event.getBatchId(), event.getInconsistencyType(), event.getAffectedRecords()),
            Map.of("batchId", event.getBatchId(), "inconsistencyType", event.getInconsistencyType(),
                "affectedRecords", event.getAffectedRecords(), "correlationId", correlationId)
        );

        metricsService.recordBatchDataInconsistencyAlert(event.getBatchId(), event.getInconsistencyType(), event.getAffectedRecords());
    }
}