package com.waqiti.payment.service;

import com.waqiti.common.outbox.OutboxService;
import com.waqiti.payment.entity.Payment;
import com.waqiti.payment.commons.domain.PaymentStatus;
import com.waqiti.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Payment Service with Outbox Pattern Integration
 * Demonstrates how to use OutboxService for reliable event publishing
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxIntegratedPaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;
    
    /**
     * Process payment with guaranteed event publishing
     */
    @Transactional
    public Payment processPaymentWithOutbox(PaymentRequest request) {
        log.info("Processing payment with outbox: amount={}, from={}, to={}", 
            request.getAmount(), request.getSenderId(), request.getReceiverId());
        
        // Step 1: Create and save payment
        Payment payment = Payment.builder()
            .id(UUID.randomUUID().toString())
            .senderId(request.getSenderId())
            .receiverId(request.getReceiverId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .status(PaymentStatus.PROCESSING)
            .createdAt(Instant.now())
            .build();
        
        payment = paymentRepository.save(payment);
        
        // Step 2: Save payment initiated event to outbox (same transaction)
        outboxService.saveEvent(
            payment.getId(),
            "Payment",
            "PaymentInitiated",
            PaymentInitiatedEvent.from(payment),
            createEventHeaders(request)
        );
        
        // Step 3: Process payment logic
        try {
            // Validate accounts
            validateAccounts(request.getSenderId(), request.getReceiverId());
            
            // Check balance
            checkSenderBalance(request.getSenderId(), request.getAmount());
            
            // Apply fraud checks
            performFraudCheck(payment);
            
            // Update payment status
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCompletedAt(Instant.now());
            payment = paymentRepository.save(payment);
            
            // Step 4: Save payment completed event to outbox (same transaction)
            outboxService.saveEvent(
                payment.getId(),
                "Payment",
                "PaymentCompleted",
                PaymentCompletedEvent.from(payment),
                createEventHeaders(request)
            );
            
            // Step 5: Save account debited/credited events
            outboxService.saveEvent(
                request.getSenderId(),
                "Account",
                "AccountDebited",
                new AccountDebitedEvent(request.getSenderId(), request.getAmount(), payment.getId()),
                createEventHeaders(request)
            );
            
            outboxService.saveEvent(
                request.getReceiverId(),
                "Account",
                "AccountCredited",
                new AccountCreditedEvent(request.getReceiverId(), request.getAmount(), payment.getId()),
                createEventHeaders(request)
            );
            
            log.info("Payment processed successfully with outbox: paymentId={}", payment.getId());
            
        } catch (Exception e) {
            log.error("Payment processing failed: paymentId={}", payment.getId(), e);
            
            // Update payment status
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            payment = paymentRepository.save(payment);
            
            // Save payment failed event to outbox
            outboxService.saveEvent(
                payment.getId(),
                "Payment",
                "PaymentFailed",
                PaymentFailedEvent.from(payment, e.getMessage()),
                createEventHeaders(request)
            );
            
            throw new PaymentProcessingException("Payment processing failed", e);
        }
        
        // All database operations and outbox events are in same transaction
        // Either all succeed or all rollback - maintaining consistency
        
        return payment;
    }
    
    /**
     * Process refund with outbox
     */
    @Transactional
    public Payment processRefundWithOutbox(String paymentId, RefundRequest request) {
        log.info("Processing refund with outbox: paymentId={}, amount={}", 
            paymentId, request.getAmount());
        
        // Get original payment
        Payment originalPayment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
        
        // Create refund payment
        Payment refund = Payment.builder()
            .id(UUID.randomUUID().toString())
            .originalPaymentId(paymentId)
            .senderId(originalPayment.getReceiverId()) // Reverse the flow
            .receiverId(originalPayment.getSenderId())
            .amount(request.getAmount())
            .currency(originalPayment.getCurrency())
            .status(PaymentStatus.PROCESSING)
            .type(PaymentType.REFUND)
            .createdAt(Instant.now())
            .build();
        
        refund = paymentRepository.save(refund);
        
        // Save refund initiated event
        outboxService.saveEvent(
            refund.getId(),
            "Payment",
            "RefundInitiated",
            RefundInitiatedEvent.from(refund, originalPayment),
            Map.of("originalPaymentId", paymentId)
        );
        
        try {
            // Process refund
            processRefundLogic(refund, originalPayment);
            
            // Update status
            refund.setStatus(PaymentStatus.COMPLETED);
            refund.setCompletedAt(Instant.now());
            refund = paymentRepository.save(refund);
            
            // Update original payment
            originalPayment.setRefundedAmount(
                originalPayment.getRefundedAmount().add(request.getAmount())
            );
            originalPayment.setLastRefundAt(Instant.now());
            paymentRepository.save(originalPayment);
            
            // Save refund completed event
            outboxService.saveEvent(
                refund.getId(),
                "Payment",
                "RefundCompleted",
                RefundCompletedEvent.from(refund),
                Map.of("originalPaymentId", paymentId)
            );
            
            log.info("Refund processed successfully: refundId={}", refund.getId());
            
        } catch (Exception e) {
            log.error("Refund processing failed: refundId={}", refund.getId(), e);
            
            refund.setStatus(PaymentStatus.FAILED);
            refund.setFailureReason(e.getMessage());
            paymentRepository.save(refund);
            
            // Save refund failed event
            outboxService.saveEvent(
                refund.getId(),
                "Payment",
                "RefundFailed",
                RefundFailedEvent.from(refund, e.getMessage()),
                Map.of("originalPaymentId", paymentId)
            );
            
            throw new RefundProcessingException("Refund processing failed", e);
        }
        
        return refund;
    }
    
    /**
     * Cancel payment with outbox
     */
    @Transactional
    public Payment cancelPaymentWithOutbox(String paymentId, String reason) {
        log.info("Cancelling payment with outbox: paymentId={}, reason={}", paymentId, reason);
        
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
        
        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Cannot cancel payment in status: " + payment.getStatus());
        }
        
        // Update payment status
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setCancelledAt(Instant.now());
        payment.setCancellationReason(reason);
        payment = paymentRepository.save(payment);
        
        // Save payment cancelled event to outbox
        outboxService.saveEvent(
            payment.getId(),
            "Payment",
            "PaymentCancelled",
            PaymentCancelledEvent.from(payment, reason),
            Map.of("cancellationReason", reason)
        );
        
        log.info("Payment cancelled successfully: paymentId={}", paymentId);
        
        return payment;
    }
    
    // Helper methods
    
    private Map<String, String> createEventHeaders(PaymentRequest request) {
        Map<String, String> headers = new HashMap<>();
        headers.put("correlationId", request.getCorrelationId());
        headers.put("userId", request.getUserId());
        headers.put("timestamp", Instant.now().toString());
        return headers;
    }
    
    private void validateAccounts(String senderId, String receiverId) {
        // Account validation logic
        log.debug("Validating accounts: sender={}, receiver={}", senderId, receiverId);
    }
    
    private void checkSenderBalance(String senderId, BigDecimal amount) {
        // Balance check logic
        log.debug("Checking balance for sender: {}", senderId);
    }
    
    private void performFraudCheck(Payment payment) {
        // Fraud check logic
        log.debug("Performing fraud check for payment: {}", payment.getId());
    }
    
    private void processRefundLogic(Payment refund, Payment originalPayment) {
        // Refund processing logic
        log.debug("Processing refund logic: refundId={}", refund.getId());
    }
    
    // Event classes
    
    @lombok.Data
    @lombok.Builder
    public static class PaymentRequest {
        private String senderId;
        private String receiverId;
        private BigDecimal amount;
        private String currency;
        private String correlationId;
        private String userId;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class RefundRequest {
        private BigDecimal amount;
        private String reason;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class PaymentInitiatedEvent {
        private String paymentId;
        private String senderId;
        private String receiverId;
        private BigDecimal amount;
        private String currency;
        private Instant timestamp;
        
        public static PaymentInitiatedEvent from(Payment payment) {
            return PaymentInitiatedEvent.builder()
                .paymentId(payment.getId())
                .senderId(payment.getSenderId())
                .receiverId(payment.getReceiverId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .timestamp(payment.getCreatedAt())
                .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class PaymentCompletedEvent {
        private String paymentId;
        private String senderId;
        private String receiverId;
        private BigDecimal amount;
        private String currency;
        private Instant completedAt;
        
        public static PaymentCompletedEvent from(Payment payment) {
            return PaymentCompletedEvent.builder()
                .paymentId(payment.getId())
                .senderId(payment.getSenderId())
                .receiverId(payment.getReceiverId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .completedAt(payment.getCompletedAt())
                .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class PaymentFailedEvent {
        private String paymentId;
        private String failureReason;
        private Instant timestamp;
        
        public static PaymentFailedEvent from(Payment payment, String reason) {
            return PaymentFailedEvent.builder()
                .paymentId(payment.getId())
                .failureReason(reason)
                .timestamp(Instant.now())
                .build();
        }
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class AccountDebitedEvent {
        private String accountId;
        private BigDecimal amount;
        private String paymentId;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class AccountCreditedEvent {
        private String accountId;
        private BigDecimal amount;
        private String paymentId;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class PaymentCancelledEvent {
        private String paymentId;
        private String reason;
        private Instant cancelledAt;
        
        public static PaymentCancelledEvent from(Payment payment, String reason) {
            return PaymentCancelledEvent.builder()
                .paymentId(payment.getId())
                .reason(reason)
                .cancelledAt(payment.getCancelledAt())
                .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class RefundInitiatedEvent {
        private String refundId;
        private String originalPaymentId;
        private BigDecimal amount;
        private Instant timestamp;
        
        public static RefundInitiatedEvent from(Payment refund, Payment original) {
            return RefundInitiatedEvent.builder()
                .refundId(refund.getId())
                .originalPaymentId(original.getId())
                .amount(refund.getAmount())
                .timestamp(refund.getCreatedAt())
                .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class RefundCompletedEvent {
        private String refundId;
        private BigDecimal amount;
        private Instant completedAt;
        
        public static RefundCompletedEvent from(Payment refund) {
            return RefundCompletedEvent.builder()
                .refundId(refund.getId())
                .amount(refund.getAmount())
                .completedAt(refund.getCompletedAt())
                .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class RefundFailedEvent {
        private String refundId;
        private String failureReason;
        private Instant timestamp;
        
        public static RefundFailedEvent from(Payment refund, String reason) {
            return RefundFailedEvent.builder()
                .refundId(refund.getId())
                .failureReason(reason)
                .timestamp(Instant.now())
                .build();
        }
    }
    
    // Exceptions
    
    public static class PaymentProcessingException extends RuntimeException {
        public PaymentProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(String message) {
            super(message);
        }
    }
    
    public static class RefundProcessingException extends RuntimeException {
        public RefundProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    // Enums
    
    public enum PaymentType {
        PAYMENT,
        REFUND,
        REVERSAL
    }
}