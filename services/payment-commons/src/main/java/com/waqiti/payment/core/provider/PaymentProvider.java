package com.waqiti.payment.core.provider;

import com.waqiti.payment.core.model.*;

/**
 * Universal payment provider interface
 * Abstracts different payment providers (Stripe, PayPal, etc.)
 */
public interface PaymentProvider {
    
    /**
     * Process a payment through this provider
     */
    PaymentResult processPayment(PaymentRequest request);
    
    /**
     * Process a refund
     */
    PaymentResult refundPayment(RefundRequest request);
    
    /**
     * Get the provider type this implementation handles
     */
    ProviderType getProviderType();
    
    /**
     * Check if provider is available
     */
    boolean isAvailable();
    
    /**
     * Check if this provider can handle the payment type
     */
    default boolean canHandle(PaymentType paymentType) {
        return true; // Most providers can handle most payment types
    }
    
    /**
     * Validate payment request for this provider
     */
    default ValidationResult validatePayment(PaymentRequest request) {
        return ValidationResult.valid(); // Default implementation
    }
    
    /**
     * Get provider-specific fees
     */
    default FeeCalculation calculateFees(PaymentRequest request) {
        return FeeCalculation.noFees(); // Default implementation
    }
    
    /**
     * Get payment status
     */
    default PaymentStatus getPaymentStatus(String paymentId) {
        return PaymentStatus.NOT_FOUND; // Default implementation
    }
    
    /**
     * Get provider capabilities
     */
    default ProviderCapabilities getCapabilities() {
        return ProviderCapabilities.builder()
                .supportsRefunds(true)
                .supportsRecurring(false)
                .supportsInstantSettlement(true)
                .build();
    }
    
    /**
     * Get provider configuration
     */
    default ProviderConfiguration getConfiguration() {
        return ProviderConfiguration.builder()
                .providerId(getProviderType().toString())
                .providerName(getProviderType().getDisplayName())
                .enabled(true)
                .sandbox(false)
                .build();
    }
}