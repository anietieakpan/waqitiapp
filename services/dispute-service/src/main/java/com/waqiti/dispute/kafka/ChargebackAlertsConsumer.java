package com.waqiti.dispute.kafka;

import com.waqiti.payment.dto.PaymentChargebackEvent;
import com.waqiti.dispute.entity.Dispute;
import com.waqiti.dispute.service.TransactionDisputeService;
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
import jakarta.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChargebackAlertsConsumer {

    private final TransactionDisputeService disputeService;
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
        successCounter = Counter.builder("chargeback_alerts_processed_total")
            .description("Total number of successfully processed chargeback alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("chargeback_alerts_errors_total")
            .description("Total number of chargeback alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("chargeback_alerts_processing_duration")
            .description("Time taken to process chargeback alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"chargeback-alerts", "chargeback-alerts.DLQ"},
        groupId = "dispute-chargeback-alerts-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "chargeback-alerts", fallbackMethod = "handleChargebackAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleChargebackAlertEvent(
            @Payload PaymentChargebackEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("chargeback-alert-%s-p%d-o%d", event.getChargebackId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getChargebackId(), event.getEventId(), event.getTimestamp());
        boolean isDLQMessage = topic.endsWith(".DLQ");

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing chargeback alert: chargebackId={}, customerId={}, amount={}, isDLQ={}",
                event.getChargebackId(), event.getCustomerId(), event.getChargebackAmount(), isDLQMessage);

            // Clean expired entries periodically
            cleanExpiredEntries();

            if (isDLQMessage) {
                processDLQChargebackAlert(event, correlationId);
            } else {
                processChargebackAlert(event, correlationId);
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logDisputeEvent("CHARGEBACK_ALERT_PROCESSED", event.getChargebackId(),
                Map.of("chargebackId", event.getChargebackId(), "customerId", event.getCustomerId(),
                    "merchantId", event.getMerchantId(), "amount", event.getChargebackAmount(),
                    "priority", event.getPriority(), "isDLQ", isDLQMessage,
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process chargeback alert: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("chargeback-alerts-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3, "isDLQ", isDLQMessage));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleChargebackAlertEventFallback(
            PaymentChargebackEvent event,
            int partition,
            long offset,
            String topic,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("chargeback-alert-fallback-%s-p%d-o%d", event.getChargebackId(), partition, offset);

        log.error("Circuit breaker fallback triggered for chargeback alert: chargebackId={}, error={}",
            event.getChargebackId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("chargeback-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Chargeback Alert Circuit Breaker Triggered",
                String.format("Chargeback %s alert processing failed: %s", event.getChargebackId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltChargebackAlertEvent(
            @Payload PaymentChargebackEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-chargeback-alert-%s-%d", event.getChargebackId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Chargeback alert permanently failed: chargebackId={}, topic={}, error={}",
            event.getChargebackId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logDisputeEvent("CHARGEBACK_ALERT_DLT_EVENT", event.getChargebackId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "chargebackId", event.getChargebackId(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Chargeback Alert Dead Letter Event",
                String.format("Chargeback %s alert sent to DLT: %s", event.getChargebackId(), exceptionMessage),
                Map.of("chargebackId", event.getChargebackId(), "topic", topic, "correlationId", correlationId)
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

    private void processChargebackAlert(PaymentChargebackEvent event, String correlationId) {
        log.info("Processing chargeback alert: chargebackId={}, priority={}, amount={}",
            event.getChargebackId(), event.getPriority(), event.getChargebackAmount());

        // Process based on chargeback status and priority
        switch (event.getStatus()) {
            case RECEIVED:
                processNewChargebackAlert(event, correlationId);
                break;

            case UNDER_REVIEW:
                processChargebackUnderReview(event, correlationId);
                break;

            case REPRESENTMENT_SUBMITTED:
                processRepresentmentSubmitted(event, correlationId);
                break;

            case WON:
                processChargebackWon(event, correlationId);
                break;

            case LOST:
                processChargebackLost(event, correlationId);
                break;

            default:
                log.warn("Unknown chargeback status for alert: {}", event.getStatus());
                break;
        }

        // Handle priority-based alerting
        handlePriorityAlerting(event, correlationId);

        // Update metrics
        meterRegistry.counter("chargeback_alerts_by_status_total",
            "status", event.getStatus().name(),
            "priority", event.getPriority().name()).increment();

        log.info("Chargeback alert processed: chargebackId={}, status={}",
            event.getChargebackId(), event.getStatus());
    }

    private void processDLQChargebackAlert(PaymentChargebackEvent event, String correlationId) {
        log.warn("Processing DLQ chargeback alert: chargebackId={}, requires manual intervention",
            event.getChargebackId());

        // Send to manual review queue with high priority
        kafkaTemplate.send("chargeback-manual-queue", Map.of(
            "chargebackId", event.getChargebackId(),
            "transactionId", event.getTransactionId(),
            "priority", "HIGH",
            "reason", "DLQ_RECOVERY",
            "source", "DLQ",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Notify operations team
        notificationService.sendOperationalAlert(
            "Chargeback DLQ Alert Recovered",
            String.format("Chargeback %s from DLQ requires manual processing", event.getChargebackId()),
            "MEDIUM"
        );

        meterRegistry.counter("chargeback_dlq_recovery_total").increment();
    }

    private void processNewChargebackAlert(PaymentChargebackEvent event, String correlationId) {
        // Create or update dispute record
        disputeService.handleChargebackReceived(event.getChargebackId(), event.getTransactionId(), correlationId);

        // Evaluate if response is required
        if (Boolean.TRUE.equals(event.getResponseRequired()) && event.canContest()) {
            kafkaTemplate.send("chargeback-investigations", Map.of(
                "chargebackId", event.getChargebackId(),
                "transactionId", event.getTransactionId(),
                "deadline", event.getResponseDeadline(),
                "priority", event.getPriority(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Notify customer
        notificationService.sendNotification(event.getCustomerId(), "Chargeback Alert",
            String.format("A chargeback has been initiated for transaction %s", event.getTransactionId()),
            correlationId);
    }

    private void processChargebackUnderReview(PaymentChargebackEvent event, String correlationId) {
        // Update dispute status
        disputeService.updateChargebackStatus(event.getChargebackId(), "UNDER_REVIEW", correlationId);

        // Set up monitoring for deadline
        if (event.getResponseDeadline() != null) {
            kafkaTemplate.send("chargeback-deadline-monitoring", Map.of(
                "chargebackId", event.getChargebackId(),
                "deadline", event.getResponseDeadline(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
    }

    private void processRepresentmentSubmitted(PaymentChargebackEvent event, String correlationId) {
        // Update dispute with representment details
        disputeService.updateChargebackRepresentment(event.getChargebackId(), correlationId);

        // Notify merchant
        notificationService.sendNotification(event.getMerchantId(), "Representment Submitted",
            String.format("Representment submitted for chargeback %s", event.getChargebackId()),
            correlationId);
    }

    private void processChargebackWon(PaymentChargebackEvent event, String correlationId) {
        // Update dispute as won
        disputeService.finalizeChargeback(event.getChargebackId(), "WON", correlationId);

        // Notify stakeholders
        notificationService.sendNotification(event.getMerchantId(), "Chargeback Won",
            String.format("Chargeback %s was successfully defended", event.getChargebackId()),
            correlationId);

        // Reverse any holds or reserves
        kafkaTemplate.send("financial-adjustments", Map.of(
            "type", "CHARGEBACK_REVERSAL",
            "chargebackId", event.getChargebackId(),
            "amount", event.getChargebackAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processChargebackLost(PaymentChargebackEvent event, String correlationId) {
        // Update dispute as lost
        disputeService.finalizeChargeback(event.getChargebackId(), "LOST", correlationId);

        // Process financial impact
        kafkaTemplate.send("financial-adjustments", Map.of(
            "type", "CHARGEBACK_LOSS",
            "chargebackId", event.getChargebackId(),
            "amount", event.getChargebackAmount(),
            "fees", event.getChargebackFee(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Notify relevant parties
        notificationService.sendNotification(event.getMerchantId(), "Chargeback Lost",
            String.format("Chargeback %s was not successfully defended", event.getChargebackId()),
            correlationId);
    }

    private void handlePriorityAlerting(PaymentChargebackEvent event, String correlationId) {
        switch (event.getPriority()) {
            case CRITICAL:
                notificationService.sendCriticalAlert(
                    "Critical Chargeback Alert",
                    String.format("Critical chargeback %s requires immediate attention", event.getChargebackId()),
                    Map.of("chargebackId", event.getChargebackId(), "priority", "CRITICAL")
                );
                break;

            case HIGH:
                if (event.isUrgent()) {
                    notificationService.sendHighPriorityAlert(
                        "Urgent Chargeback Alert",
                        String.format("Urgent chargeback %s with approaching deadline", event.getChargebackId()),
                        Map.of("chargebackId", event.getChargebackId(), "deadline", event.getResponseDeadline())
                    );
                }
                break;

            default:
                // Standard processing for medium/low priority
                break;
        }
    }
}