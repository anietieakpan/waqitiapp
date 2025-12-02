package com.waqiti.bankintegration.domain;

/**
 * Provider Type Enumeration
 * 
 * Defines the different types of payment providers and financial institutions
 * that the platform can integrate with.
 */
public enum ProviderType {
    
    /**
     * Traditional commercial banks
     */
    COMMERCIAL_BANK,
    
    /**
     * Credit unions and community banks
     */
    CREDIT_UNION,
    
    /**
     * Central banks and regulatory institutions
     */
    CENTRAL_BANK,
    
    /**
     * ACH payment processors
     */
    ACH_PROCESSOR,
    
    /**
     * Wire transfer providers
     */
    WIRE_TRANSFER,
    
    /**
     * Card payment processors (Visa, Mastercard, etc.)
     */
    CARD_PROCESSOR,
    
    /**
     * Digital wallet providers (PayPal, Apple Pay, Google Pay)
     */
    DIGITAL_WALLET,
    
    /**
     * Cryptocurrency exchanges and processors
     */
    CRYPTO_EXCHANGE,
    
    /**
     * Mobile money providers (M-Pesa, etc.)
     */
    MOBILE_MONEY,
    
    /**
     * Buy Now Pay Later providers (Klarna, Afterpay)
     */
    BNPL_PROVIDER,
    
    /**
     * Peer-to-peer payment platforms
     */
    P2P_PLATFORM,
    
    /**
     * International money transfer services (Western Union, Remitly)
     */
    REMITTANCE_SERVICE,
    
    /**
     * Real-time payment networks (FedNow, RTP)
     */
    REAL_TIME_PAYMENT,
    
    /**
     * Open banking providers and APIs
     */
    OPEN_BANKING,
    
    /**
     * Fintech aggregators and platforms
     */
    FINTECH_AGGREGATOR,
    
    /**
     * Government payment systems
     */
    GOVERNMENT_PAYMENT,
    
    /**
     * Clearing and settlement systems
     */
    CLEARING_HOUSE,
    
    /**
     * Payment gateway providers
     */
    PAYMENT_GATEWAY,
    
    /**
     * Alternative payment methods
     */
    ALTERNATIVE_PAYMENT
}