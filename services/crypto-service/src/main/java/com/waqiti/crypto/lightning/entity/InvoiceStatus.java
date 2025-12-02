package com.waqiti.crypto.lightning.entity;

/**
 * Lightning invoice status enumeration
 */
public enum InvoiceStatus {
    /**
     * Invoice created and waiting for payment
     */
    PENDING,
    
    /**
     * Invoice has been paid successfully
     */
    PAID,
    
    /**
     * Invoice has expired without payment
     */
    EXPIRED,
    
    /**
     * Invoice was cancelled by the creator
     */
    CANCELLED,
    
    /**
     * Invoice is being processed (transitional state)
     */
    PROCESSING,
    
    /**
     * Invoice payment failed after attempts
     */
    FAILED
}