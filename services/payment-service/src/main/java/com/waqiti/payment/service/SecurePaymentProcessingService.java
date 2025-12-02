package com.waqiti.payment.service;

import com.waqiti.common.lock.DistributedLockService;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.dto.PaymentRequest;
import com.waqiti.payment.dto.PaymentResult;
import com.waqiti.payment.exception.ConcurrentPaymentException;
import com.waqiti.payment.exception.InsufficientFundsException;
import com.waqiti.payment.exception.PaymentProcessingException;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Secure payment processing service with distributed locking
 * 
 * Prevents race conditions and double-spending through:
 * - Distributed locking with Redis
 * - Database-level pessimistic locking
 * - Idempotency key validation
 * - Atomic fund reservations
 * - Transaction isolation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurePaymentProcessingService {
    
    private final PaymentRepository paymentRepository;
    private final WalletService walletService;
    private final DistributedLockService lockService;
    private final PaymentValidationService validationService;
    private final FundReservationService fundReservationService;
    private final AuditService auditService;
    
    @Value("${payment.lock.ttl:PT30S}")
    private Duration paymentLockTtl;
    
    @Value("${payment.lock.retry.max:3}")
    private int maxLockRetries;
    
    @Value("${payment.fund.reservation.ttl:PT5M}")
    private Duration fundReservationTtl;
    
    /**
     * Process payment with comprehensive concurrency protection
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PaymentResult processPayment(PaymentRequest request) {
        // Step 1: Validate idempotency
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null) {
            Payment existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existingPayment != null) {
                log.info("Idempotent request detected for key: {}", idempotencyKey);
                return buildResultFromPayment(existingPayment);
            }
        }
        
        // Step 2: Create distributed lock for sender's wallet
        String senderLockKey = "wallet:" + request.getSenderId();
        String paymentId = UUID.randomUUID().toString();
        
        try {
            // Execute payment with distributed lock
            return lockService.executeWithLockRetry(
                senderLockKey,
                paymentLockTtl,
                maxLockRetries,
                () -> processPaymentWithLock(request, paymentId)
            );
            
        } catch (DistributedLockService.LockAcquisitionException e) {
            log.error("Failed to acquire lock for payment processing: {}", senderLockKey);
            throw new ConcurrentPaymentException("Payment processing is currently in progress. Please try again.", e);
        } catch (Exception e) {
            log.error("Payment processing failed for payment: {}", paymentId, e);
            throw new PaymentProcessingException("Payment processing failed", e);
        }
    }
    
    /**
     * Process payment with lock acquired
     */
    private PaymentResult processPaymentWithLock(PaymentRequest request, String paymentId) throws Exception {
        log.info("Processing payment {} with lock acquired", paymentId);
        
        // Step 3: Validate payment request
        validationService.validatePaymentRequest(request);
        
        // Step 4: Reserve funds (prevents double-spending)
        String reservationId = fundReservationService.reserveFunds(
            request.getSenderId(),
            request.getAmount(),
            request.getCurrency(),
            paymentId,
            fundReservationTtl
        );
        
        if (reservationId == null) {
            throw new InsufficientFundsException("Insufficient funds for payment");
        }
        
        Payment payment = null;
        
        try {
            // Step 5: Create payment record
            payment = createPaymentRecord(request, paymentId);
            
            // Step 6: Perform atomic wallet operations
            performWalletTransfer(
                request.getSenderId(),
                request.getRecipientId(),
                request.getAmount(),
                request.getCurrency(),
                paymentId
            );
            
            // Step 7: Update payment status
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCompletedAt(Instant.now());
            payment = paymentRepository.save(payment);
            
            // Step 8: Release fund reservation
            fundReservationService.commitReservation(reservationId);
            
            // Step 9: Audit successful payment
            auditService.auditPaymentSuccess(payment);
            
            log.info("Payment {} completed successfully", paymentId);
            
            return PaymentResult.success(
                payment.getId(),
                payment.getTransactionReference(),
                "Payment processed successfully"
            );
            
        } catch (Exception e) {
            log.error("Payment processing failed, rolling back: {}", paymentId, e);
            
            // Rollback fund reservation
            if (reservationId != null) {
                fundReservationService.releaseReservation(reservationId);
            }
            
            // Update payment status if created
            if (payment != null) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(e.getMessage());
                payment.setFailedAt(Instant.now());
                paymentRepository.save(payment);
                
                // Audit failed payment
                auditService.auditPaymentFailure(payment, e);
            }
            
            throw e;
        }
    }
    
    /**
     * Perform atomic wallet transfer with pessimistic locking
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    private void performWalletTransfer(UUID senderId, UUID recipientId, BigDecimal amount, 
                                      String currency, String transactionId) {
        
        // Use pessimistic write lock on wallets
        walletService.debitWalletWithLock(
            senderId,
            amount,
            currency,
            "Payment to recipient",
            transactionId
        );
        
        walletService.creditWalletWithLock(
            recipientId,
            amount,
            currency,
            "Payment received",
            transactionId
        );
        
        log.debug("Wallet transfer completed for transaction: {}", transactionId);
    }
    
    /**
     * Create payment record
     */
    private Payment createPaymentRecord(PaymentRequest request, String paymentId) {
        Payment payment = new Payment();
        payment.setId(UUID.fromString(paymentId));
        payment.setSenderId(request.getSenderId());
        payment.setRecipientId(request.getRecipientId());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setDescription(request.getDescription());
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setTransactionReference(generateTransactionReference());
        payment.setIdempotencyKey(request.getIdempotencyKey());
        payment.setCreatedAt(Instant.now());
        payment.setMetadata(request.getMetadata());
        
        return paymentRepository.save(payment);
    }
    
    /**
     * Process concurrent payment batch with optimistic locking
     */
    public CompletableFuture<PaymentResult> processPaymentAsync(PaymentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            int retries = 0;
            Exception lastException = null;
            
            while (retries < maxLockRetries) {
                try {
                    return processPayment(request);
                } catch (ConcurrentPaymentException e) {
                    lastException = e;
                    retries++;
                    
                    // Exponential backoff
                    try {
                        Thread.sleep((long) Math.pow(2, retries) * 100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new PaymentProcessingException("Interrupted during retry", ie);
                    }
                }
            }
            
            throw new PaymentProcessingException(
                "Failed to process payment after " + retries + " retries", 
                lastException
            );
        });
    }
    
    /**
     * Process payment with two-phase commit for distributed transactions
     */
    @Transactional
    public PaymentResult processDistributedPayment(PaymentRequest request) {
        String transactionId = UUID.randomUUID().toString();
        
        // Phase 1: Prepare
        String senderLock = "prepare:sender:" + request.getSenderId();
        String recipientLock = "prepare:recipient:" + request.getRecipientId();
        
        DistributedLockService.DistributedLock senderLockObj = null;
        DistributedLockService.DistributedLock recipientLockObj = null;
        
        try {
            // Acquire locks on both wallets
            senderLockObj = lockService.acquireLock(senderLock, Duration.ofSeconds(10));
            recipientLockObj = lockService.acquireLock(recipientLock, Duration.ofSeconds(10));
            
            if (senderLockObj == null || recipientLockObj == null) {
                throw new ConcurrentPaymentException("Unable to acquire locks for distributed payment");
            }
            
            // Validate both wallets
            boolean senderReady = walletService.prepareDebit(
                request.getSenderId(), 
                request.getAmount(), 
                transactionId
            );
            
            boolean recipientReady = walletService.prepareCredit(
                request.getRecipientId(), 
                request.getAmount(), 
                transactionId
            );
            
            if (!senderReady || !recipientReady) {
                throw new PaymentProcessingException("Wallet preparation failed");
            }
            
            // Phase 2: Commit
            walletService.commitDebit(request.getSenderId(), transactionId);
            walletService.commitCredit(request.getRecipientId(), transactionId);
            
            // Create payment record
            Payment payment = createPaymentRecord(request, transactionId);
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCompletedAt(Instant.now());
            paymentRepository.save(payment);
            
            return PaymentResult.success(
                payment.getId(),
                payment.getTransactionReference(),
                "Distributed payment completed successfully"
            );
            
        } catch (Exception e) {
            // Rollback
            log.error("Distributed payment failed, rolling back: {}", transactionId, e);
            
            walletService.rollbackDebit(request.getSenderId(), transactionId);
            walletService.rollbackCredit(request.getRecipientId(), transactionId);
            
            throw new PaymentProcessingException("Distributed payment failed", e);
            
        } finally {
            // Release locks
            if (senderLockObj != null) {
                lockService.releaseLock(senderLockObj);
            }
            if (recipientLockObj != null) {
                lockService.releaseLock(recipientLockObj);
            }
        }
    }
    
    /**
     * Generate unique transaction reference
     */
    private String generateTransactionReference() {
        return "TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    /**
     * Build result from existing payment
     */
    private PaymentResult buildResultFromPayment(Payment payment) {
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return PaymentResult.success(
                payment.getId(),
                payment.getTransactionReference(),
                "Payment already processed"
            );
        } else if (payment.getStatus() == PaymentStatus.FAILED) {
            return PaymentResult.failure(
                payment.getFailureReason(),
                "PAYMENT_FAILED"
            );
        } else {
            return PaymentResult.pending(
                payment.getId(),
                "Payment is being processed"
            );
        }
    }
}