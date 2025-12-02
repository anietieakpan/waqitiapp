package com.waqiti.payment.core.strategy;

import com.waqiti.payment.core.model.PaymentRequest;
import com.waqiti.payment.core.model.PaymentResult;
import com.waqiti.payment.core.model.PaymentType;

/**
 * Strategy interface for different payment types
 * Eliminates the need for multiple PaymentService implementations
 */
public interface PaymentStrategy {
    
    /**
     * Execute the payment using this strategy
     */
    PaymentResult executePayment(PaymentRequest request);
    
    /**
     * Get the payment type this strategy handles
     */
    PaymentType getPaymentType();
    
    /**
     * Validate if this strategy can handle the request
     */
    boolean canHandle(PaymentRequest request);
    
    /**
     * Get strategy priority (higher number = higher priority)
     */
    default int getPriority() {
        return 0;
    }
}