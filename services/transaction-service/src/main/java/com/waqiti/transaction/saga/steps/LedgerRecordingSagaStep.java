package com.waqiti.transaction.saga.steps;

import com.waqiti.transaction.client.LedgerServiceClient;
import com.waqiti.transaction.dto.LedgerEntryRequest;
import com.waqiti.transaction.dto.LedgerEntryResponse;
import com.waqiti.transaction.saga.TransactionSagaContext;
import com.waqiti.transaction.saga.SagaStepResult;
import com.waqiti.common.saga.SagaStep;
import com.waqiti.common.saga.SagaStepStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * PRODUCTION-READY Ledger Recording Saga Step
 *
 * Records transaction in double-entry ledger for accounting and compliance
 *
 * Features:
 * - Double-entry bookkeeping (debit + credit)
 * - Immutable ledger entries
 * - Accounting reconciliation
 * - Balance verification
 * - Audit trail for regulatory compliance
 * - Circuit breaker for resilience
 *
 * Compensation:
 * - Creates reversal entries (contra-entries)
 * - Maintains accounting integrity
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerRecordingSagaStep implements SagaStep<TransactionSagaContext> {

    private final LedgerServiceClient ledgerClient;
    private final MeterRegistry meterRegistry;

    private static final String STEP_NAME = "LEDGER_RECORDING";

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    @Override
    @CircuitBreaker(name = "ledgerService", fallbackMethod = "fallbackLedgerRecording")
    @Retry(name = "ledgerService")
    public CompletableFuture<SagaStepResult> execute(TransactionSagaContext context) {
        Timer.Sample timer = Timer.start(meterRegistry);

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("LEDGER RECORDING: Creating double-entry for transaction: {} amount: {} {}",
                    context.getTransactionId(), context.getAmount(), context.getCurrency());

                // Build double-entry ledger request
                LedgerEntryRequest request = LedgerEntryRequest.builder()
                    .transactionId(context.getTransactionId())
                    .transactionReference(context.getTransferReference())
                    .transactionType(context.getTransactionType().name())
                    .amount(context.getAmount())
                    .currency(context.getCurrency())
                    .sourceAccountId(context.getSourceWalletId())
                    .destinationAccountId(context.getDestinationWalletId())
                    .userId(context.getUserId())
                    .description(context.getDescription())
                    .metadata(context.getMetadata())
                    .timestamp(LocalDateTime.now())
                    .build();

                // Create ledger entry (double-entry: debit source, credit destination)
                LedgerEntryResponse response = ledgerClient.createLedgerEntry(request);

                if (response == null || !response.isSuccess()) {
                    log.error("LEDGER RECORDING FAILED: Unable to create ledger entry for transaction: {} - Reason: {}",
                        context.getTransactionId(), response != null ? response.getErrorMessage() : "null response");

                    timer.stop(Timer.builder("transaction.saga.step.ledger.time")
                        .tag("result", "failed")
                        .register(meterRegistry));

                    meterRegistry.counter("transaction.ledger.failures").increment();

                    return SagaStepResult.builder()
                        .stepName(STEP_NAME)
                        .status(SagaStepStatus.FAILED)
                        .errorMessage("Ledger recording failed: " +
                            (response != null ? response.getErrorMessage() : "Service unavailable"))
                        .timestamp(LocalDateTime.now())
                        .build();
                }

                // Store ledger entry IDs for compensation
                context.setLedgerEntryId(response.getLedgerEntryId());
                context.setDebitEntryId(response.getDebitEntryId());
                context.setCreditEntryId(response.getCreditEntryId());

                log.info("LEDGER RECORDING SUCCESS: Created ledger entry {} (Debit: {}, Credit: {}) for transaction: {}",
                    response.getLedgerEntryId(), response.getDebitEntryId(),
                    response.getCreditEntryId(), context.getTransactionId());

                timer.stop(Timer.builder("transaction.saga.step.ledger.time")
                    .tag("result", "success")
                    .tag("currency", context.getCurrency())
                    .register(meterRegistry));

                meterRegistry.counter("transaction.ledger.successes",
                    "currency", context.getCurrency()).increment();

                return SagaStepResult.builder()
                    .stepName(STEP_NAME)
                    .status(SagaStepStatus.SUCCESS)
                    .message(String.format("Ledger entry created: %s (Amount: %s %s)",
                        response.getLedgerEntryId(), context.getAmount(), context.getCurrency()))
                    .data("ledgerEntryId", response.getLedgerEntryId())
                    .data("debitEntryId", response.getDebitEntryId())
                    .data("creditEntryId", response.getCreditEntryId())
                    .data("balanceVerified", response.isBalanceVerified())
                    .timestamp(LocalDateTime.now())
                    .build();

            } catch (Exception e) {
                log.error("LEDGER RECORDING ERROR: Error creating ledger entry for transaction: {}",
                    context.getTransactionId(), e);

                timer.stop(Timer.builder("transaction.saga.step.ledger.time")
                    .tag("result", "error")
                    .tag("error_type", e.getClass().getSimpleName())
                    .register(meterRegistry));

                meterRegistry.counter("transaction.ledger.errors").increment();

                return SagaStepResult.builder()
                    .stepName(STEP_NAME)
                    .status(SagaStepStatus.FAILED)
                    .errorMessage("Ledger recording failed: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
            }
        });
    }

    /**
     * Fallback when ledger service is unavailable
     */
    private CompletableFuture<SagaStepResult> fallbackLedgerRecording(TransactionSagaContext context, Exception ex) {
        log.error("LEDGER FALLBACK: Ledger service unavailable for transaction: {} - {}",
            context.getTransactionId(), ex.getMessage());

        meterRegistry.counter("transaction.ledger.fallback").increment();

        // CRITICAL: Ledger recording is essential for compliance
        // Queue for retry or manual intervention
        context.setLedgerRecordingPending(true);

        return CompletableFuture.completedFuture(SagaStepResult.builder()
            .stepName(STEP_NAME)
            .status(SagaStepStatus.FAILED)
            .errorMessage("Ledger service unavailable - transaction queued for retry")
            .data("fallbackActivated", true)
            .data("requiresRetry", true)
            .timestamp(LocalDateTime.now())
            .build());
    }

    @Override
    public CompletableFuture<SagaStepResult> compensate(TransactionSagaContext context, SagaStepResult originalResult) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String ledgerEntryId = context.getLedgerEntryId();

                if (ledgerEntryId == null || ledgerEntryId.isEmpty()) {
                    log.warn("LEDGER COMPENSATION: No ledger entry ID found for transaction: {} - skipping",
                        context.getTransactionId());

                    return SagaStepResult.builder()
                        .stepName(STEP_NAME + "_COMPENSATION")
                        .status(SagaStepStatus.SUCCESS)
                        .message("No ledger entry to reverse")
                        .timestamp(LocalDateTime.now())
                        .build();
                }

                log.info("LEDGER COMPENSATION: Creating reversal entries for transaction: {}",
                    context.getTransactionId());

                // Build reversal request (contra-entries)
                LedgerEntryRequest reversalRequest = LedgerEntryRequest.builder()
                    .originalTransactionId(context.getTransactionId())
                    .originalLedgerEntryId(ledgerEntryId)
                    .transactionType("REVERSAL_" + context.getTransactionType().name())
                    .amount(context.getAmount())
                    .currency(context.getCurrency())
                    .sourceAccountId(context.getSourceWalletId())
                    .destinationAccountId(context.getDestinationWalletId())
                    .userId(context.getUserId())
                    .description("REVERSAL: " + context.getDescription())
                    .metadata(java.util.Map.of(
                        "originalLedgerEntryId", ledgerEntryId,
                        "reversalReason", "Saga compensation"))
                    .timestamp(LocalDateTime.now())
                    .build();

                // Create reversal entries
                LedgerEntryResponse response = ledgerClient.reverseLedgerEntry(reversalRequest);

                if (response != null && response.isSuccess()) {
                    log.info("LEDGER COMPENSATION SUCCESS: Created reversal entries {} for transaction: {}",
                        response.getLedgerEntryId(), context.getTransactionId());

                    meterRegistry.counter("transaction.ledger.compensations.success").increment();

                    return SagaStepResult.builder()
                        .stepName(STEP_NAME + "_COMPENSATION")
                        .status(SagaStepStatus.SUCCESS)
                        .message("Ledger entries reversed successfully")
                        .data("originalLedgerEntryId", ledgerEntryId)
                        .data("reversalLedgerEntryId", response.getLedgerEntryId())
                        .timestamp(LocalDateTime.now())
                        .build();
                } else {
                    log.error("LEDGER COMPENSATION FAILED: Error reversing ledger entry {} - {}",
                        ledgerEntryId, response != null ? response.getErrorMessage() : "null response");

                    meterRegistry.counter("transaction.ledger.compensations.failed").increment();

                    // CRITICAL: Ledger reversal failure requires manual intervention
                    return SagaStepResult.builder()
                        .stepName(STEP_NAME + "_COMPENSATION")
                        .status(SagaStepStatus.FAILED)
                        .errorMessage("Ledger reversal failed - manual accounting intervention required")
                        .data("originalLedgerEntryId", ledgerEntryId)
                        .data("requiresManualIntervention", true)
                        .timestamp(LocalDateTime.now())
                        .build();
                }

            } catch (Exception e) {
                log.error("LEDGER COMPENSATION ERROR: Error during compensation for transaction: {}",
                    context.getTransactionId(), e);

                meterRegistry.counter("transaction.ledger.compensations.error").increment();

                // CRITICAL: Compensation error requires manual accounting intervention
                return SagaStepResult.builder()
                    .stepName(STEP_NAME + "_COMPENSATION")
                    .status(SagaStepStatus.FAILED)
                    .errorMessage("Ledger compensation error - manual accounting intervention required: " +
                        e.getMessage())
                    .data("requiresManualIntervention", true)
                    .timestamp(LocalDateTime.now())
                    .build();
            }
        });
    }
}
