package com.waqiti.transaction.kafka;

import com.waqiti.common.events.TransactionResumedEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.transaction.service.TransactionService;
import com.waqiti.transaction.service.TransactionControlService;
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
public class TransactionResumesConsumer {

    private final TransactionService transactionService;
    private final TransactionControlService controlService;
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
        successCounter = Counter.builder("transaction_resumes_processed_total")
            .description("Total number of successfully processed transaction resume events")
            .register(meterRegistry);
        errorCounter = Counter.builder("transaction_resumes_errors_total")
            .description("Total number of transaction resume processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("transaction_resumes_processing_duration")
            .description("Time taken to process transaction resume events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"transaction-resumes", "transaction-unblocked", "transaction-processing-resumed"},
        groupId = "transaction-resumes-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "transaction-resumes", fallbackMethod = "handleTransactionResumedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleTransactionResumedEvent(
            ConsumerRecord<String, TransactionResumedEvent> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        TransactionResumedEvent event = record.value();

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("resume-%s-p%d-o%d", event.getTransactionId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getTransactionId(), event.getResumedBy(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing transaction resumed: transactionId={}, resumedBy={}, reason={}",
                event.getTransactionId(), event.getResumedBy(), event.getResumeReason());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Resume transaction processing
            resumeTransactionProcessing(event, correlationId);

            // Update transaction status
            updateTransactionStatus(event, correlationId);

            // Remove transaction blocks/holds
            removeTransactionBlocks(event, correlationId);

            // Send resume notifications
            sendResumeNotifications(event, correlationId);

            // Process pending actions
            processPendingActions(event, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logTransactionEvent("TRANSACTION_RESUMED_PROCESSED", event.getTransactionId(),
                Map.of("resumedBy", event.getResumedBy(), "resumeReason", event.getResumeReason(),
                    "blockDuration", event.getBlockDuration(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process transaction resumed event: {}", e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Transaction resumed event sent to DLQ: transactionId={}, destination={}, attemptNumber={}",
                        event.getTransactionId(), result.getDestinationTopic(), result.getAttemptNumber()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for transaction resumed event - MESSAGE MAY BE LOST! " +
                            "transactionId={}, partition={}, offset={}, error={}",
                            event.getTransactionId(), partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleTransactionResumedEventFallback(
            ConsumerRecord<String, TransactionResumedEvent> record,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        TransactionResumedEvent event = record.value();

        String correlationId = String.format("resume-fallback-%s-p%d-o%d", event.getTransactionId(), partition, offset);

        log.error("Circuit breaker fallback triggered for transaction resumed: transactionId={}, error={}",
            event.getTransactionId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("transaction-resumes-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Transaction Resume Circuit Breaker Triggered",
                String.format("Transaction %s resume processing failed: %s", event.getTransactionId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltTransactionResumedEvent(
            ConsumerRecord<String, TransactionResumedEvent> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        TransactionResumedEvent event = record.value();

        String correlationId = String.format("dlt-resume-%s-%d", event.getTransactionId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Transaction resumed permanently failed: transactionId={}, topic={}, error={}",
            event.getTransactionId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logTransactionEvent("TRANSACTION_RESUMED_DLT_EVENT", event.getTransactionId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "resumedBy", event.getResumedBy(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Transaction Resumed Dead Letter Event",
                String.format("Transaction %s resume sent to DLT: %s", event.getTransactionId(), exceptionMessage),
                Map.of("transactionId", event.getTransactionId(), "topic", topic, "correlationId", correlationId)
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

    private void resumeTransactionProcessing(TransactionResumedEvent event, String correlationId) {
        // Resume transaction in the processing pipeline
        controlService.resumeTransaction(
            event.getTransactionId(),
            event.getResumedBy(),
            event.getResumeReason(),
            event.getApprovalReference(),
            correlationId
        );

        // Update metrics
        metricsService.incrementTransactionsResumed(event.getOriginalBlockReason());
        metricsService.recordBlockDuration(
            event.getOriginalBlockReason(),
            event.getBlockDuration()
        );

        // Send transaction to appropriate processing queue based on current state
        var transactionState = transactionService.getTransactionState(event.getTransactionId());

        switch (transactionState.getStatus()) {
            case "PENDING_AUTHORIZATION":
                kafkaTemplate.send("transaction-authorization-queue", Map.of(
                    "transactionId", event.getTransactionId(),
                    "customerId", event.getCustomerId(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
                break;

            case "PENDING_SETTLEMENT":
                kafkaTemplate.send("transaction-settlement-queue", Map.of(
                    "transactionId", event.getTransactionId(),
                    "customerId", event.getCustomerId(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
                break;

            case "PENDING_COMPLETION":
                kafkaTemplate.send("transaction-completion-queue", Map.of(
                    "transactionId", event.getTransactionId(),
                    "customerId", event.getCustomerId(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
                break;

            default:
                log.warn("Unknown transaction state for resumed transaction: transactionId={}, state={}",
                    event.getTransactionId(), transactionState.getStatus());
                break;
        }

        log.info("Transaction processing resumed: transactionId={}, newState={}",
            event.getTransactionId(), transactionState.getStatus());
    }

    private void updateTransactionStatus(TransactionResumedEvent event, String correlationId) {
        // Update transaction status to reflect resume
        transactionService.updateTransactionStatus(
            event.getTransactionId(),
            "PROCESSING",
            null, // Clear block status
            event.getResumedBy(),
            event.getTimestamp(),
            correlationId
        );

        // Record resume audit trail
        transactionService.recordStatusChange(
            event.getTransactionId(),
            "BLOCKED",
            "PROCESSING",
            event.getResumedBy(),
            event.getResumeReason(),
            event.getApprovalReference(),
            correlationId
        );

        log.info("Transaction status updated to processing: transactionId={}", event.getTransactionId());
    }

    private void removeTransactionBlocks(TransactionResumedEvent event, String correlationId) {
        // Remove all active blocks for this transaction
        controlService.removeTransactionBlocks(
            event.getTransactionId(),
            event.getResumedBy(),
            event.getResumeReason(),
            correlationId
        );

        // Remove any holds on customer account
        if (event.getHoldAmount() != null) {
            kafkaTemplate.send("account-hold-removals", Map.of(
                "transactionId", event.getTransactionId(),
                "customerId", event.getCustomerId(),
                "accountId", event.getAccountId(),
                "holdAmount", event.getHoldAmount(),
                "reason", "TRANSACTION_RESUMED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Update block registry
        kafkaTemplate.send("transaction-block-registry-updates", Map.of(
            "transactionId", event.getTransactionId(),
            "action", "REMOVE_BLOCKS",
            "removedBy", event.getResumedBy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Transaction blocks removed: transactionId={}, removedBy={}",
            event.getTransactionId(), event.getResumedBy());
    }

    private void sendResumeNotifications(TransactionResumedEvent event, String correlationId) {
        // Notify customer if transaction was blocked for customer-facing reasons
        if (event.isNotifyCustomer()) {
            kafkaTemplate.send("customer-notifications", Map.of(
                "type", "TRANSACTION_RESUMED",
                "customerId", event.getCustomerId(),
                "transactionId", event.getTransactionId(),
                "message", "Your transaction has been approved and is now processing",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Notify merchant if applicable
        if (event.getMerchantId() != null) {
            kafkaTemplate.send("merchant-notifications", Map.of(
                "type", "TRANSACTION_RESUMED",
                "merchantId", event.getMerchantId(),
                "transactionId", event.getTransactionId(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Send operational notification for high-value transactions
        if (event.getAmount() != null &&
            event.getAmount().compareTo(transactionService.getHighValueThreshold()) > 0) {

            notificationService.sendOperationalAlert(
                "High Value Transaction Resumed",
                String.format("High value transaction %s has been resumed by %s",
                    event.getTransactionId(), event.getResumedBy()),
                "INFO"
            );
        }

        log.info("Resume notifications sent: transactionId={}", event.getTransactionId());
    }

    private void processPendingActions(TransactionResumedEvent event, String correlationId) {
        // Check for any pending actions that were waiting for transaction resume
        var pendingActions = controlService.getPendingActions(event.getTransactionId());

        for (var action : pendingActions) {
            try {
                switch (action.getActionType()) {
                    case "FRAUD_REVIEW_COMPLETION":
                        kafkaTemplate.send("fraud-review-completions", Map.of(
                            "transactionId", event.getTransactionId(),
                            "reviewResult", "APPROVED",
                            "reviewedBy", event.getResumedBy(),
                            "correlationId", correlationId,
                            "timestamp", Instant.now()
                        ));
                        break;

                    case "COMPLIANCE_CLEARANCE":
                        kafkaTemplate.send("compliance-clearances", Map.of(
                            "transactionId", event.getTransactionId(),
                            "clearanceType", action.getDetails().get("clearanceType"),
                            "clearedBy", event.getResumedBy(),
                            "correlationId", correlationId,
                            "timestamp", Instant.now()
                        ));
                        break;

                    case "LIMIT_OVERRIDE":
                        kafkaTemplate.send("limit-overrides", Map.of(
                            "transactionId", event.getTransactionId(),
                            "customerId", event.getCustomerId(),
                            "overrideType", action.getDetails().get("overrideType"),
                            "authorizedBy", event.getResumedBy(),
                            "correlationId", correlationId,
                            "timestamp", Instant.now()
                        ));
                        break;

                    default:
                        log.warn("Unknown pending action type: {}", action.getActionType());
                        break;
                }

                // Mark action as completed
                controlService.markActionCompleted(action.getId(), correlationId);

            } catch (Exception e) {
                log.error("Failed to process pending action: actionId={}, error={}",
                    action.getId(), e.getMessage());
            }
        }

        log.info("Processed {} pending actions for transaction: {}",
            pendingActions.size(), event.getTransactionId());
    }
}