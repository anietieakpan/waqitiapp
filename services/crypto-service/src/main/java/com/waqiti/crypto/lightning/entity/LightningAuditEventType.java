package com.waqiti.crypto.lightning.entity;

/**
 * Lightning audit event types enumeration
 * Comprehensive categorization of all auditable Lightning operations
 */
public enum LightningAuditEventType {
    // Invoice events
    INVOICE_CREATED,
    INVOICE_SETTLED,
    INVOICE_EXPIRED,
    INVOICE_CANCELLED,
    
    // Payment events
    PAYMENT_INITIATED,
    PAYMENT_SENT,
    PAYMENT_RECEIVED,
    PAYMENT_FAILED,
    
    // Keysend events
    KEYSEND_SENT,
    KEYSEND_RECEIVED,
    
    // Channel events
    CHANNEL_OPENED,
    CHANNEL_CLOSED,
    CHANNEL_FORCE_CLOSED,
    CHANNEL_REBALANCED,
    CHANNEL_BALANCE_CHANGED,
    
    // Streaming payment events
    STREAM_STARTED,
    STREAM_COMPLETED,
    STREAM_FAILED,
    STREAM_STOPPED,
    
    // Swap events
    SWAP_INITIATED,
    SWAP_COMPLETED,
    SWAP_FAILED,
    
    // LNURL events
    LNURL_PAY_GENERATED,
    LNURL_PAY_USED,
    LNURL_WITHDRAW_GENERATED,
    LNURL_WITHDRAW_USED,
    
    // Lightning address events
    LIGHTNING_ADDRESS_CREATED,
    LIGHTNING_ADDRESS_PAYMENT_RECEIVED,
    
    // Node management events
    PEER_CONNECTED,
    PEER_DISCONNECTED,
    NODE_SYNC_STATUS_CHANGED,
    
    // Security events
    SECURITY_EVENT,
    AUTHENTICATION,
    AUTHORIZATION_FAILED,
    SUSPICIOUS_ACTIVITY_DETECTED,
    RATE_LIMIT_EXCEEDED,
    
    // Administrative events
    CONFIG_CHANGED,
    FEE_POLICY_UPDATED,
    CHANNEL_POLICY_UPDATED,
    
    // Backup and recovery events
    BACKUP_CREATED,
    BACKUP_RESTORED,
    CHANNEL_BACKUP_UPLOADED,
    CHANNEL_BACKUP_DOWNLOADED,
    
    // Webhook events
    WEBHOOK_REGISTERED,
    WEBHOOK_DELETED,
    WEBHOOK_DELIVERY_SUCCESS,
    WEBHOOK_DELIVERY_FAILED,
    
    // Compliance events
    COMPLIANCE_CHECK_PERFORMED,
    COMPLIANCE_VIOLATION_DETECTED,
    AML_CHECK_PERFORMED,
    KYC_VERIFICATION_PERFORMED,
    
    // System events
    SYSTEM_STARTUP,
    SYSTEM_SHUTDOWN,
    MAINTENANCE_MODE_ENTERED,
    MAINTENANCE_MODE_EXITED,
    
    // Data events
    DATA_EXPORT_REQUESTED,
    DATA_EXPORT_COMPLETED,
    DATA_DELETION_REQUESTED,
    DATA_DELETION_COMPLETED,
    
    // Error events
    SYSTEM_ERROR,
    NETWORK_ERROR,
    DATABASE_ERROR,
    EXTERNAL_SERVICE_ERROR,
    
    // Custom events
    CUSTOM_EVENT
}