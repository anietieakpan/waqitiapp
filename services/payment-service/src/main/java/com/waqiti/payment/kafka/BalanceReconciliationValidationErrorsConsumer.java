package com.waqiti.payment.kafka;

import com.waqiti.common.events.BalanceReconciliationValidationErrorEvent;
import com.waqiti.payment.domain.ReconciliationError;
import com.waqiti.payment.repository.ReconciliationErrorRepository;
import com.waqiti.payment.service.BalanceReconciliationService;
import com.waqiti.payment.service.ValidationService;
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
public class BalanceReconciliationValidationErrorsConsumer {

    private final ReconciliationErrorRepository reconciliationErrorRepository;
    private final BalanceReconciliationService balanceReconciliationService;
    private final ValidationService validationService;
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
        successCounter = Counter.builder("balance_reconciliation_validation_errors_processed_total")
            .description("Total number of successfully processed balance reconciliation validation error events")
            .register(meterRegistry);
        errorCounter = Counter.builder("balance_reconciliation_validation_errors_errors_total")
            .description("Total number of balance reconciliation validation error processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("balance_reconciliation_validation_errors_processing_duration")
            .description("Time taken to process balance reconciliation validation error events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"balance-reconciliation-validation-errors", "reconciliation-errors", "balance-validation-failures"},
        groupId = "balance-reconciliation-validation-errors-service-group",
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
    @CircuitBreaker(name = "balance-reconciliation-validation-errors", fallbackMethod = "handleValidationErrorEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleValidationErrorEvent(
            @Payload BalanceReconciliationValidationErrorEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("validation-error-%s-p%d-o%d", event.getAccountId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAccountId(), event.getErrorType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing balance reconciliation validation error: accountId={}, error={}, severity={}",
                event.getAccountId(), event.getErrorType(), event.getSeverity());

            cleanExpiredEntries();

            switch (event.getErrorType()) {
                case BALANCE_MISMATCH:
                    processBalanceMismatch(event, correlationId);
                    break;

                case TRANSACTION_SUM_MISMATCH:
                    processTransactionSumMismatch(event, correlationId);
                    break;

                case MISSING_TRANSACTIONS:
                    processMissingTransactions(event, correlationId);
                    break;

                case DUPLICATE_TRANSACTIONS:
                    processDuplicateTransactions(event, correlationId);
                    break;

                case INVALID_BALANCE_STATE:
                    processInvalidBalanceState(event, correlationId);
                    break;

                case TIMESTAMP_INCONSISTENCY:
                    processTimestampInconsistency(event, correlationId);
                    break;

                case CURRENCY_MISMATCH:
                    processCurrencyMismatch(event, correlationId);
                    break;

                case RECONCILIATION_TIMEOUT:
                    processReconciliationTimeout(event, correlationId);
                    break;

                default:
                    log.warn("Unknown validation error type: {}", event.getErrorType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("BALANCE_RECONCILIATION_VALIDATION_ERROR_PROCESSED", event.getAccountId(),
                Map.of("errorType", event.getErrorType(), "severity", event.getSeverity(),
                    "expectedBalance", event.getExpectedBalance(), "actualBalance", event.getActualBalance(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process balance reconciliation validation error: {}", e.getMessage(), e);

            kafkaTemplate.send("balance-reconciliation-validation-errors-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handleValidationErrorEventFallback(
            BalanceReconciliationValidationErrorEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("validation-error-fallback-%s-p%d-o%d", event.getAccountId(), partition, offset);

        log.error("Circuit breaker fallback triggered for balance reconciliation validation error: accountId={}, error={}",
            event.getAccountId(), ex.getMessage());

        kafkaTemplate.send("balance-reconciliation-validation-errors-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltValidationErrorEvent(
            @Payload BalanceReconciliationValidationErrorEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-validation-error-%s-%d", event.getAccountId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Balance reconciliation validation error permanently failed: accountId={}, error={}",
            event.getAccountId(), exceptionMessage);

        auditService.logPaymentEvent("BALANCE_RECONCILIATION_VALIDATION_ERROR_DLT_EVENT", event.getAccountId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "errorType", event.getErrorType(), "correlationId", correlationId,
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

    private void processBalanceMismatch(BalanceReconciliationValidationErrorEvent event, String correlationId) {
        ReconciliationError error = ReconciliationError.builder()
            .accountId(event.getAccountId())
            .errorType("BALANCE_MISMATCH")
            .severity(event.getSeverity())
            .expectedBalance(event.getExpectedBalance())
            .actualBalance(event.getActualBalance())
            .variance(event.getExpectedBalance() - event.getActualBalance())
            .errorDescription(event.getErrorDescription())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        reconciliationErrorRepository.save(error);

        // Trigger balance reconciliation investigation
        balanceReconciliationService.investigateBalanceMismatch(
            event.getAccountId(),
            event.getExpectedBalance(),
            event.getActualBalance(),
            correlationId
        );

        // Send critical alert for significant mismatches
        double variancePercentage = Math.abs(event.getExpectedBalance() - event.getActualBalance()) /
            Math.abs(event.getExpectedBalance()) * 100;

        if (variancePercentage > 5.0) {
            notificationService.sendCriticalAlert(
                "Significant Balance Reconciliation Mismatch",
                String.format("Account %s balance mismatch: Expected=$%.2f, Actual=$%.2f (%.2f%% variance)",
                    event.getAccountId(), event.getExpectedBalance(), event.getActualBalance(), variancePercentage),
                Map.of("accountId", event.getAccountId(), "variance", variancePercentage, "correlationId", correlationId)
            );
        }

        metricsService.recordReconciliationError("BALANCE_MISMATCH", variancePercentage);
    }

    private void processTransactionSumMismatch(BalanceReconciliationValidationErrorEvent event, String correlationId) {
        // Record the transaction sum mismatch
        ReconciliationError error = ReconciliationError.builder()
            .accountId(event.getAccountId())
            .errorType("TRANSACTION_SUM_MISMATCH")
            .severity(event.getSeverity())
            .expectedSum(event.getExpectedTransactionSum())
            .actualSum(event.getActualTransactionSum())
            .variance(event.getExpectedTransactionSum() - event.getActualTransactionSum())
            .errorDescription(event.getErrorDescription())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        reconciliationErrorRepository.save(error);

        // Investigate transaction discrepancies
        balanceReconciliationService.investigateTransactionDiscrepancies(
            event.getAccountId(),
            event.getReconciliationPeriod(),
            correlationId
        );

        metricsService.recordReconciliationError("TRANSACTION_SUM_MISMATCH",
            Math.abs(event.getExpectedTransactionSum() - event.getActualTransactionSum()));
    }

    private void processMissingTransactions(BalanceReconciliationValidationErrorEvent event, String correlationId) {
        // Handle missing transactions
        validationService.handleMissingTransactions(
            event.getAccountId(),
            event.getMissingTransactionIds(),
            correlationId
        );

        // Log for audit
        auditService.logPaymentEvent("MISSING_TRANSACTIONS_DETECTED", event.getAccountId(),
            Map.of("missingCount", event.getMissingTransactionIds().size(),
                "transactionIds", event.getMissingTransactionIds(),
                "correlationId", correlationId, "timestamp", Instant.now()));

        metricsService.recordMissingTransactions(event.getAccountId(), event.getMissingTransactionIds().size());
    }

    private void processDuplicateTransactions(BalanceReconciliationValidationErrorEvent event, String correlationId) {
        // Handle duplicate transactions
        validationService.handleDuplicateTransactions(
            event.getAccountId(),
            event.getDuplicateTransactionIds(),
            correlationId
        );

        // Send operational alert
        notificationService.sendOperationalAlert(
            "Duplicate Transactions Detected",
            String.format("Account %s has %d duplicate transactions detected during reconciliation",
                event.getAccountId(), event.getDuplicateTransactionIds().size()),
            "HIGH"
        );

        metricsService.recordDuplicateTransactions(event.getAccountId(), event.getDuplicateTransactionIds().size());
    }

    private void processInvalidBalanceState(BalanceReconciliationValidationErrorEvent event, String correlationId) {
        // Handle invalid balance state
        balanceReconciliationService.handleInvalidBalanceState(
            event.getAccountId(),
            event.getInvalidStateDetails(),
            correlationId
        );

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Invalid Balance State Detected",
            String.format("Account %s has invalid balance state: %s", event.getAccountId(), event.getInvalidStateDetails()),
            Map.of("accountId", event.getAccountId(), "stateDetails", event.getInvalidStateDetails(), "correlationId", correlationId)
        );

        metricsService.recordInvalidBalanceState(event.getAccountId());
    }

    private void processTimestampInconsistency(BalanceReconciliationValidationErrorEvent event, String correlationId) {
        // Handle timestamp inconsistencies
        validationService.handleTimestampInconsistencies(
            event.getAccountId(),
            event.getInconsistentTimestamps(),
            correlationId
        );

        metricsService.recordTimestampInconsistency(event.getAccountId(), event.getInconsistentTimestamps().size());
    }

    private void processCurrencyMismatch(BalanceReconciliationValidationErrorEvent event, String correlationId) {
        // Handle currency mismatches
        validationService.handleCurrencyMismatches(
            event.getAccountId(),
            event.getExpectedCurrency(),
            event.getActualCurrency(),
            correlationId
        );

        metricsService.recordCurrencyMismatch(event.getAccountId(), event.getExpectedCurrency(), event.getActualCurrency());
    }

    private void processReconciliationTimeout(BalanceReconciliationValidationErrorEvent event, String correlationId) {
        // Handle reconciliation timeouts
        balanceReconciliationService.handleReconciliationTimeout(
            event.getAccountId(),
            event.getTimeoutDuration(),
            correlationId
        );

        // Send operational alert
        notificationService.sendOperationalAlert(
            "Reconciliation Timeout",
            String.format("Account %s reconciliation timed out after %d minutes",
                event.getAccountId(), event.getTimeoutDuration()),
            "MEDIUM"
        );

        metricsService.recordReconciliationTimeout(event.getAccountId(), event.getTimeoutDuration());
    }
}