package com.waqiti.transaction.domain;

/**
 * Transaction Event Type Enumeration
 * 
 * Defines all possible events that can occur during a transaction's lifecycle.
 * These events form an audit trail for each transaction.
 */
public enum TransactionEventType {
    
    // Lifecycle events
    CREATED,
    VALIDATION_STARTED,
    VALIDATION_PASSED,
    VALIDATION_FAILED,
    PROCESSING_STARTED,
    PROCESSING_COMPLETED,
    COMPLETED,
    FAILED,
    CANCELLED,
    REVERSED,
    EXPIRED,
    
    // State change events
    STATUS_CHANGED,
    AMOUNT_ADJUSTED,
    FEE_CALCULATED,
    TAX_CALCULATED,
    
    // Processing events
    DEBIT_INITIATED,
    DEBIT_COMPLETED,
    DEBIT_FAILED,
    CREDIT_INITIATED,
    CREDIT_COMPLETED,
    CREDIT_FAILED,
    
    // External system events
    SENT_TO_PROCESSOR,
    PROCESSOR_RESPONSE_RECEIVED,
    BANK_TRANSFER_INITIATED,
    BANK_CONFIRMATION_RECEIVED,
    
    // Error and retry events
    ERROR_OCCURRED,
    RETRY_SCHEDULED,
    RETRY_ATTEMPTED,
    MAX_RETRIES_REACHED,
    
    // Hold and review events
    PLACED_ON_HOLD,
    HOLD_RELEASED,
    MANUAL_REVIEW_REQUESTED,
    MANUAL_REVIEW_COMPLETED,
    
    // Security events
    FRAUD_CHECK_INITIATED,
    FRAUD_CHECK_PASSED,
    FRAUD_CHECK_FAILED,
    AML_CHECK_INITIATED,
    AML_CHECK_PASSED,
    AML_CHECK_FAILED,
    
    // Notification events
    NOTIFICATION_SENT,
    NOTIFICATION_FAILED,
    
    // System events
    SYSTEM_ERROR,
    TIMEOUT_OCCURRED,
    ROLLBACK_INITIATED,
    ROLLBACK_COMPLETED
}