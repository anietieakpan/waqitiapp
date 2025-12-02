package com.waqiti.card.enums;

/**
 * CardAuditEventType - Types of auditable card events
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-19
 */
public enum CardAuditEventType {
    // Card Lifecycle Events
    CARD_CREATED,
    CARD_ACTIVATED,
    CARD_DEACTIVATED,
    CARD_BLOCKED,
    CARD_UNBLOCKED,
    CARD_SUSPENDED,
    CARD_CANCELLED,
    CARD_REPLACED,
    CARD_EXPIRED,

    // PIN Management Events
    PIN_CHANGED,
    PIN_RESET,
    PIN_VERIFICATION_FAILED,
    PIN_LOCKED,
    PIN_UNLOCKED,

    // Transaction Events
    TRANSACTION_AUTHORIZED,
    TRANSACTION_DECLINED,
    TRANSACTION_REVERSED,
    TRANSACTION_SETTLED,

    // Fraud Events
    FRAUD_ALERT,
    FRAUD_REVIEW,
    FRAUD_CONFIRMED,
    FRAUD_DISMISSED,

    // Card Data Access Events (PCI-DSS Requirement 10)
    CARD_DATA_ACCESSED,
    CARD_DATA_EXPORTED,
    CARD_DETAILS_VIEWED,
    CVV_VIEWED,

    // Limit Management Events
    LIMIT_CHANGED,
    LIMIT_INCREASED,
    LIMIT_DECREASED,
    LIMIT_SUSPENDED,

    // Security Events
    ACTIVATION_CODE_GENERATED,
    ACTIVATION_ATTEMPT_FAILED,
    CARD_TOKENIZED,
    CARD_DETOKENIZED,

    // Dispute Events
    DISPUTE_CREATED,
    DISPUTE_UPDATED,
    DISPUTE_RESOLVED,

    // System Events
    BATCH_PROCESSING,
    RECONCILIATION,
    SYSTEM_MAINTENANCE
}
