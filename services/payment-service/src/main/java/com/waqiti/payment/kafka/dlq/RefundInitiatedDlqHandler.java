package com.waqiti.payment.kafka.dlq;

import com.waqiti.common.kafka.dlq.*;
import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import com.waqiti.common.events.RefundInitiatedEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.Refund;
import com.waqiti.payment.domain.RefundStatus;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.RefundRepository;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.RefundService;
import com.waqiti.payment.service.WalletService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.payment.service.LedgerService;
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
 * Production-grade DLQ handler for RefundInitiated events.
 *
 * Recovery Strategies:
 * 1. TRANSIENT_ERROR: Retry refund processing
 * 2. PAYMENT_NOT_FOUND: Log error, manual review
 * 3. REFUND_ALREADY_PROCESSED: Discard duplicate
 * 4. INSUFFICIENT_MERCHANT_BALANCE: Alert operations team
 * 5. WALLET_NOT_FOUND: Retry (eventual consistency)
 * 6. LEDGER_ERROR: Critical alert, manual reconciliation
 * 7. REFUND_WINDOW_EXPIRED: Reject, notify user
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefundInitiatedDlqHandler implements DlqMessageHandler {

    private final PaymentService paymentService;
    private final RefundService refundService;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final WalletService walletService;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;
    private final AnalyticsService analyticsService;
    private final AlertingService alertingService;
    private final ObjectMapper objectMapper;

    @Override
    public DlqEventType getEventType() {
        return DlqEventType.REFUND_INITIATED;
    }

    @Override
    @Transactional
    public DlqProcessingResult reprocess(ConsumerRecord<String, String> record, DlqRecordEntity dlqRecord) {
        try {
            log.info("DLQ: Reprocessing REFUND_INITIATED - messageId={}, attemptCount={}",
                dlqRecord.getMessageId(), dlqRecord.getRetryCount());

            RefundInitiatedEvent event = objectMapper.readValue(record.value(), RefundInitiatedEvent.class);
            String refundId = event.getRefundId();
            String paymentId = event.getPaymentId();

            // Validate refund exists
            Optional<Refund> refundOpt = refundRepository.findById(refundId);
            if (refundOpt.isEmpty()) {
                log.error("DLQ: Refund not found: {}", refundId);
                return DlqProcessingResult.permanentFailure("Refund entity not found");
            }

            Refund refund = refundOpt.get();

            // Validate payment exists
            Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
            if (paymentOpt.isEmpty()) {
                log.error("DLQ: Payment not found for refund: paymentId={}, refundId={}", paymentId, refundId);
                return handlePaymentNotFound(refund, event);
            }

            Payment payment = paymentOpt.get();
            String failureReason = dlqRecord.getLastFailureReason();

            log.info("DLQ: Processing refund - refundId={}, paymentId={}, amount={}, reason={}",
                refundId, paymentId, refund.getAmount(), failureReason);

            // Route to appropriate recovery strategy
            if (isTransientFailure(failureReason)) {
                return handleTransientError(refund, payment, event);
            } else if (isRefundAlreadyProcessed(refund)) {
                return handleDuplicateRefund(refund, event);
            } else if (isInsufficientMerchantBalance(failureReason)) {
                return handleInsufficientMerchantBalance(refund, payment, event);
            } else if (isWalletNotFound(failureReason)) {
                return handleWalletNotFound(refund, payment, event, dlqRecord.getRetryCount());
            } else if (isLedgerError(failureReason)) {
                return handleLedgerError(refund, payment, event);
            } else if (isRefundWindowExpired(refund, payment)) {
                return handleRefundWindowExpired(refund, payment, event);
            } else if (isInvalidRefundAmount(refund, payment)) {
                return handleInvalidRefundAmount(refund, payment, event);
            } else {
                return handleUnknownError(refund, payment, event, failureReason);
            }

        } catch (Exception e) {
            log.error("DLQ: Critical error reprocessing REFUND_INITIATED", e);
            return DlqProcessingResult.retryLater("Exception during recovery: " + e.getMessage());
        }
    }

    private DlqProcessingResult handleTransientError(Refund refund, Payment payment, RefundInitiatedEvent event) {
        try {
            log.info("DLQ: Handling transient error for refund: {}", refund.getId());

            // Retry refund processing
            refundService.processRefund(refund.getId());

            // Send notification
            notificationService.sendRefundProcessingNotification(
                payment.getUserId(),
                refund.getId(),
                payment.getId(),
                refund.getAmount()
            );

            analyticsService.recordRefundRecovery(refund.getId(), "TRANSIENT_ERROR");

            return DlqProcessingResult.success("Refund processed after transient error recovery");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle transient error for refund", e);
            return DlqProcessingResult.retryLater("Failed to retry refund processing");
        }
    }

    private DlqProcessingResult handlePaymentNotFound(Refund refund, RefundInitiatedEvent event) {
        log.error("DLQ: Payment not found for refund - refundId={}, paymentId={}",
            refund.getId(), event.getPaymentId());

        // Mark refund as failed
        refund.setStatus(RefundStatus.FAILED);
        refund.setFailureReason("Payment not found");
        refundRepository.save(refund);

        // Alert operations team
        alertingService.sendCriticalAlert(
            "Refund initiated for non-existent payment",
            refund.getId(),
            event.getPaymentId()
        );

        return DlqProcessingResult.permanentFailure("Payment not found - refund cannot be processed");
    }

    private DlqProcessingResult handleDuplicateRefund(Refund refund, RefundInitiatedEvent event) {
        log.info("DLQ: Duplicate refund detected - refundId={}, status={}",
            refund.getId(), refund.getStatus());

        if (refund.getStatus() == RefundStatus.COMPLETED) {
            analyticsService.recordDuplicateRefundAttempt(refund.getId());
            return DlqProcessingResult.discarded("Refund already completed");
        }

        // If in progress, let it continue
        return DlqProcessingResult.success("Refund already in progress");
    }

    private DlqProcessingResult handleInsufficientMerchantBalance(
        Refund refund, Payment payment, RefundInitiatedEvent event) {

        try {
            log.warn("DLQ: Insufficient merchant balance for refund - refundId={}, merchantId={}",
                refund.getId(), payment.getMerchantId());

            // Alert operations team
            alertingService.sendOperationsAlert(
                "Merchant has insufficient balance for refund",
                payment.getMerchantId(),
                refund.getId(),
                refund.getAmount()
            );

            // Update refund to pending merchant funding
            refund.setStatus(RefundStatus.PENDING_MERCHANT_FUNDING);
            refundRepository.save(refund);

            // Notify user about delay
            notificationService.sendRefundDelayedNotification(
                payment.getUserId(),
                refund.getId(),
                "Your refund is being processed. It may take longer than usual."
            );

            return DlqProcessingResult.manualReview("Merchant balance insufficient - operations review required");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle insufficient merchant balance", e);
            return DlqProcessingResult.retryLater("Failed to process merchant balance issue");
        }
    }

    private DlqProcessingResult handleWalletNotFound(
        Refund refund, Payment payment, RefundInitiatedEvent event, int retryCount) {

        try {
            log.warn("DLQ: Wallet not found for refund - refundId={}, userId={}, retryCount={}",
                refund.getId(), payment.getUserId(), retryCount);

            // Retry up to 5 times (eventual consistency)
            if (retryCount < 5) {
                return DlqProcessingResult.retryLater("Wallet not found - retry for eventual consistency");
            }

            // After 5 retries, escalate
            alertingService.sendCriticalAlert(
                "Wallet not found after 5 retries for refund",
                payment.getUserId(),
                refund.getId()
            );

            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureReason("Wallet not found");
            refundRepository.save(refund);

            return DlqProcessingResult.manualReview("Wallet not found after retries - manual investigation required");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle wallet not found", e);
            return DlqProcessingResult.retryLater("Error processing wallet not found");
        }
    }

    private DlqProcessingResult handleLedgerError(Refund refund, Payment payment, RefundInitiatedEvent event) {
        try {
            log.error("DLQ: Ledger error for refund - refundId={}, paymentId={}",
                refund.getId(), payment.getId());

            // Critical alert - ledger inconsistency
            alertingService.sendCriticalAlert(
                "Ledger error during refund processing",
                refund.getId(),
                payment.getId(),
                refund.getAmount()
            );

            // Mark for manual reconciliation
            refund.setStatus(RefundStatus.RECONCILIATION_REQUIRED);
            refundRepository.save(refund);

            // Alert finance team
            alertingService.sendFinanceAlert(
                "Manual ledger reconciliation required for refund",
                refund.getId(),
                payment.getId()
            );

            return DlqProcessingResult.manualReview("Ledger error - manual reconciliation required");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle ledger error", e);
            return DlqProcessingResult.permanentFailure("Critical ledger error");
        }
    }

    private DlqProcessingResult handleRefundWindowExpired(Refund refund, Payment payment, RefundInitiatedEvent event) {
        try {
            log.warn("DLQ: Refund window expired - refundId={}, paymentDate={}",
                refund.getId(), payment.getCreatedAt());

            // Mark refund as rejected
            refund.setStatus(RefundStatus.REJECTED);
            refund.setFailureReason("Refund window expired");
            refundRepository.save(refund);

            // Notify user
            notificationService.sendRefundRejectedNotification(
                payment.getUserId(),
                refund.getId(),
                "Refund request was made outside the allowed refund window."
            );

            analyticsService.recordRefundRejection(refund.getId(), "WINDOW_EXPIRED");

            return DlqProcessingResult.success("Refund rejected - window expired");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle expired refund window", e);
            return DlqProcessingResult.retryLater("Error processing expired window");
        }
    }

    private DlqProcessingResult handleInvalidRefundAmount(Refund refund, Payment payment, RefundInitiatedEvent event) {
        try {
            log.error("DLQ: Invalid refund amount - refundAmount={}, paymentAmount={}",
                refund.getAmount(), payment.getAmount());

            refund.setStatus(RefundStatus.REJECTED);
            refund.setFailureReason("Refund amount exceeds payment amount");
            refundRepository.save(refund);

            // Alert fraud team (potential manipulation)
            alertingService.sendSecurityAlert(
                "Invalid refund amount detected",
                payment.getUserId(),
                refund.getId(),
                refund.getAmount()
            );

            notificationService.sendRefundRejectedNotification(
                payment.getUserId(),
                refund.getId(),
                "Refund request could not be processed. Please contact support."
            );

            return DlqProcessingResult.permanentFailure("Invalid refund amount - security review");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle invalid amount", e);
            return DlqProcessingResult.retryLater("Error processing invalid amount");
        }
    }

    private DlqProcessingResult handleUnknownError(
        Refund refund, Payment payment, RefundInitiatedEvent event, String reason) {

        try {
            log.error("DLQ: Unknown error for refund - refundId={}, reason={}", refund.getId(), reason);

            alertingService.sendAlert(
                "Unknown refund processing error in DLQ",
                refund.getId(),
                reason
            );

            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureReason("Unknown error: " + reason);
            refundRepository.save(refund);

            notificationService.sendGenericRefundFailureNotification(
                payment.getUserId(),
                refund.getId(),
                "Refund processing failed. Please contact support."
            );

            return DlqProcessingResult.manualReview("Unknown error - investigation required");

        } catch (Exception e) {
            log.error("DLQ: Failed to handle unknown error", e);
            return DlqProcessingResult.retryLater("Error handling unknown error");
        }
    }

    private boolean isTransientFailure(String reason) {
        return reason != null && (
            reason.contains("timeout") ||
            reason.contains("connection") ||
            reason.contains("database")
        );
    }

    private boolean isRefundAlreadyProcessed(Refund refund) {
        return refund.getStatus() == RefundStatus.COMPLETED ||
               refund.getStatus() == RefundStatus.PROCESSING;
    }

    private boolean isInsufficientMerchantBalance(String reason) {
        return reason != null && reason.toLowerCase().contains("insufficient merchant balance");
    }

    private boolean isWalletNotFound(String reason) {
        return reason != null && reason.toLowerCase().contains("wallet not found");
    }

    private boolean isLedgerError(String reason) {
        return reason != null && reason.toLowerCase().contains("ledger");
    }

    private boolean isRefundWindowExpired(Refund refund, Payment payment) {
        // 90-day refund window
        Instant refundWindowEnd = payment.getCreatedAt().plusSeconds(90 * 24 * 3600);
        return refund.getCreatedAt().isAfter(refundWindowEnd);
    }

    private boolean isInvalidRefundAmount(Refund refund, Payment payment) {
        return refund.getAmount().compareTo(payment.getAmount()) > 0;
    }
}
