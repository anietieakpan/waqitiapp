package com.waqiti.payment.service;

import com.waqiti.common.transaction.CompensationHandler;
import com.waqiti.common.transaction.DistributedTransactionContext;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Payment Compensation Service
 * 
 * Handles all payment-related compensations including:
 * - Payment reversals
 * - Refund processing
 * - Authorization releases
 * - Provider-specific compensations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCompensationService implements CompensationHandler {

    private final PaymentRepository paymentRepository;
    private final PaymentProviderService paymentProviderService;
    private final PaymentProviderFallbackService fallbackService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String COMPENSATION_TOPIC = "payment-compensations";
    
    /**
     * Main compensation entry point
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public CompensationResult compensate(DistributedTransactionContext context, Map<String, Object> participantData) {
        String paymentId = (String) participantData.get("paymentId");
        String operation = (String) participantData.get("operation");
        
        log.info("Starting compensation for payment {} operation {}", paymentId, operation);
        
        try {
            switch (operation) {
                case "PAYMENT_INITIATE":
                    return compensatePaymentInitiation(paymentId, participantData);
                case "PAYMENT_CAPTURE":
                    return compensatePaymentCapture(paymentId, participantData);
                case "PAYMENT_AUTHORIZE":
                    return compensatePaymentAuthorization(paymentId, participantData);
                case "PAYMENT_SETTLEMENT":
                    return compensatePaymentSettlement(paymentId, participantData);
                case "REFUND_INITIATE":
                    return compensateRefundInitiation(paymentId, participantData);
                default:
                    log.warn("Unknown compensation operation: {}", operation);
                    return CompensationResult.failure("Unknown operation: " + operation);
            }
        } catch (Exception e) {
            log.error("Compensation failed for payment {} operation {}", paymentId, operation, e);
            return CompensationResult.failure("Compensation failed: " + e.getMessage());
        }
    }
    
    /**
     * Compensate payment initiation
     */
    @CircuitBreaker(name = "payment-compensation")
    @Retry(name = "payment-compensation")
    private CompensationResult compensatePaymentInitiation(String paymentId, Map<String, Object> data) {
        log.info("Compensating payment initiation for {}", paymentId);
        
        Payment payment = paymentRepository.findById(UUID.fromString(paymentId))
            .orElse(null);
            
        if (payment == null) {
            log.warn("Payment not found for compensation: {}", paymentId);
            return CompensationResult.success("Payment not found - no compensation needed");
        }
        
        // Check if payment is in a compensatable state
        if (!isCompensatable(payment.getStatus())) {
            log.info("Payment {} in status {} is not compensatable", paymentId, payment.getStatus());
            return CompensationResult.success("Payment not in compensatable state");
        }
        
        try {
            // Cancel with payment provider
            if (payment.getProviderReference() != null) {
                cancelWithProvider(payment);
            }
            
            // Update payment status
            payment.setStatus(PaymentStatus.CANCELLED);
            payment.setCancelledAt(LocalDateTime.now());
            payment.setCancellationReason("Transaction compensation");
            paymentRepository.save(payment);
            
            // Publish compensation event
            publishCompensationEvent(payment, "PAYMENT_CANCELLED");
            
            log.info("Successfully compensated payment initiation for {}", paymentId);
            return CompensationResult.success("Payment cancelled");
            
        } catch (Exception e) {
            log.error("Failed to compensate payment initiation for {}", paymentId, e);
            
            // Mark payment for manual review
            payment.setStatus(PaymentStatus.COMPENSATION_FAILED);
            payment.setNeedsManualReview(true);
            paymentRepository.save(payment);
            
            return CompensationResult.failure("Compensation failed: " + e.getMessage());
        }
    }
    
    /**
     * Compensate payment capture
     */
    @CircuitBreaker(name = "payment-compensation")
    @Retry(name = "payment-compensation")
    private CompensationResult compensatePaymentCapture(String paymentId, Map<String, Object> data) {
        log.info("Compensating payment capture for {}", paymentId);
        
        Payment payment = paymentRepository.findById(UUID.fromString(paymentId))
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        
        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            return CompensationResult.success("Payment not captured - no compensation needed");
        }
        
        try {
            // Initiate refund with provider
            String refundId = refundWithProvider(payment, payment.getAmount());
            
            // Update payment status
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setRefundedAt(LocalDateTime.now());
            payment.setRefundReference(refundId);
            payment.setRefundReason("Transaction compensation");
            paymentRepository.save(payment);
            
            // Publish compensation event
            publishCompensationEvent(payment, "PAYMENT_REFUNDED");
            
            log.info("Successfully compensated payment capture for {} with refund {}", paymentId, refundId);
            return CompensationResult.success("Payment refunded", Map.of("refundId", refundId));
            
        } catch (Exception e) {
            log.error("Failed to compensate payment capture for {}", paymentId, e);
            
            // Try fallback provider
            try {
                return compensateWithFallback(payment, "REFUND");
            } catch (Exception fallbackError) {
                payment.setStatus(PaymentStatus.COMPENSATION_FAILED);
                payment.setNeedsManualReview(true);
                paymentRepository.save(payment);
                
                return CompensationResult.failure("Compensation and fallback failed: " + fallbackError.getMessage());
            }
        }
    }
    
    /**
     * Compensate payment authorization
     */
    private CompensationResult compensatePaymentAuthorization(String paymentId, Map<String, Object> data) {
        log.info("Compensating payment authorization for {}", paymentId);
        
        Payment payment = paymentRepository.findById(UUID.fromString(paymentId))
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            return CompensationResult.success("Payment not authorized - no compensation needed");
        }
        
        try {
            // Release authorization with provider
            releaseAuthorizationWithProvider(payment);
            
            // Update payment status
            payment.setStatus(PaymentStatus.AUTHORIZATION_RELEASED);
            payment.setAuthorizationReleasedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            
            // Publish compensation event
            publishCompensationEvent(payment, "AUTHORIZATION_RELEASED");
            
            log.info("Successfully released authorization for {}", paymentId);
            return CompensationResult.success("Authorization released");
            
        } catch (Exception e) {
            log.error("Failed to release authorization for {}", paymentId, e);
            return CompensationResult.failure("Failed to release authorization: " + e.getMessage());
        }
    }
    
    /**
     * Compensate payment settlement
     */
    private CompensationResult compensatePaymentSettlement(String paymentId, Map<String, Object> data) {
        log.info("Compensating payment settlement for {}", paymentId);
        
        Payment payment = paymentRepository.findById(UUID.fromString(paymentId))
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        
        if (payment.getStatus() != PaymentStatus.SETTLED) {
            return CompensationResult.success("Payment not settled - no compensation needed");
        }
        
        // Settled payments require special handling
        try {
            // Create reversal transaction
            Payment reversal = createReversalPayment(payment);
            paymentRepository.save(reversal);
            
            // Process reversal with provider
            String reversalId = processReversalWithProvider(reversal);
            reversal.setProviderReference(reversalId);
            reversal.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(reversal);
            
            // Update original payment
            payment.setReversalPaymentId(reversal.getId());
            payment.setReversedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            
            // Publish compensation event
            publishCompensationEvent(payment, "PAYMENT_REVERSED");
            
            log.info("Successfully created reversal {} for settled payment {}", reversal.getId(), paymentId);
            return CompensationResult.success("Payment reversed", Map.of("reversalId", reversal.getId()));
            
        } catch (Exception e) {
            log.error("Failed to reverse settled payment {}", paymentId, e);
            return CompensationResult.failure("Failed to reverse settlement: " + e.getMessage());
        }
    }
    
    /**
     * Compensate refund initiation
     */
    private CompensationResult compensateRefundInitiation(String refundId, Map<String, Object> data) {
        log.info("Compensating refund initiation for {}", refundId);
        
        Payment refund = paymentRepository.findById(UUID.fromString(refundId))
            .orElseThrow(() -> new IllegalArgumentException("Refund not found: " + refundId));
        
        if (refund.getStatus() == PaymentStatus.COMPLETED) {
            log.warn("Refund {} already completed - cannot compensate", refundId);
            return CompensationResult.failure("Refund already completed");
        }
        
        try {
            // Cancel refund with provider
            cancelRefundWithProvider(refund);
            
            // Update refund status
            refund.setStatus(PaymentStatus.CANCELLED);
            refund.setCancelledAt(LocalDateTime.now());
            refund.setCancellationReason("Refund compensation");
            paymentRepository.save(refund);
            
            // Publish compensation event
            publishCompensationEvent(refund, "REFUND_CANCELLED");
            
            log.info("Successfully cancelled refund {}", refundId);
            return CompensationResult.success("Refund cancelled");
            
        } catch (Exception e) {
            log.error("Failed to cancel refund {}", refundId, e);
            return CompensationResult.failure("Failed to cancel refund: " + e.getMessage());
        }
    }
    
    /**
     * Compensate with fallback provider
     */
    private CompensationResult compensateWithFallback(Payment payment, String operation) {
        log.info("Attempting compensation with fallback provider for payment {}", payment.getId());
        
        try {
            PaymentResult result = fallbackService.processCompensationWithFallback(
                payment, operation);
                
            if (result.isSuccess()) {
                payment.setFallbackProvider(result.getProvider());
                payment.setFallbackReference(result.getReference());
                paymentRepository.save(payment);
                
                return CompensationResult.success("Compensated with fallback provider", 
                    Map.of("provider", result.getProvider()));
            } else {
                return CompensationResult.failure("Fallback compensation failed");
            }
        } catch (Exception e) {
            log.error("Fallback compensation failed for payment {}", payment.getId(), e);
            throw e;
        }
    }
    
    // Helper methods
    
    private boolean isCompensatable(PaymentStatus status) {
        return status == PaymentStatus.PENDING || 
               status == PaymentStatus.PROCESSING ||
               status == PaymentStatus.AUTHORIZED ||
               status == PaymentStatus.CAPTURED;
    }
    
    private void cancelWithProvider(Payment payment) {
        paymentProviderService.cancelPayment(payment.getProvider(), payment.getProviderReference());
    }
    
    private String refundWithProvider(Payment payment, BigDecimal amount) {
        return paymentProviderService.refundPayment(
            payment.getProvider(), payment.getProviderReference(), amount);
    }
    
    private void releaseAuthorizationWithProvider(Payment payment) {
        paymentProviderService.releaseAuthorization(
            payment.getProvider(), payment.getProviderReference());
    }
    
    private void cancelRefundWithProvider(Payment refund) {
        paymentProviderService.cancelRefund(
            refund.getProvider(), refund.getProviderReference());
    }
    
    private String processReversalWithProvider(Payment reversal) {
        return paymentProviderService.processPayment(reversal);
    }
    
    private Payment createReversalPayment(Payment original) {
        return Payment.builder()
            .id(UUID.randomUUID())
            .type(PaymentType.REVERSAL)
            .originalPaymentId(original.getId())
            .amount(original.getAmount().negate())
            .currency(original.getCurrency())
            .provider(original.getProvider())
            .status(PaymentStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .metadata(Map.of(
                "originalPaymentId", original.getId().toString(),
                "reason", "Compensation reversal"
            ))
            .build();
    }
    
    private void publishCompensationEvent(Payment payment, String eventType) {
        Map<String, Object> event = Map.of(
            "paymentId", payment.getId(),
            "eventType", eventType,
            "status", payment.getStatus(),
            "timestamp", LocalDateTime.now(),
            "provider", payment.getProvider()
        );
        
        kafkaTemplate.send(COMPENSATION_TOPIC, payment.getId().toString(), event);
    }
    
    // Async compensation support
    
    @Override
    public CompletableFuture<Boolean> compensate() {
        return CompletableFuture.supplyAsync(() -> {
            // This would be called for simple compensations without context
            log.info("Executing async compensation");
            return true;
        });
    }
    
    /**
     * Batch compensation for multiple payments
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public CompletableFuture<BatchCompensationResult> compensateBatch(List<String> paymentIds) {
        return CompletableFuture.supplyAsync(() -> {
            int successful = 0;
            int failed = 0;
            
            for (String paymentId : paymentIds) {
                try {
                    CompensationResult result = compensatePaymentInitiation(paymentId, Map.of());
                    if (result.isSuccess()) {
                        successful++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    log.error("Batch compensation failed for payment {}", paymentId, e);
                    failed++;
                }
            }
            
            return BatchCompensationResult.builder()
                .total(paymentIds.size())
                .successful(successful)
                .failed(failed)
                .build();
        });
    }
    
    @lombok.Data
    @lombok.Builder
    static class BatchCompensationResult {
        private int total;
        private int successful;
        private int failed;
    }
    
    @lombok.Data
    @lombok.Builder
    static class PaymentResult {
        private boolean success;
        private String provider;
        private String reference;
    }
    
    enum PaymentType {
        PAYMENT, REFUND, REVERSAL
    }
}