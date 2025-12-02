package com.waqiti.bankintegration.strategy;

import com.waqiti.bankintegration.domain.PaymentProvider;
import com.waqiti.bankintegration.dto.PaymentRequest;
import com.waqiti.bankintegration.dto.PaymentResponse;
import com.waqiti.bankintegration.dto.RefundRequest;
import com.waqiti.bankintegration.dto.RefundResponse;

/**
 * Payment Strategy Interface
 * 
 * Defines the contract for implementing payment processing strategies
 * for different types of payment providers. Each provider type has
 * its own strategy implementation.
 */
public interface PaymentStrategy {
    
    /**
     * Processes a payment request through the specific provider
     * 
     * @param provider The payment provider to use
     * @param request The payment request details
     * @return PaymentResponse containing the result
     */
    PaymentResponse processPayment(PaymentProvider provider, PaymentRequest request);
    
    /**
     * Processes a refund request through the specific provider
     * 
     * @param provider The payment provider to use
     * @param request The refund request details
     * @return RefundResponse containing the result
     */
    RefundResponse processRefund(PaymentProvider provider, RefundRequest request);
    
    /**
     * Checks the status of a payment transaction
     * 
     * @param provider The payment provider to use
     * @param transactionId The transaction identifier
     * @return PaymentResponse with current status
     */
    PaymentResponse checkPaymentStatus(PaymentProvider provider, String transactionId);
    
    /**
     * Validates if the provider can handle the payment request
     * 
     * @param provider The payment provider
     * @param request The payment request
     * @return true if the provider can handle the request
     */
    boolean canHandle(PaymentProvider provider, PaymentRequest request);
    
    /**
     * Performs health check on the provider
     * 
     * @param provider The payment provider to check
     * @return true if the provider is healthy and responsive
     */
    boolean isProviderHealthy(PaymentProvider provider);
    
    /**
     * Cancels a pending payment transaction
     * 
     * @param provider The payment provider
     * @param transactionId The transaction to cancel
     * @return PaymentResponse with cancellation result
     */
    PaymentResponse cancelPayment(PaymentProvider provider, String transactionId);
}