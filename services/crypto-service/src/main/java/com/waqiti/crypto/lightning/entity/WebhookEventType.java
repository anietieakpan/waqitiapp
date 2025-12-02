package com.waqiti.crypto.lightning.entity;

/**
 * Lightning webhook event types enumeration
 */
public enum WebhookEventType {
    /**
     * Test webhook event for validation
     */
    WEBHOOK_TEST,
    
    /**
     * Invoice was created
     */
    INVOICE_CREATED,
    
    /**
     * Invoice was paid/settled
     */
    INVOICE_SETTLED,
    
    /**
     * Invoice expired without payment
     */
    INVOICE_EXPIRED,
    
    /**
     * Invoice was cancelled
     */
    INVOICE_CANCELLED,
    
    /**
     * Payment was initiated
     */
    PAYMENT_INITIATED,
    
    /**
     * Payment was received (incoming)
     */
    PAYMENT_RECEIVED,
    
    /**
     * Payment was sent successfully (outgoing)
     */
    PAYMENT_SENT,
    
    /**
     * Payment failed
     */
    PAYMENT_FAILED,
    
    /**
     * Payment is being processed (in-flight)
     */
    PAYMENT_IN_FLIGHT,
    
    /**
     * Channel was opened
     */
    CHANNEL_OPENED,
    
    /**
     * Channel was closed
     */
    CHANNEL_CLOSED,
    
    /**
     * Channel is being force-closed
     */
    CHANNEL_FORCE_CLOSING,
    
    /**
     * Channel needs attention (stuck, disputed, etc.)
     */
    CHANNEL_ATTENTION_REQUIRED,
    
    /**
     * Channel was rebalanced
     */
    CHANNEL_REBALANCED,
    
    /**
     * Channel balance changed significantly
     */
    CHANNEL_BALANCE_CHANGED,
    
    /**
     * Payment stream started
     */
    STREAM_STARTED,
    
    /**
     * Payment stream completed
     */
    STREAM_COMPLETED,
    
    /**
     * Payment stream failed
     */
    STREAM_FAILED,
    
    /**
     * Payment stream was stopped
     */
    STREAM_STOPPED,
    
    /**
     * Submarine swap initiated
     */
    SWAP_INITIATED,
    
    /**
     * Submarine swap completed
     */
    SWAP_COMPLETED,
    
    /**
     * Submarine swap failed
     */
    SWAP_FAILED,
    
    /**
     * LNURL request processed
     */
    LNURL_REQUEST,
    
    /**
     * Lightning address payment received
     */
    LIGHTNING_ADDRESS_PAYMENT,
    
    /**
     * Keysend payment received
     */
    KEYSEND_RECEIVED,
    
    /**
     * Forward payment event
     */
    FORWARD_EVENT,
    
    /**
     * Routing failure event
     */
    ROUTING_FAILURE,
    
    /**
     * Fee rate updated
     */
    FEE_RATE_UPDATED,
    
    /**
     * Node sync status changed
     */
    NODE_SYNC_CHANGED,
    
    /**
     * Peer connected
     */
    PEER_CONNECTED,
    
    /**
     * Peer disconnected
     */
    PEER_DISCONNECTED,
    
    /**
     * Backup created
     */
    BACKUP_CREATED,
    
    /**
     * Backup restored
     */
    BACKUP_RESTORED,
    
    /**
     * Security alert triggered
     */
    SECURITY_ALERT,
    
    /**
     * Rate limit exceeded
     */
    RATE_LIMIT_EXCEEDED,
    
    /**
     * System maintenance notification
     */
    MAINTENANCE_NOTIFICATION,
    
    /**
     * Custom event (user-defined)
     */
    CUSTOM_EVENT
}