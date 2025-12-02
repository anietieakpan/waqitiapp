package com.waqiti.payment.kafka;

import com.waqiti.common.events.BatchEvent;
import com.waqiti.payment.domain.BatchProcessing;
import com.waqiti.payment.repository.BatchProcessingRepository;
import com.waqiti.payment.service.BatchProcessingService;
import com.waqiti.payment.service.PaymentProcessingService;
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
public class BatchEventsConsumer {

    private final BatchProcessingRepository batchProcessingRepository;
    private final BatchProcessingService batchProcessingService;
    private final PaymentProcessingService paymentProcessingService;
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
        successCounter = Counter.builder("batch_events_processed_total")
            .description("Total number of successfully processed batch events")
            .register(meterRegistry);
        errorCounter = Counter.builder("batch_events_errors_total")
            .description("Total number of batch event processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("batch_events_processing_duration")
            .description("Time taken to process batch events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"batch-events", "payment-batch-processing", "bulk-operation-events"},
        groupId = "batch-events-service-group",
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
    @CircuitBreaker(name = "batch-events", fallbackMethod = "handleBatchEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBatchEvent(
            @Payload BatchEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("batch-%s-p%d-o%d", event.getBatchId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getBatchId(), event.getEventType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing batch event: batchId={}, type={}, status={}",
                event.getBatchId(), event.getEventType(), event.getStatus());

            cleanExpiredEntries();

            switch (event.getEventType()) {
                case BATCH_STARTED:
                    processBatchStarted(event, correlationId);
                    break;

                case BATCH_COMPLETED:
                    processBatchCompleted(event, correlationId);
                    break;

                case BATCH_FAILED:
                    processBatchFailed(event, correlationId);
                    break;

                case BATCH_PROGRESS_UPDATE:
                    processBatchProgressUpdate(event, correlationId);
                    break;

                case BATCH_PAUSED:
                    processBatchPaused(event, correlationId);
                    break;

                case BATCH_RESUMED:
                    processBatchResumed(event, correlationId);
                    break;

                case BATCH_CANCELLED:
                    processBatchCancelled(event, correlationId);
                    break;

                case BATCH_VALIDATION_STARTED:
                    processBatchValidationStarted(event, correlationId);
                    break;

                case BATCH_VALIDATION_COMPLETED:
                    processBatchValidationCompleted(event, correlationId);
                    break;

                case BATCH_VALIDATION_FAILED:
                    processBatchValidationFailed(event, correlationId);
                    break;

                default:
                    log.warn("Unknown batch event type: {}", event.getEventType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("BATCH_EVENT_PROCESSED", event.getBatchId(),
                Map.of("eventType", event.getEventType(), "status", event.getStatus(),
                    "totalItems", event.getTotalItems(), "processedItems", event.getProcessedItems(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process batch event: {}", e.getMessage(), e);

            kafkaTemplate.send("batch-events-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handleBatchEventFallback(
            BatchEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("batch-fallback-%s-p%d-o%d", event.getBatchId(), partition, offset);

        log.error("Circuit breaker fallback triggered for batch event: batchId={}, error={}",
            event.getBatchId(), ex.getMessage());

        kafkaTemplate.send("batch-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBatchEvent(
            @Payload BatchEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-batch-%s-%d", event.getBatchId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Batch event permanently failed: batchId={}, error={}",
            event.getBatchId(), exceptionMessage);

        auditService.logPaymentEvent("BATCH_EVENT_DLT_EVENT", event.getBatchId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
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

    private void processBatchStarted(BatchEvent event, String correlationId) {
        BatchProcessing batch = BatchProcessing.builder()
            .batchId(event.getBatchId())
            .batchType(event.getBatchType())
            .status("STARTED")
            .totalItems(event.getTotalItems())
            .processedItems(0)
            .failedItems(0)
            .startTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        batchProcessingRepository.save(batch);

        // Initialize batch monitoring
        batchProcessingService.initializeBatchMonitoring(event.getBatchId(), correlationId);

        // Record metrics
        metricsService.recordBatchStarted(event.getBatchType(), event.getTotalItems());

        // Send notification for large batches
        if (event.getTotalItems() > 10000) {
            notificationService.sendOperationalAlert(
                "Large Batch Processing Started",
                String.format("Batch %s started with %d items", event.getBatchId(), event.getTotalItems()),
                "INFO"
            );
        }

        log.info("Batch started: batchId={}, type={}, totalItems={}",
            event.getBatchId(), event.getBatchType(), event.getTotalItems());
    }

    private void processBatchCompleted(BatchEvent event, String correlationId) {
        BatchProcessing batch = batchProcessingRepository.findByBatchId(event.getBatchId())
            .orElseThrow(() -> new RuntimeException("Batch not found"));

        batch.setStatus("COMPLETED");
        batch.setProcessedItems(event.getProcessedItems());
        batch.setFailedItems(event.getFailedItems());
        batch.setEndTime(LocalDateTime.now());
        batchProcessingRepository.save(batch);

        // Generate batch completion report
        batchProcessingService.generateCompletionReport(
            event.getBatchId(),
            event.getProcessedItems(),
            event.getFailedItems(),
            correlationId
        );

        // Record metrics
        metricsService.recordBatchCompleted(event.getBatchType(), event.getProcessedItems(),
            event.getFailedItems(), event.getDurationMinutes());

        // Send completion notification
        notificationService.sendOperationalAlert(
            "Batch Processing Completed",
            String.format("Batch %s completed: %d processed, %d failed",
                event.getBatchId(), event.getProcessedItems(), event.getFailedItems()),
            "INFO"
        );

        log.info("Batch completed: batchId={}, processed={}, failed={}, duration={}min",
            event.getBatchId(), event.getProcessedItems(), event.getFailedItems(), event.getDurationMinutes());
    }

    private void processBatchFailed(BatchEvent event, String correlationId) {
        BatchProcessing batch = batchProcessingRepository.findByBatchId(event.getBatchId())
            .orElseThrow(() -> new RuntimeException("Batch not found"));

        batch.setStatus("FAILED");
        batch.setProcessedItems(event.getProcessedItems());
        batch.setFailedItems(event.getFailedItems());
        batch.setFailureReason(event.getFailureReason());
        batch.setEndTime(LocalDateTime.now());
        batchProcessingRepository.save(batch);

        // Handle batch failure
        batchProcessingService.handleBatchFailure(
            event.getBatchId(),
            event.getFailureReason(),
            correlationId
        );

        // Record metrics
        metricsService.recordBatchFailed(event.getBatchType(), event.getFailureReason());

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Batch Processing Failed",
            String.format("Batch %s failed: %s", event.getBatchId(), event.getFailureReason()),
            Map.of("batchId", event.getBatchId(), "failureReason", event.getFailureReason(), "correlationId", correlationId)
        );

        log.error("Batch failed: batchId={}, reason={}, processed={}, failed={}",
            event.getBatchId(), event.getFailureReason(), event.getProcessedItems(), event.getFailedItems());
    }

    private void processBatchProgressUpdate(BatchEvent event, String correlationId) {
        BatchProcessing batch = batchProcessingRepository.findByBatchId(event.getBatchId())
            .orElseThrow(() -> new RuntimeException("Batch not found"));

        batch.setProcessedItems(event.getProcessedItems());
        batch.setFailedItems(event.getFailedItems());
        batch.setProgress(event.getProgressPercentage());
        batchProcessingRepository.save(batch);

        // Update progress monitoring
        batchProcessingService.updateBatchProgress(
            event.getBatchId(),
            event.getProcessedItems(),
            event.getFailedItems(),
            event.getProgressPercentage(),
            correlationId
        );

        // Check for performance issues
        if (event.getProgressPercentage() > 50 && event.getDurationMinutes() > event.getEstimatedDurationMinutes() * 1.5) {
            notificationService.sendOperationalAlert(
                "Batch Processing Performance Warning",
                String.format("Batch %s is running slower than expected: %d%% complete in %d minutes",
                    event.getBatchId(), event.getProgressPercentage(), event.getDurationMinutes()),
                "MEDIUM"
            );
        }

        log.debug("Batch progress update: batchId={}, progress={}%, processed={}, failed={}",
            event.getBatchId(), event.getProgressPercentage(), event.getProcessedItems(), event.getFailedItems());
    }

    private void processBatchPaused(BatchEvent event, String correlationId) {
        BatchProcessing batch = batchProcessingRepository.findByBatchId(event.getBatchId())
            .orElseThrow(() -> new RuntimeException("Batch not found"));

        batch.setStatus("PAUSED");
        batch.setPausedAt(LocalDateTime.now());
        batch.setPauseReason(event.getPauseReason());
        batchProcessingRepository.save(batch);

        // Handle batch pause
        batchProcessingService.pauseBatch(event.getBatchId(), event.getPauseReason(), correlationId);

        // Record metrics
        metricsService.recordBatchPaused(event.getBatchType(), event.getPauseReason());

        log.info("Batch paused: batchId={}, reason={}", event.getBatchId(), event.getPauseReason());
    }

    private void processBatchResumed(BatchEvent event, String correlationId) {
        BatchProcessing batch = batchProcessingRepository.findByBatchId(event.getBatchId())
            .orElseThrow(() -> new RuntimeException("Batch not found"));

        batch.setStatus("RUNNING");
        batch.setResumedAt(LocalDateTime.now());
        batchProcessingRepository.save(batch);

        // Resume batch processing
        batchProcessingService.resumeBatch(event.getBatchId(), correlationId);

        // Record metrics
        metricsService.recordBatchResumed(event.getBatchType());

        log.info("Batch resumed: batchId={}", event.getBatchId());
    }

    private void processBatchCancelled(BatchEvent event, String correlationId) {
        BatchProcessing batch = batchProcessingRepository.findByBatchId(event.getBatchId())
            .orElseThrow(() -> new RuntimeException("Batch not found"));

        batch.setStatus("CANCELLED");
        batch.setCancelledAt(LocalDateTime.now());
        batch.setCancellationReason(event.getCancellationReason());
        batch.setEndTime(LocalDateTime.now());
        batchProcessingRepository.save(batch);

        // Handle batch cancellation
        batchProcessingService.cancelBatch(event.getBatchId(), event.getCancellationReason(), correlationId);

        // Record metrics
        metricsService.recordBatchCancelled(event.getBatchType(), event.getCancellationReason());

        log.info("Batch cancelled: batchId={}, reason={}", event.getBatchId(), event.getCancellationReason());
    }

    private void processBatchValidationStarted(BatchEvent event, String correlationId) {
        // Start batch validation
        batchProcessingService.startBatchValidation(event.getBatchId(), correlationId);

        // Record metrics
        metricsService.recordBatchValidationStarted(event.getBatchType());

        log.info("Batch validation started: batchId={}", event.getBatchId());
    }

    private void processBatchValidationCompleted(BatchEvent event, String correlationId) {
        // Complete batch validation
        batchProcessingService.completeBatchValidation(
            event.getBatchId(),
            event.getValidationResults(),
            correlationId
        );

        // Record metrics
        metricsService.recordBatchValidationCompleted(event.getBatchType(), event.getValidationResults().isValid());

        log.info("Batch validation completed: batchId={}, valid={}",
            event.getBatchId(), event.getValidationResults().isValid());
    }

    private void processBatchValidationFailed(BatchEvent event, String correlationId) {
        // Handle validation failure
        batchProcessingService.handleBatchValidationFailure(
            event.getBatchId(),
            event.getValidationErrors(),
            correlationId
        );

        // Record metrics
        metricsService.recordBatchValidationFailed(event.getBatchType());

        // Send alert
        notificationService.sendOperationalAlert(
            "Batch Validation Failed",
            String.format("Batch %s validation failed with %d errors",
                event.getBatchId(), event.getValidationErrors().size()),
            "HIGH"
        );

        log.error("Batch validation failed: batchId={}, errors={}",
            event.getBatchId(), event.getValidationErrors().size());
    }
}