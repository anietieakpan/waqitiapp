package com.waqiti.bankintegration.dto;

/**
 * Payment Status Enum
 * 
 * Represents the various states a payment can be in
 */
public enum PaymentStatus {
    /**
     * Payment is initiated but not yet processed
     */
    INITIATED,
    
    /**
     * Payment is awaiting further action (e.g., 3D Secure authentication)
     */
    PENDING,
    
    /**
     * Payment is being processed by the provider
     */
    PROCESSING,
    
    /**
     * Payment has been authorized but not yet captured
     */
    AUTHORIZED,
    
    /**
     * Payment has been successfully completed
     */
    COMPLETED,
    
    /**
     * Payment was successful (alias for COMPLETED)
     */
    SUCCESS,
    
    /**
     * Payment has failed
     */
    FAILED,
    
    /**
     * Payment has been cancelled
     */
    CANCELLED,
    
    /**
     * Payment has been partially refunded
     */
    PARTIALLY_REFUNDED,
    
    /**
     * Payment has been fully refunded
     */
    REFUNDED,
    
    /**
     * Payment is on hold for review
     */
    ON_HOLD,
    
    /**
     * Payment has expired
     */
    EXPIRED
}