package com.waqiti.payment.service;

import com.waqiti.common.distributed.DistributedLockService;
import com.waqiti.common.events.*;
import com.waqiti.payment.client.FraudDetectionServiceClient;
import com.waqiti.payment.client.WalletServiceClient;
import com.waqiti.payment.domain.*;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.exception.*;
import com.waqiti.payment.integration.stripe.StripePaymentProvider;
import com.waqiti.payment.integration.dwolla.DwollaPaymentProvider;
import com.waqiti.payment.integration.adyen.AdyenPaymentProvider;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.TransactionRepository;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL FIX: Complete implementation of payment processor
 * Replaces mock payment processing with real implementation
 * 
 * Features:
 * - Multi-provider payment processing (Stripe, Dwolla, Adyen)
 * - Distributed locking for atomic operations
 * - Fraud detection integration
 * - Wallet fund reservation and commitment
 * - Comprehensive error handling and compensation
 * - Circuit breaker pattern for resilience
 * - Idempotency support
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessorService {
    
    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final WalletServiceClient walletClient;
    private final FraudDetectionServiceClient fraudClient;
    private final StripePaymentProvider stripeProvider;
    private final DwollaPaymentProvider dwollaProvider;
    private final AdyenPaymentProvider adyenProvider;
    private final DistributedLockService lockService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final IdempotencyService idempotencyService;
    
    private static final String PAYMENT_INITIATED_TOPIC = "payment-initiated-events";
    private static final String PAYMENT_COMPLETED_TOPIC = "payment-completed-events";
    private static final String PAYMENT_FAILED_TOPIC = "payment-failed-events";
    private static final int LOCK_TIMEOUT_SECONDS = 30;
    
    /**
     * Process a payment request with complete flow
     * REPLACES MOCK IMPLEMENTATION
     * 
     * @param request The payment request
     * @param userId The user initiating the payment
     * @param idempotencyKey Unique key for idempotent processing
     * @return Payment result with transaction details
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CircuitBreaker(name = "payment-processor", fallbackMethod = "processPaymentFallback")
    @Bulkhead(name = "payment-processor")
    @Retry(name = "payment-processor")
    public PaymentResult processPayment(PaymentRequest request, UUID userId, String idempotencyKey) {
        
        Timer.Sample timer = Timer.start(meterRegistry);
        
        try {
            // Check idempotency
            PaymentResult existingResult = idempotencyService.getExistingResult(idempotencyKey);
            if (existingResult != null) {
                log.info("Returning cached result for idempotency key: {}", idempotencyKey);
                return existingResult;
            }
            
            // Acquire distributed lock for atomic processing
            String lockKey = generateLockKey(userId, request.getRecipientId());
            
            return lockService.executeWithLock(lockKey, LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS, () -> {
                
                // Step 1: Create payment record
                Payment payment = createPaymentRecord(request, userId, idempotencyKey);
                
                // Step 2: Publish payment initiated event
                publishPaymentInitiated(payment);
                
                // Step 3: Validate and reserve funds
                validateAndReserveFunds(payment);
                
                // Step 4: Perform fraud check
                FraudCheckResult fraudResult = performFraudCheck(payment, request.getDeviceFingerprint());
                
                if (fraudResult.isRejected()) {
                    return handleFraudRejection(payment, fraudResult);
                }
                
                if (fraudResult.requiresReview()) {
                    return handleFraudReview(payment, fraudResult);
                }
                
                // Step 5: Process with payment provider
                PaymentProviderResult providerResult = processWithProvider(payment, request);
                
                // Step 6: Complete or fail the payment
                PaymentResult result;
                if (providerResult.isSuccessful()) {
                    result = completePayment(payment, providerResult);
                } else {
                    result = failPayment(payment, providerResult);
                }
                
                // Cache result for idempotency
                idempotencyService.cacheResult(idempotencyKey, result);
                
                return result;
            });
            
        } catch (Exception e) {
            log.error("Payment processing failed for user {}: {}", userId, e.getMessage(), e);
            meterRegistry.counter("payments.failed", "reason", "processing_error").increment();
            throw new PaymentProcessingException("Payment processing failed", e);
            
        } finally {
            timer.stop(meterRegistry.timer("payment.processing.duration"));
        }
    }
    
    /**
     * Create initial payment record
     */
    private Payment createPaymentRecord(PaymentRequest request, UUID userId, String idempotencyKey) {
        Payment payment = Payment.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .recipientId(request.getRecipientId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .paymentMethod(request.getPaymentMethod())
            .sourceWalletId(request.getSourceWalletId())
            .description(request.getDescription())
            .status(PaymentStatus.PENDING)
            .idempotencyKey(idempotencyKey)
            .metadata(request.getMetadata())
            .createdAt(LocalDateTime.now())
            .build();
        
        payment = paymentRepository.save(payment);
        
        log.info("Created payment record: id={}, amount={} {}", 
            payment.getId(), payment.getAmount(), payment.getCurrency());
        
        return payment;
    }
    
    /**
     * Validate and reserve funds from wallet
     */
    private void validateAndReserveFunds(Payment payment) {
        try {
            // Check wallet balance
            WalletBalanceResponse balance = walletClient.getBalance(payment.getSourceWalletId());
            
            if (balance.getAvailableBalance().compareTo(payment.getAmount()) < 0) {
                throw new InsufficientBalanceException(
                    String.format("Insufficient balance: available=%s, required=%s",
                        balance.getAvailableBalance(), payment.getAmount())
                );
            }
            
            // Reserve funds
            ReserveFundsRequest reserveRequest = ReserveFundsRequest.builder()
                .walletId(payment.getSourceWalletId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .referenceId(payment.getId())
                .reason("PAYMENT")
                .build();
            
            ReserveFundsResponse reserveResponse = walletClient.reserveFunds(reserveRequest);
            
            if (!reserveResponse.isSuccessful()) {
                throw new FundReservationException("Failed to reserve funds: " + reserveResponse.getReason());
            }
            
            payment.setFundsReserved(true);
            payment.setReservationId(reserveResponse.getReservationId());
            paymentRepository.save(payment);
            
            log.info("Funds reserved for payment {}: reservationId={}", 
                payment.getId(), reserveResponse.getReservationId());
            
        } catch (Exception e) {
            log.error("Failed to reserve funds for payment {}", payment.getId(), e);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Fund reservation failed: " + e.getMessage());
            paymentRepository.save(payment);
            throw new PaymentProcessingException("Fund reservation failed", e);
        }
    }
    
    /**
     * Perform fraud check
     */
    private FraudCheckResult performFraudCheck(Payment payment, String deviceFingerprint) {
        try {
            FraudCheckRequest fraudRequest = FraudCheckRequest.builder()
                .paymentId(payment.getId())
                .userId(payment.getUserId())
                .recipientId(payment.getRecipientId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentMethod(payment.getPaymentMethod())
                .deviceFingerprint(deviceFingerprint)
                .timestamp(Instant.now())
                .build();
            
            FraudCheckResponse fraudResponse = fraudClient.checkPayment(fraudRequest);
            
            payment.setFraudCheckId(fraudResponse.getCheckId());
            payment.setFraudScore(fraudResponse.getRiskScore());
            payment.setFraudDecision(fraudResponse.getDecision());
            paymentRepository.save(payment);
            
            log.info("Fraud check completed for payment {}: score={}, decision={}", 
                payment.getId(), fraudResponse.getRiskScore(), fraudResponse.getDecision());
            
            return FraudCheckResult.builder()
                .checkId(fraudResponse.getCheckId())
                .riskScore(fraudResponse.getRiskScore())
                .decision(fraudResponse.getDecision())
                .riskFactors(fraudResponse.getRiskFactors())
                .build();
            
        } catch (Exception e) {
            log.error("Fraud check failed for payment {}, proceeding with caution", payment.getId(), e);
            
            // In case of fraud service failure, apply conservative approach
            return FraudCheckResult.builder()
                .checkId(UUID.randomUUID())
                .riskScore(50.0) // Medium risk
                .decision("ADDITIONAL_VERIFICATION")
                .build();
        }
    }
    
    /**
     * Process payment with the appropriate provider
     */
    private PaymentProviderResult processWithProvider(Payment payment, PaymentRequest request) {
        try {
            PaymentProviderResult result;
            
            switch (payment.getPaymentMethod()) {
                case CARD:
                case APPLE_PAY:
                case GOOGLE_PAY:
                    result = stripeProvider.processPayment(payment, request);
                    payment.setProvider("STRIPE");
                    break;
                    
                case BANK_ACCOUNT:
                case ACH:
                    result = dwollaProvider.processPayment(payment, request);
                    payment.setProvider("DWOLLA");
                    break;
                    
                case INTERNATIONAL_WIRE:
                case SEPA:
                    result = adyenProvider.processPayment(payment, request);
                    payment.setProvider("ADYEN");
                    break;
                    
                default:
                    throw new UnsupportedPaymentMethodException(
                        "Unsupported payment method: " + payment.getPaymentMethod()
                    );
            }
            
            payment.setProviderTransactionId(result.getTransactionId());
            payment.setProviderResponse(result.getResponseCode());
            paymentRepository.save(payment);
            
            log.info("Provider processing completed for payment {}: provider={}, transactionId={}", 
                payment.getId(), payment.getProvider(), result.getTransactionId());
            
            return result;
            
        } catch (Exception e) {
            log.error("Provider processing failed for payment {}", payment.getId(), e);
            throw new PaymentProcessingException("Provider processing failed", e);
        }
    }
    
    /**
     * Complete successful payment
     */
    private PaymentResult completePayment(Payment payment, PaymentProviderResult providerResult) {
        try {
            // Commit reserved funds
            CommitFundsRequest commitRequest = CommitFundsRequest.builder()
                .walletId(payment.getSourceWalletId())
                .reservationId(payment.getReservationId())
                .build();
            
            walletClient.commitReservedFunds(commitRequest);
            
            // Credit recipient wallet
            CreditWalletRequest creditRequest = CreditWalletRequest.builder()
                .walletId(payment.getRecipientId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .referenceId(payment.getId())
                .description("Payment received")
                .build();
            
            walletClient.creditWallet(creditRequest);
            
            // Update payment status
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCompletedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            
            // Create transaction record
            Transaction transaction = createTransaction(payment, TransactionStatus.COMPLETED);
            
            // Publish completion event
            publishPaymentCompleted(payment, transaction);
            
            log.info("Payment completed successfully: id={}, amount={} {}", 
                payment.getId(), payment.getAmount(), payment.getCurrency());
            
            meterRegistry.counter("payments.completed").increment();
            
            return PaymentResult.success(
                payment.getId(),
                transaction.getId(),
                providerResult.getTransactionId(),
                "Payment processed successfully"
            );
            
        } catch (Exception e) {
            log.error("Failed to complete payment {}", payment.getId(), e);
            // Initiate reversal
            initiatePaymentReversal(payment, "Completion failed: " + e.getMessage());
            throw new PaymentProcessingException("Payment completion failed", e);
        }
    }
    
    /**
     * Handle payment failure
     */
    private PaymentResult failPayment(Payment payment, PaymentProviderResult providerResult) {
        try {
            // Release reserved funds
            if (payment.getFundsReserved() && payment.getReservationId() != null) {
                ReleaseFundsRequest releaseRequest = ReleaseFundsRequest.builder()
                    .walletId(payment.getSourceWalletId())
                    .reservationId(payment.getReservationId())
                    .build();
                
                walletClient.releaseReservedFunds(releaseRequest);
            }
            
            // Update payment status
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(providerResult.getErrorMessage());
            payment.setFailedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            
            // Create transaction record
            Transaction transaction = createTransaction(payment, TransactionStatus.FAILED);
            
            // Publish failure event
            publishPaymentFailed(payment, transaction);
            
            log.info("Payment failed: id={}, reason={}", 
                payment.getId(), providerResult.getErrorMessage());
            
            meterRegistry.counter("payments.failed", "reason", providerResult.getErrorCode()).increment();
            
            return PaymentResult.failed(
                payment.getId(),
                providerResult.getErrorCode(),
                providerResult.getErrorMessage()
            );
            
        } catch (Exception e) {
            log.error("Error handling payment failure for {}", payment.getId(), e);
            return PaymentResult.failed(payment.getId(), "FAILURE_HANDLING_ERROR", e.getMessage());
        }
    }
    
    /**
     * Create transaction record
     */
    private Transaction createTransaction(Payment payment, TransactionStatus status) {
        Transaction transaction = Transaction.builder()
            .id(UUID.randomUUID())
            .paymentId(payment.getId())
            .userId(payment.getUserId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .type(TransactionType.PAYMENT)
            .status(status)
            .provider(payment.getProvider())
            .providerTransactionId(payment.getProviderTransactionId())
            .description(payment.getDescription())
            .metadata(payment.getMetadata())
            .createdAt(LocalDateTime.now())
            .build();
        
        return transactionRepository.save(transaction);
    }
    
    /**
     * Publish payment initiated event
     */
    private void publishPaymentInitiated(Payment payment) {
        PaymentInitiatedEvent event = PaymentInitiatedEvent.builder()
            .paymentId(payment.getId())
            .userId(payment.getUserId())
            .recipientId(payment.getRecipientId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .paymentMethod(payment.getPaymentMethod())
            .sourceAccountId(payment.getSourceWalletId())
            .targetAccountId(payment.getRecipientId())
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send(PAYMENT_INITIATED_TOPIC, event);
    }
    
    /**
     * Publish payment completed event
     */
    private void publishPaymentCompleted(Payment payment, Transaction transaction) {
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
            .paymentId(payment.getId())
            .transactionId(transaction.getId())
            .userId(payment.getUserId())
            .recipientId(payment.getRecipientId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .providerTransactionId(payment.getProviderTransactionId())
            .completedAt(Instant.now())
            .build();
        
        kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC, event);
    }
    
    /**
     * Publish payment failed event
     */
    private void publishPaymentFailed(Payment payment, Transaction transaction) {
        PaymentFailedEvent event = PaymentFailedEvent.builder()
            .paymentId(payment.getId())
            .transactionId(transaction.getId())
            .userId(payment.getUserId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .failureReason(payment.getFailureReason())
            .failedAt(Instant.now())
            .build();
        
        kafkaTemplate.send(PAYMENT_FAILED_TOPIC, event);
    }
    
    /**
     * Initiate payment reversal
     */
    private void initiatePaymentReversal(Payment payment, String reason) {
        try {
            log.info("Initiating reversal for payment {}: {}", payment.getId(), reason);
            
            TransactionReversedEvent reversalEvent = TransactionReversedEvent.builder()
                .transactionId(payment.getId())
                .reversalId(UUID.randomUUID())
                .walletId(payment.getSourceWalletId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .reason(reason)
                .reversalType("AUTOMATIC")
                .initiatedBy("SYSTEM")
                .initiatedAt(Instant.now())
                .build();
            
            kafkaTemplate.send("transaction-reversed-events", reversalEvent);
            
            payment.setReversalInitiated(true);
            paymentRepository.save(payment);
            
        } catch (Exception e) {
            log.error("Failed to initiate reversal for payment {}", payment.getId(), e);
        }
    }
    
    /**
     * Handle fraud rejection
     */
    private PaymentResult handleFraudRejection(Payment payment, FraudCheckResult fraudResult) {
        // Release reserved funds
        if (payment.getFundsReserved()) {
            try {
                walletClient.releaseReservedFunds(
                    ReleaseFundsRequest.builder()
                        .walletId(payment.getSourceWalletId())
                        .reservationId(payment.getReservationId())
                        .build()
                );
            } catch (Exception e) {
                log.error("Failed to release funds for fraud-rejected payment {}", payment.getId(), e);
            }
        }
        
        payment.setStatus(PaymentStatus.REJECTED_FRAUD);
        payment.setRejectionReason("Fraud risk: " + fraudResult.getRiskScore());
        payment.setCompletedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        
        meterRegistry.counter("payments.rejected", "reason", "fraud").increment();
        
        return PaymentResult.failed(
            payment.getId(),
            "FRAUD_DETECTED",
            "Payment rejected due to security concerns"
        );
    }
    
    /**
     * Handle fraud review requirement
     */
    private PaymentResult handleFraudReview(Payment payment, FraudCheckResult fraudResult) {
        payment.setStatus(PaymentStatus.PENDING_REVIEW);
        payment.setReviewReason("Fraud score: " + fraudResult.getRiskScore());
        paymentRepository.save(payment);
        
        return PaymentResult.pending(
            payment.getId(),
            "Payment is under review and will be processed shortly"
        );
    }
    
    /**
     * Generate distributed lock key
     */
    private String generateLockKey(UUID userId, UUID recipientId) {
        // Sort IDs to prevent deadlock
        String id1 = userId.toString();
        String id2 = recipientId.toString();
        
        if (id1.compareTo(id2) < 0) {
            return String.format("payment:lock:%s:%s", id1, id2);
        } else {
            return String.format("payment:lock:%s:%s", id2, id1);
        }
    }
    
    /**
     * Fallback method for circuit breaker
     */
    public PaymentResult processPaymentFallback(PaymentRequest request, UUID userId, 
                                               String idempotencyKey, Exception ex) {
        log.error("Payment processing circuit breaker triggered for user {}", userId, ex);
        meterRegistry.counter("payments.circuit_breaker").increment();
        
        return PaymentResult.failed(
            null,
            "SERVICE_UNAVAILABLE",
            "Payment service is temporarily unavailable. Please try again later."
        );
    }
}