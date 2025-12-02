package com.waqiti.transaction.saga.steps;

import com.waqiti.transaction.client.NotificationServiceClient;
import com.waqiti.transaction.dto.TransactionNotificationRequest;
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
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * PRODUCTION-READY Notification Saga Step
 *
 * Sends transaction notifications via multiple channels
 *
 * Features:
 * - Multi-channel notifications (email, SMS, push)
 * - Transaction receipts
 * - Success/failure notifications
 * - Real-time push notifications
 * - Notification preferences
 * - Circuit breaker for resilience
 *
 * Compensation:
 * - Sends compensation/failure notification to user
 * - Always succeeds (best-effort notification)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSagaStep implements SagaStep<TransactionSagaContext> {

    private final NotificationServiceClient notificationClient;
    private final MeterRegistry meterRegistry;

    private static final String STEP_NAME = "NOTIFICATION";

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    @Override
    @CircuitBreaker(name = "notificationService", fallbackMethod = "fallbackNotification")
    @Retry(name = "notificationService")
    public CompletableFuture<SagaStepResult> execute(TransactionSagaContext context) {
        Timer.Sample timer = Timer.start(meterRegistry);

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("NOTIFICATION: Sending transaction success notification for transaction: {}",
                    context.getTransactionId());

                // Build notification request
                TransactionNotificationRequest request = TransactionNotificationRequest.builder()
                    .userId(context.getUserId())
                    .transactionId(context.getTransactionId())
                    .transactionReference(context.getTransferReference())
                    .notificationType("TRANSACTION_SUCCESS")
                    .channels(Arrays.asList("EMAIL", "PUSH", "SMS")) // Multi-channel
                    .priority("HIGH")
                    .amount(context.getAmount())
                    .currency(context.getCurrency())
                    .sourceWalletId(context.getSourceWalletId())
                    .destinationWalletId(context.getDestinationWalletId())
                    .description(context.getDescription())
                    .timestamp(LocalDateTime.now())
                    // Include transaction details for receipt
                    .receiptData(buildReceiptData(context))
                    .build();

                // Send notification (best-effort - don't fail saga if notification fails)
                try {
                    notificationClient.sendTransactionNotification(request);

                    log.info("NOTIFICATION SUCCESS: Sent transaction notification for transaction: {}",
                        context.getTransactionId());

                    timer.stop(Timer.builder("transaction.saga.step.notification.time")
                        .tag("result", "success")
                        .tag("type", "transaction_success")
                        .register(meterRegistry));

                    meterRegistry.counter("transaction.notifications.sent",
                        "type", "success").increment();

                } catch (Exception notificationException) {
                    // Log but don't fail saga - notification is best-effort
                    log.warn("NOTIFICATION WARNING: Failed to send notification for transaction: {} - {}",
                        context.getTransactionId(), notificationException.getMessage());

                    meterRegistry.counter("transaction.notifications.failed",
                        "type", "success").increment();
                }

                // Always succeed - notification failure doesn't invalidate transaction
                return SagaStepResult.builder()
                    .stepName(STEP_NAME)
                    .status(SagaStepStatus.SUCCESS)
                    .message("Transaction notification sent (best-effort)")
                    .data("channels", Arrays.asList("EMAIL", "PUSH", "SMS"))
                    .timestamp(LocalDateTime.now())
                    .build();

            } catch (Exception e) {
                log.error("NOTIFICATION ERROR: Error during notification for transaction: {}",
                    context.getTransactionId(), e);

                timer.stop(Timer.builder("transaction.saga.step.notification.time")
                    .tag("result", "error")
                    .tag("error_type", e.getClass().getSimpleName())
                    .register(meterRegistry));

                meterRegistry.counter("transaction.notifications.errors").increment();

                // Don't fail saga - notification is non-critical
                return SagaStepResult.builder()
                    .stepName(STEP_NAME)
                    .status(SagaStepStatus.SUCCESS)
                    .message("Notification failed (non-critical)")
                    .data("error", e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
            }
        });
    }

    /**
     * Build receipt data for notification
     */
    private java.util.Map<String, Object> buildReceiptData(TransactionSagaContext context) {
        return java.util.Map.of(
            "transactionId", context.getTransactionId(),
            "transferReference", context.getTransferReference() != null ? context.getTransferReference() : "",
            "amount", context.getAmount().toString(),
            "currency", context.getCurrency(),
            "sourceBalanceAfter", context.getSourceBalanceAfter() != null ?
                context.getSourceBalanceAfter().toString() : "N/A",
            "destinationBalanceAfter", context.getDestinationBalanceAfter() != null ?
                context.getDestinationBalanceAfter().toString() : "N/A",
            "transactionType", context.getTransactionType().name(),
            "timestamp", LocalDateTime.now().toString(),
            "status", "COMPLETED"
        );
    }

    /**
     * Fallback when notification service is unavailable
     */
    private CompletableFuture<SagaStepResult> fallbackNotification(TransactionSagaContext context, Exception ex) {
        log.warn("NOTIFICATION FALLBACK: Notification service unavailable for transaction: {} - {}",
            context.getTransactionId(), ex.getMessage());

        meterRegistry.counter("transaction.notifications.fallback").increment();

        // Queue notification for later delivery
        context.setNotificationPending(true);

        // Don't fail saga - notification is non-critical
        return CompletableFuture.completedFuture(SagaStepResult.builder()
            .stepName(STEP_NAME)
            .status(SagaStepStatus.SUCCESS)
            .message("Notification queued for later delivery (service unavailable)")
            .data("fallbackActivated", true)
            .data("queued", true)
            .timestamp(LocalDateTime.now())
            .build());
    }

    @Override
    public CompletableFuture<SagaStepResult> compensate(TransactionSagaContext context, SagaStepResult originalResult) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("NOTIFICATION COMPENSATION: Sending transaction failure notification for transaction: {}",
                    context.getTransactionId());

                // Build failure notification request
                TransactionNotificationRequest request = TransactionNotificationRequest.builder()
                    .userId(context.getUserId())
                    .transactionId(context.getTransactionId())
                    .notificationType("TRANSACTION_FAILED")
                    .channels(Arrays.asList("EMAIL", "PUSH", "SMS"))
                    .priority("HIGH")
                    .amount(context.getAmount())
                    .currency(context.getCurrency())
                    .sourceWalletId(context.getSourceWalletId())
                    .destinationWalletId(context.getDestinationWalletId())
                    .description("FAILED: " + context.getDescription())
                    .timestamp(LocalDateTime.now())
                    .failureReason(context.getErrorMessage())
                    .build();

                // Send failure notification (best-effort)
                try {
                    notificationClient.sendTransactionNotification(request);

                    log.info("NOTIFICATION COMPENSATION SUCCESS: Sent failure notification for transaction: {}",
                        context.getTransactionId());

                    meterRegistry.counter("transaction.notifications.compensations.success").increment();

                } catch (Exception notificationException) {
                    log.warn("NOTIFICATION COMPENSATION WARNING: Failed to send failure notification - {}",
                        notificationException.getMessage());

                    meterRegistry.counter("transaction.notifications.compensations.failed").increment();
                }

                // Always succeed - notification is best-effort
                return SagaStepResult.builder()
                    .stepName(STEP_NAME + "_COMPENSATION")
                    .status(SagaStepStatus.SUCCESS)
                    .message("Failure notification sent (best-effort)")
                    .timestamp(LocalDateTime.now())
                    .build();

            } catch (Exception e) {
                log.error("NOTIFICATION COMPENSATION ERROR: Error sending failure notification for transaction: {}",
                    context.getTransactionId(), e);

                meterRegistry.counter("transaction.notifications.compensations.error").increment();

                // Still succeed - notification is non-critical
                return SagaStepResult.builder()
                    .stepName(STEP_NAME + "_COMPENSATION")
                    .status(SagaStepStatus.SUCCESS)
                    .message("Failure notification error (non-critical)")
                    .data("error", e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
            }
        });
    }
}
