package com.waqiti.crypto.lightning.entity;

/**
 * Lightning webhook delivery status enumeration
 */
public enum WebhookDeliveryStatus {
    /**
     * Delivery is pending/queued
     */
    PENDING,
    
    /**
     * Delivery was successful
     */
    DELIVERED,
    
    /**
     * Delivery failed after all retry attempts
     */
    FAILED,
    
    /**
     * Delivery was cancelled
     */
    CANCELLED
}