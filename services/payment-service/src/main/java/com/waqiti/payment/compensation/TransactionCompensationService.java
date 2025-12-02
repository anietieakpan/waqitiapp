package com.waqiti.payment.compensation;

import com.waqiti.payment.domain.*;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.entity.*;
import com.waqiti.payment.repository.*;
import com.waqiti.payment.service.*;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive transaction compensation service implementing the Saga pattern
 * for distributed transaction management. Handles rollbacks, compensations,
 * and recovery for failed financial operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionCompensationService {

    private final PaymentRepository paymentRepository;
    private final CompensationTransactionRepository compensationRepository;
    private final WalletService walletService;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    private static final String COMPENSATION_TOPIC = "compensation-events";
    private static final int MAX_COMPENSATION_ATTEMPTS = 3;
    private static final long COMPENSATION_TIMEOUT_MINUTES = 30;

    /**
     * Initiate compensation for a failed payment transaction.
     * This is the main entry point for transaction rollbacks.
     */
    @Transactional
    public CompletableFuture<CompensationResult> compensateFailedPayment(
            String paymentId, CompensationReason reason, String initiatedBy) {
        
        log.info("Starting compensation for failed payment: {}, reason: {}", paymentId, reason);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Load payment and validate
                Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
                
                validateCompensationEligibility(payment);
                
                // Create compensation record
                CompensationTransaction compensation = createCompensationRecord(payment, reason, initiatedBy);
                
                // Execute compensation steps
                CompensationResult result = executeCompensationSteps(compensation, payment);
                
                // Update compensation status
                compensation.setStatus(result.isSuccessful() ? 
                    CompensationStatus.COMPLETED : CompensationStatus.FAILED);
                compensation.setCompletedAt(LocalDateTime.now());
                compensation.setResult(result);
                compensationRepository.save(compensation);
                
                // Publish compensation event
                publishCompensationEvent(compensation, payment, result);
                
                // Update metrics
                updateCompensationMetrics(result);
                
                log.info("Compensation completed for payment: {}, success: {}", 
                    paymentId, result.isSuccessful());
                
                return result;
                
            } catch (Exception e) {
                log.error("Compensation failed for payment: {}", paymentId, e);
                meterRegistry.counter("compensation.failed").increment();
                
                return CompensationResult.builder()
                    .successful(false)
                    .failureReason("Compensation execution failed: " + e.getMessage())
                    .completedAt(LocalDateTime.now())
                    .build();
            }
        });
    }

    /**
     * Execute all compensation steps in the correct order.
     */
    private CompensationResult executeCompensationSteps(CompensationTransaction compensation, Payment payment) {
        log.info("Executing compensation steps for transaction: {}", compensation.getId());
        
        CompensationResult.Builder resultBuilder = CompensationResult.builder()
            .compensationId(compensation.getId())
            .paymentId(payment.getPaymentId())
            .startedAt(LocalDateTime.now());
        
        List<CompensationStep> steps = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            // Step 1: Reverse wallet operations
            CompensationStep walletStep = reverseWalletOperations(payment);
            steps.add(walletStep);
            if (!walletStep.isSuccessful()) {
                errors.add("Wallet reversal failed: " + walletStep.getErrorMessage());
            }
            
            // Step 2: Reverse ledger entries
            CompensationStep ledgerStep = reverseLedgerEntries(payment);
            steps.add(ledgerStep);
            if (!ledgerStep.isSuccessful()) {
                errors.add("Ledger reversal failed: " + ledgerStep.getErrorMessage());
            }
            
            // Step 3: Reverse external provider transactions
            CompensationStep providerStep = reverseProviderTransactions(payment);
            steps.add(providerStep);
            if (!providerStep.isSuccessful()) {
                errors.add("Provider reversal failed: " + providerStep.getErrorMessage());
            }
            
            // Step 4: Release reserved funds
            CompensationStep reservationStep = releaseReservedFunds(payment);
            steps.add(reservationStep);
            if (!reservationStep.isSuccessful()) {
                errors.add("Reservation release failed: " + reservationStep.getErrorMessage());
            }
            
            // Step 5: Update payment status
            CompensationStep statusStep = updatePaymentStatus(payment);
            steps.add(statusStep);
            if (!statusStep.isSuccessful()) {
                errors.add("Status update failed: " + statusStep.getErrorMessage());
            }
            
            // Step 6: Send notifications
            CompensationStep notificationStep = sendCompensationNotifications(payment);
            steps.add(notificationStep);
            // Don't fail compensation if notifications fail
            
            boolean overallSuccess = errors.isEmpty();
            
            return resultBuilder
                .successful(overallSuccess)
                .steps(steps)
                .failureReasons(errors)
                .completedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Critical error during compensation execution", e);
            return resultBuilder
                .successful(false)
                .failureReason("Critical compensation error: " + e.getMessage())
                .steps(steps)
                .completedAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Reverse wallet operations (credits/debits).
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    private CompensationStep reverseWalletOperations(Payment payment) {
        log.debug("Reversing wallet operations for payment: {}", payment.getPaymentId());
        
        try {
            CompensationStep.Builder stepBuilder = CompensationStep.builder()
                .stepName("WALLET_REVERSAL")
                .startedAt(LocalDateTime.now());
            
            // Reverse sender debit (credit back)
            if (payment.getSenderWalletId() != null) {
                walletService.compensationCredit(
                    payment.getSenderWalletId(),
                    payment.getAmount(),
                    "Compensation for failed payment: " + payment.getPaymentId(),
                    "SYSTEM_COMPENSATION"
                );
            }
            
            // Reverse recipient credit (debit back)
            if (payment.getRecipientWalletId() != null && 
                payment.getStatus() != PaymentStatus.FAILED_BEFORE_RECIPIENT) {
                
                walletService.compensationDebit(
                    payment.getRecipientWalletId(),
                    payment.getAmount(),
                    "Compensation for failed payment: " + payment.getPaymentId(),
                    "SYSTEM_COMPENSATION"
                );
            }
            
            return stepBuilder
                .successful(true)
                .completedAt(LocalDateTime.now())
                .result("Wallet operations reversed successfully")
                .build();
                
        } catch (Exception e) {
            log.error("Failed to reverse wallet operations", e);
            return CompensationStep.builder()
                .stepName("WALLET_REVERSAL")
                .successful(false)
                .errorMessage(e.getMessage())
                .completedAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Reverse ledger entries using compensating transactions.
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    private CompensationStep reverseLedgerEntries(Payment payment) {
        log.debug("Reversing ledger entries for payment: {}", payment.getPaymentId());
        
        try {
            // Create compensating ledger entries
            ledgerService.createCompensatingEntries(
                payment.getPaymentId(),
                payment.getAmount(),
                "Compensation for failed payment",
                payment.getSenderAccountId(),
                payment.getRecipientAccountId()
            );
            
            return CompensationStep.builder()
                .stepName("LEDGER_REVERSAL")
                .successful(true)
                .completedAt(LocalDateTime.now())
                .result("Ledger entries reversed with compensating transactions")
                .build();
                
        } catch (Exception e) {
            log.error("Failed to reverse ledger entries", e);
            return CompensationStep.builder()
                .stepName("LEDGER_REVERSAL")
                .successful(false)
                .errorMessage(e.getMessage())
                .completedAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Reverse external provider transactions.
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    private CompensationStep reverseProviderTransactions(Payment payment) {
        log.debug("Reversing provider transactions for payment: {}", payment.getPaymentId());
        
        try {
            if (payment.getExternalTransactionId() == null) {
                return CompensationStep.builder()
                    .stepName("PROVIDER_REVERSAL")
                    .successful(true)
                    .result("No external transaction to reverse")
                    .completedAt(LocalDateTime.now())
                    .build();
            }
            
            // Call provider's reversal API
            ProviderReversalResult reversalResult = callProviderReversal(payment);
            
            return CompensationStep.builder()
                .stepName("PROVIDER_REVERSAL")
                .successful(reversalResult.isSuccessful())
                .errorMessage(reversalResult.isSuccessful() ? null : reversalResult.getErrorMessage())
                .result(reversalResult.getResult())
                .completedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to reverse provider transaction", e);
            return CompensationStep.builder()
                .stepName("PROVIDER_REVERSAL")
                .successful(false)
                .errorMessage(e.getMessage())
                .completedAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Release any reserved funds.
     */
    private CompensationStep releaseReservedFunds(Payment payment) {
        log.debug("Releasing reserved funds for payment: {}", payment.getPaymentId());
        
        try {
            if (payment.getReservationId() != null) {
                walletService.releaseReservation(
                    payment.getReservationId(),
                    "Failed payment compensation"
                );
            }
            
            return CompensationStep.builder()
                .stepName("RESERVATION_RELEASE")
                .successful(true)
                .result("Reserved funds released")
                .completedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to release reserved funds", e);
            return CompensationStep.builder()
                .stepName("RESERVATION_RELEASE")
                .successful(false)
                .errorMessage(e.getMessage())
                .completedAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Update payment status to compensated.
     */
    private CompensationStep updatePaymentStatus(Payment payment) {
        try {
            payment.setStatus(PaymentStatus.COMPENSATED);
            payment.setCompensatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            
            return CompensationStep.builder()
                .stepName("STATUS_UPDATE")
                .successful(true)
                .result("Payment status updated to COMPENSATED")
                .completedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to update payment status", e);
            return CompensationStep.builder()
                .stepName("STATUS_UPDATE")
                .successful(false)
                .errorMessage(e.getMessage())
                .completedAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Send compensation notifications.
     */
    private CompensationStep sendCompensationNotifications(Payment payment) {
        try {
            // Notify sender
            notificationService.sendPaymentCompensationNotification(
                payment.getSenderId(),
                payment.getPaymentId(),
                payment.getAmount(),
                "Payment has been reversed due to processing failure"
            );
            
            // Notify recipient if they were credited
            if (payment.getStatus() != PaymentStatus.FAILED_BEFORE_RECIPIENT) {
                notificationService.sendPaymentCompensationNotification(
                    payment.getRecipientId(),
                    payment.getPaymentId(),
                    payment.getAmount(),
                    "Payment has been reversed due to processing failure"
                );
            }
            
            return CompensationStep.builder()
                .stepName("NOTIFICATIONS")
                .successful(true)
                .result("Compensation notifications sent")
                .completedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.warn("Failed to send compensation notifications (non-critical)", e);
            return CompensationStep.builder()
                .stepName("NOTIFICATIONS")
                .successful(false)
                .errorMessage(e.getMessage())
                .completedAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Automated recovery for stuck transactions.
     */
    @Transactional
    public void performAutomatedRecovery() {
        log.info("Starting automated compensation recovery");
        
        try {
            // Find stuck payments that need compensation
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(COMPENSATION_TIMEOUT_MINUTES);
            List<Payment> stuckPayments = paymentRepository.findStuckPayments(cutoffTime);
            
            log.info("Found {} stuck payments requiring recovery", stuckPayments.size());
            
            for (Payment payment : stuckPayments) {
                try {
                    compensateFailedPayment(
                        payment.getPaymentId(),
                        CompensationReason.TIMEOUT_RECOVERY,
                        "AUTOMATED_RECOVERY"
                    );
                } catch (Exception e) {
                    log.error("Automated recovery failed for payment: {}", payment.getPaymentId(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("Automated recovery process failed", e);
        }
    }

    // Private helper methods

    private void validateCompensationEligibility(Payment payment) {
        if (payment.getStatus() == PaymentStatus.COMPENSATED) {
            throw new IllegalStateException("Payment already compensated");
        }
        
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Cannot compensate completed payment");
        }
        
        // Check if compensation window has expired
        LocalDateTime compensationDeadline = payment.getCreatedAt().plusHours(24);
        if (LocalDateTime.now().isAfter(compensationDeadline)) {
            throw new IllegalStateException("Compensation window expired");
        }
    }

    private CompensationTransaction createCompensationRecord(Payment payment, 
                                                           CompensationReason reason, 
                                                           String initiatedBy) {
        CompensationTransaction compensation = CompensationTransaction.builder()
            .id(UUID.randomUUID().toString())
            .paymentId(payment.getPaymentId())
            .originalAmount(payment.getAmount())
            .reason(reason)
            .status(CompensationStatus.IN_PROGRESS)
            .initiatedBy(initiatedBy)
            .initiatedAt(LocalDateTime.now())
            .build();
        
        return compensationRepository.save(compensation);
    }

    private ProviderReversalResult callProviderReversal(Payment payment) {
        // Implementation would call specific provider's reversal API
        // This is a simplified version
        try {
            // Simulate provider call
            Thread.sleep(1000);
            
            return ProviderReversalResult.builder()
                .successful(true)
                .result("Provider transaction reversed")
                .build();
                
        } catch (Exception e) {
            return ProviderReversalResult.builder()
                .successful(false)
                .errorMessage("Provider reversal failed: " + e.getMessage())
                .build();
        }
    }

    private void publishCompensationEvent(CompensationTransaction compensation, 
                                        Payment payment, 
                                        CompensationResult result) {
        try {
            CompensationEvent event = CompensationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .compensationId(compensation.getId())
                .paymentId(payment.getPaymentId())
                .successful(result.isSuccessful())
                .timestamp(LocalDateTime.now())
                .build();
            
            kafkaTemplate.send(COMPENSATION_TOPIC, event);
        } catch (Exception e) {
            log.error("Failed to publish compensation event", e);
        }
    }

    private void updateCompensationMetrics(CompensationResult result) {
        meterRegistry.counter("compensation.executed",
            "success", String.valueOf(result.isSuccessful())
        ).increment();
        
        if (result.isSuccessful()) {
            meterRegistry.timer("compensation.duration")
                .record(result.getDurationMs(), TimeUnit.MILLISECONDS);
        }
    }
}