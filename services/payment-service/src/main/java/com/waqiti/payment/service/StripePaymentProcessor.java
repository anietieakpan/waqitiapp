package com.waqiti.payment.service;

import com.waqiti.payment.dto.PaymentRequest;
import com.waqiti.payment.dto.PaymentResponse;
import com.waqiti.payment.integration.stripe.StripePaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Stripe Payment Processor
 * Wrapper service for StripePaymentProvider to match autowiring expectations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StripePaymentProcessor {
    
    private final StripePaymentProvider stripePaymentProvider;
    
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing payment via Stripe: {}", request.getTransactionId());
        return stripePaymentProvider.processPayment(request);
    }
    
    public PaymentResponse capturePayment(String transactionId, BigDecimal amount) {
        log.info("Capturing Stripe payment: {} amount: {}", transactionId, amount);
        return stripePaymentProvider.capturePayment(transactionId, amount);
    }
    
    public PaymentResponse refundPayment(String transactionId, BigDecimal amount, String reason) {
        log.info("Refunding Stripe payment: {} amount: {}", transactionId, amount);
        return stripePaymentProvider.refundPayment(transactionId, amount, reason);
    }
    
    public PaymentResponse cancelPayment(String transactionId) {
        log.info("Cancelling Stripe payment: {}", transactionId);
        return stripePaymentProvider.cancelPayment(transactionId);
    }
    
    public boolean isAvailable() {
        return stripePaymentProvider.isAvailable();
    }
}
