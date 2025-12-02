package com.waqiti.bankintegration.dto;

/**
 * Provider Type Enum
 * 
 * Defines the types of payment providers supported
 */
public enum ProviderType {
    /**
     * Stripe payment gateway
     */
    STRIPE,
    
    /**
     * PayPal payment gateway
     */
    PAYPAL,
    
    /**
     * Plaid for bank account linking and verification
     */
    PLAID,
    
    /**
     * Square payment gateway
     */
    SQUARE,
    
    /**
     * Automated Clearing House for bank transfers
     */
    ACH,
    
    /**
     * Wire transfer provider
     */
    WIRE,
    
    /**
     * SWIFT international transfers
     */
    SWIFT,
    
    /**
     * SEPA European transfers
     */
    SEPA,
    
    /**
     * Internal banking system
     */
    INTERNAL,
    
    /**
     * Test/Mock provider for development
     */
    MOCK
}