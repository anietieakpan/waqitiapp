package com.waqiti.crypto.lightning.entity;

/**
 * Lightning webhook status enumeration
 */
public enum WebhookStatus {
    /**
     * Webhook is active and receiving events
     */
    ACTIVE,
    
    /**
     * Webhook is temporarily inactive
     */
    INACTIVE,
    
    /**
     * Webhook has failed too many times and is suspended
     */
    SUSPENDED,
    
    /**
     * Webhook failed validation or configuration
     */
    FAILED
}