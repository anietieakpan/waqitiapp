package com.waqiti.transaction.saga.steps;

import com.waqiti.common.client.WalletServiceClient;
import com.waqiti.common.client.ServiceResponse;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * PRODUCTION-READY P2P Transfer Saga Step
 *
 * Executes atomic wallet-to-wallet transfer using reserved funds
 *
 * Features:
 * - Atomic debit/credit operations
 * - Uses fund reservation to prevent double-spending
 * - Idempotent transfer execution
 * - Comprehensive error handling
 * - Audit trail
 *
 * Compensation:
 * - Reverses the transfer (credit source, debit destination)
 * - Idempotent reversal operation
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class P2PTransferSagaStep implements SagaStep<TransactionSagaContext> {

    private final WalletServiceClient walletClient;
    private final MeterRegistry meterRegistry;

    private static final String STEP_NAME = "P2P_TRANSFER";

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    @Override
    @CircuitBreaker(name = "walletService", fallbackMethod = "fallbackTransfer")
    @Retry(name = "walletService")
    public CompletableFuture<SagaStepResult> execute(TransactionSagaContext context) {
        Timer.Sample timer = Timer.start(meterRegistry);

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("P2P TRANSFER: Executing transfer of {} {} from wallet {} to wallet {} for transaction: {}",
                    context.getAmount(), context.getCurrency(),
                    context.getSourceWalletId(), context.getDestinationWalletId(),
                    context.getTransactionId());

                // Build transfer request
                WalletServiceClient.TransferRequest request = WalletServiceClient.TransferRequest.builder()
                    .fromWalletId(UUID.fromString(context.getSourceWalletId()))
                    .toWalletId(UUID.fromString(context.getDestinationWalletId()))
                    .amount(context.getAmount())
                    .currency(context.getCurrency())
                    .transactionId(context.getTransactionId())
                    .reservationId(context.getReservationId() != null ?
                        UUID.fromString(context.getReservationId()) : null)
                    .description(context.getDescription())
                    .metadata(context.getMetadata())
                    .build();

                // Execute atomic transfer
                ServiceResponse<WalletServiceClient.TransferResultDTO> response =
                    walletClient.transferFunds(request).get();

                if (!response.isSuccess() || response.getData() == null) {
                    log.error("P2P TRANSFER FAILED: Transfer failed for transaction: {} - Reason: {}",
                        context.getTransactionId(), response.getErrorMessage());

                    timer.stop(Timer.builder("transaction.saga.step.transfer.time")
                        .tag("result", "failed")
                        .tag("type", "p2p")
                        .register(meterRegistry));

                    meterRegistry.counter("transaction.transfer.failures",
                        "type", "p2p").increment();

                    return SagaStepResult.builder()
                        .stepName(STEP_NAME)
                        .status(SagaStepStatus.FAILED)
                        .errorMessage("Transfer failed: " + response.getErrorMessage())
                        .timestamp(LocalDateTime.now())
                        .build();
                }

                WalletServiceClient.TransferResultDTO transferResult = response.getData();

                // Store transfer ID for compensation
                context.setTransferReference(transferResult.getTransferReference());
                context.setSourceBalanceAfter(transferResult.getSourceBalanceAfter());
                context.setDestinationBalanceAfter(transferResult.getDestinationBalanceAfter());

                log.info("P2P TRANSFER SUCCESS: Transferred {} {} (Reference: {}) for transaction: {}",
                    context.getAmount(), context.getCurrency(),
                    transferResult.getTransferReference(), context.getTransactionId());

                timer.stop(Timer.builder("transaction.saga.step.transfer.time")
                    .tag("result", "success")
                    .tag("type", "p2p")
                    .tag("currency", context.getCurrency())
                    .register(meterRegistry));

                meterRegistry.counter("transaction.transfer.successes",
                    "type", "p2p",
                    "currency", context.getCurrency()).increment();

                meterRegistry.summary("transaction.transfer.amount",
                    "currency", context.getCurrency())
                    .record(context.getAmount().doubleValue());

                return SagaStepResult.builder()
                    .stepName(STEP_NAME)
                    .status(SagaStepStatus.SUCCESS)
                    .message(String.format("Transfer completed: %s %s (Ref: %s)",
                        context.getAmount(), context.getCurrency(), transferResult.getTransferReference()))
                    .data("transferReference", transferResult.getTransferReference())
                    .data("sourceBalanceAfter", transferResult.getSourceBalanceAfter())
                    .data("destinationBalanceAfter", transferResult.getDestinationBalanceAfter())
                    .data("timestamp", transferResult.getCompletedAt())
                    .timestamp(LocalDateTime.now())
                    .build();

            } catch (Exception e) {
                log.error("P2P TRANSFER ERROR: Error executing transfer for transaction: {}",
                    context.getTransactionId(), e);

                timer.stop(Timer.builder("transaction.saga.step.transfer.time")
                    .tag("result", "error")
                    .tag("type", "p2p")
                    .tag("error_type", e.getClass().getSimpleName())
                    .register(meterRegistry));

                meterRegistry.counter("transaction.transfer.errors",
                    "type", "p2p").increment();

                return SagaStepResult.builder()
                    .stepName(STEP_NAME)
                    .status(SagaStepStatus.FAILED)
                    .errorMessage("Transfer execution failed: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
            }
        });
    }

    /**
     * Fallback when wallet service is unavailable
     */
    private CompletableFuture<SagaStepResult> fallbackTransfer(TransactionSagaContext context, Exception ex) {
        log.error("TRANSFER FALLBACK: Wallet service unavailable for transaction: {} - {}",
            context.getTransactionId(), ex.getMessage());

        meterRegistry.counter("transaction.transfer.fallback").increment();

        // CRITICAL: Cannot proceed without successful transfer
        return CompletableFuture.completedFuture(SagaStepResult.builder()
            .stepName(STEP_NAME)
            .status(SagaStepStatus.FAILED)
            .errorMessage("Wallet service unavailable - transfer cannot complete")
            .data("fallbackActivated", true)
            .timestamp(LocalDateTime.now())
            .build());
    }

    @Override
    public CompletableFuture<SagaStepResult> compensate(TransactionSagaContext context, SagaStepResult originalResult) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String transferReference = context.getTransferReference();

                if (transferReference == null || transferReference.isEmpty()) {
                    log.warn("TRANSFER COMPENSATION: No transfer reference found for transaction: {} - skipping",
                        context.getTransactionId());

                    return SagaStepResult.builder()
                        .stepName(STEP_NAME + "_COMPENSATION")
                        .status(SagaStepStatus.SUCCESS)
                        .message("No transfer to reverse")
                        .timestamp(LocalDateTime.now())
                        .build();
                }

                log.info("TRANSFER COMPENSATION: Reversing transfer {} for transaction: {}",
                    transferReference, context.getTransactionId());

                // Build reversal request (swap source and destination)
                WalletServiceClient.TransferRequest reversalRequest = WalletServiceClient.TransferRequest.builder()
                    .fromWalletId(UUID.fromString(context.getDestinationWalletId())) // Reversed
                    .toWalletId(UUID.fromString(context.getSourceWalletId())) // Reversed
                    .amount(context.getAmount())
                    .currency(context.getCurrency())
                    .transactionId(context.getTransactionId() + "-REVERSAL")
                    .description("REVERSAL: " + context.getDescription())
                    .metadata(java.util.Map.of(
                        "originalTransferId", transferReference,
                        "reversalReason", "Saga compensation"))
                    .build();

                // Execute reversal transfer
                ServiceResponse<WalletServiceClient.TransferResultDTO> response =
                    walletClient.transferFunds(reversalRequest).get();

                if (response.isSuccess()) {
                    log.info("TRANSFER COMPENSATION SUCCESS: Reversed transfer {} for transaction: {}",
                        transferReference, context.getTransactionId());

                    meterRegistry.counter("transaction.transfer.compensations.success").increment();

                    return SagaStepResult.builder()
                        .stepName(STEP_NAME + "_COMPENSATION")
                        .status(SagaStepStatus.SUCCESS)
                        .message("Transfer reversed successfully")
                        .data("originalTransferReference", transferReference)
                        .data("reversalReference", response.getData().getTransferReference())
                        .timestamp(LocalDateTime.now())
                        .build();
                } else {
                    log.error("TRANSFER COMPENSATION FAILED: Error reversing transfer {} - {}",
                        transferReference, response.getErrorMessage());

                    meterRegistry.counter("transaction.transfer.compensations.failed").increment();

                    // CRITICAL: Compensation failure requires manual intervention
                    return SagaStepResult.builder()
                        .stepName(STEP_NAME + "_COMPENSATION")
                        .status(SagaStepStatus.FAILED)
                        .errorMessage("Transfer reversal failed - manual intervention required: " +
                            response.getErrorMessage())
                        .data("originalTransferReference", transferReference)
                        .data("requiresManualIntervention", true)
                        .timestamp(LocalDateTime.now())
                        .build();
                }

            } catch (Exception e) {
                log.error("TRANSFER COMPENSATION ERROR: Error during compensation for transaction: {}",
                    context.getTransactionId(), e);

                meterRegistry.counter("transaction.transfer.compensations.error").increment();

                // CRITICAL: Compensation error requires manual intervention
                return SagaStepResult.builder()
                    .stepName(STEP_NAME + "_COMPENSATION")
                    .status(SagaStepStatus.FAILED)
                    .errorMessage("Compensation error - manual intervention required: " + e.getMessage())
                    .data("requiresManualIntervention", true)
                    .timestamp(LocalDateTime.now())
                    .build();
            }
        });
    }
}
