package com.waqiti.common.kafka.dlq;

/**
 * DLQ Event Types
 *
 * Categorization of events for DLQ processing priority and routing.
 */
public enum DlqEventType {
    // Payment Events (CRITICAL - Revenue Impact)
    PAYMENT_AUTHORIZATION,
    PAYMENT_CAPTURE,
    PAYMENT_SETTLEMENT,
    PAYMENT_REFUND,
    PAYMENT_CHARGEBACK,
    PAYMENT_FAILED,

    // Compliance Events (CRITICAL - Regulatory Impact)
    AML_SCREENING,
    KYC_VERIFICATION,
    SAR_FILING,
    SANCTIONS_SCREENING,
    REGULATORY_REPORTING,
    COMPLIANCE_ALERT,

    // Fraud Events (CRITICAL - Security Impact)
    FRAUD_DETECTION,
    FRAUD_ALERT,
    SUSPICIOUS_ACTIVITY,
    ACCOUNT_TAKEOVER,

    // Transaction Events
    TRANSACTION_CREATED,
    TRANSACTION_UPDATED,
    TRANSACTION_RECONCILIATION,

    // Wallet Events
    WALLET_BALANCE_UPDATE,
    WALLET_FREEZE,
    WALLET_LIMIT_EXCEEDED,

    // User Events
    USER_REGISTRATION,
    USER_VERIFICATION,
    USER_STATUS_CHANGE,

    // Ledger Events
    LEDGER_ENTRY,
    LEDGER_RECONCILIATION,
    LEDGER_BALANCE_CHECK,

    // Loan Events
    LOAN_APPLICATION,
    LOAN_DISBURSEMENT,
    LOAN_REPAYMENT,
    LOAN_DEFAULT,

    // Card Events
    CARD_AUTHORIZATION,
    CARD_ACTIVATION,
    CARD_TRANSACTION,

    // Notification Events
    EMAIL_NOTIFICATION,
    SMS_NOTIFICATION,
    PUSH_NOTIFICATION,

    // Analytics Events
    ANALYTICS_EVENT,
    REPORTING_EVENT,

    // Settlement Events
    MERCHANT_SETTLEMENT,
    SETTLEMENT_DISCREPANCY,

    // Dispute Events
    DISPUTE_OPENED,
    CHARGEBACK_INITIATED,
    DISPUTE_RESOLUTION,

    // Generic/Other
    SYSTEM_EVENT,
    AUDIT_EVENT,
    OTHER
}
