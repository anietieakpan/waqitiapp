package com.waqiti.ledger.kafka;

import com.waqiti.common.events.LedgerBalanceMismatchEvent;
import com.waqiti.ledger.domain.BalanceDiscrepancy;
import com.waqiti.ledger.repository.BalanceDiscrepancyRepository;
import com.waqiti.ledger.service.LedgerBalanceService;
import com.waqiti.ledger.service.BalanceReconciliationService;
import com.waqiti.ledger.service.LedgerAuditService;
import com.waqiti.ledger.metrics.LedgerMetricsService;
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
public class LedgerBalanceMismatchesConsumer {

    private final BalanceDiscrepancyRepository discrepancyRepository;
    private final LedgerBalanceService balanceService;
    private final BalanceReconciliationService reconciliationService;
    private final LedgerAuditService ledgerAuditService;
    private final LedgerMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Financial impact tracking
    private final AtomicLong totalMismatchAmount = new AtomicLong(0);
    private final AtomicLong mismatchCount = new AtomicLong(0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter financialImpactCounter;
    private Timer processingTimer;
    private Gauge mismatchAmountGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("ledger_balance_mismatches_processed_total")
            .description("Total number of successfully processed ledger balance mismatch events")
            .register(meterRegistry);
        errorCounter = Counter.builder("ledger_balance_mismatches_errors_total")
            .description("Total number of ledger balance mismatch processing errors")
            .register(meterRegistry);
        financialImpactCounter = Counter.builder("ledger_balance_mismatches_financial_impact")
            .description("Financial impact of ledger balance mismatches")
            .register(meterRegistry);
        processingTimer = Timer.builder("ledger_balance_mismatches_processing_duration")
            .description("Time taken to process ledger balance mismatch events")
            .register(meterRegistry);
        mismatchAmountGauge = Gauge.builder("ledger_balance_mismatch_amount_total")
            .description("Total amount of ledger balance mismatches")
            .register(meterRegistry, totalMismatchAmount, AtomicLong::get);
    }

    @KafkaListener(
        topics = {"ledger-balance-mismatches", "balance-integrity-alerts", "ledger-inconsistency-events"},
        groupId = "ledger-balance-mismatches-service-group",
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
    @CircuitBreaker(name = "ledger-balance-mismatches", fallbackMethod = "handleBalanceMismatchFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleLedgerBalanceMismatchEvent(
            @Payload LedgerBalanceMismatchEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("balance-mismatch-%s-p%d-o%d", event.getAccountId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAccountId(), event.getMismatchType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing ledger balance mismatch: accountId={}, mismatchType={}, discrepancyAmount={}, ledgerType={}",
                event.getAccountId(), event.getMismatchType(), event.getDiscrepancyAmount(), event.getLedgerType());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Financial impact assessment
            assessFinancialImpact(event.getDiscrepancyAmount(), event.getMismatchType(), correlationId);

            switch (event.getMismatchType()) {
                case CREDIT_DEBIT_IMBALANCE:
                    handleCreditDebitImbalance(event, correlationId);
                    break;

                case GENERAL_LEDGER_MISMATCH:
                    handleGeneralLedgerMismatch(event, correlationId);
                    break;

                case SUBSIDIARY_LEDGER_MISMATCH:
                    handleSubsidiaryLedgerMismatch(event, correlationId);
                    break;

                case TRIAL_BALANCE_ERROR:
                    handleTrialBalanceError(event, correlationId);
                    break;

                case CONTROL_ACCOUNT_MISMATCH:
                    handleControlAccountMismatch(event, correlationId);
                    break;

                case FLOATING_BALANCE_ERROR:
                    handleFloatingBalanceError(event, correlationId);
                    break;

                case CURRENCY_CONVERSION_ERROR:
                    handleCurrencyConversionError(event, correlationId);
                    break;

                case PERIOD_END_IMBALANCE:
                    handlePeriodEndImbalance(event, correlationId);
                    break;

                case NOSTRO_VOSTRO_MISMATCH:
                    handleNostroVostroMismatch(event, correlationId);
                    break;

                case INTER_BRANCH_IMBALANCE:
                    handleInterBranchImbalance(event, correlationId);
                    break;

                default:
                    log.warn("Unknown balance mismatch type: {}", event.getMismatchType());
                    handleGenericBalanceMismatch(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logLedgerEvent("LEDGER_BALANCE_MISMATCH_PROCESSED", event.getAccountId(),
                Map.of("mismatchType", event.getMismatchType(), "discrepancyAmount", event.getDiscrepancyAmount(),
                    "ledgerType", event.getLedgerType(), "expectedBalance", event.getExpectedBalance(),
                    "actualBalance", event.getActualBalance(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process ledger balance mismatch event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("balance-mismatch-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleBalanceMismatchFallback(
            LedgerBalanceMismatchEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("balance-mismatch-fallback-%s-p%d-o%d", event.getAccountId(), partition, offset);

        log.error("Circuit breaker fallback triggered for balance mismatch: accountId={}, error={}",
            event.getAccountId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("ledger-balance-mismatches-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for significant mismatches
        if (event.getDiscrepancyAmount().abs().compareTo(BigDecimal.valueOf(50000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "Critical Ledger Balance Mismatch - Circuit Breaker Triggered",
                    String.format("Account %s (Discrepancy: %s) failed: %s",
                        event.getAccountId(), event.getDiscrepancyAmount(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send executive alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBalanceMismatchEvent(
            @Payload LedgerBalanceMismatchEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-balance-mismatch-%s-%d", event.getAccountId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Balance mismatch permanently failed: accountId={}, topic={}, error={}",
            event.getAccountId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logLedgerEvent("BALANCE_MISMATCH_DLT_EVENT", event.getAccountId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "mismatchType", event.getMismatchType(), "discrepancyAmount", event.getDiscrepancyAmount(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Ledger Balance Mismatch Dead Letter Event",
                String.format("Account %s (Discrepancy: %s) sent to DLT: %s",
                    event.getAccountId(), event.getDiscrepancyAmount(), exceptionMessage),
                Map.of("accountId", event.getAccountId(), "topic", topic,
                    "correlationId", correlationId, "discrepancyAmount", event.getDiscrepancyAmount())
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void assessFinancialImpact(BigDecimal discrepancyAmount, String mismatchType, String correlationId) {
        long amountCents = discrepancyAmount.abs().multiply(BigDecimal.valueOf(100)).longValue();
        totalMismatchAmount.addAndGet(amountCents);
        mismatchCount.incrementAndGet();
        financialImpactCounter.increment(amountCents);

        // Alert if cumulative mismatch exceeds threshold
        if (totalMismatchAmount.get() > 200000000) { // $2M in cents
            try {
                notificationService.sendExecutiveAlert(
                    "CRITICAL: Ledger Balance Mismatches Exceed $2M",
                    String.format("Cumulative balance mismatches: $%,.2f. Emergency ledger audit required.",
                        totalMismatchAmount.get() / 100.0),
                    "CRITICAL"
                );
                // Reset counter after alert
                totalMismatchAmount.set(0);
            } catch (Exception ex) {
                log.error("Failed to send financial impact alert: {}", ex.getMessage());
            }
        }

        // High-value mismatch alert
        if (discrepancyAmount.abs().compareTo(BigDecimal.valueOf(50000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "High-Value Ledger Balance Mismatch",
                    String.format("Mismatch amount: $%,.2f, Type: %s", discrepancyAmount, mismatchType),
                    "HIGH"
                );
            } catch (Exception ex) {
                log.error("Failed to send high-value mismatch alert: {}", ex.getMessage());
            }
        }
    }

    private void handleCreditDebitImbalance(LedgerBalanceMismatchEvent event, String correlationId) {
        createDiscrepancyRecord(event, "CREDIT_DEBIT_IMBALANCE", correlationId);

        // Immediate ledger freeze
        kafkaTemplate.send("ledger-freeze-requests", Map.of(
            "accountId", event.getAccountId(),
            "freezeType", "CREDIT_DEBIT_IMBALANCE",
            "duration", "4_HOURS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Trigger emergency double-entry validation
        kafkaTemplate.send("double-entry-validation-requests", Map.of(
            "accountId", event.getAccountId(),
            "validationType", "EMERGENCY",
            "priority", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Recalculate all balances
        reconciliationService.recalculateAccountBalance(event.getAccountId());

        notificationService.sendOperationalAlert("Critical Credit-Debit Imbalance",
            String.format("Account %s has credit-debit imbalance: $%,.2f",
                event.getAccountId(), event.getDiscrepancyAmount()),
            "CRITICAL");

        metricsService.recordBalanceMismatch("CREDIT_DEBIT_IMBALANCE", event.getDiscrepancyAmount());
    }

    private void handleGeneralLedgerMismatch(LedgerBalanceMismatchEvent event, String correlationId) {
        createDiscrepancyRecord(event, "GENERAL_LEDGER_MISMATCH", correlationId);

        // Perform general ledger reconciliation
        kafkaTemplate.send("general-ledger-reconciliation-requests", Map.of(
            "accountId", event.getAccountId(),
            "reconciliationType", "FULL",
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check chart of accounts
        kafkaTemplate.send("chart-of-accounts-validation", Map.of(
            "accountId", event.getAccountId(),
            "validationType", "INTEGRITY_CHECK",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("General Ledger Mismatch",
            String.format("General ledger mismatch for account %s: $%,.2f",
                event.getAccountId(), event.getDiscrepancyAmount()),
            "HIGH");

        metricsService.recordBalanceMismatch("GENERAL_LEDGER_MISMATCH", event.getDiscrepancyAmount());
    }

    private void handleSubsidiaryLedgerMismatch(LedgerBalanceMismatchEvent event, String correlationId) {
        createDiscrepancyRecord(event, "SUBSIDIARY_LEDGER_MISMATCH", correlationId);

        // Reconcile subsidiary with general ledger
        kafkaTemplate.send("subsidiary-reconciliation-requests", Map.of(
            "accountId", event.getAccountId(),
            "subsidiaryType", event.getSubsidiaryType(),
            "reconciliationType", "IMMEDIATE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Validate subsidiary ledger entries
        kafkaTemplate.send("subsidiary-ledger-validation", Map.of(
            "accountId", event.getAccountId(),
            "validationType", "ENTRIES_VALIDATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordBalanceMismatch("SUBSIDIARY_LEDGER_MISMATCH", event.getDiscrepancyAmount());
    }

    private void handleTrialBalanceError(LedgerBalanceMismatchEvent event, String correlationId) {
        createDiscrepancyRecord(event, "TRIAL_BALANCE_ERROR", correlationId);

        // Generate new trial balance
        kafkaTemplate.send("trial-balance-generation-requests", Map.of(
            "periodEnd", event.getPeriodEnd(),
            "generationType", "EMERGENCY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Validate all ledger accounts
        kafkaTemplate.send("ledger-accounts-validation", Map.of(
            "validationType", "TRIAL_BALANCE_PREPARATION",
            "periodEnd", event.getPeriodEnd(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Trial Balance Error",
            String.format("Trial balance error detected: $%,.2f imbalance",
                event.getDiscrepancyAmount()),
            "HIGH");

        metricsService.recordBalanceMismatch("TRIAL_BALANCE_ERROR", event.getDiscrepancyAmount());
    }

    private void handleControlAccountMismatch(LedgerBalanceMismatchEvent event, String correlationId) {
        createDiscrepancyRecord(event, "CONTROL_ACCOUNT_MISMATCH", correlationId);

        // Reconcile control account with subsidiary accounts
        kafkaTemplate.send("control-account-reconciliation", Map.of(
            "controlAccountId", event.getControlAccountId(),
            "reconciliationType", "IMMEDIATE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Validate subsidiary account totals
        kafkaTemplate.send("subsidiary-totals-validation", Map.of(
            "controlAccountId", event.getControlAccountId(),
            "validationType", "BALANCE_VERIFICATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Control Account Mismatch",
            String.format("Control account %s mismatch: $%,.2f",
                event.getControlAccountId(), event.getDiscrepancyAmount()),
            "HIGH");

        metricsService.recordBalanceMismatch("CONTROL_ACCOUNT_MISMATCH", event.getDiscrepancyAmount());
    }

    private void handleFloatingBalanceError(LedgerBalanceMismatchEvent event, String correlationId) {
        createDiscrepancyRecord(event, "FLOATING_BALANCE_ERROR", correlationId);

        // Identify source of floating balance
        kafkaTemplate.send("floating-balance-investigation", Map.of(
            "accountId", event.getAccountId(),
            "investigationType", "SOURCE_IDENTIFICATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Create adjustment entry
        kafkaTemplate.send("balance-adjustment-requests", Map.of(
            "accountId", event.getAccountId(),
            "adjustmentAmount", event.getDiscrepancyAmount().negate(),
            "adjustmentType", "FLOATING_BALANCE_CORRECTION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordBalanceMismatch("FLOATING_BALANCE_ERROR", event.getDiscrepancyAmount());
    }

    private void handleCurrencyConversionError(LedgerBalanceMismatchEvent event, String correlationId) {
        createDiscrepancyRecord(event, "CURRENCY_CONVERSION_ERROR", correlationId);

        // Validate exchange rates
        kafkaTemplate.send("exchange-rate-validation", Map.of(
            "fromCurrency", event.getFromCurrency(),
            "toCurrency", event.getToCurrency(),
            "conversionDate", event.getConversionDate(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Recalculate currency conversions
        kafkaTemplate.send("currency-recalculation-requests", Map.of(
            "accountId", event.getAccountId(),
            "recalculationType", "IMMEDIATE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordBalanceMismatch("CURRENCY_CONVERSION_ERROR", event.getDiscrepancyAmount());
    }

    private void handlePeriodEndImbalance(LedgerBalanceMismatchEvent event, String correlationId) {
        createDiscrepancyRecord(event, "PERIOD_END_IMBALANCE", correlationId);

        // Freeze period-end processing
        kafkaTemplate.send("period-end-freeze-requests", Map.of(
            "periodEnd", event.getPeriodEnd(),
            "freezeType", "IMBALANCE_DETECTED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Trigger period-end reconciliation
        kafkaTemplate.send("period-end-reconciliation", Map.of(
            "periodEnd", event.getPeriodEnd(),
            "reconciliationType", "EMERGENCY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Period-End Imbalance",
            String.format("Period-end imbalance detected: $%,.2f",
                event.getDiscrepancyAmount()),
            "CRITICAL");

        metricsService.recordBalanceMismatch("PERIOD_END_IMBALANCE", event.getDiscrepancyAmount());
    }

    private void handleNostroVostroMismatch(LedgerBalanceMismatchEvent event, String correlationId) {
        createDiscrepancyRecord(event, "NOSTRO_VOSTRO_MISMATCH", correlationId);

        // Reconcile with correspondent bank
        kafkaTemplate.send("correspondent-bank-reconciliation", Map.of(
            "bankCode", event.getCorrespondentBankCode(),
            "accountType", event.getAccountType(),
            "reconciliationType", "IMMEDIATE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Validate SWIFT messages
        kafkaTemplate.send("swift-message-validation", Map.of(
            "accountId", event.getAccountId(),
            "validationType", "BALANCE_CONFIRMATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Nostro/Vostro Mismatch",
            String.format("Nostro/Vostro mismatch for account %s: $%,.2f",
                event.getAccountId(), event.getDiscrepancyAmount()),
            "HIGH");

        metricsService.recordBalanceMismatch("NOSTRO_VOSTRO_MISMATCH", event.getDiscrepancyAmount());
    }

    private void handleInterBranchImbalance(LedgerBalanceMismatchEvent event, String correlationId) {
        createDiscrepancyRecord(event, "INTER_BRANCH_IMBALANCE", correlationId);

        // Reconcile inter-branch accounts
        kafkaTemplate.send("inter-branch-reconciliation", Map.of(
            "fromBranch", event.getFromBranch(),
            "toBranch", event.getToBranch(),
            "reconciliationType", "IMMEDIATE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Validate inter-branch transfers
        kafkaTemplate.send("inter-branch-transfer-validation", Map.of(
            "accountId", event.getAccountId(),
            "validationType", "BALANCE_VERIFICATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordBalanceMismatch("INTER_BRANCH_IMBALANCE", event.getDiscrepancyAmount());
    }

    private void handleGenericBalanceMismatch(LedgerBalanceMismatchEvent event, String correlationId) {
        createDiscrepancyRecord(event, "UNKNOWN_MISMATCH", correlationId);

        // Log for investigation
        auditService.logLedgerEvent("UNKNOWN_BALANCE_MISMATCH", event.getAccountId(),
            Map.of("mismatchType", event.getMismatchType(), "discrepancyAmount", event.getDiscrepancyAmount(),
                "mismatchDescription", event.getMismatchDescription(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        notificationService.sendOperationalAlert("Unknown Balance Mismatch",
            String.format("Unknown balance mismatch for account %s: $%,.2f",
                event.getAccountId(), event.getDiscrepancyAmount()),
            "HIGH");

        metricsService.recordBalanceMismatch("UNKNOWN", event.getDiscrepancyAmount());
    }

    private void createDiscrepancyRecord(LedgerBalanceMismatchEvent event, String mismatchType, String correlationId) {
        try {
            BalanceDiscrepancy discrepancy = BalanceDiscrepancy.builder()
                .accountId(event.getAccountId())
                .mismatchType(mismatchType)
                .discrepancyAmount(event.getDiscrepancyAmount())
                .expectedBalance(event.getExpectedBalance())
                .actualBalance(event.getActualBalance())
                .ledgerType(event.getLedgerType())
                .detectionTime(LocalDateTime.now())
                .status("OPEN")
                .correlationId(correlationId)
                .build();

            discrepancyRepository.save(discrepancy);
        } catch (Exception e) {
            log.error("Failed to create balance discrepancy record: {}", e.getMessage());
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