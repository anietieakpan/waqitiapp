package com.waqiti.ledger.kafka;

import com.waqiti.common.events.ReconciliationErrorEvent;
import com.waqiti.ledger.domain.ReconciliationDiscrepancy;
import com.waqiti.ledger.repository.ReconciliationDiscrepancyRepository;
import com.waqiti.ledger.service.ReconciliationService;
import com.waqiti.ledger.service.LedgerBalanceService;
import com.waqiti.ledger.service.ReconciliationRetryService;
import com.waqiti.ledger.metrics.ReconciliationMetricsService;
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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import java.math.BigDecimal;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReconciliationCriticalErrorsConsumer {

    private final ReconciliationDiscrepancyRepository discrepancyRepository;
    private final ReconciliationService reconciliationService;
    private final LedgerBalanceService balanceService;
    private final ReconciliationRetryService retryService;
    private final ReconciliationMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Financial impact tracking
    private final AtomicLong totalDiscrepancyAmount = new AtomicLong(0);
    private final AtomicLong criticalErrorCount = new AtomicLong(0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter financialImpactCounter;
    private Timer processingTimer;
    private Gauge discrepancyAmountGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("reconciliation_critical_errors_processed_total")
            .description("Total number of successfully processed reconciliation critical error events")
            .register(meterRegistry);
        errorCounter = Counter.builder("reconciliation_critical_errors_errors_total")
            .description("Total number of reconciliation critical error processing errors")
            .register(meterRegistry);
        financialImpactCounter = Counter.builder("reconciliation_critical_errors_financial_impact")
            .description("Financial impact of reconciliation critical errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("reconciliation_critical_errors_processing_duration")
            .description("Time taken to process reconciliation critical error events")
            .register(meterRegistry);
        discrepancyAmountGauge = Gauge.builder("reconciliation_discrepancy_amount_total")
            .description("Total amount of reconciliation discrepancies")
            .register(meterRegistry, totalDiscrepancyAmount, AtomicLong::get);
    }

    @KafkaListener(
        topics = {"reconciliation-critical-errors", "ledger-balance-mismatches", "reconciliation-timeout-errors"},
        groupId = "reconciliation-critical-errors-service-group",
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
    @CircuitBreaker(name = "reconciliation-critical-errors", fallbackMethod = "handleReconciliationErrorFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleReconciliationCriticalErrorEvent(
            @Payload ReconciliationErrorEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("recon-error-%s-p%d-o%d", event.getReconciliationId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getReconciliationId(), event.getErrorType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing reconciliation critical error: reconciliationId={}, errorType={}, discrepancyAmount={}, accountId={}",
                event.getReconciliationId(), event.getErrorType(), event.getDiscrepancyAmount(), event.getAccountId());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Financial impact assessment
            assessFinancialImpact(event.getDiscrepancyAmount(), event.getErrorType(), correlationId);

            switch (event.getErrorType()) {
                case BALANCE_MISMATCH:
                    handleBalanceMismatchError(event, correlationId);
                    break;

                case TRANSACTION_MISSING:
                    handleTransactionMissingError(event, correlationId);
                    break;

                case DUPLICATE_TRANSACTION:
                    handleDuplicateTransactionError(event, correlationId);
                    break;

                case AMOUNT_DISCREPANCY:
                    handleAmountDiscrepancyError(event, correlationId);
                    break;

                case TIMESTAMP_MISMATCH:
                    handleTimestampMismatchError(event, correlationId);
                    break;

                case ACCOUNT_NOT_FOUND:
                    handleAccountNotFoundError(event, correlationId);
                    break;

                case CURRENCY_MISMATCH:
                    handleCurrencyMismatchError(event, correlationId);
                    break;

                case DATA_CORRUPTION:
                    handleDataCorruptionError(event, correlationId);
                    break;

                case SYSTEM_INCONSISTENCY:
                    handleSystemInconsistencyError(event, correlationId);
                    break;

                case EXTERNAL_SYSTEM_ERROR:
                    handleExternalSystemError(event, correlationId);
                    break;

                default:
                    log.warn("Unknown reconciliation error type: {}", event.getErrorType());
                    handleGenericReconciliationError(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logReconciliationEvent("RECONCILIATION_CRITICAL_ERROR_PROCESSED", event.getReconciliationId(),
                Map.of("errorType", event.getErrorType(), "discrepancyAmount", event.getDiscrepancyAmount(),
                    "accountId", event.getAccountId(), "externalReference", event.getExternalReference(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process reconciliation critical error event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("reconciliation-error-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleReconciliationErrorFallback(
            ReconciliationErrorEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("recon-error-fallback-%s-p%d-o%d", event.getReconciliationId(), partition, offset);

        log.error("Circuit breaker fallback triggered for reconciliation error: reconciliationId={}, error={}",
            event.getReconciliationId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("reconciliation-critical-errors-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for significant discrepancies
        if (event.getDiscrepancyAmount().abs().compareTo(BigDecimal.valueOf(10000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "Critical Reconciliation Error - Circuit Breaker Triggered",
                    String.format("Reconciliation %s (Discrepancy: %s) failed: %s",
                        event.getReconciliationId(), event.getDiscrepancyAmount(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send executive alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltReconciliationErrorEvent(
            @Payload ReconciliationErrorEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-recon-error-%s-%d", event.getReconciliationId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Reconciliation error permanently failed: reconciliationId={}, topic={}, error={}",
            event.getReconciliationId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logReconciliationEvent("RECONCILIATION_ERROR_DLT_EVENT", event.getReconciliationId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "errorType", event.getErrorType(), "discrepancyAmount", event.getDiscrepancyAmount(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Reconciliation Critical Error Dead Letter Event",
                String.format("Reconciliation %s (Discrepancy: %s) sent to DLT: %s",
                    event.getReconciliationId(), event.getDiscrepancyAmount(), exceptionMessage),
                Map.of("reconciliationId", event.getReconciliationId(), "topic", topic,
                    "correlationId", correlationId, "discrepancyAmount", event.getDiscrepancyAmount())
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void assessFinancialImpact(BigDecimal discrepancyAmount, String errorType, String correlationId) {
        long amountCents = discrepancyAmount.abs().multiply(BigDecimal.valueOf(100)).longValue();
        totalDiscrepancyAmount.addAndGet(amountCents);
        criticalErrorCount.incrementAndGet();
        financialImpactCounter.increment(amountCents);

        // Alert if cumulative discrepancy exceeds threshold
        if (totalDiscrepancyAmount.get() > 100000000) { // $1M in cents
            try {
                notificationService.sendExecutiveAlert(
                    "CRITICAL: Reconciliation Discrepancies Exceed $1M",
                    String.format("Cumulative reconciliation discrepancies: $%,.2f. Financial audit required.",
                        totalDiscrepancyAmount.get() / 100.0),
                    "CRITICAL"
                );
                // Reset counter after alert
                totalDiscrepancyAmount.set(0);
            } catch (Exception ex) {
                log.error("Failed to send financial impact alert: {}", ex.getMessage());
            }
        }

        // High-value discrepancy alert
        if (discrepancyAmount.abs().compareTo(BigDecimal.valueOf(25000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "High-Value Reconciliation Discrepancy",
                    String.format("Discrepancy amount: $%,.2f, Error: %s", discrepancyAmount, errorType),
                    "HIGH"
                );
            } catch (Exception ex) {
                log.error("Failed to send high-value discrepancy alert: {}", ex.getMessage());
            }
        }
    }

    private void handleBalanceMismatchError(ReconciliationErrorEvent event, String correlationId) {
        createDiscrepancyRecord(event, "BALANCE_MISMATCH", correlationId);

        // Immediate balance verification
        balanceService.verifyAccountBalance(event.getAccountId());

        // Freeze account temporarily
        kafkaTemplate.send("account-freeze-requests", Map.of(
            "accountId", event.getAccountId(),
            "freezeType", "BALANCE_DISCREPANCY",
            "duration", "2_HOURS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Trigger complete account reconciliation
        kafkaTemplate.send("account-reconciliation-requests", Map.of(
            "accountId", event.getAccountId(),
            "reconciliationType", "COMPLETE",
            "priority", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Critical Balance Mismatch",
            String.format("Account %s balance mismatch: $%,.2f discrepancy",
                event.getAccountId(), event.getDiscrepancyAmount()),
            "CRITICAL");

        metricsService.recordReconciliationError("BALANCE_MISMATCH", event.getDiscrepancyAmount());
    }

    private void handleTransactionMissingError(ReconciliationErrorEvent event, String correlationId) {
        createDiscrepancyRecord(event, "TRANSACTION_MISSING", correlationId);

        // Search for missing transaction in all systems
        kafkaTemplate.send("transaction-search-requests", Map.of(
            "transactionId", event.getTransactionId(),
            "accountId", event.getAccountId(),
            "externalReference", event.getExternalReference(),
            "searchScope", "ALL_SYSTEMS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check external system for transaction
        kafkaTemplate.send("external-system-queries", Map.of(
            "system", event.getExternalSystem(),
            "transactionId", event.getTransactionId(),
            "queryType", "TRANSACTION_VERIFICATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Missing Transaction Detected",
            String.format("Transaction %s missing from reconciliation",
                event.getTransactionId()),
            "HIGH");

        metricsService.recordReconciliationError("TRANSACTION_MISSING", event.getDiscrepancyAmount());
    }

    private void handleDuplicateTransactionError(ReconciliationErrorEvent event, String correlationId) {
        createDiscrepancyRecord(event, "DUPLICATE_TRANSACTION", correlationId);

        // Identify and resolve duplicate
        kafkaTemplate.send("duplicate-resolution-requests", Map.of(
            "transactionId", event.getTransactionId(),
            "accountId", event.getAccountId(),
            "duplicateCount", event.getDuplicateCount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Reverse excess transactions
        if (event.getDuplicateCount() > 1) {
            kafkaTemplate.send("transaction-reversal-requests", Map.of(
                "transactionId", event.getTransactionId(),
                "reversalCount", event.getDuplicateCount() - 1,
                "reason", "DUPLICATE_CORRECTION",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        notificationService.sendOperationalAlert("Duplicate Transaction Detected",
            String.format("Transaction %s duplicated %d times",
                event.getTransactionId(), event.getDuplicateCount()),
            "HIGH");

        metricsService.recordReconciliationError("DUPLICATE_TRANSACTION", event.getDiscrepancyAmount());
    }

    private void handleAmountDiscrepancyError(ReconciliationErrorEvent event, String correlationId) {
        createDiscrepancyRecord(event, "AMOUNT_DISCREPANCY", correlationId);

        // Verify original transaction amount
        kafkaTemplate.send("transaction-verification-requests", Map.of(
            "transactionId", event.getTransactionId(),
            "expectedAmount", event.getExpectedAmount(),
            "actualAmount", event.getActualAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Create adjustment entry if needed
        if (event.getDiscrepancyAmount().abs().compareTo(BigDecimal.valueOf(1000)) > 0) {
            kafkaTemplate.send("adjustment-entry-requests", Map.of(
                "accountId", event.getAccountId(),
                "adjustmentAmount", event.getDiscrepancyAmount().negate(),
                "reason", "AMOUNT_DISCREPANCY_CORRECTION",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        notificationService.sendOperationalAlert("Amount Discrepancy Detected",
            String.format("Transaction %s amount discrepancy: $%,.2f",
                event.getTransactionId(), event.getDiscrepancyAmount()),
            "HIGH");

        metricsService.recordReconciliationError("AMOUNT_DISCREPANCY", event.getDiscrepancyAmount());
    }

    private void handleTimestampMismatchError(ReconciliationErrorEvent event, String correlationId) {
        createDiscrepancyRecord(event, "TIMESTAMP_MISMATCH", correlationId);

        // Verify transaction timing
        kafkaTemplate.send("timestamp-verification-requests", Map.of(
            "transactionId", event.getTransactionId(),
            "expectedTimestamp", event.getExpectedTimestamp(),
            "actualTimestamp", event.getActualTimestamp(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordReconciliationError("TIMESTAMP_MISMATCH", BigDecimal.ZERO);
    }

    private void handleAccountNotFoundError(ReconciliationErrorEvent event, String correlationId) {
        createDiscrepancyRecord(event, "ACCOUNT_NOT_FOUND", correlationId);

        // Verify account existence
        kafkaTemplate.send("account-verification-requests", Map.of(
            "accountId", event.getAccountId(),
            "verificationType", "EXISTENCE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check if account was closed or moved
        kafkaTemplate.send("account-status-checks", Map.of(
            "accountId", event.getAccountId(),
            "checkType", "HISTORICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Account Not Found in Reconciliation",
            String.format("Account %s not found during reconciliation",
                event.getAccountId()),
            "HIGH");

        metricsService.recordReconciliationError("ACCOUNT_NOT_FOUND", event.getDiscrepancyAmount());
    }

    private void handleCurrencyMismatchError(ReconciliationErrorEvent event, String correlationId) {
        createDiscrepancyRecord(event, "CURRENCY_MISMATCH", correlationId);

        // Verify currency conversion rates
        kafkaTemplate.send("currency-verification-requests", Map.of(
            "fromCurrency", event.getExpectedCurrency(),
            "toCurrency", event.getActualCurrency(),
            "conversionDate", event.getTransactionDate(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordReconciliationError("CURRENCY_MISMATCH", event.getDiscrepancyAmount());
    }

    private void handleDataCorruptionError(ReconciliationErrorEvent event, String correlationId) {
        createDiscrepancyRecord(event, "DATA_CORRUPTION", correlationId);

        // Trigger data integrity check
        kafkaTemplate.send("data-integrity-checks", Map.of(
            "dataType", "TRANSACTION_DATA",
            "affectedId", event.getTransactionId(),
            "priority", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Backup and restore from clean data source
        kafkaTemplate.send("data-restoration-requests", Map.of(
            "transactionId", event.getTransactionId(),
            "restorationType", "FROM_BACKUP",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendCriticalAlert("Data Corruption Detected",
            String.format("Data corruption in transaction %s",
                event.getTransactionId()),
            Map.of("transactionId", event.getTransactionId(), "correlationId", correlationId));

        metricsService.recordReconciliationError("DATA_CORRUPTION", event.getDiscrepancyAmount());
    }

    private void handleSystemInconsistencyError(ReconciliationErrorEvent event, String correlationId) {
        createDiscrepancyRecord(event, "SYSTEM_INCONSISTENCY", correlationId);

        // Trigger system-wide consistency check
        kafkaTemplate.send("system-consistency-checks", Map.of(
            "scope", "LEDGER_SYSTEM",
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("System Inconsistency Detected",
            String.format("System inconsistency in reconciliation %s",
                event.getReconciliationId()),
            "HIGH");

        metricsService.recordReconciliationError("SYSTEM_INCONSISTENCY", event.getDiscrepancyAmount());
    }

    private void handleExternalSystemError(ReconciliationErrorEvent event, String correlationId) {
        createDiscrepancyRecord(event, "EXTERNAL_SYSTEM_ERROR", correlationId);

        // Check external system connectivity
        kafkaTemplate.send("external-system-health-checks", Map.of(
            "system", event.getExternalSystem(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule reconciliation retry
        retryService.scheduleReconciliationRetry(event.getReconciliationId(), "EXTERNAL_SYSTEM_ERROR", 1800000); // 30 minutes

        metricsService.recordReconciliationError("EXTERNAL_SYSTEM_ERROR", event.getDiscrepancyAmount());
    }

    private void handleGenericReconciliationError(ReconciliationErrorEvent event, String correlationId) {
        createDiscrepancyRecord(event, "UNKNOWN_ERROR", correlationId);

        // Log for investigation
        auditService.logReconciliationEvent("UNKNOWN_RECONCILIATION_ERROR", event.getReconciliationId(),
            Map.of("errorType", event.getErrorType(), "discrepancyAmount", event.getDiscrepancyAmount(),
                "errorDescription", event.getErrorDescription(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        notificationService.sendOperationalAlert("Unknown Reconciliation Error",
            String.format("Unknown reconciliation error %s: %s",
                event.getReconciliationId(), event.getErrorDescription()),
            "HIGH");

        metricsService.recordReconciliationError("UNKNOWN", event.getDiscrepancyAmount());
    }

    private void createDiscrepancyRecord(ReconciliationErrorEvent event, String errorType, String correlationId) {
        try {
            ReconciliationDiscrepancy discrepancy = ReconciliationDiscrepancy.builder()
                .reconciliationId(event.getReconciliationId())
                .accountId(event.getAccountId())
                .transactionId(event.getTransactionId())
                .errorType(errorType)
                .discrepancyAmount(event.getDiscrepancyAmount())
                .expectedAmount(event.getExpectedAmount())
                .actualAmount(event.getActualAmount())
                .externalReference(event.getExternalReference())
                .status("OPEN")
                .correlationId(correlationId)
                .createdAt(LocalDateTime.now())
                .build();

            discrepancyRepository.save(discrepancy);
        } catch (Exception e) {
            log.error("Failed to create discrepancy record: {}", e.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

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
}