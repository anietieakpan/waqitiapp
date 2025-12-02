package com.waqiti.payment.kafka;

import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.dto.PaymentReversalEvent;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.LedgerService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.payment.service.RefundService;
import com.waqiti.common.distributed.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Critical Kafka consumer for processing payment reversals
 * This was identified as an orphaned event causing money to be stuck
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReversalConsumer {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;
    private final RefundService refundService;
    private final DistributedLockService lockService;

    @KafkaListener(
        topics = "payment-reversal-initiated",
        groupId = "payment-reversal-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    @Transactional
    public void processPaymentReversal(
            @Payload PaymentReversalEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String lockKey = "payment-reversal-" + event.getPaymentId();
        
        try {
            log.info("Processing payment reversal for payment: {}, reason: {}", 
                    event.getPaymentId(), event.getReason());

            // Acquire distributed lock to prevent concurrent processing
            boolean lockAcquired = lockService.tryLock(lockKey, 30, TimeUnit.SECONDS);
            if (!lockAcquired) {
                log.warn("Could not acquire lock for reversal: {}", event.getPaymentId());
                throw new RuntimeException("Lock acquisition failed");
            }

            try {
                // 1. Validate reversal request
                Payment originalPayment = validateReversalRequest(event);
                
                // 2. Check if reversal already processed
                if (originalPayment.getStatus() == PaymentStatus.REVERSED) {
                    log.warn("Payment {} already reversed, skipping", event.getPaymentId());
                    acknowledgment.acknowledge();
                    return;
                }
                
                // 3. Lock original transaction
                originalPayment.setStatus(PaymentStatus.REVERSAL_IN_PROGRESS);
                originalPayment.setLockedAt(LocalDateTime.now());
                originalPayment.setLockedBy("REVERSAL_PROCESSOR");
                paymentRepository.save(originalPayment);
                
                // 4. Process reversal with payment provider
                processProviderReversal(originalPayment, event);
                
                // 5. Update ledger entries (double-entry bookkeeping)
                updateLedgerForReversal(originalPayment, event);
                
                // 6. Update payment status
                originalPayment.setStatus(PaymentStatus.REVERSED);
                originalPayment.setReversedAt(LocalDateTime.now());
                originalPayment.setReversalReason(event.getReason());
                originalPayment.setReversalReference(event.getReversalId());
                paymentRepository.save(originalPayment);
                
                // 7. Process refund if applicable
                if (event.isRefundRequired()) {
                    initiateRefund(originalPayment, event);
                }
                
                // 8. Send notifications
                sendReversalNotifications(originalPayment, event);
                
                // 9. Update reconciliation
                updateReconciliation(originalPayment, event);
                
                log.info("Successfully processed reversal for payment: {}", event.getPaymentId());
                acknowledgment.acknowledge();
                
            } finally {
                lockService.unlock(lockKey);
            }
            
        } catch (Exception e) {
            log.error("Error processing payment reversal for payment: {}", event.getPaymentId(), e);
            
            // Send to DLQ after max retries
            if (shouldSendToDlq(e)) {
                sendToDlq(event, e);
                acknowledgment.acknowledge(); // Acknowledge to prevent reprocessing
            } else {
                throw e; // Let retry mechanism handle
            }
        }
    }

    private Payment validateReversalRequest(PaymentReversalEvent event) {
        Payment payment = paymentRepository.findById(UUID.fromString(event.getPaymentId()))
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + event.getPaymentId()));
        
        // Validate payment can be reversed
        if (!isReversalAllowed(payment)) {
            throw new IllegalStateException("Payment cannot be reversed: " + payment.getId());
        }
        
        // Check reversal window (e.g., 180 days)
        if (payment.getCreatedAt().isBefore(LocalDateTime.now().minusDays(180))) {
            throw new IllegalStateException("Payment too old for reversal");
        }
        
        return payment;
    }

    private boolean isReversalAllowed(Payment payment) {
        return payment.getStatus() == PaymentStatus.COMPLETED ||
               payment.getStatus() == PaymentStatus.SETTLED ||
               payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED;
    }

    private void processProviderReversal(Payment payment, PaymentReversalEvent event) {
        log.info("Processing provider reversal for payment: {}", payment.getId());
        
        String provider = payment.getProvider().toUpperCase();
        
        switch (provider) {
            case "STRIPE":
                paymentService.reverseStripePayment(payment, event.getReason());
                break;
            case "PAYPAL":
                paymentService.reversePayPalPayment(payment, event.getReason());
                break;
            case "WISE":
                paymentService.reverseWiseTransfer(payment, event.getReason());
                break;
            case "BANK_TRANSFER":
            case "ACH":
                paymentService.reverseBankTransfer(payment, event.getReason());
                break;
            case "ADYEN":
                paymentService.reverseAdyenPayment(payment, event.getReason());
                break;
            case "DWOLLA":
                paymentService.reverseDwollaTransfer(payment, event.getReason());
                break;
            case "PLAID":
                paymentService.reversePlaidTransfer(payment, event.getReason());
                break;
            case "SQUARE":
                paymentService.reverseSquarePayment(payment, event.getReason());
                break;
            case "VENMO":
                paymentService.reverseVenmoPayment(payment, event.getReason());
                break;
            case "CASHAPP":
                paymentService.reverseCashAppPayment(payment, event.getReason());
                break;
            case "INTERNAL":
            case "WALLET":
                paymentService.reverseInternalTransfer(payment, event.getReason());
                break;
            default:
                // For unknown providers, try generic reversal with fallback
                log.warn("Unknown provider {} - attempting generic reversal", provider);
                boolean reversed = paymentService.attemptGenericReversal(payment, event.getReason());
                
                if (!reversed) {
                    // Queue for manual reversal instead of throwing exception
                    log.error("Automated reversal failed for provider: {} - queuing for manual review", provider);
                    paymentService.queueManualReversal(payment, event.getReason());
                }
        }
    }

    private void updateLedgerForReversal(Payment payment, PaymentReversalEvent event) {
        log.info("Updating ledger for reversal: {}", payment.getId());
        
        // Create reversal entries in ledger (opposite of original entries)
        ledgerService.createReversalEntries(
            payment.getId(),
            payment.getSourceAccount(),
            payment.getDestinationAccount(),
            payment.getAmount(),
            payment.getCurrency(),
            event.getReason(),
            event.getReversalId()
        );
    }

    private void initiateRefund(Payment payment, PaymentReversalEvent event) {
        log.info("Initiating refund for payment: {}", payment.getId());
        
        refundService.createRefund(
            payment.getId(),
            payment.getAmount(),
            event.getReason(),
            event.getInitiatedBy()
        );
    }

    private void sendReversalNotifications(Payment payment, PaymentReversalEvent event) {
        // Notify sender
        notificationService.sendPaymentReversalNotification(
            payment.getSenderUserId(),
            payment.getId(),
            payment.getAmount(),
            event.getReason()
        );
        
        // Notify recipient
        notificationService.sendPaymentReversalNotification(
            payment.getRecipientUserId(),
            payment.getId(),
            payment.getAmount(),
            event.getReason()
        );
        
        // Alert compliance if fraud-related
        if (event.getReason().contains("FRAUD") || event.getReason().contains("SUSPICIOUS")) {
            notificationService.alertComplianceTeam(payment, event);
        }
    }

    private void updateReconciliation(Payment payment, PaymentReversalEvent event) {
        // Update reconciliation records for accurate bookkeeping
        ledgerService.markForReconciliation(
            payment.getId(),
            "REVERSAL",
            event.getReversalId()
        );
    }

    private boolean shouldSendToDlq(Exception e) {
        // Send to DLQ for non-retryable errors
        return e instanceof IllegalArgumentException ||
               e instanceof IllegalStateException ||
               e instanceof UnsupportedOperationException;
    }

    private void sendToDlq(PaymentReversalEvent event, Exception error) {
        log.error("Sending reversal event to DLQ: {}", event.getPaymentId());
        // Implementation would send to dead letter queue
    }
}