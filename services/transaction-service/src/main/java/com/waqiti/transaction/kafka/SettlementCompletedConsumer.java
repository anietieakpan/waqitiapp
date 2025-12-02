package com.waqiti.transaction.kafka;

import com.waqiti.common.events.SettlementCompletedEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.transaction.service.SettlementService;
import com.waqiti.transaction.service.TransactionService;
import com.waqiti.transaction.service.TransactionMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
public class SettlementCompletedConsumer {

    private final SettlementService settlementService;
    private final TransactionService transactionService;
    private final TransactionMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler dlqHandler;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("settlement_completed_processed_total")
            .description("Total number of successfully processed settlement completed events")
            .register(meterRegistry);
        errorCounter = Counter.builder("settlement_completed_errors_total")
            .description("Total number of settlement completed processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("settlement_completed_processing_duration")
            .description("Time taken to process settlement completed events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"settlement-completed", "batch-settlement-completed", "settlement-finalized"},
        groupId = "settlement-completed-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "settlement-completed", fallbackMethod = "handleSettlementCompletedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleSettlementCompletedEvent(
            ConsumerRecord<String, SettlementCompletedEvent> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        SettlementCompletedEvent event = record.value();

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("settlement-%s-p%d-o%d", event.getSettlementId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getSettlementId(), event.getBatchId(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing settlement completed: settlementId={}, batchId={}, totalAmount={}, transactionCount={}",
                event.getSettlementId(), event.getBatchId(), event.getTotalAmount(), event.getTransactionCount());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Update settlement status
            updateSettlementStatus(event, correlationId);

            // Process settled transactions
            processSettledTransactions(event, correlationId);

            // Update merchant balances
            updateMerchantBalances(event, correlationId);

            // Generate settlement report
            generateSettlementReport(event, correlationId);

            // Send settlement notifications
            sendSettlementNotifications(event, correlationId);

            // Update accounting records
            updateAccountingRecords(event, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logTransactionEvent("SETTLEMENT_COMPLETED_PROCESSED", event.getSettlementId(),
                Map.of("batchId", event.getBatchId(), "totalAmount", event.getTotalAmount(),
                    "transactionCount", event.getTransactionCount(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process settlement completed event: {}", e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Settlement completed event sent to DLQ: settlementId={}, destination={}, attemptNumber={}",
                        event.getSettlementId(), result.getDestinationTopic(), result.getAttemptNumber()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for settlement completed event - MESSAGE MAY BE LOST! " +
                            "settlementId={}, partition={}, offset={}, error={}",
                            event.getSettlementId(), partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleSettlementCompletedEventFallback(
            ConsumerRecord<String, SettlementCompletedEvent> record,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        SettlementCompletedEvent event = record.value();

        String correlationId = String.format("settlement-fallback-%s-p%d-o%d", event.getSettlementId(), partition, offset);

        log.error("Circuit breaker fallback triggered for settlement completed: settlementId={}, error={}",
            event.getSettlementId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("settlement-completed-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical notification
        try {
            notificationService.sendCriticalAlert(
                "Settlement Completed Circuit Breaker Triggered",
                String.format("Settlement %s completion processing failed: %s", event.getSettlementId(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltSettlementCompletedEvent(
            ConsumerRecord<String, SettlementCompletedEvent> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        SettlementCompletedEvent event = record.value();

        String correlationId = String.format("dlt-settlement-%s-%d", event.getSettlementId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Settlement completed permanently failed: settlementId={}, topic={}, error={}",
            event.getSettlementId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logTransactionEvent("SETTLEMENT_COMPLETED_DLT_EVENT", event.getSettlementId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "batchId", event.getBatchId(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "Settlement Completed Dead Letter Event",
                String.format("Settlement %s completion sent to DLT: %s", event.getSettlementId(), exceptionMessage),
                Map.of("settlementId", event.getSettlementId(), "topic", topic, "correlationId", correlationId)
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

    private void updateSettlementStatus(SettlementCompletedEvent event, String correlationId) {
        // Update settlement record to completed
        settlementService.markSettlementAsCompleted(
            event.getSettlementId(),
            event.getBatchId(),
            event.getTotalAmount(),
            event.getTransactionCount(),
            event.getCompletedAt(),
            correlationId
        );

        // Update metrics
        metricsService.incrementSettlementsCompleted();
        metricsService.recordSettlementAmount(event.getTotalAmount(), event.getCurrency());
        metricsService.recordSettlementProcessingTime(
            event.getCompletedAt().toEpochMilli() - event.getInitiatedAt().toEpochMilli()
        );

        log.info("Settlement status updated to completed: settlementId={}, amount={}",
            event.getSettlementId(), event.getTotalAmount());
    }

    private void processSettledTransactions(SettlementCompletedEvent event, String correlationId) {
        // Update individual transaction statuses
        for (var transactionId : event.getTransactionIds()) {
            try {
                transactionService.markTransactionAsSettled(
                    transactionId,
                    event.getSettlementId(),
                    event.getCompletedAt(),
                    correlationId
                );

                // Send transaction settled event
                kafkaTemplate.send("transaction-settled", Map.of(
                    "transactionId", transactionId,
                    "settlementId", event.getSettlementId(),
                    "settledAt", event.getCompletedAt(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));

            } catch (Exception e) {
                log.error("Failed to mark transaction as settled: transactionId={}, error={}",
                    transactionId, e.getMessage());

                // Send to transaction settlement errors queue
                kafkaTemplate.send("transaction-settlement-errors", Map.of(
                    "transactionId", transactionId,
                    "settlementId", event.getSettlementId(),
                    "error", e.getMessage(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
            }
        }

        log.info("Processed {} settled transactions for settlement: {}",
            event.getTransactionIds().size(), event.getSettlementId());
    }

    private void updateMerchantBalances(SettlementCompletedEvent event, String correlationId) {
        // Update merchant account balances based on settlement
        for (var merchantSettlement : event.getMerchantSettlements()) {
            try {
                settlementService.updateMerchantBalance(
                    merchantSettlement.getMerchantId(),
                    merchantSettlement.getNetAmount(),
                    merchantSettlement.getFeeAmount(),
                    event.getSettlementId(),
                    correlationId
                );

                // Send merchant settlement notification
                kafkaTemplate.send("merchant-settlement-notifications", Map.of(
                    "merchantId", merchantSettlement.getMerchantId(),
                    "settlementId", event.getSettlementId(),
                    "netAmount", merchantSettlement.getNetAmount(),
                    "feeAmount", merchantSettlement.getFeeAmount(),
                    "transactionCount", merchantSettlement.getTransactionCount(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));

            } catch (Exception e) {
                log.error("Failed to update merchant balance: merchantId={}, error={}",
                    merchantSettlement.getMerchantId(), e.getMessage());

                // Send to merchant balance error queue
                kafkaTemplate.send("merchant-balance-errors", Map.of(
                    "merchantId", merchantSettlement.getMerchantId(),
                    "settlementId", event.getSettlementId(),
                    "error", e.getMessage(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
            }
        }

        log.info("Updated balances for {} merchants in settlement: {}",
            event.getMerchantSettlements().size(), event.getSettlementId());
    }

    private void generateSettlementReport(SettlementCompletedEvent event, String correlationId) {
        // Generate detailed settlement report
        var reportId = settlementService.generateSettlementReport(
            event.getSettlementId(),
            event.getBatchId(),
            event.getTotalAmount(),
            event.getTransactionCount(),
            event.getMerchantSettlements(),
            correlationId
        );

        // Send report generation event
        kafkaTemplate.send("settlement-report-generated", Map.of(
            "reportId", reportId,
            "settlementId", event.getSettlementId(),
            "batchId", event.getBatchId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Settlement report generated: reportId={}, settlementId={}",
            reportId, event.getSettlementId());
    }

    private void sendSettlementNotifications(SettlementCompletedEvent event, String correlationId) {
        // Send notifications to relevant parties
        for (var merchantSettlement : event.getMerchantSettlements()) {
            try {
                notificationService.sendMerchantNotification(
                    merchantSettlement.getMerchantId(),
                    "Settlement Completed",
                    String.format("Your settlement of %s %s has been completed. " +
                            "Net amount: %s, Fee: %s, Transactions: %d",
                        event.getTotalAmount(), event.getCurrency(),
                        merchantSettlement.getNetAmount(), merchantSettlement.getFeeAmount(),
                        merchantSettlement.getTransactionCount()),
                    correlationId
                );
            } catch (Exception e) {
                log.error("Failed to send settlement notification: merchantId={}, error={}",
                    merchantSettlement.getMerchantId(), e.getMessage());
            }
        }

        // Send operational notifications if large settlement
        if (event.getTotalAmount().compareTo(settlementService.getLargeSettlementThreshold()) > 0) {
            notificationService.sendOperationalAlert(
                "Large Settlement Completed",
                String.format("Large settlement completed: %s %s (Settlement ID: %s)",
                    event.getTotalAmount(), event.getCurrency(), event.getSettlementId()),
                "INFO"
            );
        }

        log.info("Settlement notifications sent: settlementId={}", event.getSettlementId());
    }

    private void updateAccountingRecords(SettlementCompletedEvent event, String correlationId) {
        // Send accounting journal entries
        kafkaTemplate.send("accounting-journal-entries", Map.of(
            "entryType", "SETTLEMENT_COMPLETED",
            "settlementId", event.getSettlementId(),
            "batchId", event.getBatchId(),
            "totalAmount", event.getTotalAmount(),
            "currency", event.getCurrency(),
            "transactionCount", event.getTransactionCount(),
            "merchantSettlements", event.getMerchantSettlements(),
            "completedAt", event.getCompletedAt(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Update financial reporting
        kafkaTemplate.send("financial-reporting-events", Map.of(
            "eventType", "SETTLEMENT_COMPLETED",
            "settlementId", event.getSettlementId(),
            "amount", event.getTotalAmount(),
            "currency", event.getCurrency(),
            "date", event.getCompletedAt(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send to reconciliation service
        kafkaTemplate.send("reconciliation-events", Map.of(
            "type", "SETTLEMENT_COMPLETED",
            "settlementId", event.getSettlementId(),
            "batchId", event.getBatchId(),
            "totalAmount", event.getTotalAmount(),
            "transactionIds", event.getTransactionIds(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Accounting records updated for settlement: {}", event.getSettlementId());
    }
}