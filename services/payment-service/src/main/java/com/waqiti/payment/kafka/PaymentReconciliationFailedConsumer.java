package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentReconciliationFailedEvent;
import com.waqiti.payment.domain.PaymentReconciliation;
import com.waqiti.payment.domain.ReconciliationFailure;
import com.waqiti.payment.repository.PaymentReconciliationRepository;
import com.waqiti.payment.repository.ReconciliationFailureRepository;
import com.waqiti.payment.service.ReconciliationService;
import com.waqiti.payment.service.FailureAnalysisService;
import com.waqiti.payment.service.EscalationService;
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
public class PaymentReconciliationFailedConsumer {

    private final PaymentReconciliationRepository reconciliationRepository;
    private final ReconciliationFailureRepository failureRepository;
    private final ReconciliationService reconciliationService;
    private final FailureAnalysisService failureAnalysisService;
    private final EscalationService escalationService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    private Counter criticalFailureCounter;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("payment_reconciliation_failed_processed_total")
            .description("Total number of successfully processed payment reconciliation failed events")
            .register(meterRegistry);
        errorCounter = Counter.builder("payment_reconciliation_failed_errors_total")
            .description("Total number of payment reconciliation failed processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("payment_reconciliation_failed_processing_duration")
            .description("Time taken to process payment reconciliation failed events")
            .register(meterRegistry);
        criticalFailureCounter = Counter.builder("payment_reconciliation_critical_failures_total")
            .description("Total number of critical payment reconciliation failures")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"payment.reconciliation.failed", "payment-reconciliation-failed", "reconciliation-failed-events"},
        groupId = "payment-reconciliation-failed-service-group",
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
    @CircuitBreaker(name = "payment-reconciliation-failed", fallbackMethod = "handlePaymentReconciliationFailedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handlePaymentReconciliationFailedEvent(
            @Payload PaymentReconciliationFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("rec-failed-%s-p%d-o%d", event.getReconciliationId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getReconciliationId(), event.getFailureType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing payment reconciliation failure: reconciliationId={}, failureType={}, error={}",
                event.getReconciliationId(), event.getFailureType(), event.getErrorMessage());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getFailureType()) {
                case DATA_MISMATCH:
                    processDataMismatchFailure(event, correlationId);
                    break;

                case TIMEOUT_FAILURE:
                    processTimeoutFailure(event, correlationId);
                    break;

                case SYSTEM_ERROR:
                    processSystemErrorFailure(event, correlationId);
                    break;

                case VALIDATION_ERROR:
                    processValidationError(event, correlationId);
                    break;

                case DISCREPANCY_THRESHOLD_EXCEEDED:
                    processDiscrepancyThresholdExceeded(event, correlationId);
                    break;

                case GATEWAY_UNAVAILABLE:
                    processGatewayUnavailable(event, correlationId);
                    break;

                case CRITICAL_FAILURE:
                    processCriticalFailure(event, correlationId);
                    break;

                case MANUAL_INTERVENTION_REQUIRED:
                    processManualInterventionRequired(event, correlationId);
                    break;

                default:
                    log.warn("Unknown payment reconciliation failure type: {}", event.getFailureType());
                    processUnknownFailure(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("PAYMENT_RECONCILIATION_FAILED_EVENT_PROCESSED", event.getReconciliationId(),
                Map.of("failureType", event.getFailureType(), "errorMessage", event.getErrorMessage(),
                    "severity", event.getSeverity(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process payment reconciliation failed event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("payment-reconciliation-failed-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handlePaymentReconciliationFailedEventFallback(
            PaymentReconciliationFailedEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("rec-failed-fallback-%s-p%d-o%d", event.getReconciliationId(), partition, offset);

        log.error("Circuit breaker fallback triggered for payment reconciliation failed: reconciliationId={}, error={}",
            event.getReconciliationId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("payment-reconciliation-failed-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Payment Reconciliation Failed Circuit Breaker Triggered",
                String.format("Reconciliation %s failure processing failed: %s", event.getReconciliationId(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltPaymentReconciliationFailedEvent(
            @Payload PaymentReconciliationFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-rec-failed-%s-%d", event.getReconciliationId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Payment reconciliation failed permanently failed: reconciliationId={}, topic={}, error={}",
            event.getReconciliationId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logPaymentEvent("PAYMENT_RECONCILIATION_FAILED_DLT_EVENT", event.getReconciliationId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "failureType", event.getFailureType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "Payment Reconciliation Failed Dead Letter Event",
                String.format("Critical: Reconciliation %s failure sent to DLT: %s", event.getReconciliationId(), exceptionMessage),
                Map.of("reconciliationId", event.getReconciliationId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
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
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processDataMismatchFailure(PaymentReconciliationFailedEvent event, String correlationId) {
        updateReconciliationStatus(event, "FAILED_DATA_MISMATCH", correlationId);

        ReconciliationFailure failure = createFailureRecord(event, correlationId);
        failureRepository.save(failure);

        failureAnalysisService.analyzeDataMismatch(event.getReconciliationId());

        // Create retry task with manual review
        kafkaTemplate.send("reconciliation-retry-queue", Map.of(
            "reconciliationId", event.getReconciliationId(),
            "failureType", "DATA_MISMATCH",
            "retryAfter", LocalDateTime.now().plusHours(1),
            "requiresManualReview", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendNotification("RECONCILIATION_TEAM", "Data Mismatch in Reconciliation",
            String.format("Reconciliation %s failed due to data mismatch: %s",
                event.getReconciliationId(), event.getErrorMessage()),
            correlationId);

        metricsService.recordReconciliationFailure("DATA_MISMATCH");

        log.warn("Reconciliation data mismatch failure: reconciliationId={}, error={}",
            event.getReconciliationId(), event.getErrorMessage());
    }

    private void processTimeoutFailure(PaymentReconciliationFailedEvent event, String correlationId) {
        updateReconciliationStatus(event, "FAILED_TIMEOUT", correlationId);

        ReconciliationFailure failure = createFailureRecord(event, correlationId);
        failureRepository.save(failure);

        // Automatic retry for timeout failures
        kafkaTemplate.send("reconciliation-retry-queue", Map.of(
            "reconciliationId", event.getReconciliationId(),
            "failureType", "TIMEOUT",
            "retryAfter", LocalDateTime.now().plusMinutes(30),
            "maxRetries", 3,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordReconciliationFailure("TIMEOUT");

        log.warn("Reconciliation timeout failure: reconciliationId={}, will retry in 30 minutes",
            event.getReconciliationId());
    }

    private void processSystemErrorFailure(PaymentReconciliationFailedEvent event, String correlationId) {
        updateReconciliationStatus(event, "FAILED_SYSTEM_ERROR", correlationId);

        ReconciliationFailure failure = createFailureRecord(event, correlationId);
        failureRepository.save(failure);

        escalationService.escalateSystemError(event.getReconciliationId(), event.getErrorMessage());

        notificationService.sendOperationalAlert(
            "System Error in Payment Reconciliation",
            String.format("Reconciliation %s failed due to system error: %s",
                event.getReconciliationId(), event.getErrorMessage()),
            "HIGH"
        );

        kafkaTemplate.send("system-health-alerts", Map.of(
            "component", "PAYMENT_RECONCILIATION",
            "errorType", "SYSTEM_ERROR",
            "reconciliationId", event.getReconciliationId(),
            "errorMessage", event.getErrorMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordReconciliationFailure("SYSTEM_ERROR");

        log.error("Reconciliation system error failure: reconciliationId={}, error={}",
            event.getReconciliationId(), event.getErrorMessage());
    }

    private void processValidationError(PaymentReconciliationFailedEvent event, String correlationId) {
        updateReconciliationStatus(event, "FAILED_VALIDATION", correlationId);

        ReconciliationFailure failure = createFailureRecord(event, correlationId);
        failureRepository.save(failure);

        failureAnalysisService.analyzeValidationError(event.getReconciliationId(), event.getValidationErrors());

        notificationService.sendNotification("DATA_QUALITY_TEAM", "Validation Error in Reconciliation",
            String.format("Reconciliation %s failed validation: %s",
                event.getReconciliationId(), event.getErrorMessage()),
            correlationId);

        metricsService.recordReconciliationFailure("VALIDATION_ERROR");

        log.warn("Reconciliation validation error: reconciliationId={}, errors={}",
            event.getReconciliationId(), event.getValidationErrors());
    }

    private void processDiscrepancyThresholdExceeded(PaymentReconciliationFailedEvent event, String correlationId) {
        updateReconciliationStatus(event, "FAILED_DISCREPANCY_THRESHOLD", correlationId);

        ReconciliationFailure failure = createFailureRecord(event, correlationId);
        failureRepository.save(failure);

        escalationService.escalateDiscrepancyThreshold(event.getReconciliationId(), event.getDiscrepancyAmount());

        notificationService.sendCriticalAlert(
            "Discrepancy Threshold Exceeded",
            String.format("Reconciliation %s exceeded discrepancy threshold: %s",
                event.getReconciliationId(), event.getDiscrepancyAmount()),
            Map.of("reconciliationId", event.getReconciliationId(),
                   "discrepancyAmount", event.getDiscrepancyAmount(),
                   "correlationId", correlationId)
        );

        metricsService.recordReconciliationFailure("DISCREPANCY_THRESHOLD_EXCEEDED");
        metricsService.recordDiscrepancyThresholdBreach(event.getDiscrepancyAmount());

        log.error("Reconciliation discrepancy threshold exceeded: reconciliationId={}, amount={}",
            event.getReconciliationId(), event.getDiscrepancyAmount());
    }

    private void processGatewayUnavailable(PaymentReconciliationFailedEvent event, String correlationId) {
        updateReconciliationStatus(event, "FAILED_GATEWAY_UNAVAILABLE", correlationId);

        ReconciliationFailure failure = createFailureRecord(event, correlationId);
        failureRepository.save(failure);

        // Retry when gateway becomes available
        kafkaTemplate.send("reconciliation-retry-queue", Map.of(
            "reconciliationId", event.getReconciliationId(),
            "failureType", "GATEWAY_UNAVAILABLE",
            "retryAfter", LocalDateTime.now().plusHours(2),
            "maxRetries", 5,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        kafkaTemplate.send("gateway-health-alerts", Map.of(
            "gateway", event.getGatewayName(),
            "status", "UNAVAILABLE",
            "reconciliationId", event.getReconciliationId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordReconciliationFailure("GATEWAY_UNAVAILABLE");

        log.warn("Reconciliation gateway unavailable: reconciliationId={}, gateway={}",
            event.getReconciliationId(), event.getGatewayName());
    }

    private void processCriticalFailure(PaymentReconciliationFailedEvent event, String correlationId) {
        updateReconciliationStatus(event, "CRITICAL_FAILURE", correlationId);
        criticalFailureCounter.increment();

        ReconciliationFailure failure = createFailureRecord(event, correlationId);
        failure.setCritical(true);
        failureRepository.save(failure);

        escalationService.escalateCriticalFailure(event.getReconciliationId(), event.getErrorMessage());

        notificationService.sendEmergencyAlert(
            "Critical Payment Reconciliation Failure",
            String.format("CRITICAL: Reconciliation %s critical failure: %s",
                event.getReconciliationId(), event.getErrorMessage()),
            Map.of("reconciliationId", event.getReconciliationId(),
                   "errorMessage", event.getErrorMessage(),
                   "correlationId", correlationId)
        );

        metricsService.recordReconciliationFailure("CRITICAL_FAILURE");

        log.error("CRITICAL reconciliation failure: reconciliationId={}, error={}",
            event.getReconciliationId(), event.getErrorMessage());
    }

    private void processManualInterventionRequired(PaymentReconciliationFailedEvent event, String correlationId) {
        updateReconciliationStatus(event, "MANUAL_INTERVENTION_REQUIRED", correlationId);

        ReconciliationFailure failure = createFailureRecord(event, correlationId);
        failure.setRequiresManualIntervention(true);
        failureRepository.save(failure);

        kafkaTemplate.send("manual-intervention-queue", Map.of(
            "reconciliationId", event.getReconciliationId(),
            "failureType", event.getFailureType(),
            "priority", "HIGH",
            "assignedTo", "RECONCILIATION_SPECIALIST",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendNotification("RECONCILIATION_SPECIALIST", "Manual Intervention Required",
            String.format("Reconciliation %s requires manual intervention: %s",
                event.getReconciliationId(), event.getErrorMessage()),
            correlationId);

        metricsService.recordReconciliationFailure("MANUAL_INTERVENTION_REQUIRED");

        log.warn("Reconciliation requires manual intervention: reconciliationId={}, reason={}",
            event.getReconciliationId(), event.getErrorMessage());
    }

    private void processUnknownFailure(PaymentReconciliationFailedEvent event, String correlationId) {
        updateReconciliationStatus(event, "FAILED_UNKNOWN", correlationId);

        ReconciliationFailure failure = createFailureRecord(event, correlationId);
        failure.setRequiresManualIntervention(true);
        failureRepository.save(failure);

        escalationService.escalateUnknownFailure(event.getReconciliationId(), event.getFailureType());

        notificationService.sendOperationalAlert(
            "Unknown Payment Reconciliation Failure",
            String.format("Unknown failure type in reconciliation %s: %s",
                event.getReconciliationId(), event.getFailureType()),
            "MEDIUM"
        );

        metricsService.recordReconciliationFailure("UNKNOWN");

        log.warn("Unknown reconciliation failure type: reconciliationId={}, failureType={}",
            event.getReconciliationId(), event.getFailureType());
    }

    private void updateReconciliationStatus(PaymentReconciliationFailedEvent event, String status, String correlationId) {
        PaymentReconciliation reconciliation = reconciliationRepository.findById(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));

        reconciliation.setStatus(status);
        reconciliation.setFailedAt(LocalDateTime.now());
        reconciliation.setFailureReason(event.getErrorMessage());
        reconciliation.setRetryCount(reconciliation.getRetryCount() + 1);
        reconciliationRepository.save(reconciliation);
    }

    private ReconciliationFailure createFailureRecord(PaymentReconciliationFailedEvent event, String correlationId) {
        return ReconciliationFailure.builder()
            .reconciliationId(event.getReconciliationId())
            .failureType(event.getFailureType())
            .errorMessage(event.getErrorMessage())
            .severity(event.getSeverity())
            .failedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
    }
}