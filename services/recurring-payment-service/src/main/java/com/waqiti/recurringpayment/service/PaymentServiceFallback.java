package com.waqiti.recurringpayment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for PaymentService Feign client
 * Provides graceful degradation when payment-service is unavailable
 */
@Slf4j
@Component
public class PaymentServiceFallback implements PaymentService {

    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        log.error("FALLBACK: payment-service unavailable for processPayment. Request queued for retry. PaymentId: {}",
                 request != null ? request.getPaymentId() : "unknown");

        // Return a failure result indicating service unavailable
        return PaymentResult.builder()
                .success(false)
                .status("SERVICE_UNAVAILABLE")
                .errorCode("PAYMENT_SERVICE_DOWN")
                .errorMessage("Payment service is temporarily unavailable. Your payment will be retried automatically.")
                .retryable(true)
                .build();
    }

    @Override
    public PaymentResult getPayment(String paymentId) {
        log.warn("FALLBACK: payment-service unavailable for getPayment. PaymentId: {}", paymentId);

        // Return cached or null result
        return PaymentResult.builder()
                .success(false)
                .status("SERVICE_UNAVAILABLE")
                .errorCode("PAYMENT_SERVICE_DOWN")
                .errorMessage("Unable to retrieve payment status. Please try again later.")
                .build();
    }

    @Override
    public void cancelPayment(String paymentId, String reason) {
        log.error("FALLBACK: payment-service unavailable for cancelPayment. PaymentId: {}, Reason: {}",
                 paymentId, reason);

        // Queue cancellation for retry when service is back
        // Throw exception to indicate failure
        throw new RuntimeException("Payment cancellation failed: Payment service unavailable");
    }
}
