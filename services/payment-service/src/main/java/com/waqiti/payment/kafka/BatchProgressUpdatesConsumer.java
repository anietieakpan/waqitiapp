package com.waqiti.payment.kafka;

import com.waqiti.common.events.BatchProgressUpdateEvent;
import com.waqiti.payment.domain.BatchProgress;
import com.waqiti.payment.repository.BatchProgressRepository;
import com.waqiti.payment.service.BatchProcessingService;
import com.waqiti.payment.service.ProgressTrackingService;
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
public class BatchProgressUpdatesConsumer {

    private final BatchProgressRepository batchProgressRepository;
    private final BatchProcessingService batchProcessingService;
    private final ProgressTrackingService progressTrackingService;
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
        successCounter = Counter.builder("batch_progress_updates_processed_total")
            .description("Total number of successfully processed batch progress update events")
            .register(meterRegistry);
        errorCounter = Counter.builder("batch_progress_updates_errors_total")
            .description("Total number of batch progress update processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("batch_progress_updates_processing_duration")
            .description("Time taken to process batch progress update events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"batch.progress.updates", "batch-progress-tracking", "bulk-processing-progress"},
        groupId = "batch-progress-updates-service-group",
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
    @CircuitBreaker(name = "batch-progress-updates", fallbackMethod = "handleBatchProgressUpdateEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBatchProgressUpdateEvent(
            @Payload BatchProgressUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("batch-progress-%s-p%d-o%d", event.getBatchId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getBatchId(), event.getProgressType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.debug("Processing batch progress update: batchId={}, progress={}%, type={}",
                event.getBatchId(), event.getProgressPercentage(), event.getProgressType());

            cleanExpiredEntries();

            switch (event.getProgressType()) {
                case PROCESSING_PROGRESS:
                    processProcessingProgress(event, correlationId);
                    break;

                case VALIDATION_PROGRESS:
                    processValidationProgress(event, correlationId);
                    break;

                case TRANSMISSION_PROGRESS:
                    processTransmissionProgress(event, correlationId);
                    break;

                case SETTLEMENT_PROGRESS:
                    processSettlementProgress(event, correlationId);
                    break;

                case RECONCILIATION_PROGRESS:
                    processReconciliationProgress(event, correlationId);
                    break;

                case MILESTONE_REACHED:
                    processMilestoneReached(event, correlationId);
                    break;

                case PERFORMANCE_METRICS_UPDATE:
                    processPerformanceMetricsUpdate(event, correlationId);
                    break;

                case ERROR_RATE_UPDATE:
                    processErrorRateUpdate(event, correlationId);
                    break;

                case THROUGHPUT_UPDATE:
                    processThroughputUpdate(event, correlationId);
                    break;

                case ETA_UPDATE:
                    processEtaUpdate(event, correlationId);
                    break;

                default:
                    log.warn("Unknown batch progress update type: {}", event.getProgressType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("BATCH_PROGRESS_UPDATE_PROCESSED", event.getBatchId(),
                Map.of("progressType", event.getProgressType(), "progressPercentage", event.getProgressPercentage(),
                    "processedItems", event.getProcessedItems(), "totalItems", event.getTotalItems(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process batch progress update: {}", e.getMessage(), e);

            kafkaTemplate.send("batch-progress-updates-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handleBatchProgressUpdateEventFallback(
            BatchProgressUpdateEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("batch-progress-fallback-%s-p%d-o%d", event.getBatchId(), partition, offset);

        log.error("Circuit breaker fallback triggered for batch progress update: batchId={}, error={}",
            event.getBatchId(), ex.getMessage());

        kafkaTemplate.send("batch-progress-updates-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBatchProgressUpdateEvent(
            @Payload BatchProgressUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-batch-progress-%s-%d", event.getBatchId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Batch progress update permanently failed: batchId={}, error={}",
            event.getBatchId(), exceptionMessage);

        auditService.logPaymentEvent("BATCH_PROGRESS_UPDATE_DLT_EVENT", event.getBatchId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "progressType", event.getProgressType(), "correlationId", correlationId,
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

    private void processProcessingProgress(BatchProgressUpdateEvent event, String correlationId) {
        BatchProgress progress = BatchProgress.builder()
            .batchId(event.getBatchId())
            .progressType("PROCESSING_PROGRESS")
            .progressPercentage(event.getProgressPercentage())
            .processedItems(event.getProcessedItems())
            .totalItems(event.getTotalItems())
            .failedItems(event.getFailedItems())
            .currentThroughput(event.getCurrentThroughput())
            .estimatedCompletion(event.getEstimatedCompletion())
            .lastUpdated(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        batchProgressRepository.save(progress);

        // Update processing metrics
        progressTrackingService.updateProcessingMetrics(
            event.getBatchId(),
            event.getProcessedItems(),
            event.getTotalItems(),
            event.getCurrentThroughput(),
            correlationId
        );

        // Check for processing delays
        if (event.getProgressPercentage() > 25 && event.getCurrentThroughput() < event.getExpectedThroughput() * 0.7) {
            progressTrackingService.alertSlowProcessing(
                event.getBatchId(),
                event.getCurrentThroughput(),
                event.getExpectedThroughput(),
                correlationId
            );
        }

        metricsService.recordBatchProgress(event.getBatchId(), "PROCESSING", event.getProgressPercentage());
    }

    private void processValidationProgress(BatchProgressUpdateEvent event, String correlationId) {
        BatchProgress progress = BatchProgress.builder()
            .batchId(event.getBatchId())
            .progressType("VALIDATION_PROGRESS")
            .progressPercentage(event.getProgressPercentage())
            .processedItems(event.getProcessedItems())
            .totalItems(event.getTotalItems())
            .validationErrors(event.getValidationErrors())
            .validatedItems(event.getValidatedItems())
            .lastUpdated(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        batchProgressRepository.save(progress);

        // Update validation metrics
        progressTrackingService.updateValidationMetrics(
            event.getBatchId(),
            event.getValidatedItems(),
            event.getValidationErrors(),
            event.getTotalItems(),
            correlationId
        );

        // Check validation error rate
        double errorRate = (double) event.getValidationErrors() / event.getProcessedItems() * 100;
        if (errorRate > 10.0) {
            progressTrackingService.alertHighValidationErrorRate(
                event.getBatchId(),
                errorRate,
                event.getValidationErrors(),
                correlationId
            );
        }

        metricsService.recordBatchProgress(event.getBatchId(), "VALIDATION", event.getProgressPercentage());
    }

    private void processTransmissionProgress(BatchProgressUpdateEvent event, String correlationId) {
        BatchProgress progress = BatchProgress.builder()
            .batchId(event.getBatchId())
            .progressType("TRANSMISSION_PROGRESS")
            .progressPercentage(event.getProgressPercentage())
            .processedItems(event.getProcessedItems())
            .totalItems(event.getTotalItems())
            .transmittedItems(event.getTransmittedItems())
            .transmissionErrors(event.getTransmissionErrors())
            .lastUpdated(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        batchProgressRepository.save(progress);

        // Update transmission metrics
        progressTrackingService.updateTransmissionMetrics(
            event.getBatchId(),
            event.getTransmittedItems(),
            event.getTransmissionErrors(),
            event.getTotalItems(),
            correlationId
        );

        // Monitor transmission quality
        double transmissionSuccessRate = (double) event.getTransmittedItems() / event.getProcessedItems() * 100;
        if (transmissionSuccessRate < 95.0) {
            progressTrackingService.alertLowTransmissionSuccessRate(
                event.getBatchId(),
                transmissionSuccessRate,
                event.getTransmissionErrors(),
                correlationId
            );
        }

        metricsService.recordBatchProgress(event.getBatchId(), "TRANSMISSION", event.getProgressPercentage());
    }

    private void processSettlementProgress(BatchProgressUpdateEvent event, String correlationId) {
        BatchProgress progress = BatchProgress.builder()
            .batchId(event.getBatchId())
            .progressType("SETTLEMENT_PROGRESS")
            .progressPercentage(event.getProgressPercentage())
            .processedItems(event.getProcessedItems())
            .totalItems(event.getTotalItems())
            .settledItems(event.getSettledItems())
            .settlementValue(event.getSettlementValue())
            .lastUpdated(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        batchProgressRepository.save(progress);

        // Update settlement metrics
        progressTrackingService.updateSettlementMetrics(
            event.getBatchId(),
            event.getSettledItems(),
            event.getSettlementValue(),
            event.getTotalItems(),
            correlationId
        );

        // Monitor settlement value reconciliation
        if (event.getSettlementValue() != null && event.getExpectedSettlementValue() != null) {
            double variance = Math.abs(event.getSettlementValue() - event.getExpectedSettlementValue());
            if (variance > 1000.0) { // $1000 variance threshold
                progressTrackingService.alertSettlementValueVariance(
                    event.getBatchId(),
                    event.getSettlementValue(),
                    event.getExpectedSettlementValue(),
                    variance,
                    correlationId
                );
            }
        }

        metricsService.recordBatchProgress(event.getBatchId(), "SETTLEMENT", event.getProgressPercentage());
    }

    private void processReconciliationProgress(BatchProgressUpdateEvent event, String correlationId) {
        BatchProgress progress = BatchProgress.builder()
            .batchId(event.getBatchId())
            .progressType("RECONCILIATION_PROGRESS")
            .progressPercentage(event.getProgressPercentage())
            .processedItems(event.getProcessedItems())
            .totalItems(event.getTotalItems())
            .reconciledItems(event.getReconciledItems())
            .reconciliationDiscrepancies(event.getReconciliationDiscrepancies())
            .lastUpdated(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        batchProgressRepository.save(progress);

        // Update reconciliation metrics
        progressTrackingService.updateReconciliationMetrics(
            event.getBatchId(),
            event.getReconciledItems(),
            event.getReconciliationDiscrepancies(),
            event.getTotalItems(),
            correlationId
        );

        // Monitor reconciliation quality
        if (event.getReconciliationDiscrepancies() > 0) {
            progressTrackingService.alertReconciliationDiscrepancies(
                event.getBatchId(),
                event.getReconciliationDiscrepancies(),
                correlationId
            );
        }

        metricsService.recordBatchProgress(event.getBatchId(), "RECONCILIATION", event.getProgressPercentage());
    }

    private void processMilestoneReached(BatchProgressUpdateEvent event, String correlationId) {
        // Record milestone achievement
        progressTrackingService.recordMilestone(
            event.getBatchId(),
            event.getMilestoneName(),
            event.getMilestoneValue(),
            event.getAchievedAt(),
            correlationId
        );

        // Send milestone notification for significant milestones
        if (isSignificantMilestone(event.getMilestoneName(), event.getMilestoneValue())) {
            notificationService.sendOperationalAlert(
                "Batch Processing Milestone Reached",
                String.format("Batch %s reached milestone: %s (%s)",
                    event.getBatchId(), event.getMilestoneName(), event.getMilestoneValue()),
                "INFO"
            );
        }

        // Update milestone metrics
        metricsService.recordBatchMilestone(event.getBatchId(), event.getMilestoneName(), event.getMilestoneValue());

        log.info("Batch milestone reached: batchId={}, milestone={}, value={}",
            event.getBatchId(), event.getMilestoneName(), event.getMilestoneValue());
    }

    private void processPerformanceMetricsUpdate(BatchProgressUpdateEvent event, String correlationId) {
        // Update performance metrics
        progressTrackingService.updatePerformanceMetrics(
            event.getBatchId(),
            event.getPerformanceMetrics(),
            correlationId
        );

        // Check for performance degradation
        if (event.getPerformanceMetrics().getCurrentThroughput() <
            event.getPerformanceMetrics().getExpectedThroughput() * 0.8) {

            progressTrackingService.alertPerformanceDegradation(
                event.getBatchId(),
                event.getPerformanceMetrics(),
                correlationId
            );
        }

        // Record performance metrics
        metricsService.recordBatchPerformanceMetrics(
            event.getBatchId(),
            event.getPerformanceMetrics().getCurrentThroughput(),
            event.getPerformanceMetrics().getAverageProcessingTime(),
            event.getPerformanceMetrics().getCpuUtilization(),
            event.getPerformanceMetrics().getMemoryUtilization()
        );
    }

    private void processErrorRateUpdate(BatchProgressUpdateEvent event, String correlationId) {
        // Update error rate tracking
        progressTrackingService.updateErrorRateTracking(
            event.getBatchId(),
            event.getCurrentErrorRate(),
            event.getErrorRateThreshold(),
            correlationId
        );

        // Check if error rate exceeds threshold
        if (event.getCurrentErrorRate() > event.getErrorRateThreshold()) {
            progressTrackingService.alertHighErrorRate(
                event.getBatchId(),
                event.getCurrentErrorRate(),
                event.getErrorRateThreshold(),
                correlationId
            );

            // Consider pausing batch if error rate is critically high
            if (event.getCurrentErrorRate() > event.getErrorRateThreshold() * 2) {
                batchProcessingService.pauseBatch(
                    event.getBatchId(),
                    String.format("Critical error rate: %.2f%%", event.getCurrentErrorRate()),
                    correlationId
                );
            }
        }

        metricsService.recordBatchErrorRate(event.getBatchId(), event.getCurrentErrorRate());
    }

    private void processThroughputUpdate(BatchProgressUpdateEvent event, String correlationId) {
        // Update throughput tracking
        progressTrackingService.updateThroughputTracking(
            event.getBatchId(),
            event.getCurrentThroughput(),
            event.getExpectedThroughput(),
            correlationId
        );

        // Analyze throughput trends
        progressTrackingService.analyzeThroughputTrends(
            event.getBatchId(),
            event.getThroughputHistory(),
            correlationId
        );

        // Alert if throughput is consistently low
        if (event.getCurrentThroughput() < event.getExpectedThroughput() * 0.6) {
            progressTrackingService.alertLowThroughput(
                event.getBatchId(),
                event.getCurrentThroughput(),
                event.getExpectedThroughput(),
                correlationId
            );
        }

        metricsService.recordBatchThroughput(event.getBatchId(), event.getCurrentThroughput());
    }

    private void processEtaUpdate(BatchProgressUpdateEvent event, String correlationId) {
        // Update ETA calculations
        progressTrackingService.updateEtaCalculations(
            event.getBatchId(),
            event.getEstimatedCompletion(),
            event.getOriginalEta(),
            correlationId
        );

        // Check for significant ETA changes
        if (event.getEstimatedCompletion() != null && event.getOriginalEta() != null) {
            long etaVarianceMinutes = Math.abs(
                event.getEstimatedCompletion().toEpochSecond() - event.getOriginalEta().toEpochSecond()) / 60;

            if (etaVarianceMinutes > 60) { // More than 1 hour variance
                progressTrackingService.alertEtaVariance(
                    event.getBatchId(),
                    event.getEstimatedCompletion(),
                    event.getOriginalEta(),
                    etaVarianceMinutes,
                    correlationId
                );
            }
        }

        metricsService.recordBatchEtaUpdate(event.getBatchId(), event.getEstimatedCompletion());
    }

    private boolean isSignificantMilestone(String milestoneName, String milestoneValue) {
        // Define what constitutes a significant milestone
        return milestoneName != null && (
            milestoneName.contains("50%") ||
            milestoneName.contains("75%") ||
            milestoneName.contains("90%") ||
            milestoneName.contains("COMPLETED") ||
            milestoneName.contains("MILESTONE") ||
            (milestoneValue != null && milestoneValue.contains("100"))
        );
    }
}