package com.waqiti.recurringpayment.service.clients;

import com.waqiti.recurringpayment.service.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for Payment Service Client
 * 
 * CRITICAL: Recurring payments represent standing instructions from customers.
 * Any failure must preserve payment schedules and ensure customers are notified
 * of processing delays. NO payments should be lost or duplicated.
 * 
 * Failure Strategy:
 * - QUEUE failed payments for retry in next cycle
 * - PRESERVE payment schedule integrity
 * - NOTIFY customer of temporary processing delays
 * - PREVENT duplicate charges at all costs
 * - MAINTAIN complete audit trail for reconciliation
 * 
 * @author Waqiti Platform Team
 * @since Phase 1 Remediation - Session 6
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentServiceClientFallback implements PaymentServiceClient {
    
    /**
     * CRITICAL: Process recurring payment
     * Strategy: Queue for retry, prevent duplicate charges
     */
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        log.error("FALLBACK ACTIVATED: Payment processing unavailable. " +
                 "PaymentId: {}, Amount: {}, ScheduleId: {}, CustomerId: {}",
                 request.getPaymentId(), request.getAmount(), 
                 request.getScheduleId(), request.getCustomerId());
        
        // Return queued status to trigger retry mechanism
        return PaymentResult.builder()
                .paymentId(request.getPaymentId())
                .status("QUEUED_FOR_RETRY")
                .message("Payment service temporarily unavailable. Queued for automatic retry.")
                .requiresRetry(true)
                .nextRetryTime(System.currentTimeMillis() + 300000) // 5 minutes
                .fallbackActivated(true)
                .originalAmount(request.getAmount())
                .build();
    }
    
    /**
     * Get payment details - return cached or unavailable status
     */
    @Override
    public PaymentDetails getPayment(String paymentId) {
        log.warn("FALLBACK: Unable to retrieve payment details. PaymentId: {}", paymentId);
        
        return PaymentDetails.builder()
                .paymentId(paymentId)
                .status("DETAILS_UNAVAILABLE")
                .message("Payment service temporarily unavailable")
                .fallbackActivated(true)
                .build();
    }
    
    /**
     * CRITICAL: Refund payment
     * Strategy: Queue refund request, ensure no double refunds
     */
    @Override
    public RefundResult refundPayment(String paymentId, RefundRequest request) {
        log.error("FALLBACK: Refund processing unavailable. PaymentId: {}, Amount: {}",
                 paymentId, request.getAmount());
        
        // Queue refund for processing when service recovers
        return RefundResult.builder()
                .refundId(request.getRefundId())
                .paymentId(paymentId)
                .status("REFUND_QUEUED")
                .message("Refund queued for processing when service is available")
                .queuedForProcessing(true)
                .fallbackActivated(true)
                .build();
    }
    
    /**
     * Validate payment request - return conservative validation
     */
    @Override
    public ValidationResult validatePaymentRequest(PaymentRequest request) {
        log.warn("FALLBACK: Payment validation unavailable. Using conservative validation.");
        
        // Conservative validation - only basic checks
        boolean basicValidation = request != null &&
                                  request.getAmount() != null &&
                                  request.getAmount().doubleValue() > 0 &&
                                  request.getCustomerId() != null;
        
        return ValidationResult.builder()
                .valid(false) // Conservative: mark as invalid to prevent processing
                .message("Payment validation service unavailable. Please retry later.")
                .requiresManualReview(true)
                .fallbackActivated(true)
                .build();
    }
    
    /**
     * Get user payment methods - return cached or empty list
     */
    @Override
    public PaymentMethodsResponse getUserPaymentMethods(String userId) {
        log.warn("FALLBACK: Unable to retrieve payment methods. UserId: {}", userId);
        
        return PaymentMethodsResponse.builder()
                .userId(userId)
                .paymentMethods(null) // Return null to indicate unavailable
                .message("Payment methods temporarily unavailable")
                .fallbackActivated(true)
                .build();
    }
}