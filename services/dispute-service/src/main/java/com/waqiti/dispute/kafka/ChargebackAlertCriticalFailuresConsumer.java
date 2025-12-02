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
public class ChargebackAlertCriticalFailuresConsumer {

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
        successCounter = Counter.builder("chargeback_alert_critical_failures_processed_total")
            .description("Total number of successfully processed chargeback critical failure alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("chargeback_alert_critical_failures_errors_total")
            .description("Total number of chargeback critical failure alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("chargeback_alert_critical_failures_processing_duration")
            .description("Time taken to process chargeback critical failure alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"chargeback-alert-critical-failures"},
        groupId = "dispute-chargeback-critical-failures-group",
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
    @CircuitBreaker(name = "chargeback-alert-critical-failures", fallbackMethod = "handleChargebackCriticalFailureEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleChargebackCriticalFailureEvent(
            @Payload PaymentChargebackEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("critical-failure-%s-p%d-o%d", event.getChargebackId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getChargebackId(), event.getEventId(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing chargeback critical failure alert: chargebackId={}, customerId={}, amount={}",
                event.getChargebackId(), event.getCustomerId(), event.getChargebackAmount());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Process critical failure alert
            processCriticalFailureAlert(event, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logDisputeEvent("CHARGEBACK_CRITICAL_FAILURE_ALERT_PROCESSED", event.getChargebackId(),
                Map.of("chargebackId", event.getChargebackId(), "customerId", event.getCustomerId(),
                    "merchantId", event.getMerchantId(), "amount", event.getChargebackAmount(),
                    "priority", event.getPriority(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process chargeback critical failure alert: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("chargeback-critical-failure-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleChargebackCriticalFailureEventFallback(
            PaymentChargebackEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("critical-failure-fallback-%s-p%d-o%d", event.getChargebackId(), partition, offset);

        log.error("Circuit breaker fallback triggered for chargeback critical failure: chargebackId={}, error={}",
            event.getChargebackId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("chargeback-alert-critical-failures-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendCriticalAlert(
                "Chargeback Critical Failure Circuit Breaker Triggered",
                String.format("Chargeback %s critical failure processing failed: %s", event.getChargebackId(), ex.getMessage()),
                Map.of("chargebackId", event.getChargebackId(), "customerId", event.getCustomerId(),
                       "merchantId", event.getMerchantId(), "correlationId", correlationId)
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltChargebackCriticalFailureEvent(
            @Payload PaymentChargebackEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-critical-failure-%s-%d", event.getChargebackId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Chargeback critical failure permanently failed: chargebackId={}, topic={}, error={}",
            event.getChargebackId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logDisputeEvent("CHARGEBACK_CRITICAL_FAILURE_DLT_EVENT", event.getChargebackId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "chargebackId", event.getChargebackId(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendEmergencyAlert(
                "Chargeback Critical Failure Dead Letter Event",
                String.format("Chargeback %s critical failure sent to DLT: %s", event.getChargebackId(), exceptionMessage),
                Map.of("chargebackId", event.getChargebackId(), "topic", topic, "correlationId", correlationId)
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

    private void processCriticalFailureAlert(PaymentChargebackEvent event, String correlationId) {
        // Handle critical chargeback failures that require immediate escalation
        log.warn("Processing critical chargeback failure: chargebackId={}, priority={}, amount={}",
            event.getChargebackId(), event.getPriority(), event.getChargebackAmount());

        // Create or update dispute record with critical failure status
        disputeService.handleChargebackCriticalFailure(event.getChargebackId(), event.getTransactionId(), correlationId);

        // Escalate to senior management if high value
        if (event.isHighValue()) {
            notificationService.sendExecutiveAlert(
                "High-Value Chargeback Critical Failure",
                String.format("Critical failure processing chargeback %s for amount %s %s",
                    event.getChargebackId(), event.getChargebackAmount(), event.getCurrency()),
                Map.of("chargebackId", event.getChargebackId(), "amount", event.getChargebackAmount(),
                       "customerId", event.getCustomerId(), "merchantId", event.getMerchantId(),
                       "priority", "EXECUTIVE", "correlationId", correlationId)
            );
        }

        // Send immediate notification to dispute team
        notificationService.sendCriticalAlert(
            "Chargeback Critical Failure Alert",
            String.format("Chargeback %s encountered critical failure and requires immediate attention", event.getChargebackId()),
            Map.of("chargebackId", event.getChargebackId(), "customerId", event.getCustomerId(),
                   "merchantId", event.getMerchantId(), "urgency", "CRITICAL")
        );

        // Queue for manual review with highest priority
        kafkaTemplate.send("chargeback-manual-queue", Map.of(
            "chargebackId", event.getChargebackId(),
            "transactionId", event.getTransactionId(),
            "priority", "CRITICAL",
            "reason", "CRITICAL_FAILURE_ALERT",
            "assignedTo", "SENIOR_DISPUTE_ANALYST",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Update metrics for critical failures
        meterRegistry.counter("chargeback_critical_failures_total",
            "network", event.getCardNetwork().name(),
            "reason", event.getReasonCode()).increment();

        log.info("Critical chargeback failure processed: chargebackId={}, escalated to manual review",
            event.getChargebackId());
    }
}