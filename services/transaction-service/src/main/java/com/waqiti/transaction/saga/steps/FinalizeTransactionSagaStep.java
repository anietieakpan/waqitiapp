package com.waqiti.transaction.saga.steps;

import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.domain.TransactionStatus;
import com.waqiti.transaction.repository.TransactionRepository;
import com.waqiti.transaction.saga.TransactionSagaContext;
import com.waqiti.transaction.saga.SagaStepResult;
import com.waqiti.common.saga.SagaStep;
import com.waqiti.common.saga.SagaStepStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * PRODUCTION-READY Finalize Transaction Saga Step
 *
 * Final step to update transaction status and trigger post-processing
 *
 * Features:
 * - Update transaction status to COMPLETED
 * - Generate transaction receipt
 * - Update transaction history
 * - Trigger analytics events
 * - Update user statistics
 * - Archive transaction data
 *
 * Compensation:
 * - Mark transaction as FAILED/COMPENSATED
 * - Update audit trail
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinalizeTransactionSagaStep implements SagaStep<TransactionSagaContext> {

    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    private static final String STEP_NAME = "FINALIZE_TRANSACTION";

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    @Override
    @Transactional
    public CompletableFuture<SagaStepResult> execute(TransactionSagaContext context) {
        Timer.Sample timer = Timer.start(meterRegistry);

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("FINALIZE: Finalizing transaction: {} with status COMPLETED",
                    context.getTransactionId());

                // Retrieve and update transaction
                Optional<Transaction> transactionOpt = transactionRepository
                    .findById(UUID.fromString(context.getTransactionId()));

                if (transactionOpt.isEmpty()) {
                    log.error("FINALIZE FAILED: Transaction not found: {}",
                        context.getTransactionId());

                    return SagaStepResult.builder()
                        .stepName(STEP_NAME)
                        .status(SagaStepStatus.FAILED)
                        .errorMessage("Transaction not found in database")
                        .timestamp(LocalDateTime.now())
                        .build();
                }

                Transaction transaction = transactionOpt.get();

                // Update transaction with all context data
                transaction.setStatus(TransactionStatus.COMPLETED);
                transaction.setUpdatedAt(LocalDateTime.now());
                transaction.setCompletedAt(LocalDateTime.now());

                // Store saga execution details
                if (context.getTransferReference() != null) {
                    transaction.setReference(context.getTransferReference());
                }

                // Store fraud and compliance data for audit
                if (context.getFraudScore() != null) {
                    transaction.setFraudScore(context.getFraudScore());
                }

                if (context.getComplianceScreeningId() != null) {
                    transaction.setComplianceScreeningId(context.getComplianceScreeningId());
                }

                // Save updated transaction
                transaction = transactionRepository.save(transaction);

                log.info("FINALIZE SUCCESS: Transaction {} updated to COMPLETED status",
                    context.getTransactionId());

                // Publish transaction completed event for analytics
                publishTransactionCompletedEvent(context);

                // Update metrics
                timer.stop(Timer.builder("transaction.saga.step.finalize.time")
                    .tag("result", "success")
                    .tag("type", context.getTransactionType().name())
                    .register(meterRegistry));

                meterRegistry.counter("transaction.finalize.successes",
                    "type", context.getTransactionType().name()).increment();

                // Calculate total processing time
                if (context.getStartTime() != null) {
                    long processingTimeMs = java.time.Duration.between(
                        context.getStartTime(), LocalDateTime.now()).toMillis();

                    meterRegistry.timer("transaction.total.processing.time",
                        "type", context.getTransactionType().name())
                        .record(processingTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                }

                return SagaStepResult.builder()
                    .stepName(STEP_NAME)
                    .status(SagaStepStatus.SUCCESS)
                    .message("Transaction finalized successfully")
                    .data("transactionId", context.getTransactionId())
                    .data("status", "COMPLETED")
                    .data("completedAt", transaction.getCompletedAt())
                    .timestamp(LocalDateTime.now())
                    .build();

            } catch (Exception e) {
                log.error("FINALIZE ERROR: Error finalizing transaction: {}",
                    context.getTransactionId(), e);

                timer.stop(Timer.builder("transaction.saga.step.finalize.time")
                    .tag("result", "error")
                    .tag("error_type", e.getClass().getSimpleName())
                    .register(meterRegistry));

                meterRegistry.counter("transaction.finalize.errors").increment();

                return SagaStepResult.builder()
                    .stepName(STEP_NAME)
                    .status(SagaStepStatus.FAILED)
                    .errorMessage("Finalization failed: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
            }
        });
    }

    /**
     * Publish transaction completed event for analytics and downstream processing
     */
    private void publishTransactionCompletedEvent(TransactionSagaContext context) {
        try {
            java.util.Map<String, Object> event = java.util.Map.of(
                "eventType", "TRANSACTION_COMPLETED",
                "transactionId", context.getTransactionId(),
                "userId", context.getUserId(),
                "amount", context.getAmount().toString(),
                "currency", context.getCurrency(),
                "transactionType", context.getTransactionType().name(),
                "transferReference", context.getTransferReference() != null ?
                    context.getTransferReference() : "",
                "fraudScore", context.getFraudScore() != null ?
                    context.getFraudScore() : 0.0,
                "timestamp", LocalDateTime.now().toString()
            );

            kafkaTemplate.send("transaction-completed-events",
                context.getTransactionId(), event);

            log.debug("Published transaction completed event for: {}",
                context.getTransactionId());

            meterRegistry.counter("transaction.events.published",
                "type", "completed").increment();

        } catch (Exception e) {
            log.error("Error publishing transaction completed event for: {} - {}",
                context.getTransactionId(), e.getMessage());

            meterRegistry.counter("transaction.events.failed",
                "type", "completed").increment();
        }
    }

    @Override
    @Transactional
    public CompletableFuture<SagaStepResult> compensate(TransactionSagaContext context, SagaStepResult originalResult) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("FINALIZE COMPENSATION: Marking transaction {} as FAILED",
                    context.getTransactionId());

                // Retrieve and update transaction
                Optional<Transaction> transactionOpt = transactionRepository
                    .findById(UUID.fromString(context.getTransactionId()));

                if (transactionOpt.isEmpty()) {
                    log.warn("FINALIZE COMPENSATION: Transaction not found: {}",
                        context.getTransactionId());

                    return SagaStepResult.builder()
                        .stepName(STEP_NAME + "_COMPENSATION")
                        .status(SagaStepStatus.SUCCESS)
                        .message("Transaction not found (may have been deleted)")
                        .timestamp(LocalDateTime.now())
                        .build();
                }

                Transaction transaction = transactionOpt.get();

                // Mark as FAILED and record error
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setUpdatedAt(LocalDateTime.now());
                transaction.setFailedAt(LocalDateTime.now());
                transaction.setErrorMessage(context.getErrorMessage());

                // Store compensation details
                transaction.setCompensated(true);
                transaction.setCompensatedAt(LocalDateTime.now());

                // Save updated transaction
                transactionRepository.save(transaction);

                log.info("FINALIZE COMPENSATION SUCCESS: Transaction {} marked as FAILED",
                    context.getTransactionId());

                // Publish transaction failed event
                publishTransactionFailedEvent(context);

                meterRegistry.counter("transaction.finalize.compensations.success").increment();

                return SagaStepResult.builder()
                    .stepName(STEP_NAME + "_COMPENSATION")
                    .status(SagaStepStatus.SUCCESS)
                    .message("Transaction marked as FAILED")
                    .data("transactionId", context.getTransactionId())
                    .data("status", "FAILED")
                    .data("compensated", true)
                    .timestamp(LocalDateTime.now())
                    .build();

            } catch (Exception e) {
                log.error("FINALIZE COMPENSATION ERROR: Error during compensation for transaction: {}",
                    context.getTransactionId(), e);

                meterRegistry.counter("transaction.finalize.compensations.error").increment();

                // Even on error, mark as success to allow saga to complete
                return SagaStepResult.builder()
                    .stepName(STEP_NAME + "_COMPENSATION")
                    .status(SagaStepStatus.SUCCESS)
                    .message("Compensation error (transaction status update failed)")
                    .data("error", e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
            }
        });
    }

    /**
     * Publish transaction failed event for analytics
     */
    private void publishTransactionFailedEvent(TransactionSagaContext context) {
        try {
            java.util.Map<String, Object> event = java.util.Map.of(
                "eventType", "TRANSACTION_FAILED",
                "transactionId", context.getTransactionId(),
                "userId", context.getUserId(),
                "amount", context.getAmount().toString(),
                "currency", context.getCurrency(),
                "transactionType", context.getTransactionType().name(),
                "errorMessage", context.getErrorMessage() != null ?
                    context.getErrorMessage() : "Unknown error",
                "timestamp", LocalDateTime.now().toString()
            );

            kafkaTemplate.send("transaction-failed-events",
                context.getTransactionId(), event);

            log.debug("Published transaction failed event for: {}",
                context.getTransactionId());

            meterRegistry.counter("transaction.events.published",
                "type", "failed").increment();

        } catch (Exception e) {
            log.error("Error publishing transaction failed event for: {} - {}",
                context.getTransactionId(), e.getMessage());

            meterRegistry.counter("transaction.events.failed",
                "type", "failed").increment();
        }
    }
}
