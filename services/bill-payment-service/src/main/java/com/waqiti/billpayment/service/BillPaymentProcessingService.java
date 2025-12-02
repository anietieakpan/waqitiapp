package com.waqiti.billpayment.service;

import com.waqiti.billpayment.entity.*;
import com.waqiti.billpayment.repository.*;
import com.waqiti.billpayment.client.WalletServiceClient;
import com.waqiti.billpayment.client.NotificationServiceClient;
import com.waqiti.billpayment.client.dto.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for processing bill payments with transaction safety
 * Implements Saga pattern for distributed transactions
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BillPaymentProcessingService {

    private final BillPaymentRepository paymentRepository;
    private final BillRepository billRepository;
    private final BillPaymentAuditLogRepository auditLogRepository;
    private final BillService billService;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    // External service clients
    private final WalletServiceClient walletServiceClient;
    private final NotificationServiceClient notificationClient;
    private final AlertingService alertingService;

    private Counter paymentInitiatedCounter;
    private Counter paymentCompletedCounter;
    private Counter paymentFailedCounter;
    private Timer paymentProcessingTimer;

    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        paymentInitiatedCounter = Counter.builder("bill.payment.initiated")
                .description("Number of payments initiated")
                .register(meterRegistry);

        paymentCompletedCounter = Counter.builder("bill.payment.completed")
                .description("Number of payments completed")
                .register(meterRegistry);

        paymentFailedCounter = Counter.builder("bill.payment.failed")
                .description("Number of payments failed")
                .register(meterRegistry);

        paymentProcessingTimer = Timer.builder("bill.payment.processing.time")
                .description("Time taken to process payments")
                .register(meterRegistry);
    }

    /**
     * Initiate bill payment with idempotency support
     * Implements Saga pattern for transaction safety
     * Uses REPEATABLE_READ isolation to prevent phantom reads during payment initiation
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public BillPayment initiatePayment(String userId, UUID billId, BigDecimal amount,
                                       PaymentMethod paymentMethod, String idempotencyKey) {
        Timer.Sample sample = Timer.start(meterRegistry);

        log.info("Initiating payment for bill: {}, user: {}, amount: {}, idempotency: {}",
                billId, userId, amount, idempotencyKey);

        // Check idempotency
        if (idempotencyKey != null) {
            BillPayment existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existingPayment != null) {
                log.warn("Duplicate payment request detected for idempotency key: {}", idempotencyKey);
                return existingPayment;
            }
        }

        // Validate bill
        Bill bill = billService.getBillById(billId, userId);
        validatePaymentAmount(bill, amount);

        // Create payment entity
        BillPayment payment = BillPayment.builder()
                .userId(userId)
                .billId(billId)
                .amount(amount)
                .currency(bill.getCurrency())
                .status(BillPaymentStatus.PENDING)
                .paymentMethod(paymentMethod)
                .idempotencyKey(idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString())
                .retryCount(0)
                .maxRetries(3)
                .build();

        BillPayment savedPayment = paymentRepository.save(payment);

        // Audit log
        auditLog(savedPayment, "PAYMENT_INITIATED", userId);

        paymentInitiatedCounter.increment();

        // Process payment asynchronously (Saga orchestration)
        processPaymentSaga(savedPayment, bill);

        sample.stop(paymentProcessingTimer);

        log.info("Payment initiated: {}", savedPayment.getId());
        return savedPayment;
    }

    /**
     * Process payment using Saga pattern
     * Steps: 1) Debit wallet 2) Submit to biller 3) Update bill 4) Send notification
     * Each step has compensation logic for rollback
     * Uses REPEATABLE_READ to ensure consistent view of payment state throughout saga
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ,
                   rollbackFor = Exception.class)
    public void processPaymentSaga(BillPayment payment, Bill bill) {
        log.info("Processing payment saga: {}", payment.getId());

        try {
            // Step 1: Mark as processing
            payment.markAsProcessing();
            paymentRepository.save(payment);

            // Step 2: Debit wallet (with compensation support)
            UUID walletTransactionId = debitWallet(payment);
            payment.setWalletTransactionId(walletTransactionId);
            paymentRepository.save(payment);

            // Step 3: Submit payment to biller (with compensation support)
            String billerConfirmation = submitToBiller(payment, bill);
            payment.setBillerConfirmationNumber(billerConfirmation);

            // Step 4: Mark payment as completed
            payment.markAsCompleted(billerConfirmation);
            paymentRepository.save(payment);

            // Step 5: Update bill status
            billService.markAsPaid(bill.getId(), payment.getUserId(), payment.getAmount(), payment.getId());

            // Step 6: Send notification
            sendPaymentNotification(payment, bill, "SUCCESS");

            // Audit log
            auditLog(payment, "PAYMENT_COMPLETED", payment.getUserId());

            paymentCompletedCounter.increment();

            log.info("Payment saga completed successfully: {}", payment.getId());

        } catch (Exception e) {
            log.error("Payment saga failed: {}", payment.getId(), e);

            // Compensation: Rollback steps
            compensatePaymentSaga(payment, e.getMessage());

            paymentFailedCounter.increment();
        }
    }

    /**
     * Compensate (rollback) payment saga on failure
     * CRITICAL: This must succeed to refund customer money
     * Uses SERIALIZABLE isolation for maximum data integrity
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE,
                   propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
                   rollbackFor = Exception.class)
    public void compensatePaymentSaga(BillPayment payment, String failureReason) {
        log.warn("Compensating payment saga: {}, reason: {}", payment.getId(), failureReason);

        try {
            // If wallet was debited, credit it back
            if (payment.getWalletTransactionId() != null) {
                creditWallet(payment);
            }

            // Mark payment as failed
            payment.markAsFailed(failureReason);
            paymentRepository.save(payment);

            // Send failure notification
            Bill bill = billRepository.findById(payment.getBillId()).orElse(null);
            if (bill != null) {
                sendPaymentNotification(payment, bill, "FAILED");
            }

            // Audit log
            auditLog(payment, "PAYMENT_FAILED_COMPENSATED", payment.getUserId());

            log.info("Payment saga compensated: {}", payment.getId());

        } catch (Exception e) {
            log.error("CRITICAL: Failed to compensate payment saga: {}", payment.getId(), e);
            // Alert operations team for manual intervention
        }
    }

    /**
     * Retry failed payment
     */
    @Transactional
    public void retryFailedPayment(UUID paymentId) {
        log.info("Retrying failed payment: {}", paymentId);

        BillPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (!payment.isRetryable()) {
            log.warn("Payment not retryable: {}, retries: {}/{}", paymentId,
                    payment.getRetryCount(), payment.getMaxRetries());
            return;
        }

        Bill bill = billRepository.findById(payment.getBillId()).orElse(null);
        if (bill == null) {
            log.error("Bill not found for payment: {}", paymentId);
            return;
        }

        processPaymentSaga(payment, bill);
    }

    /**
     * Process scheduled payments (called by scheduler)
     */
    @Transactional
    public void processScheduledPayments() {
        log.info("Processing scheduled payments");

        List<BillPayment> scheduledPayments = paymentRepository
                .findScheduledPaymentsDueNow(LocalDateTime.now());

        for (BillPayment payment : scheduledPayments) {
            try {
                Bill bill = billRepository.findById(payment.getBillId()).orElse(null);
                if (bill != null) {
                    processPaymentSaga(payment, bill);
                }
            } catch (Exception e) {
                log.error("Error processing scheduled payment: {}", payment.getId(), e);
            }
        }

        log.info("Processed {} scheduled payments", scheduledPayments.size());
    }

    /**
     * Process failed payment retries (called by scheduler)
     */
    @Transactional
    public void processFailedPaymentRetries() {
        log.info("Processing failed payment retries");

        List<BillPayment> failedPayments = paymentRepository
                .findFailedPaymentsForRetry(LocalDateTime.now());

        for (BillPayment payment : failedPayments) {
            try {
                retryFailedPayment(payment.getId());
            } catch (Exception e) {
                log.error("Error retrying payment: {}", payment.getId(), e);
            }
        }

        log.info("Processed {} failed payment retries", failedPayments.size());
    }

    /**
     * Get payment by ID
     */
    @Transactional(readOnly = true)
    public BillPayment getPaymentById(UUID paymentId, String userId) {
        return paymentRepository.findById(paymentId)
                .filter(p -> p.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
    }

    /**
     * Get payments for user
     */
    @Transactional(readOnly = true)
    public List<BillPayment> getPaymentsByUser(String userId) {
        return paymentRepository.findByUserId(userId, org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    /**
     * Get payments for bill
     */
    @Transactional(readOnly = true)
    public List<BillPayment> getPaymentsByBill(UUID billId) {
        return paymentRepository.findByBillId(billId);
    }

    /**
     * Cancel pending payment
     */
    @Transactional
    public void cancelPayment(UUID paymentId, String userId) {
        log.info("Cancelling payment: {}", paymentId);

        BillPayment payment = getPaymentById(paymentId, userId);

        if (payment.getStatus() != BillPaymentStatus.PENDING &&
            payment.getStatus() != BillPaymentStatus.SCHEDULED) {
            throw new IllegalStateException("Cannot cancel payment in status: " + payment.getStatus());
        }

        payment.setStatus(BillPaymentStatus.CANCELLED);
        paymentRepository.save(payment);

        // Audit log
        auditLog(payment, "PAYMENT_CANCELLED", userId);

        log.info("Payment cancelled: {}", paymentId);
    }

    // Private helper methods (placeholders for external service calls)

    private UUID debitWallet(BillPayment payment) {
        log.info("Debiting wallet for payment: {}, amount: {}", payment.getId(), payment.getAmount());

        try {
            WalletDebitRequest request = WalletDebitRequest.builder()
                    .userId(payment.getUserId())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .description("Bill payment: " + payment.getId())
                    .referenceId(payment.getId().toString())
                    .referenceType("BILL_PAYMENT")
                    .idempotencyKey(payment.getIdempotencyKey() + "-debit")
                    .build();

            WalletDebitResponse response = walletServiceClient.debit(request);

            if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
                String errorMsg = response != null ? response.getErrorMessage() : "Null response from wallet service";
                throw new RuntimeException("Wallet debit failed: " + errorMsg);
            }

            log.info("Wallet debited successfully: transactionId={}, newBalance={}",
                    response.getTransactionId(), response.getNewBalance());

            return response.getTransactionId();

        } catch (Exception e) {
            log.error("Failed to debit wallet for payment: {}", payment.getId(), e);
            throw new RuntimeException("Wallet debit operation failed: " + e.getMessage(), e);
        }
    }

    private void creditWallet(BillPayment payment) {
        log.info("Crediting wallet (compensation) for payment: {}, amount: {}", payment.getId(), payment.getAmount());

        try {
            WalletCreditRequest request = WalletCreditRequest.builder()
                    .userId(payment.getUserId())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .description("Bill payment refund: " + payment.getId())
                    .referenceId(payment.getId().toString())
                    .originalTransactionId(payment.getWalletTransactionId())
                    .reason("Bill payment failed - automatic compensation")
                    .idempotencyKey(payment.getIdempotencyKey() + "-refund")
                    .build();

            WalletCreditResponse response = walletServiceClient.credit(request);

            if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
                String errorMsg = response != null ? response.getErrorMessage() : "Null response from wallet service";
                log.error("CRITICAL: Wallet compensation failed for payment: {}, error: {}",
                        payment.getId(), errorMsg);
                // Alert operations team - this is a critical financial integrity issue
                throw new RuntimeException("Wallet credit failed: " + errorMsg);
            }

            log.info("Wallet credited successfully (compensation): transactionId={}, newBalance={}",
                    response.getTransactionId(), response.getNewBalance());

        } catch (Exception e) {
            log.error("CRITICAL: Failed to credit wallet (compensation) for payment: {}", payment.getId(), e);

            // Alert operations team - this is a critical financial integrity issue
            alertingService.alertCompensationFailure(
                    payment.getId(),
                    payment.getUserId(),
                    "Wallet credit (refund) failed after debit: " + e.getMessage(),
                    e
            );

            // This is a critical error - money was debited but cannot be refunded
            // Manual intervention required
            throw new RuntimeException("Wallet credit operation failed: " + e.getMessage(), e);
        }
    }

    private String submitToBiller(BillPayment payment, Bill bill) {
        log.info("Submitting payment to biller: {}", bill.getBillerName());

        try {
            // For now, generate confirmation number
            // TODO: Integrate with actual biller APIs when available
            // Different billers will have different integration methods:
            // - Direct API integration
            // - Third-party bill payment aggregators (e.g., PayNearMe, Speedpay)
            // - ACH transfers
            // - Check printing services

            String confirmationNumber = "BP-" + System.currentTimeMillis() + "-" +
                    UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            log.info("Bill payment submitted to biller: {}, confirmation: {}",
                    bill.getBillerName(), confirmationNumber);

            // Simulate processing delay (remove in production with real API)
            Thread.sleep(100);

            return confirmationNumber;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Biller submission interrupted for payment: {}", payment.getId(), e);
            throw new RuntimeException("Biller submission interrupted", e);
        } catch (Exception e) {
            log.error("Failed to submit payment to biller for payment: {}", payment.getId(), e);
            throw new RuntimeException("Biller submission failed: " + e.getMessage(), e);
        }
    }

    private void sendPaymentNotification(BillPayment payment, Bill bill, String status) {
        log.info("Sending payment notification: {}, status: {}", payment.getId(), status);

        try {
            String message;
            String title;

            if ("SUCCESS".equals(status)) {
                title = "Bill Payment Successful";
                message = String.format(
                        "Your payment of %s %s to %s was successful. Confirmation: %s",
                        payment.getCurrency(),
                        payment.getAmount(),
                        bill.getBillerName(),
                        payment.getBillerConfirmationNumber()
                );
            } else {
                title = "Bill Payment Failed";
                message = String.format(
                        "Your payment of %s %s to %s failed. Reason: %s. Your wallet has been refunded.",
                        payment.getCurrency(),
                        payment.getAmount(),
                        bill.getBillerName(),
                        payment.getFailureReason()
                );
            }

            NotificationRequest request = NotificationRequest.builder()
                    .userId(payment.getUserId())
                    .channel("EMAIL") // Can be configured based on user preferences
                    .title(title)
                    .message(message)
                    .priority("HIGH")
                    .build();

            notificationClient.send(request);

            log.info("Payment notification sent successfully for payment: {}", payment.getId());

        } catch (Exception e) {
            // Don't fail payment if notification fails - just log
            log.error("Failed to send payment notification for payment: {}", payment.getId(), e);
        }
    }

    private void validatePaymentAmount(Bill bill, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }

        if (bill.getMinimumAmountDue() != null && amount.compareTo(bill.getMinimumAmountDue()) < 0) {
            throw new IllegalArgumentException("Payment amount below minimum: " + bill.getMinimumAmountDue());
        }

        BigDecimal remaining = bill.getRemainingAmount();
        if (amount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Payment amount exceeds remaining balance: " + remaining);
        }
    }

    private void auditLog(BillPayment payment, String action, String userId) {
        try {
            BillPaymentAuditLog auditLog = BillPaymentAuditLog.builder()
                    .entityType("BILL_PAYMENT")
                    .entityId(payment.getId())
                    .action(action)
                    .userId(userId)
                    .timestamp(LocalDateTime.now())
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to create audit log for payment: {}", payment.getId(), e);

            // Alert on audit failure - compliance requirement
            alertingService.alertAuditFailure(
                    "BILL_PAYMENT",
                    payment.getId(),
                    action,
                    "Audit log creation failed: " + e.getMessage()
            );
        }
    }
}
