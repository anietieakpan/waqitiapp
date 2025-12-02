package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentDiscrepancyResolvedEvent;
import com.waqiti.payment.service.PaymentDiscrepancyService;
import com.waqiti.payment.service.PaymentReconciliationService;
import com.waqiti.payment.service.PaymentMetricsService;
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
public class PaymentDiscrepancyResolvedConsumer {

    private final PaymentDiscrepancyService discrepancyService;
    private final PaymentReconciliationService reconciliationService;
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

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("payment_discrepancy_resolved_processed_total")
            .description("Total number of successfully processed payment discrepancy resolved events")
            .register(meterRegistry);
        errorCounter = Counter.builder("payment_discrepancy_resolved_errors_total")
            .description("Total number of payment discrepancy resolved processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("payment_discrepancy_resolved_processing_duration")
            .description("Time taken to process payment discrepancy resolved events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"payment.discrepancy.resolved", "payment-discrepancies-resolved", "reconciliation-resolved"},
        groupId = "payment-discrepancy-resolved-service-group",
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
    @CircuitBreaker(name = "payment-discrepancy-resolved", fallbackMethod = "handlePaymentDiscrepancyResolvedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handlePaymentDiscrepancyResolvedEvent(
            @Payload PaymentDiscrepancyResolvedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("resolved-%s-p%d-o%d", event.getPaymentId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getPaymentId(), event.getDiscrepancyId(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing payment discrepancy resolved: paymentId={}, discrepancyId={}, resolvedBy={}, method={}",
                event.getPaymentId(), event.getDiscrepancyId(), event.getResolvedBy(), event.getResolutionMethod());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Update discrepancy status
            updateDiscrepancyStatus(event, correlationId);

            // Process resolution based on method
            processResolutionMethod(event, correlationId);

            // Resume payment processing if needed
            resumePaymentProcessing(event, correlationId);

            // Send notifications
            sendResolutionNotifications(event, correlationId);

            // Update analytics and reporting
            updateAnalyticsAndReporting(event, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("PAYMENT_DISCREPANCY_RESOLVED_PROCESSED", event.getPaymentId(),
                Map.of("discrepancyId", event.getDiscrepancyId(), "resolutionMethod", event.getResolutionMethod(),
                    "resolvedBy", event.getResolvedBy(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process payment discrepancy resolved event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("payment-discrepancy-resolved-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handlePaymentDiscrepancyResolvedEventFallback(
            PaymentDiscrepancyResolvedEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("resolved-fallback-%s-p%d-o%d", event.getPaymentId(), partition, offset);

        log.error("Circuit breaker fallback triggered for payment discrepancy resolved: paymentId={}, error={}",
            event.getPaymentId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("payment-discrepancy-resolved-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Payment Discrepancy Resolution Circuit Breaker Triggered",
                String.format("Payment %s discrepancy resolution processing failed: %s", event.getPaymentId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltPaymentDiscrepancyResolvedEvent(
            @Payload PaymentDiscrepancyResolvedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-resolved-%s-%d", event.getPaymentId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Payment discrepancy resolved permanently failed: paymentId={}, topic={}, error={}",
            event.getPaymentId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logPaymentEvent("PAYMENT_DISCREPANCY_RESOLVED_DLT_EVENT", event.getPaymentId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "discrepancyId", event.getDiscrepancyId(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Payment Discrepancy Resolved Dead Letter Event",
                String.format("Payment %s discrepancy resolution sent to DLT: %s", event.getPaymentId(), exceptionMessage),
                Map.of("paymentId", event.getPaymentId(), "topic", topic, "correlationId", correlationId)
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

    private void updateDiscrepancyStatus(PaymentDiscrepancyResolvedEvent event, String correlationId) {
        // Update the discrepancy record as resolved
        discrepancyService.markDiscrepancyAsResolved(
            event.getDiscrepancyId(),
            event.getResolutionMethod(),
            event.getResolvedBy(),
            event.getResolutionNotes(),
            event.getTimestamp()
        );

        // Update metrics
        metricsService.incrementDiscrepanciesResolved(event.getDiscrepancyType(), event.getResolutionMethod());
        metricsService.recordDiscrepancyResolutionTime(
            event.getDiscrepancyType(),
            event.getTimestamp().toEpochMilli() - event.getDetectedTimestamp().toEpochMilli()
        );

        log.info("Discrepancy status updated to resolved: discrepancyId={}, method={}",
            event.getDiscrepancyId(), event.getResolutionMethod());
    }

    private void processResolutionMethod(PaymentDiscrepancyResolvedEvent event, String correlationId) {
        switch (event.getResolutionMethod().toUpperCase()) {
            case "AUTOMATIC":
                processAutomaticResolution(event, correlationId);
                break;

            case "MANUAL_ADJUSTMENT":
                processManualAdjustment(event, correlationId);
                break;

            case "RECONCILIATION":
                processReconciliation(event, correlationId);
                break;

            case "REVERSAL":
                processReversal(event, correlationId);
                break;

            case "WRITE_OFF":
                processWriteOff(event, correlationId);
                break;

            case "DISPUTE_RESOLUTION":
                processDisputeResolution(event, correlationId);
                break;

            default:
                log.warn("Unknown resolution method: {}", event.getResolutionMethod());
                processGenericResolution(event, correlationId);
                break;
        }
    }

    private void processAutomaticResolution(PaymentDiscrepancyResolvedEvent event, String correlationId) {
        // Log successful automatic resolution
        discrepancyService.logAutomaticResolution(
            event.getPaymentId(),
            event.getDiscrepancyId(),
            event.getResolutionNotes()
        );

        // Update automatic resolution success rate
        metricsService.recordAutomaticResolutionSuccess(event.getDiscrepancyType());

        log.info("Automatic resolution processed: paymentId={}, discrepancyId={}",
            event.getPaymentId(), event.getDiscrepancyId());
    }

    private void processManualAdjustment(PaymentDiscrepancyResolvedEvent event, String correlationId) {
        // Record manual adjustment
        reconciliationService.recordManualAdjustment(
            event.getPaymentId(),
            event.getAdjustmentAmount(),
            event.getResolvedBy(),
            event.getResolutionNotes(),
            correlationId
        );

        // Send adjustment event for accounting
        kafkaTemplate.send("accounting-adjustments", Map.of(
            "paymentId", event.getPaymentId(),
            "adjustmentAmount", event.getAdjustmentAmount(),
            "reason", "DISCREPANCY_RESOLUTION",
            "adjustedBy", event.getResolvedBy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Manual adjustment processed: paymentId={}, amount={}",
            event.getPaymentId(), event.getAdjustmentAmount());
    }

    private void processReconciliation(PaymentDiscrepancyResolvedEvent event, String correlationId) {
        // Mark reconciliation as complete
        reconciliationService.markReconciliationComplete(
            event.getPaymentId(),
            event.getDiscrepancyId(),
            event.getResolvedBy(),
            correlationId
        );

        // Send reconciliation completion event
        kafkaTemplate.send("payment.reconciliation.completed", Map.of(
            "paymentId", event.getPaymentId(),
            "discrepancyId", event.getDiscrepancyId(),
            "reconciledBy", event.getResolvedBy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Reconciliation processed: paymentId={}, discrepancyId={}",
            event.getPaymentId(), event.getDiscrepancyId());
    }

    private void processReversal(PaymentDiscrepancyResolvedEvent event, String correlationId) {
        // Process payment reversal
        reconciliationService.processPaymentReversal(
            event.getPaymentId(),
            event.getReversalAmount(),
            "DISCREPANCY_RESOLUTION",
            correlationId
        );

        // Send reversal event
        kafkaTemplate.send("payment-reversals", Map.of(
            "paymentId", event.getPaymentId(),
            "reversalAmount", event.getReversalAmount(),
            "reason", "DISCREPANCY_RESOLUTION",
            "initiatedBy", event.getResolvedBy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Reversal processed: paymentId={}, amount={}",
            event.getPaymentId(), event.getReversalAmount());
    }

    private void processWriteOff(PaymentDiscrepancyResolvedEvent event, String correlationId) {
        // Process write-off
        reconciliationService.processWriteOff(
            event.getPaymentId(),
            event.getWriteOffAmount(),
            event.getResolvedBy(),
            event.getResolutionNotes(),
            correlationId
        );

        // Send write-off event for accounting
        kafkaTemplate.send("accounting-writeoffs", Map.of(
            "paymentId", event.getPaymentId(),
            "writeOffAmount", event.getWriteOffAmount(),
            "reason", "DISCREPANCY_RESOLUTION",
            "authorizedBy", event.getResolvedBy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Write-off processed: paymentId={}, amount={}",
            event.getPaymentId(), event.getWriteOffAmount());
    }

    private void processDisputeResolution(PaymentDiscrepancyResolvedEvent event, String correlationId) {
        // Update dispute status
        reconciliationService.updateDisputeStatus(
            event.getPaymentId(),
            "RESOLVED",
            event.getResolutionNotes(),
            correlationId
        );

        // Send dispute resolution event
        kafkaTemplate.send("dispute-resolution-events", Map.of(
            "paymentId", event.getPaymentId(),
            "resolution", "DISCREPANCY_RESOLVED",
            "resolvedBy", event.getResolvedBy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Dispute resolution processed: paymentId={}", event.getPaymentId());
    }

    private void processGenericResolution(PaymentDiscrepancyResolvedEvent event, String correlationId) {
        // Handle generic resolution
        discrepancyService.processGenericResolution(
            event.getPaymentId(),
            event.getDiscrepancyId(),
            event.getResolutionMethod(),
            event.getResolutionNotes(),
            correlationId
        );

        log.info("Generic resolution processed: paymentId={}, method={}",
            event.getPaymentId(), event.getResolutionMethod());
    }

    private void resumePaymentProcessing(PaymentDiscrepancyResolvedEvent event, String correlationId) {
        // Check if payment was held due to this discrepancy
        if (discrepancyService.wasPaymentHeldForDiscrepancy(event.getPaymentId(), event.getDiscrepancyId())) {
            // Resume payment processing
            discrepancyService.resumePaymentProcessing(event.getPaymentId(), correlationId);

            // Send resume event
            kafkaTemplate.send("payment-processing-resumed", Map.of(
                "paymentId", event.getPaymentId(),
                "reason", "DISCREPANCY_RESOLVED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            log.info("Payment processing resumed: paymentId={}", event.getPaymentId());
        }
    }

    private void sendResolutionNotifications(PaymentDiscrepancyResolvedEvent event, String correlationId) {
        // Send notification to relevant stakeholders
        if (event.getOriginalSeverity() != null &&
            ("HIGH".equals(event.getOriginalSeverity()) || "CRITICAL".equals(event.getOriginalSeverity()))) {

            notificationService.sendOperationalAlert(
                "Payment Discrepancy Resolved",
                String.format("Payment %s discrepancy has been resolved using %s method by %s",
                    event.getPaymentId(), event.getResolutionMethod(), event.getResolvedBy()),
                "INFO"
            );
        }

        // Send to operations dashboard
        kafkaTemplate.send("operations-dashboard-updates", Map.of(
            "type", "DISCREPANCY_RESOLVED",
            "paymentId", event.getPaymentId(),
            "discrepancyId", event.getDiscrepancyId(),
            "resolutionMethod", event.getResolutionMethod(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Resolution notifications sent: paymentId={}, discrepancyId={}",
            event.getPaymentId(), event.getDiscrepancyId());
    }

    private void updateAnalyticsAndReporting(PaymentDiscrepancyResolvedEvent event, String correlationId) {
        // Send to payment analytics
        kafkaTemplate.send("payment-analytics", Map.of(
            "type", "DISCREPANCY_RESOLVED",
            "paymentId", event.getPaymentId(),
            "discrepancyType", event.getDiscrepancyType(),
            "resolutionMethod", event.getResolutionMethod(),
            "resolutionTime", event.getTimestamp().toEpochMilli() - event.getDetectedTimestamp().toEpochMilli(),
            "resolvedBy", event.getResolvedBy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Update reporting metrics
        metricsService.updateDiscrepancyResolutionMetrics(
            event.getDiscrepancyType(),
            event.getResolutionMethod(),
            event.getTimestamp().toEpochMilli() - event.getDetectedTimestamp().toEpochMilli()
        );

        log.info("Analytics and reporting updated: paymentId={}, discrepancyId={}",
            event.getPaymentId(), event.getDiscrepancyId());
    }
}