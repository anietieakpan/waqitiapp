package com.waqiti.payment.kafka;

import com.waqiti.common.events.BalanceUpdateEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.idempotency.IdempotentPaymentProcessor;
import com.waqiti.payment.domain.BalanceUpdate;
import com.waqiti.payment.repository.BalanceUpdateRepository;
import com.waqiti.payment.service.BalanceService;
import com.waqiti.payment.service.AccountService;
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

/**
 * Balance Updates Consumer with Industrial-Strength Idempotency
 *
 * CRITICAL SECURITY FIX (2025-11-08):
 * - Replaced in-memory ConcurrentHashMap with 3-layer idempotency defense
 * - Prevents duplicate balance updates that cause account discrepancies
 * - Database-backed idempotency survives crashes and provides audit trail
 *
 * THREE-LAYER DEFENSE:
 * Layer 1: Redis Cache - <1ms duplicate detection
 * Layer 2: Distributed Lock - Prevents race conditions
 * Layer 3: Database Constraint - ACID guarantees
 *
 * IMPACT: Eliminates account balance corruption from duplicate events
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BalanceUpdatesConsumer {

    private final BalanceUpdateRepository balanceUpdateRepository;
    private final BalanceService balanceService;
    private final AccountService accountService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler dlqHandler;
    private final IdempotentPaymentProcessor idempotentProcessor;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("balance_updates_processed_total")
            .description("Total number of successfully processed balance update events")
            .register(meterRegistry);
        errorCounter = Counter.builder("balance_updates_errors_total")
            .description("Total number of balance update processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("balance_updates_processing_duration")
            .description("Time taken to process balance update events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"balance-updates", "account-balance-changes", "real-time-balance-updates"},
        groupId = "balance-updates-service-group",
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
    @CircuitBreaker(name = "balance-updates", fallbackMethod = "handleBalanceUpdateEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBalanceUpdateEvent(
            @Payload BalanceUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("balance-update-%s-p%d-o%d", event.getAccountId(), partition, offset);

        // Generate idempotency key from event data
        String eventId = String.format("%s-%s-%s", event.getAccountId(), event.getTransactionId(), event.getTimestamp());

        try {
            log.info("Processing balance update: accountId={}, transactionId={}, updateType={}, amount={}",
                event.getAccountId(), event.getTransactionId(), event.getUpdateType(), event.getAmount());

            // ═══════════════════════════════════════════════════════════════════════
            // IDEMPOTENT PROCESSING (3-Layer Defense)
            // ═══════════════════════════════════════════════════════════════════════
            BalanceUpdate result = idempotentProcessor.process(
                eventId,                              // Unique event ID (idempotency key)
                event.getAccountId(),                 // Business entity ID
                "BALANCE_UPDATE",                     // Entity type
                "balance-updates-consumer",           // Consumer name
                () -> processBalanceUpdate(event, correlationId),  // Business logic
                BalanceUpdate.class                   // Result class
            );

            log.info("Balance update processed successfully: accountId={}, updateType={}, newBalance={}",
                event.getAccountId(), event.getUpdateType(), result.getNewBalance());

            // Audit log
            auditService.logPaymentEvent("BALANCE_UPDATE_EVENT_PROCESSED", event.getAccountId(),
                Map.of("updateType", event.getUpdateType(), "amount", event.getAmount(),
                    "previousBalance", event.getPreviousBalance(), "newBalance", event.getNewBalance(),
                    "transactionId", event.getTransactionId(), "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process balance update event: {}", e.getMessage(), e);

            // Send to DLQ with context
            dlqHandler.handleFailedMessage(
                "balance-updates",
                event,
                e,
                Map.of(
                    "accountId", event.getAccountId(),
                    "transactionId", event.getTransactionId(),
                    "updateType", event.getUpdateType().toString(),
                    "amount", event.getAmount().toString(),
                    "correlationId", correlationId,
                    "partition", String.valueOf(partition),
                    "offset", String.valueOf(offset)
                )
            );

            kafkaTemplate.send("balance-updates-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handleBalanceUpdateEventFallback(
            BalanceUpdateEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("balance-update-fallback-%s-p%d-o%d", event.getAccountId(), partition, offset);

        log.error("Circuit breaker fallback triggered for balance update: accountId={}, error={}",
            event.getAccountId(), ex.getMessage());

        kafkaTemplate.send("balance-updates-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBalanceUpdateEvent(
            @Payload BalanceUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-balance-update-%s-%d", event.getAccountId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Balance update permanently failed: accountId={}, error={}",
            event.getAccountId(), exceptionMessage);

        auditService.logPaymentEvent("BALANCE_UPDATE_DLT_EVENT", event.getAccountId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "updateType", event.getUpdateType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));
    }

    /**
     * Process balance update (business logic - called within idempotency wrapper)
     *
     * This method contains the actual balance update logic.
     * It is wrapped by IdempotentPaymentProcessor to ensure exactly-once execution.
     */
    private BalanceUpdate processBalanceUpdate(BalanceUpdateEvent event, String correlationId) {
        log.info("Executing balance update logic: updateType={}, accountId={}",
            event.getUpdateType(), event.getAccountId());

        BalanceUpdate update = null;

        switch (event.getUpdateType()) {
            case CREDIT:
                update = processCreditUpdate(event, correlationId);
                break;

            case DEBIT:
                update = processDebitUpdate(event, correlationId);
                break;

            case ADJUSTMENT:
                update = processAdjustmentUpdate(event, correlationId);
                break;

            case HOLD:
                update = processHoldUpdate(event, correlationId);
                break;

            case RELEASE_HOLD:
                update = processReleaseHoldUpdate(event, correlationId);
                break;

            case REVERSAL:
                update = processReversalUpdate(event, correlationId);
                break;

            case INTEREST_ACCRUAL:
                update = processInterestAccrualUpdate(event, correlationId);
                break;

            case FEE_DEDUCTION:
                update = processFeeDeductionUpdate(event, correlationId);
                break;

            default:
                log.warn("Unknown balance update type: {}", event.getUpdateType());
                throw new IllegalArgumentException("Unknown balance update type: " + event.getUpdateType());
        }

        if (update == null) {
            throw new RuntimeException("Balance update processing returned null");
        }

        return update;
    }

    private BalanceUpdate processCreditUpdate(BalanceUpdateEvent event, String correlationId) {
        // Update account balance with credit
        balanceService.creditAccount(
            event.getAccountId(),
            event.getAmount(),
            event.getTransactionId(),
            event.getDescription(),
            correlationId
        );

        // Record balance update
        BalanceUpdate update = BalanceUpdate.builder()
            .accountId(event.getAccountId())
            .transactionId(event.getTransactionId())
            .updateType("CREDIT")
            .amount(event.getAmount())
            .previousBalance(event.getPreviousBalance())
            .newBalance(event.getNewBalance())
            .description(event.getDescription())
            .processedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceUpdateRepository.save(update);

        // Check for balance thresholds
        balanceService.checkBalanceThresholds(event.getAccountId(), event.getNewBalance(), correlationId);

        // Record metrics
        metricsService.recordBalanceCredit(event.getAccountId(), event.getAmount());

        log.info("Credit processed: accountId={}, amount=${}, newBalance=${}",
            event.getAccountId(), event.getAmount(), event.getNewBalance());

        return update;
    }

    private BalanceUpdate processDebitUpdate(BalanceUpdateEvent event, String correlationId) {
        // Validate sufficient funds before debiting
        if (event.getNewBalance() < 0 && !accountService.hasOverdraftProtection(event.getAccountId())) {
            log.warn("Insufficient funds for debit: accountId={}, requestedAmount={}, availableBalance={}",
                event.getAccountId(), event.getAmount(), event.getPreviousBalance());

            // Send insufficient funds notification
            notificationService.sendNotification(event.getUserId(), "Transaction Declined",
                "Your transaction was declined due to insufficient funds.",
                correlationId);

            throw new RuntimeException("Insufficient funds for debit operation");
        }

        // Update account balance with debit
        balanceService.debitAccount(
            event.getAccountId(),
            event.getAmount(),
            event.getTransactionId(),
            event.getDescription(),
            correlationId
        );

        // Record balance update
        BalanceUpdate update = BalanceUpdate.builder()
            .accountId(event.getAccountId())
            .transactionId(event.getTransactionId())
            .updateType("DEBIT")
            .amount(event.getAmount())
            .previousBalance(event.getPreviousBalance())
            .newBalance(event.getNewBalance())
            .description(event.getDescription())
            .processedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceUpdateRepository.save(update);

        // Check for low balance alerts
        if (event.getNewBalance() < 100.0) { // Example threshold
            balanceService.sendLowBalanceAlert(event.getAccountId(), event.getNewBalance(), correlationId);
        }

        // Record metrics
        metricsService.recordBalanceDebit(event.getAccountId(), event.getAmount());

        log.info("Debit processed: accountId={}, amount=${}, newBalance=${}",
            event.getAccountId(), event.getAmount(), event.getNewBalance());

        return update;
    }

    private BalanceUpdate processAdjustmentUpdate(BalanceUpdateEvent event, String correlationId) {
        // Process balance adjustment (could be positive or negative)
        balanceService.adjustBalance(
            event.getAccountId(),
            event.getAmount(),
            event.getAdjustmentReason(),
            event.getDescription(),
            correlationId
        );

        // Record balance update
        BalanceUpdate update = BalanceUpdate.builder()
            .accountId(event.getAccountId())
            .transactionId(event.getTransactionId())
            .updateType("ADJUSTMENT")
            .amount(event.getAmount())
            .previousBalance(event.getPreviousBalance())
            .newBalance(event.getNewBalance())
            .description(event.getDescription())
            .adjustmentReason(event.getAdjustmentReason())
            .processedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceUpdateRepository.save(update);

        // Send notification for significant adjustments
        if (Math.abs(event.getAmount()) > 1000.0) {
            notificationService.sendNotification(event.getUserId(), "Balance Adjustment",
                String.format("A balance adjustment of $%.2f has been applied to your account: %s",
                    event.getAmount(), event.getDescription()),
                correlationId);
        }

        // Record metrics
        metricsService.recordBalanceAdjustment(event.getAccountId(), event.getAmount(), event.getAdjustmentReason());

        log.info("Adjustment processed: accountId={}, amount=${}, reason={}",
            event.getAccountId(), event.getAmount(), event.getAdjustmentReason());
    }

    private BalanceUpdate processHoldUpdate(BalanceUpdateEvent event, String correlationId) {
        // Place hold on funds
        balanceService.holdFunds(
            event.getAccountId(),
            event.getAmount(),
            event.getHoldReason(),
            event.getHoldExpiryTime(),
            correlationId
        );

        // Record hold update
        BalanceUpdate update = BalanceUpdate.builder()
            .accountId(event.getAccountId())
            .transactionId(event.getTransactionId())
            .updateType("HOLD")
            .amount(event.getAmount())
            .previousBalance(event.getPreviousBalance())
            .newBalance(event.getNewBalance())
            .availableBalance(event.getAvailableBalance())
            .description(event.getDescription())
            .holdReason(event.getHoldReason())
            .processedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceUpdateRepository.save(update);

        // Record metrics
        metricsService.recordFundsHold(event.getAccountId(), event.getAmount());

        log.info("Hold placed: accountId={}, amount=${}, reason={}",
            event.getAccountId(), event.getAmount(), event.getHoldReason());
    }

    private BalanceUpdate processReleaseHoldUpdate(BalanceUpdateEvent event, String correlationId) {
        // Release held funds
        balanceService.releaseFunds(
            event.getAccountId(),
            event.getAmount(),
            event.getOriginalHoldId(),
            correlationId
        );

        // Record hold release
        BalanceUpdate update = BalanceUpdate.builder()
            .accountId(event.getAccountId())
            .transactionId(event.getTransactionId())
            .updateType("RELEASE_HOLD")
            .amount(event.getAmount())
            .previousBalance(event.getPreviousBalance())
            .newBalance(event.getNewBalance())
            .availableBalance(event.getAvailableBalance())
            .description(event.getDescription())
            .processedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceUpdateRepository.save(update);

        // Record metrics
        metricsService.recordFundsRelease(event.getAccountId(), event.getAmount());

        log.info("Hold released: accountId={}, amount=${}",
            event.getAccountId(), event.getAmount());
    }

    private BalanceUpdate processReversalUpdate(BalanceUpdateEvent event, String correlationId) {
        // Process transaction reversal
        balanceService.reverseTransaction(
            event.getAccountId(),
            event.getOriginalTransactionId(),
            event.getAmount(),
            event.getReversalReason(),
            correlationId
        );

        // Record reversal update
        BalanceUpdate update = BalanceUpdate.builder()
            .accountId(event.getAccountId())
            .transactionId(event.getTransactionId())
            .originalTransactionId(event.getOriginalTransactionId())
            .updateType("REVERSAL")
            .amount(event.getAmount())
            .previousBalance(event.getPreviousBalance())
            .newBalance(event.getNewBalance())
            .description(event.getDescription())
            .reversalReason(event.getReversalReason())
            .processedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceUpdateRepository.save(update);

        // Send notification about reversal
        notificationService.sendNotification(event.getUserId(), "Transaction Reversed",
            String.format("A transaction of $%.2f has been reversed: %s",
                event.getAmount(), event.getReversalReason()),
            correlationId);

        // Record metrics
        metricsService.recordTransactionReversal(event.getAccountId(), event.getAmount(), event.getReversalReason());

        log.info("Reversal processed: accountId={}, amount=${}, reason={}",
            event.getAccountId(), event.getAmount(), event.getReversalReason());
    }

    private BalanceUpdate processInterestAccrualUpdate(BalanceUpdateEvent event, String correlationId) {
        // Process interest accrual
        balanceService.accrueInterest(
            event.getAccountId(),
            event.getAmount(),
            event.getInterestRate(),
            event.getInterestPeriod(),
            correlationId
        );

        // Record interest accrual
        BalanceUpdate update = BalanceUpdate.builder()
            .accountId(event.getAccountId())
            .transactionId(event.getTransactionId())
            .updateType("INTEREST_ACCRUAL")
            .amount(event.getAmount())
            .previousBalance(event.getPreviousBalance())
            .newBalance(event.getNewBalance())
            .description(event.getDescription())
            .interestRate(event.getInterestRate())
            .processedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceUpdateRepository.save(update);

        // Record metrics
        metricsService.recordInterestAccrual(event.getAccountId(), event.getAmount(), event.getInterestRate());

        log.info("Interest accrued: accountId={}, amount=${}, rate={}%",
            event.getAccountId(), event.getAmount(), event.getInterestRate());
    }

    private BalanceUpdate processFeeDeductionUpdate(BalanceUpdateEvent event, String correlationId) {
        // Process fee deduction
        balanceService.deductFee(
            event.getAccountId(),
            event.getAmount(),
            event.getFeeType(),
            event.getDescription(),
            correlationId
        );

        // Record fee deduction
        BalanceUpdate update = BalanceUpdate.builder()
            .accountId(event.getAccountId())
            .transactionId(event.getTransactionId())
            .updateType("FEE_DEDUCTION")
            .amount(event.getAmount())
            .previousBalance(event.getPreviousBalance())
            .newBalance(event.getNewBalance())
            .description(event.getDescription())
            .feeType(event.getFeeType())
            .processedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        balanceUpdateRepository.save(update);

        // Send fee notification
        notificationService.sendNotification(event.getUserId(), "Fee Charged",
            String.format("A fee of $%.2f has been charged to your account: %s",
                event.getAmount(), event.getDescription()),
            correlationId);

        // Record metrics
        metricsService.recordFeeDeduction(event.getAccountId(), event.getAmount(), event.getFeeType());

        log.info("Fee deducted: accountId={}, amount=${}, type={}",
            event.getAccountId(), event.getAmount(), event.getFeeType());
    }
}