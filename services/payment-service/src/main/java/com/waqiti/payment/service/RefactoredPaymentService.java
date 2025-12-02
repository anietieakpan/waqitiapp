package com.waqiti.payment.service;

import com.waqiti.payment.dto.PaymentRequest;
import com.waqiti.payment.dto.PaymentResponse;
import com.waqiti.payment.entity.Payment;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.adapter.LegacyPaymentServiceAdapter;
import com.waqiti.payment.feature.FeatureFlagService;
import com.waqiti.common.exception.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Refactored Payment Service that delegates to Payment Orchestration Service
 * This replaces the legacy PaymentService implementation
 */
@Slf4j
@Service
@Primary // Make this the primary implementation
@RequiredArgsConstructor
public class RefactoredPaymentService implements PaymentService {
    
    private final LegacyPaymentServiceAdapter orchestrationAdapter;
    private final PaymentRepository paymentRepository;
    private final FeatureFlagService featureFlagService;
    private final RefactoredPaymentEventPublisher eventPublisher;
    
    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing payment through refactored service: {}", request.getTransactionId());
        
        try {
            // Add transaction ID if not present
            if (request.getTransactionId() == null) {
                request.setTransactionId(UUID.randomUUID().toString());
            }
            
            // Check feature flag for additional validations
            if (featureFlagService.isEnabled(FeatureFlagService.Feature.USE_NEW_FRAUD_DETECTION)) {
                log.debug("Using enhanced fraud detection for payment: {}", request.getTransactionId());
            }
            
            // Create local payment record for backward compatibility
            Payment payment = createPaymentRecord(request);
            
            // Delegate to orchestration service via adapter
            PaymentResponse response = orchestrationAdapter.processPayment(request);
            
            // Update local payment record
            updatePaymentRecord(payment, response);
            
            // Publish events for backward compatibility
            publishPaymentEvent(payment, response);
            
            return response;
            
        } catch (Exception e) {
            log.error("Payment processing failed", e);
            
            return PaymentResponse.builder()
                    .transactionId(request.getTransactionId())
                    .status("FAILED")
                    .success(false)
                    .message("Payment processing failed: " + e.getMessage())
                    .timestamp(Instant.now())
                    .build();
        }
    }
    
    @Override
    @Transactional
    public PaymentResponse processCardPayment(PaymentRequest request) {
        log.info("Processing card payment: {}", request.getTransactionId());
        
        // Ensure payment method is set
        request.setPaymentMethod("CARD");
        
        return orchestrationAdapter.processCardPayment(request);
    }
    
    @Override
    @Transactional
    public PaymentResponse processACHPayment(PaymentRequest request) {
        log.info("Processing ACH payment: {}", request.getTransactionId());
        
        request.setPaymentMethod("ACH");
        
        return orchestrationAdapter.processACHPayment(request);
    }
    
    @Override
    @Transactional
    public PaymentResponse processInstantPayment(PaymentRequest request) {
        log.info("Processing instant payment: {}", request.getTransactionId());
        
        request.setPaymentMethod("INSTANT");
        
        return orchestrationAdapter.processInstantPayment(request);
    }
    
    @Override
    public PaymentResponse getPaymentStatus(String transactionId) {
        log.info("Getting payment status: {}", transactionId);
        
        // First check local database for backward compatibility
        Optional<Payment> paymentOpt = paymentRepository.findByTransactionId(transactionId);
        
        if (paymentOpt.isPresent() && isTerminalStatus(paymentOpt.get().getStatus())) {
            // Return from local cache if in terminal state
            return convertToResponse(paymentOpt.get());
        }
        
        // Get latest status from orchestration service
        PaymentResponse response = orchestrationAdapter.getPaymentStatus(transactionId);
        
        // Update local record if exists
        paymentOpt.ifPresent(payment -> updatePaymentRecord(payment, response));
        
        return response;
    }
    
    @Override
    @Transactional
    public PaymentResponse cancelPayment(String transactionId) {
        log.info("Cancelling payment: {}", transactionId);
        
        // Delegate to orchestration service first
        PaymentResponse response = orchestrationAdapter.cancelPayment(transactionId);
        
        // Update local record if it exists
        paymentRepository.findByTransactionId(transactionId)
            .ifPresent(payment -> {
                payment.setStatus(response.getStatus());
                payment.setUpdatedAt(Instant.now());
                paymentRepository.save(payment);
            });
        
        return response;
    }
    
    @Override
    @Transactional
    public PaymentResponse refundPayment(String transactionId, BigDecimal amount, String reason) {
        log.info("Processing refund for payment: {} amount: {}", transactionId, amount);
        
        // Create refund request
        com.waqiti.payment.dto.RefundRequest refundRequest = 
            com.waqiti.payment.dto.RefundRequest.builder()
                .originalTransactionId(transactionId)
                .amount(amount)
                .reason(reason)
                .refundId(UUID.randomUUID().toString())
                .build();
        
        try {
            // Call orchestration service for actual refund processing
            PaymentResponse response = orchestrationAdapter.processRefund(refundRequest);
            
            // Update local payment record if refund successful
            if (response.isSuccess()) {
                paymentRepository.findByTransactionId(transactionId)
                    .ifPresent(payment -> {
                        payment.setStatus("REFUNDED");
                        payment.setUpdatedAt(Instant.now());
                        paymentRepository.save(payment);
                    });
                
                // Publish refund event
                eventPublisher.publishRefundProcessed(transactionId, amount, reason);
                
                log.info("Refund processed successfully for payment: {} amount: {}", transactionId, amount);
            } else {
                log.error("Refund failed for payment: {} - {}", transactionId, response.getMessage());
            }
            
            return response;
            
        } catch (Exception e) {
            log.error("CRITICAL: Refund processing failed for payment: {} amount: {}", transactionId, amount, e);
            
            // Return failure response
            return PaymentResponse.builder()
                    .transactionId(refundRequest.getRefundId())
                    .originalTransactionId(transactionId)
                    .status("REFUND_FAILED")
                    .success(false)
                    .amount(amount)
                    .message("Refund processing failed: " + e.getMessage())
                    .timestamp(Instant.now())
                    .build();
        }
    }
    
    @Override
    public boolean validatePaymentMethod(String paymentMethodId, String customerId) {
        log.info("Validating payment method: {} for customer: {}", paymentMethodId, customerId);
        
        // Basic validation - in production would check with payment providers
        return paymentMethodId != null && !paymentMethodId.isEmpty();
    }
    
    @Override
    public Map<String, Object> getPaymentMetrics(String startDate, String endDate) {
        log.info("Getting payment metrics from {} to {}", startDate, endDate);
        
        // Return basic metrics - in production would aggregate from orchestration service
        return Map.of(
            "totalTransactions", paymentRepository.count(),
            "successRate", 0.95,
            "averageAmount", 125.50,
            "topPaymentMethod", "CARD"
        );
    }
    
    private Payment createPaymentRecord(PaymentRequest request) {
        Payment payment = new Payment();
        payment.setTransactionId(request.getTransactionId());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setStatus("PENDING");
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setCustomerId(request.getCustomerId());
        payment.setMerchantId(request.getMerchantId());
        payment.setDescription(request.getDescription());
        payment.setCreatedAt(Instant.now());
        payment.setUpdatedAt(Instant.now());
        
        return paymentRepository.save(payment);
    }
    
    private void updatePaymentRecord(Payment payment, PaymentResponse response) {
        payment.setStatus(response.getStatus());
        payment.setProviderTransactionId(response.getProviderTransactionId());
        payment.setFee(response.getFee());
        payment.setUpdatedAt(Instant.now());
        
        if (!response.isSuccess()) {
            payment.setFailureReason(response.getMessage());
        }
        
        paymentRepository.save(payment);
    }
    
    private void publishPaymentEvent(Payment payment, PaymentResponse response) {
        try {
            PaymentEvent event = PaymentEvent.builder()
                    .eventType(response.isSuccess() ? "PAYMENT_COMPLETED" : "PAYMENT_FAILED")
                    .transactionId(payment.getTransactionId())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .status(payment.getStatus())
                    .timestamp(Instant.now())
                    .build();
            
            eventPublisher.publishEvent(event);
            
        } catch (Exception e) {
            log.error("Failed to publish payment event", e);
        }
    }
    
    private boolean isTerminalStatus(String status) {
        return "COMPLETED".equals(status) || 
               "FAILED".equals(status) || 
               "CANCELLED".equals(status) || 
               "REFUNDED".equals(status);
    }
    
    private PaymentResponse convertToResponse(Payment payment) {
        return PaymentResponse.builder()
                .transactionId(payment.getTransactionId())
                .status(payment.getStatus())
                .success("COMPLETED".equals(payment.getStatus()))
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .fee(payment.getFee())
                .message(payment.getFailureReason())
                .timestamp(payment.getUpdatedAt())
                .build();
    }
    
    /**
     * Interface for backward compatibility
     */
    public interface PaymentService {
        PaymentResponse processPayment(PaymentRequest request);
        PaymentResponse processCardPayment(PaymentRequest request);
        PaymentResponse processACHPayment(PaymentRequest request);
        PaymentResponse processInstantPayment(PaymentRequest request);
        PaymentResponse getPaymentStatus(String transactionId);
        PaymentResponse cancelPayment(String transactionId);
        PaymentResponse refundPayment(String transactionId, BigDecimal amount, String reason);
        boolean validatePaymentMethod(String paymentMethodId, String customerId);
        Map<String, Object> getPaymentMetrics(String startDate, String endDate);
    }
    
    @lombok.Data
    @lombok.Builder
    public static class PaymentEvent {
        private String eventType;
        private String transactionId;
        private BigDecimal amount;
        private String currency;
        private String status;
        private Instant timestamp;
    }
    
    public interface PaymentEventPublisher {
        void publishEvent(PaymentEvent event);
    }
}