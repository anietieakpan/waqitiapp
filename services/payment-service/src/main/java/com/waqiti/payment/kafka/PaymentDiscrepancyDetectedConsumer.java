package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentDiscrepancyDetectedEvent;
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
public class PaymentDiscrepancyDetectedConsumer {

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
        successCounter = Counter.builder("payment_discrepancy_detected_processed_total")
            .description("Total number of successfully processed payment discrepancy detected events")
            .register(meterRegistry);
        errorCounter = Counter.builder("payment_discrepancy_detected_errors_total")
            .description("Total number of payment discrepancy detected processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("payment_discrepancy_detected_processing_duration")
            .description("Time taken to process payment discrepancy detected events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"payment.discrepancy.detected", "payment-discrepancies", "reconciliation-discrepancies"},
        groupId = "payment-discrepancy-detected-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "payment-discrepancy-detected", fallbackMethod = "handlePaymentDiscrepancyDetectedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handlePaymentDiscrepancyDetectedEvent(
            @Payload PaymentDiscrepancyDetectedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("discrepancy-%s-p%d-o%d", event.getPaymentId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getPaymentId(), event.getDiscrepancyType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing payment discrepancy detected: paymentId={}, type={}, severity={}, amount={}",
                event.getPaymentId(), event.getDiscrepancyType(), event.getSeverity(), event.getDiscrepancyAmount());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Record the discrepancy
            recordDiscrepancy(event, correlationId);

            // Determine action based on severity
            handleDiscrepancyBySeverity(event, correlationId);

            // Perform automatic resolution attempts if applicable
            attemptAutomaticResolution(event, correlationId);

            // Send alerts based on severity
            sendDiscrepancyAlerts(event, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("PAYMENT_DISCREPANCY_DETECTED_PROCESSED", event.getPaymentId(),
                Map.of("discrepancyType", event.getDiscrepancyType(), "severity", event.getSeverity(),
                    "discrepancyAmount", event.getDiscrepancyAmount(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process payment discrepancy detected event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("payment-discrepancy-detected-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handlePaymentDiscrepancyDetectedEventFallback(
            PaymentDiscrepancyDetectedEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("discrepancy-fallback-%s-p%d-o%d", event.getPaymentId(), partition, offset);

        log.error("Circuit breaker fallback triggered for payment discrepancy detected: paymentId={}, error={}",
            event.getPaymentId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("payment-discrepancy-detected-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical notification for high severity discrepancies
        if ("HIGH".equals(event.getSeverity()) || "CRITICAL".equals(event.getSeverity())) {
            try {
                notificationService.sendCriticalAlert(
                    "Critical Payment Discrepancy Processing Failed",
                    String.format("Payment %s discrepancy processing failed: %s", event.getPaymentId(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send critical alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltPaymentDiscrepancyDetectedEvent(
            @Payload PaymentDiscrepancyDetectedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-discrepancy-%s-%d", event.getPaymentId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Payment discrepancy detected permanently failed: paymentId={}, topic={}, error={}",
            event.getPaymentId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logPaymentEvent("PAYMENT_DISCREPANCY_DETECTED_DLT_EVENT", event.getPaymentId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "discrepancyType", event.getDiscrepancyType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "Payment Discrepancy Detected Dead Letter Event",
                String.format("Payment %s discrepancy detection sent to DLT: %s", event.getPaymentId(), exceptionMessage),
                Map.of("paymentId", event.getPaymentId(), "topic", topic, "correlationId", correlationId)
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

    private void recordDiscrepancy(PaymentDiscrepancyDetectedEvent event, String correlationId) {
        // Record the discrepancy in the discrepancy service
        discrepancyService.recordDiscrepancy(
            event.getPaymentId(),
            event.getDiscrepancyType(),
            event.getDiscrepancyAmount(),
            event.getExpectedAmount(),
            event.getActualAmount(),
            event.getSeverity(),
            event.getDescription(),
            event.getDetectedBy(),
            event.getTimestamp()
        );

        // Update metrics
        metricsService.incrementDiscrepanciesDetected(event.getDiscrepancyType(), event.getSeverity());
        metricsService.recordDiscrepancyAmount(event.getDiscrepancyAmount());

        log.info("Payment discrepancy recorded: paymentId={}, type={}, amount={}",
            event.getPaymentId(), event.getDiscrepancyType(), event.getDiscrepancyAmount());
    }

    private void handleDiscrepancyBySeverity(PaymentDiscrepancyDetectedEvent event, String correlationId) {
        switch (event.getSeverity().toUpperCase()) {
            case "CRITICAL":
                handleCriticalDiscrepancy(event, correlationId);
                break;

            case "HIGH":
                handleHighSeverityDiscrepancy(event, correlationId);
                break;

            case "MEDIUM":
                handleMediumSeverityDiscrepancy(event, correlationId);
                break;

            case "LOW":
                handleLowSeverityDiscrepancy(event, correlationId);
                break;

            default:
                log.warn("Unknown discrepancy severity: {}", event.getSeverity());
                handleMediumSeverityDiscrepancy(event, correlationId);
                break;
        }
    }

    private void handleCriticalDiscrepancy(PaymentDiscrepancyDetectedEvent event, String correlationId) {
        // Immediately halt payment processing if needed
        discrepancyService.haltRelatedPaymentProcessing(event.getPaymentId());

        // Create urgent investigation case
        discrepancyService.createUrgentInvestigationCase(
            event.getPaymentId(),
            event.getDiscrepancyType(),
            event.getDiscrepancyAmount(),
            correlationId
        );

        // Send to fraud detection if suspicious
        if (discrepancyService.isSuspiciousDiscrepancy(event)) {
            kafkaTemplate.send("fraud-detection-events", Map.of(
                "type", "SUSPICIOUS_DISCREPANCY",
                "paymentId", event.getPaymentId(),
                "discrepancyAmount", event.getDiscrepancyAmount(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Escalate to financial operations
        kafkaTemplate.send("financial-operations-escalations", Map.of(
            "type", "CRITICAL_PAYMENT_DISCREPANCY",
            "paymentId", event.getPaymentId(),
            "severity", "CRITICAL",
            "priority", "EMERGENCY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.error("Critical payment discrepancy handled: paymentId={}, amount={}",
            event.getPaymentId(), event.getDiscrepancyAmount());
    }

    private void handleHighSeverityDiscrepancy(PaymentDiscrepancyDetectedEvent event, String correlationId) {
        // Create high priority investigation case
        discrepancyService.createHighPriorityInvestigationCase(
            event.getPaymentId(),
            event.getDiscrepancyType(),
            event.getDiscrepancyAmount(),
            correlationId
        );

        // Check if payment should be held for review
        if (discrepancyService.shouldHoldPaymentForReview(event)) {
            discrepancyService.holdPaymentForReview(event.getPaymentId(), correlationId);

            kafkaTemplate.send("payment-holds", Map.of(
                "paymentId", event.getPaymentId(),
                "reason", "DISCREPANCY_REVIEW",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Send to reconciliation team
        kafkaTemplate.send("reconciliation-review-queue", Map.of(
            "paymentId", event.getPaymentId(),
            "discrepancyType", event.getDiscrepancyType(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.warn("High severity payment discrepancy handled: paymentId={}, amount={}",
            event.getPaymentId(), event.getDiscrepancyAmount());
    }

    private void handleMediumSeverityDiscrepancy(PaymentDiscrepancyDetectedEvent event, String correlationId) {
        // Create standard investigation case
        discrepancyService.createStandardInvestigationCase(
            event.getPaymentId(),
            event.getDiscrepancyType(),
            event.getDiscrepancyAmount(),
            correlationId
        );

        // Add to reconciliation queue
        kafkaTemplate.send("reconciliation-queue", Map.of(
            "paymentId", event.getPaymentId(),
            "discrepancyType", event.getDiscrepancyType(),
            "priority", "MEDIUM",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Medium severity payment discrepancy handled: paymentId={}, amount={}",
            event.getPaymentId(), event.getDiscrepancyAmount());
    }

    private void handleLowSeverityDiscrepancy(PaymentDiscrepancyDetectedEvent event, String correlationId) {
        // Log for monitoring and potential batch processing
        discrepancyService.logLowSeverityDiscrepancy(
            event.getPaymentId(),
            event.getDiscrepancyType(),
            event.getDiscrepancyAmount(),
            correlationId
        );

        // Add to low priority reconciliation batch
        kafkaTemplate.send("reconciliation-batch-queue", Map.of(
            "paymentId", event.getPaymentId(),
            "discrepancyType", event.getDiscrepancyType(),
            "priority", "LOW",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.debug("Low severity payment discrepancy handled: paymentId={}, amount={}",
            event.getPaymentId(), event.getDiscrepancyAmount());
    }

    private void attemptAutomaticResolution(PaymentDiscrepancyDetectedEvent event, String correlationId) {
        // Check if this discrepancy type can be automatically resolved
        if (discrepancyService.canAutoResolve(event.getDiscrepancyType())) {
            try {
                boolean resolved = discrepancyService.attemptAutoResolution(
                    event.getPaymentId(),
                    event.getDiscrepancyType(),
                    event.getDiscrepancyAmount(),
                    correlationId
                );

                if (resolved) {
                    // Send resolution event
                    kafkaTemplate.send("payment.discrepancy.resolved", Map.of(
                        "paymentId", event.getPaymentId(),
                        "discrepancyType", event.getDiscrepancyType(),
                        "resolutionMethod", "AUTOMATIC",
                        "resolvedBy", "SYSTEM",
                        "correlationId", correlationId,
                        "timestamp", Instant.now()
                    ));

                    metricsService.incrementAutoResolvedDiscrepancies(event.getDiscrepancyType());

                    log.info("Payment discrepancy auto-resolved: paymentId={}, type={}",
                        event.getPaymentId(), event.getDiscrepancyType());
                } else {
                    log.info("Payment discrepancy auto-resolution failed: paymentId={}, type={}",
                        event.getPaymentId(), event.getDiscrepancyType());
                }
            } catch (Exception e) {
                log.error("Error during automatic resolution attempt: {}", e.getMessage(), e);
            }
        } else {
            log.debug("Payment discrepancy type not eligible for auto-resolution: type={}",
                event.getDiscrepancyType());
        }
    }

    private void sendDiscrepancyAlerts(PaymentDiscrepancyDetectedEvent event, String correlationId) {
        // Send alerts based on severity and amount thresholds
        if ("CRITICAL".equals(event.getSeverity()) ||
            discrepancyService.exceedsAlertThreshold(event.getDiscrepancyAmount())) {

            // Send immediate alert
            notificationService.sendCriticalAlert(
                "Critical Payment Discrepancy Detected",
                String.format("Critical discrepancy detected for payment %s: %s (%s)",
                    event.getPaymentId(), event.getDescription(), event.getDiscrepancyAmount()),
                Map.of("paymentId", event.getPaymentId(), "discrepancyType", event.getDiscrepancyType(),
                    "amount", event.getDiscrepancyAmount(), "correlationId", correlationId)
            );

            // Send to operations dashboard
            kafkaTemplate.send("operations-dashboard-alerts", Map.of(
                "type", "PAYMENT_DISCREPANCY",
                "severity", event.getSeverity(),
                "paymentId", event.getPaymentId(),
                "discrepancyAmount", event.getDiscrepancyAmount(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else if ("HIGH".equals(event.getSeverity())) {
            // Send operational alert
            notificationService.sendOperationalAlert(
                "Payment Discrepancy Detected",
                String.format("Discrepancy detected for payment %s: %s",
                    event.getPaymentId(), event.getDescription()),
                "HIGH"
            );
        }

        // Send to analytics for pattern detection
        kafkaTemplate.send("payment-analytics", Map.of(
            "type", "DISCREPANCY_DETECTED",
            "paymentId", event.getPaymentId(),
            "discrepancyType", event.getDiscrepancyType(),
            "severity", event.getSeverity(),
            "amount", event.getDiscrepancyAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Discrepancy alerts sent: paymentId={}, severity={}",
            event.getPaymentId(), event.getSeverity());
    }
}