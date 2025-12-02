package com.waqiti.payment.kafka.dlq;

import com.waqiti.common.kafka.dlq.*;
import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import com.waqiti.common.events.PaymentCompletedEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Production-grade DLQ handler for PaymentCompleted events.
 *
 * Handles post-payment completion activities:
 * - Receipt generation
 * - Settlement batch updates
 * - Rewards/cashback processing
 * - Analytics recording
 * - User notifications
 *
 * Recovery Strategies:
 * 1. TRANSIENT_ERROR: Retry all post-completion activities
 * 2. RECEIPT_GENERATION_FAILED: Regenerate receipt
 * 3. REWARDS_FAILED: Retry rewards calculation
 * 4. NOTIFICATION_FAILED: Retry notification
 * 5. SETTLEMENT_FAILED: Alert operations, manual settlement
 * 6. DUPLICATE: Discard (already processed)
 * 7. PAYMENT_NOT_FOUND: Critical alert (data inconsistency)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCompletedDlqHandler implements DlqMessageHandler {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final ReceiptService receiptService;
    private final SettlementService settlementService;
    private final RewardsService rewardsService;
    private final NotificationService notificationService;
    private final AnalyticsService analyticsService;
    private final AlertingService alertingService;
    private final ObjectMapper objectMapper;

    @Override
    public DlqEventType getEventType() {
        return DlqEventType.PAYMENT_COMPLETED;
    }

    @Override
    @Transactional
    public DlqProcessingResult reprocess(ConsumerRecord<String, String> record, DlqRecordEntity dlqRecord) {
        try {
            log.info("DLQ: Reprocessing PAYMENT_COMPLETED - messageId={}, attemptCount={}",
                dlqRecord.getMessageId(), dlqRecord.getRetryCount());

            PaymentCompletedEvent event = objectMapper.readValue(record.value(), PaymentCompletedEvent.class);
            String paymentId = event.getPaymentId();

            // Validate payment exists and is completed
            Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
            if (paymentOpt.isEmpty()) {
                log.error("DLQ: Payment not found for completed event: {}", paymentId);
                return handlePaymentNotFound(event);
            }

            Payment payment = paymentOpt.get();
            String failureReason = dlqRecord.getLastFailureReason();

            // Verify payment is actually completed
            if (payment.getStatus() != PaymentStatus.COMPLETED) {
                log.warn("DLQ: Payment not in completed status: paymentId={}, status={}",
                    paymentId, payment.getStatus());
                return handleIncorrectStatus(payment, event);
            }

            log.info("DLQ: Processing payment completion activities - paymentId={}, amount={}, reason={}",
                paymentId, payment.getAmount(), failureReason);

            // Route to recovery strategy
            if (isTransientFailure(failureReason)) {
                return handleTransientError(payment, event);
            } else if (isReceiptFailed(failureReason)) {
                return handleReceiptFailure(payment, event);
            } else if (isRewardsFailed(failureReason)) {
                return handleRewardsFailure(payment, event);
            } else if (isNotificationFailed(failureReason)) {
                return handleNotificationFailure(payment, event);
            } else if (isSettlementFailed(failureReason)) {
                return handleSettlementFailure(payment, event);
            } else if (isDuplicate(failureReason)) {
                return handleDuplicate(payment, event);
            } else {
                return handleUnknownError(payment, event, failureReason);
            }

        } catch (Exception e) {
            log.error("DLQ: Critical error reprocessing PAYMENT_COMPLETED", e);
            return DlqProcessingResult.retryLater("Exception during recovery: " + e.getMessage());
        }
    }

    private DlqProcessingResult handleTransientError(Payment payment, PaymentCompletedEvent event) {
        try {
            log.info("DLQ: Handling transient error for payment completion: {}", payment.getId());

            // Retry all post-completion activities
            processPostCompletion(payment, event);

            return DlqProcessingResult.success("Post-completion activities processed after transient error");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle transient error", e);
            return DlqProcessingResult.retryLater("Failed to retry post-completion");
        }
    }

    private DlqProcessingResult handleReceiptFailure(Payment payment, PaymentCompletedEvent event) {
        try {
            log.info("DLQ: Regenerating receipt for payment: {}", payment.getId());

            // Regenerate receipt
            receiptService.generateReceipt(payment.getId());

            // Send receipt to user
            notificationService.sendReceiptNotification(
                payment.getUserId(),
                payment.getId()
            );

            analyticsService.recordReceiptRegeneration(payment.getId());

            return DlqProcessingResult.success("Receipt regenerated successfully");

        } catch (Exception e) {
            log.error("DLQ: Failed to regenerate receipt", e);
            return DlqProcessingResult.retryLater("Failed to regenerate receipt");
        }
    }

    private DlqProcessingResult handleRewardsFailure(Payment payment, PaymentCompletedEvent event) {
        try {
            log.info("DLQ: Retrying rewards processing for payment: {}", payment.getId());

            // Retry rewards calculation and crediting
            rewardsService.processPaymentRewards(payment.getId(), payment.getAmount());

            analyticsService.recordRewardsRecovery(payment.getId());

            return DlqProcessingResult.success("Rewards processed successfully");

        } catch (Exception e) {
            log.error("DLQ: Failed to process rewards", e);

            // Alert if rewards consistently failing
            if (dlqRecord.getRetryCount() > 3) {
                alertingService.sendAlert(
                    "Rewards processing failing for payment",
                    payment.getId(),
                    e.getMessage()
                );
            }

            return DlqProcessingResult.retryLater("Failed to process rewards");
        }
    }

    private DlqProcessingResult handleNotificationFailure(Payment payment, PaymentCompletedEvent event) {
        try {
            log.info("DLQ: Retrying payment completion notification: {}", payment.getId());

            // Retry sending completion notification
            notificationService.sendPaymentCompletedNotification(
                payment.getUserId(),
                payment.getId(),
                payment.getAmount(),
                payment.getDescription()
            );

            analyticsService.recordNotificationRecovery(payment.getId());

            return DlqProcessingResult.success("Notification sent successfully");

        } catch (Exception e) {
            log.error("DLQ: Failed to send notification", e);
            // Don't block for notification failures
            return DlqProcessingResult.success("Notification failed but not critical");
        }
    }

    private DlqProcessingResult handleSettlementFailure(Payment payment, PaymentCompletedEvent event) {
        try {
            log.warn("DLQ: Settlement failed for payment: {}", payment.getId());

            // Alert operations team
            alertingService.sendOperationsAlert(
                "Payment settlement failed - manual review required",
                payment.getId(),
                payment.getMerchantId(),
                payment.getAmount()
            );

            // Mark for manual settlement
            settlementService.markForManualSettlement(payment.getId());

            return DlqProcessingResult.manualReview("Settlement requires manual review");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle settlement failure", e);
            return DlqProcessingResult.retryLater("Error handling settlement failure");
        }
    }

    private DlqProcessingResult handleDuplicate(Payment payment, PaymentCompletedEvent event) {
        log.info("DLQ: Duplicate payment completion event detected: {}", payment.getId());

        analyticsService.recordDuplicateCompletionEvent(payment.getId());

        return DlqProcessingResult.discarded("Duplicate event - already processed");
    }

    private DlqProcessingResult handlePaymentNotFound(PaymentCompletedEvent event) {
        log.error("DLQ: CRITICAL - Payment not found for completion event: {}", event.getPaymentId());

        // Critical alert - data inconsistency
        alertingService.sendCriticalAlert(
            "Payment completed event for non-existent payment",
            event.getPaymentId(),
            event.getUserId()
        );

        return DlqProcessingResult.permanentFailure("Payment not found - data inconsistency");
    }

    private DlqProcessingResult handleIncorrectStatus(Payment payment, PaymentCompletedEvent event) {
        log.warn("DLQ: Payment not in completed status: paymentId={}, status={}",
            payment.getId(), payment.getStatus());

        if (payment.getStatus() == PaymentStatus.PENDING ||
            payment.getStatus() == PaymentStatus.PROCESSING) {
            // Payment might still be processing
            return DlqProcessingResult.retryLater("Payment still processing");
        }

        // Alert about status mismatch
        alertingService.sendAlert(
            "Payment completion event for non-completed payment",
            payment.getId(),
            "Status: " + payment.getStatus()
        );

        return DlqProcessingResult.permanentFailure("Payment status mismatch");
    }

    private DlqProcessingResult handleUnknownError(Payment payment, PaymentCompletedEvent event, String reason) {
        try {
            log.error("DLQ: Unknown error for payment completion: paymentId={}, reason={}",
                payment.getId(), reason);

            alertingService.sendAlert(
                "Unknown payment completion error in DLQ",
                payment.getId(),
                reason
            );

            // Try to process anyway
            processPostCompletion(payment, event);

            return DlqProcessingResult.success("Processed despite unknown error");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle unknown error", e);
            return DlqProcessingResult.retryLater("Unknown error recovery failed");
        }
    }

    /**
     * Process all post-completion activities
     */
    private void processPostCompletion(Payment payment, PaymentCompletedEvent event) {
        try {
            // Generate receipt
            receiptService.generateReceipt(payment.getId());

            // Add to settlement batch
            settlementService.addToSettlementBatch(payment.getId(), payment.getMerchantId());

            // Process rewards
            rewardsService.processPaymentRewards(payment.getId(), payment.getAmount());

            // Record analytics
            analyticsService.recordPaymentCompletion(payment.getId(), payment.getAmount());

            // Send notification
            notificationService.sendPaymentCompletedNotification(
                payment.getUserId(),
                payment.getId(),
                payment.getAmount(),
                payment.getDescription()
            );

            log.info("DLQ: Successfully completed all post-completion activities for payment: {}",
                payment.getId());

        } catch (Exception e) {
            log.error("DLQ: Error in post-completion processing", e);
            throw e;
        }
    }

    private boolean isTransientFailure(String reason) {
        return reason != null && (
            reason.toLowerCase().contains("timeout") ||
            reason.toLowerCase().contains("connection") ||
            reason.toLowerCase().contains("database")
        );
    }

    private boolean isReceiptFailed(String reason) {
        return reason != null && reason.toLowerCase().contains("receipt");
    }

    private boolean isRewardsFailed(String reason) {
        return reason != null && reason.toLowerCase().contains("reward");
    }

    private boolean isNotificationFailed(String reason) {
        return reason != null && reason.toLowerCase().contains("notification");
    }

    private boolean isSettlementFailed(String reason) {
        return reason != null && reason.toLowerCase().contains("settlement");
    }

    private boolean isDuplicate(String reason) {
        return reason != null && (
            reason.toLowerCase().contains("duplicate") ||
            reason.toLowerCase().contains("already processed")
        );
    }
}
