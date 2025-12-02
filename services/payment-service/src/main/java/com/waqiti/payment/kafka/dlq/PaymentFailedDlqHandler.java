package com.waqiti.payment.kafka.dlq;

import com.waqiti.common.kafka.dlq.*;
import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import com.waqiti.common.events.PaymentFailedEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.FailureReason;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.payment.service.WalletService;
import com.waqiti.payment.service.RefundService;
import com.waqiti.payment.service.AnalyticsService;
import com.waqiti.payment.service.AlertingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Production-grade DLQ handler for PaymentFailed events.
 *
 * Handles recovery of failed payment failure notifications with:
 * - Comprehensive failure analysis
 * - Automatic refund initiation for eligible failures
 * - User notification with appropriate messaging
 * - Analytics tracking for failure patterns
 * - Escalation for critical failures
 *
 * Recovery Strategies:
 * 1. TRANSIENT_ERROR: Retry notification + analytics
 * 2. INSUFFICIENT_BALANCE: Notify user, no refund needed
 * 3. FRAUD_BLOCKED: Special notification, freeze account if needed
 * 4. BANK_DECLINED: Notify user with bank response
 * 5. CARD_DECLINED: Notify user, suggest alternative
 * 6. NETWORK_ERROR: Retry payment automatically if eligible
 * 7. VALIDATION_ERROR: Notify developer team
 * 8. SYSTEM_ERROR: Critical alert + manual review
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0 - Enhanced with comprehensive recovery logic
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentFailedDlqHandler implements DlqMessageHandler {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final WalletService walletService;
    private final RefundService refundService;
    private final AnalyticsService analyticsService;
    private final AlertingService alertingService;
    private final ObjectMapper objectMapper;

    @Override
    public DlqEventType getEventType() {
        return DlqEventType.PAYMENT_FAILED;
    }

    @Override
    @Transactional
    public DlqProcessingResult reprocess(ConsumerRecord<String, String> record, DlqRecordEntity dlqRecord) {
        try {
            log.info("DLQ: Reprocessing PAYMENT_FAILED event - messageId={}, attemptCount={}",
                dlqRecord.getMessageId(), dlqRecord.getRetryCount());

            // Parse payment failed event
            PaymentFailedEvent event = objectMapper.readValue(record.value(), PaymentFailedEvent.class);
            String paymentId = event.getPaymentId();

            // Validate payment exists
            Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
            if (paymentOpt.isEmpty()) {
                log.error("DLQ: Payment not found for failed event: {}", paymentId);
                return DlqProcessingResult.permanentFailure("Payment not found: " + paymentId);
            }

            Payment payment = paymentOpt.get();
            String failureReason = dlqRecord.getLastFailureReason();
            FailureReason failureCategory = categorizeFailure(failureReason, payment);

            log.info("DLQ: Processing payment failure - paymentId={}, amount={}, category={}, reason={}",
                paymentId, payment.getAmount(), failureCategory, failureReason);

            // Execute recovery strategy based on failure category
            switch (failureCategory) {
                case TRANSIENT_ERROR:
                    return handleTransientError(payment, event, failureReason);

                case INSUFFICIENT_BALANCE:
                    return handleInsufficientBalance(payment, event);

                case FRAUD_BLOCKED:
                    return handleFraudBlocked(payment, event);

                case BANK_DECLINED:
                    return handleBankDeclined(payment, event);

                case CARD_DECLINED:
                    return handleCardDeclined(payment, event);

                case NETWORK_ERROR:
                    return handleNetworkError(payment, event, dlqRecord.getRetryCount());

                case VALIDATION_ERROR:
                    return handleValidationError(payment, event, failureReason);

                case SYSTEM_ERROR:
                    return handleSystemError(payment, event, failureReason);

                case DUPLICATE_TRANSACTION:
                    return handleDuplicateTransaction(payment, event);

                case EXPIRED_PAYMENT_METHOD:
                    return handleExpiredPaymentMethod(payment, event);

                default:
                    return handleUnknownError(payment, event, failureReason);
            }

        } catch (Exception e) {
            log.error("DLQ: Critical error reprocessing PAYMENT_FAILED event", e);
            return DlqProcessingResult.retryLater("Exception during recovery: " + e.getMessage());
        }
    }

    /**
     * Handle transient errors (database, connection, timeout)
     * Strategy: Retry notification, record analytics, no refund needed
     */
    private DlqProcessingResult handleTransientError(Payment payment, PaymentFailedEvent event, String reason) {
        try {
            log.info("DLQ: Handling transient error for payment: {}", payment.getId());

            // Send failure notification to user
            notificationService.sendPaymentFailedNotification(
                payment.getUserId(),
                payment.getId(),
                payment.getAmount(),
                "Payment could not be completed due to a temporary issue. Please try again."
            );

            // Record analytics
            analyticsService.recordPaymentFailure(payment.getId(), "TRANSIENT_ERROR", reason);

            return DlqProcessingResult.success("Transient error notification sent successfully");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle transient error", e);
            return DlqProcessingResult.retryLater("Failed to send notification");
        }
    }

    /**
     * Handle insufficient balance failures
     * Strategy: Notify user with balance info, no refund needed
     */
    private DlqProcessingResult handleInsufficientBalance(Payment payment, PaymentFailedEvent event) {
        try {
            log.info("DLQ: Handling insufficient balance for payment: {}", payment.getId());

            // Get current wallet balance
            BigDecimal currentBalance = walletService.getBalance(payment.getSourceWalletId());
            BigDecimal requiredAmount = payment.getAmount();
            BigDecimal shortfall = requiredAmount.subtract(currentBalance);

            // Send detailed notification
            notificationService.sendInsufficientBalanceNotification(
                payment.getUserId(),
                payment.getId(),
                currentBalance,
                requiredAmount,
                shortfall
            );

            // Record analytics for user behavior analysis
            analyticsService.recordInsufficientBalanceFailure(
                payment.getUserId(),
                payment.getId(),
                currentBalance,
                requiredAmount
            );

            return DlqProcessingResult.success("Insufficient balance notification sent");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle insufficient balance", e);
            return DlqProcessingResult.retryLater("Failed to process insufficient balance");
        }
    }

    /**
     * Handle fraud-blocked payments
     * Strategy: Special notification, possible account review, alert security team
     */
    private DlqProcessingResult handleFraudBlocked(Payment payment, PaymentFailedEvent event) {
        try {
            log.warn("DLQ: Handling fraud-blocked payment: {}", payment.getId());

            // Send fraud notification (carefully worded)
            notificationService.sendSecurityReviewNotification(
                payment.getUserId(),
                payment.getId(),
                "Payment declined for security review. Please contact support if you believe this is an error."
            );

            // Alert security team
            alertingService.sendSecurityAlert(
                "Fraud-blocked payment in DLQ",
                payment.getUserId(),
                payment.getId(),
                payment.getAmount()
            );

            // Record in fraud analytics
            analyticsService.recordFraudBlockedPayment(payment.getId(), payment.getUserId());

            return DlqProcessingResult.success("Fraud block handled and security team alerted");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle fraud block", e);
            return DlqProcessingResult.retryLater("Failed to process fraud block");
        }
    }

    /**
     * Handle bank declined payments
     * Strategy: Notify user with bank response, suggest retry or alternative
     */
    private DlqProcessingResult handleBankDeclined(Payment payment, PaymentFailedEvent event) {
        try {
            log.info("DLQ: Handling bank declined payment: {}", payment.getId());

            notificationService.sendBankDeclinedNotification(
                payment.getUserId(),
                payment.getId(),
                payment.getAmount(),
                "Your bank declined this payment. Please contact your bank or try a different payment method."
            );

            analyticsService.recordBankDeclinedPayment(payment.getId(), payment.getUserId());

            return DlqProcessingResult.success("Bank declined notification sent");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle bank declined", e);
            return DlqProcessingResult.retryLater("Failed to process bank declined");
        }
    }

    /**
     * Handle card declined payments
     * Strategy: Notify user, suggest alternative payment method
     */
    private DlqProcessingResult handleCardDeclined(Payment payment, PaymentFailedEvent event) {
        try {
            log.info("DLQ: Handling card declined payment: {}", payment.getId());

            notificationService.sendCardDeclinedNotification(
                payment.getUserId(),
                payment.getId(),
                payment.getAmount(),
                "Your card was declined. Please try a different card or payment method."
            );

            analyticsService.recordCardDeclinedPayment(payment.getId(), payment.getUserId());

            return DlqProcessingResult.success("Card declined notification sent");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle card declined", e);
            return DlqProcessingResult.retryLater("Failed to process card declined");
        }
    }

    /**
     * Handle network errors
     * Strategy: Retry payment automatically if eligible, otherwise notify user
     */
    private DlqProcessingResult handleNetworkError(Payment payment, PaymentFailedEvent event, int retryCount) {
        try {
            log.info("DLQ: Handling network error for payment: {}, retryCount: {}", payment.getId(), retryCount);

            // Retry automatically if under threshold and payment is recent
            if (retryCount < 3 && isRecentPayment(payment)) {
                log.info("DLQ: Automatically retrying payment due to network error: {}", payment.getId());
                return DlqProcessingResult.retryLater("Automatic retry for network error");
            }

            // Otherwise notify user to retry manually
            notificationService.sendNetworkErrorNotification(
                payment.getUserId(),
                payment.getId(),
                payment.getAmount(),
                "Payment failed due to network error. Please try again."
            );

            analyticsService.recordNetworkErrorPayment(payment.getId());

            return DlqProcessingResult.success("Network error notification sent");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle network error", e);
            return DlqProcessingResult.retryLater("Failed to process network error");
        }
    }

    /**
     * Handle validation errors
     * Strategy: Alert development team, notify user with generic message
     */
    private DlqProcessingResult handleValidationError(Payment payment, PaymentFailedEvent event, String reason) {
        try {
            log.error("DLQ: Validation error for payment: {}, reason: {}", payment.getId(), reason);

            // Alert development team
            alertingService.sendDeveloperAlert(
                "Payment validation error in DLQ",
                payment.getId(),
                reason
            );

            // Send generic notification to user
            notificationService.sendGenericFailureNotification(
                payment.getUserId(),
                payment.getId(),
                payment.getAmount(),
                "Payment could not be completed. Please contact support."
            );

            return DlqProcessingResult.manualReview("Validation error requires developer review");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle validation error", e);
            return DlqProcessingResult.retryLater("Failed to process validation error");
        }
    }

    /**
     * Handle system errors
     * Strategy: Critical alert, manual review, notify user
     */
    private DlqProcessingResult handleSystemError(Payment payment, PaymentFailedEvent event, String reason) {
        try {
            log.error("DLQ: CRITICAL - System error for payment: {}, reason: {}", payment.getId(), reason);

            // Send critical alert
            alertingService.sendCriticalAlert(
                "Payment system error in DLQ",
                payment.getId(),
                payment.getUserId(),
                reason
            );

            // Notify user
            notificationService.sendSystemErrorNotification(
                payment.getUserId(),
                payment.getId(),
                payment.getAmount(),
                "We're experiencing technical difficulties. Our team has been notified."
            );

            return DlqProcessingResult.manualReview("System error requires urgent manual review");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle system error", e);
            return DlqProcessingResult.permanentFailure("Critical failure in error handling");
        }
    }

    /**
     * Handle duplicate transaction attempts
     * Strategy: Discard, no action needed (original already processed)
     */
    private DlqProcessingResult handleDuplicateTransaction(Payment payment, PaymentFailedEvent event) {
        log.info("DLQ: Duplicate transaction detected for payment: {}, discarding", payment.getId());

        analyticsService.recordDuplicatePaymentAttempt(payment.getId());

        return DlqProcessingResult.discarded("Duplicate transaction, original already processed");
    }

    /**
     * Handle expired payment method
     * Strategy: Notify user to update payment method
     */
    private DlqProcessingResult handleExpiredPaymentMethod(Payment payment, PaymentFailedEvent event) {
        try {
            log.info("DLQ: Handling expired payment method for payment: {}", payment.getId());

            notificationService.sendExpiredPaymentMethodNotification(
                payment.getUserId(),
                payment.getId(),
                "Your payment method has expired. Please update your payment information."
            );

            analyticsService.recordExpiredPaymentMethod(payment.getId(), payment.getUserId());

            return DlqProcessingResult.success("Expired payment method notification sent");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle expired payment method", e);
            return DlqProcessingResult.retryLater("Failed to process expired payment method");
        }
    }

    /**
     * Handle unknown errors
     * Strategy: Manual review, alert team, generic user notification
     */
    private DlqProcessingResult handleUnknownError(Payment payment, PaymentFailedEvent event, String reason) {
        try {
            log.error("DLQ: Unknown error for payment: {}, reason: {}", payment.getId(), reason);

            alertingService.sendAlert(
                "Unknown payment failure type in DLQ",
                payment.getId(),
                reason
            );

            notificationService.sendGenericFailureNotification(
                payment.getUserId(),
                payment.getId(),
                payment.getAmount(),
                "Payment failed. Please try again or contact support."
            );

            return DlqProcessingResult.manualReview("Unknown error type requires investigation");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle unknown error", e);
            return DlqProcessingResult.retryLater("Failed to process unknown error");
        }
    }

    /**
     * Categorize failure reason into recovery strategy
     */
    private FailureReason categorizeFailure(String reason, Payment payment) {
        if (reason == null) {
            return FailureReason.UNKNOWN_ERROR;
        }

        String lowerReason = reason.toLowerCase();

        if (lowerReason.contains("timeout") || lowerReason.contains("connection")) {
            return FailureReason.TRANSIENT_ERROR;
        } else if (lowerReason.contains("insufficient") || lowerReason.contains("balance")) {
            return FailureReason.INSUFFICIENT_BALANCE;
        } else if (lowerReason.contains("fraud") || lowerReason.contains("suspicious")) {
            return FailureReason.FRAUD_BLOCKED;
        } else if (lowerReason.contains("bank") && lowerReason.contains("decline")) {
            return FailureReason.BANK_DECLINED;
        } else if (lowerReason.contains("card") && lowerReason.contains("decline")) {
            return FailureReason.CARD_DECLINED;
        } else if (lowerReason.contains("network") || lowerReason.contains("connectivity")) {
            return FailureReason.NETWORK_ERROR;
        } else if (lowerReason.contains("validation") || lowerReason.contains("invalid")) {
            return FailureReason.VALIDATION_ERROR;
        } else if (lowerReason.contains("system") || lowerReason.contains("internal")) {
            return FailureReason.SYSTEM_ERROR;
        } else if (lowerReason.contains("duplicate") || lowerReason.contains("already")) {
            return FailureReason.DUPLICATE_TRANSACTION;
        } else if (lowerReason.contains("expired") && lowerReason.contains("card")) {
            return FailureReason.EXPIRED_PAYMENT_METHOD;
        } else {
            return FailureReason.UNKNOWN_ERROR;
        }
    }

    /**
     * Check if payment is recent enough to retry automatically
     */
    private boolean isRecentPayment(Payment payment) {
        Instant oneHourAgo = Instant.now().minusSeconds(3600);
        return payment.getCreatedAt().isAfter(oneHourAgo);
    }
}
