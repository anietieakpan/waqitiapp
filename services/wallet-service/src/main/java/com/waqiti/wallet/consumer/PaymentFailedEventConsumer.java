package com.waqiti.wallet.consumer;

import com.waqiti.common.events.PaymentFailedEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.service.FundReservationService;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.wallet.service.WalletCompensationService;
import com.waqiti.wallet.repository.ProcessedEventRepository;
import com.waqiti.wallet.model.ProcessedEvent;
import com.waqiti.wallet.model.CompensationRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.Instant;

/**
 * Consumer for PaymentFailedEvent - Critical for fund recovery
 * Handles payment failures by releasing holds and restoring balances
 * ZERO TOLERANCE: All failed payments must be properly compensated
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentFailedEventConsumer {
    
    private final FundReservationService fundReservationService;
    private final WalletService walletService;
    private final WalletCompensationService compensationService;
    private final ProcessedEventRepository processedEventRepository;
    private final WalletTransactionService transactionService;
    private final UniversalDLQHandler dlqHandler;
    
    @KafkaListener(
        topics = "payment.failed",
        groupId = "wallet-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for fund recovery
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.error("Processing payment failure for immediate fund recovery: {}", event.getPaymentId());
        
        // IDEMPOTENCY CHECK - Critical for preventing duplicate compensation
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Payment failure already processed for event: {}", event.getEventId());
            return;
        }
        
        try {
            // STEP 1: Release any fund holds immediately
            boolean holdsReleased = fundReservationService.releaseFundHolds(
                event.getPaymentId(),
                event.getFromAccount(),
                "Payment failed: " + event.getFailureReason()
            );
            
            if (holdsReleased) {
                log.info("Fund holds released for failed payment: {}", event.getPaymentId());
            }
            
            // STEP 2: Restore wallet balance if funds were debited
            if (event.getFundsDebited()) {
                restoreWalletBalance(event);
            }
            
            // STEP 3: Reverse any fees charged
            if (event.getFeesCharged() != null && event.getFeesCharged().compareTo(BigDecimal.ZERO) > 0) {
                reverseFees(event);
            }
            
            // STEP 4: Create comprehensive compensation record
            CompensationRecord compensationRecord = createCompensationRecord(event);
            compensationService.recordCompensation(compensationRecord);
            
            // STEP 5: Update wallet transaction history
            updateTransactionHistory(event);
            
            // STEP 6: Notify user of failure and compensation
            notifyUserOfFailureAndCompensation(event);
            
            // STEP 7: Alert operations team for high-value failures
            if (event.getAmount().compareTo(new BigDecimal("1000")) >= 0) {
                alertOperationsTeam(event);
            }
            
            // STEP 8: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType(\"PaymentFailedEvent\")
                .processedAt(Instant.now())
                .paymentId(event.getPaymentId())
                .compensationAmount(calculateTotalCompensation(event))
                .fundsRestored(event.getFundsDebited())
                .holdsReleased(holdsReleased)
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info(\"Successfully processed payment failure and compensated user: {}\", 
                event.getPaymentId());
                
        } catch (Exception e) {
            log.error(\"CRITICAL: Failed to process payment failure compensation for: {}\", 
                event.getPaymentId(), e);
                
            // Create high-priority manual intervention record
            createManualInterventionRecord(event, e);
            

            dlqHandler.handleFailedMessage(event, "payment.failed", 0, 0L, e)
                .thenAccept(result -> log.info("Payment failed event sent to DLQ: eventId={}, paymentId={}, destination={}, category={}",
                        event.getEventId(), event.getPaymentId(), result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for payment failed event - MESSAGE MAY BE LOST! " +
                            "eventId={}, paymentId={}, error={}",
                            event.getEventId(), event.getPaymentId(), dlqError.getMessage(), dlqError);
                    return null;
                });
            throw new RuntimeException(\"Payment failure compensation failed\", e);
        }
    }
    
    private void restoreWalletBalance(PaymentFailedEvent event) {
        log.info(\"Restoring wallet balance for failed payment: {} amount: {}\", 
            event.getPaymentId(), event.getAmount());
        
        try {
            // Credit back the debited amount
            WalletTransaction creditTransaction = WalletTransaction.builder()
                .walletId(getWalletIdFromAccount(event.getFromAccount()))
                .transactionType(\"PAYMENT_REVERSAL\")
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .description(\"Refund for failed payment: \" + event.getPaymentId())
                .referenceId(event.getPaymentId())
                .reversalReason(event.getFailureReason())
                .originalTransactionId(event.getOriginalTransactionId())
                .timestamp(Instant.now())
                .build();
                
            walletService.creditWallet(creditTransaction);
            
            log.info(\"Successfully restored {} {} to wallet for failed payment: {}\", 
                event.getAmount(), event.getCurrency(), event.getPaymentId());
                
        } catch (Exception e) {
            log.error(\"CRITICAL: Failed to restore wallet balance for payment: {}\", 
                event.getPaymentId(), e);
                
            // Escalate to manual processing
            manualProcessingService.createUrgentTask(
                \"WALLET_BALANCE_RESTORATION_FAILED\",
                String.format(\"Failed to restore %s %s for payment %s. Immediate manual intervention required.\", 
                    event.getAmount(), event.getCurrency(), event.getPaymentId()),
                event
            );
            
            throw e;
        }
    }
    
    private void reverseFees(PaymentFailedEvent event) {
        log.info(\"Reversing fees for failed payment: {} amount: {}\", 
            event.getPaymentId(), event.getFeesCharged());
        
        try {
            WalletTransaction feeReversalTransaction = WalletTransaction.builder()
                .walletId(getWalletIdFromAccount(event.getFromAccount()))
                .transactionType(\"FEE_REVERSAL\")
                .amount(event.getFeesCharged())
                .currency(event.getCurrency())
                .description(\"Fee refund for failed payment: \" + event.getPaymentId())
                .referenceId(event.getPaymentId())
                .reversalReason(\"Payment failed: \" + event.getFailureReason())
                .timestamp(Instant.now())
                .build();
                
            walletService.creditWallet(feeReversalTransaction);
            
            log.info(\"Successfully reversed fees: {} {} for failed payment: {}\", 
                event.getFeesCharged(), event.getCurrency(), event.getPaymentId());
                
        } catch (Exception e) {
            log.error(\"Failed to reverse fees for payment: {}\", event.getPaymentId(), e);
            throw e;
        }
    }
    
    private CompensationRecord createCompensationRecord(PaymentFailedEvent event) {
        return CompensationRecord.builder()
            .paymentId(event.getPaymentId())
            .eventId(event.getEventId())
            .userId(event.getSenderUserId())
            .walletId(getWalletIdFromAccount(event.getFromAccount()))
            .originalAmount(event.getAmount())
            .feesCharged(event.getFeesCharged())
            .totalCompensation(calculateTotalCompensation(event))
            .currency(event.getCurrency())
            .failureReason(event.getFailureReason())
            .compensationType(\"PAYMENT_FAILURE\")
            .status(\"COMPLETED\")
            .processedAt(Instant.now())
            .build();
    }
    
    private void updateTransactionHistory(PaymentFailedEvent event) {
        // Update original transaction status
        transactionService.updateTransactionStatus(
            event.getOriginalTransactionId(),
            \"FAILED\",
            event.getFailureReason()
        );
        
        // Create reversal transaction entries
        if (event.getFundsDebited()) {
            transactionService.createReversalTransaction(
                event.getPaymentId(),
                event.getFromAccount(),
                event.getAmount(),
                \"PAYMENT_REVERSAL\",
                \"Failed payment fund recovery\"
            );
        }
        
        if (event.getFeesCharged() != null && event.getFeesCharged().compareTo(BigDecimal.ZERO) > 0) {
            transactionService.createReversalTransaction(
                event.getPaymentId(),
                event.getFromAccount(),
                event.getFeesCharged(),
                \"FEE_REVERSAL\",
                \"Failed payment fee refund\"
            );
        }
    }
    
    private void notifyUserOfFailureAndCompensation(PaymentFailedEvent event) {
        BigDecimal totalRefund = calculateTotalCompensation(event);
        
        String message = String.format(
            \"Your payment of %s %s failed and has been refunded. Total refund: %s %s. Reason: %s\",
            event.getAmount(), event.getCurrency(),
            totalRefund, event.getCurrency(),
            event.getFailureReason()
        );
        
        notificationService.sendPaymentFailureNotification(
            event.getSenderUserId(),
            event.getPaymentId(),
            \"Payment Failed - Funds Refunded\",
            message,
            totalRefund
        );
    }
    
    private void alertOperationsTeam(PaymentFailedEvent event) {
        alertService.sendHighValuePaymentFailureAlert(
            \"High-Value Payment Failure\",
            String.format(
                \"Payment failure for amount %s %s. Payment ID: %s. Reason: %s. User compensation completed.\",
                event.getAmount(), event.getCurrency(),
                event.getPaymentId(), event.getFailureReason()
            ),
            event
        );
    }
    
    private BigDecimal calculateTotalCompensation(PaymentFailedEvent event) {
        BigDecimal total = BigDecimal.ZERO;
        
        if (event.getFundsDebited()) {
            total = total.add(event.getAmount());
        }
        
        if (event.getFeesCharged() != null) {
            total = total.add(event.getFeesCharged());
        }
        
        return total;
    }
    
    private UUID getWalletIdFromAccount(String accountNumber) {
        // Implementation to get wallet ID from account number
        return walletService.getWalletIdByAccountNumber(accountNumber);
    }
    
    private void createManualInterventionRecord(PaymentFailedEvent event, Exception exception) {
        manualInterventionService.createCriticalTask(
            \"PAYMENT_FAILURE_COMPENSATION_FAILED\",
            String.format(
                \"CRITICAL: Failed to compensate user for payment failure. \" +
                \"Payment ID: %s, Amount: %s %s, User: %s. Exception: %s. \" +
                \"IMMEDIATE MANUAL INTERVENTION REQUIRED.\",
                event.getPaymentId(),
                event.getAmount(),
                event.getCurrency(),
                event.getSenderUserId(),
                exception.getMessage()
            ),
            \"CRITICAL\",
            event,
            exception
        );
    }
}