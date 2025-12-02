package com.waqiti.common.kafka;

/**
 * Enterprise-Grade Kafka Topic Definitions
 *
 * Centralized topic taxonomy for the Waqiti fintech platform.
 * Follows event sourcing and CQRS patterns with complete lifecycle tracking.
 *
 * Naming Convention: {domain}.{entity}.{action}
 * Example: payment.transaction.initiated
 *
 * Topic Organization:
 * - Lifecycle Events: initiated, processing, completed, failed
 * - State Changes: created, updated, deleted, activated, deactivated
 * - Business Events: approved, rejected, cancelled, refunded
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-10-16
 */
public final class KafkaTopics {

    // ========================================================================
    // PAYMENT DOMAIN - Complete Lifecycle Events
    // ========================================================================

    /**
     * Payment Lifecycle Topics
     * Critical for: Ledger entries, reconciliation, fraud detection, notifications
     */
    public static final String PAYMENT_INITIATED = "payment.transaction.initiated";
    public static final String PAYMENT_PROCESSING = "payment.transaction.processing";
    public static final String PAYMENT_AUTHORIZED = "payment.transaction.authorized";
    public static final String PAYMENT_CAPTURED = "payment.transaction.captured";
    public static final String PAYMENT_COMPLETED = "payment.transaction.completed";
    public static final String PAYMENT_FAILED = "payment.transaction.failed";
    public static final String PAYMENT_CANCELLED = "payment.transaction.cancelled";
    public static final String PAYMENT_REFUNDED = "payment.transaction.refunded";
    public static final String PAYMENT_PARTIAL_REFUND = "payment.transaction.partial-refunded";

    /**
     * Payment Method Events
     */
    public static final String PAYMENT_METHOD_ADDED = "payment.method.added";
    public static final String PAYMENT_METHOD_VERIFIED = "payment.method.verified";
    public static final String PAYMENT_METHOD_REMOVED = "payment.method.removed";
    public static final String PAYMENT_METHOD_EXPIRED = "payment.method.expired";

    /**
     * 3DS Authentication Events
     */
    public static final String PAYMENT_3DS_INITIATED = "payment.3ds.initiated";
    public static final String PAYMENT_3DS_CHALLENGED = "payment.3ds.challenged";
    public static final String PAYMENT_3DS_COMPLETED = "payment.3ds.completed";
    public static final String PAYMENT_3DS_FAILED = "payment.3ds.failed";

    /**
     * Payment Tokenization Events
     */
    public static final String PAYMENT_TOKEN_CREATED = "payment.token.created";
    public static final String PAYMENT_TOKEN_USED = "payment.token.used";
    public static final String PAYMENT_TOKEN_REVOKED = "payment.token.revoked";

    /**
     * Legacy Topics (Maintain for backward compatibility)
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String PAYMENT_EVENTS = "payment-events";
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String PAYMENT_STATUS = "payment-status";
    public static final String PAYMENT_TRACKING = "payment-tracking";
    public static final String PAYMENT_FAILURES = "payment-failures";

    // ========================================================================
    // WALLET DOMAIN - Complete Lifecycle Events
    // ========================================================================

    /**
     * Wallet Lifecycle Topics
     * Critical for: Balance tracking, ledger entries, audit trails
     */
    public static final String WALLET_CREATED = "wallet.account.created";
    public static final String WALLET_ACTIVATED = "wallet.account.activated";
    public static final String WALLET_SUSPENDED = "wallet.account.suspended";
    public static final String WALLET_CLOSED = "wallet.account.closed";

    /**
     * Wallet Balance Operations
     * CRITICAL: Every balance change must be tracked for reconciliation
     */
    public static final String WALLET_FUNDED = "wallet.balance.funded";
    public static final String WALLET_DEBITED = "wallet.balance.debited";
    public static final String WALLET_CREDITED = "wallet.balance.credited";
    public static final String WALLET_BALANCE_ADJUSTED = "wallet.balance.adjusted";
    public static final String WALLET_BALANCE_LOCKED = "wallet.balance.locked";
    public static final String WALLET_BALANCE_UNLOCKED = "wallet.balance.unlocked";

    /**
     * Wallet Transfer Events
     * CRITICAL: P2P transfers, merchant payments
     */
    public static final String WALLET_TRANSFER_INITIATED = "wallet.transfer.initiated";
    public static final String WALLET_TRANSFER_PROCESSING = "wallet.transfer.processing";
    public static final String WALLET_TRANSFER_COMPLETED = "wallet.transfer.completed";
    public static final String WALLET_TRANSFER_FAILED = "wallet.transfer.failed";
    public static final String WALLET_TRANSFER_REVERSED = "wallet.transfer.reversed";

    /**
     * Wallet Limits and Restrictions
     */
    public static final String WALLET_LIMIT_UPDATED = "wallet.limit.updated";
    public static final String WALLET_LIMIT_EXCEEDED = "wallet.limit.exceeded";
    public static final String WALLET_FROZEN = "wallet.account.frozen";
    public static final String WALLET_UNFROZEN = "wallet.account.unfrozen";

    /**
     * Legacy Topics (Maintain for backward compatibility)
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String WALLET_EVENTS = "wallet-events";
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String WALLET_BALANCE_UPDATES = "wallet-balance-updates";

    // ========================================================================
    // TRANSACTION DOMAIN - Complete Lifecycle Events
    // ========================================================================

    /**
     * Transaction Lifecycle Topics
     * Critical for: Double-entry ledger, reconciliation, audit
     */
    public static final String TRANSACTION_CREATED = "transaction.entry.created";
    public static final String TRANSACTION_PENDING = "transaction.entry.pending";
    public static final String TRANSACTION_PROCESSING = "transaction.entry.processing";
    public static final String TRANSACTION_SETTLED = "transaction.entry.settled";
    public static final String TRANSACTION_COMPLETED = "transaction.entry.completed";
    public static final String TRANSACTION_FAILED = "transaction.entry.failed";
    public static final String TRANSACTION_ROLLED_BACK = "transaction.entry.rolled-back";

    /**
     * Transaction State Machine Events
     */
    public static final String TRANSACTION_STATE_CHANGED = "transaction.state.changed";
    public static final String TRANSACTION_TIMEOUT = "transaction.state.timeout";

    /**
     * Legacy Topics
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String TRANSACTION_EVENTS = "transaction-events";
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String TRANSACTION_STATUS = "transaction-status";
    public static final String TRANSACTION_ANALYTICS = "transaction-analytics";

    // ========================================================================
    // LEDGER DOMAIN - Double-Entry Accounting Events
    // ========================================================================

    /**
     * Ledger Entry Events
     * CRITICAL: Every financial operation must create ledger entries
     */
    public static final String LEDGER_ENTRY_CREATED = "ledger.entry.created";
    public static final String LEDGER_ENTRY_POSTED = "ledger.entry.posted";
    public static final String LEDGER_ENTRY_REVERSED = "ledger.entry.reversed";
    public static final String LEDGER_BALANCE_CALCULATED = "ledger.balance.calculated";

    /**
     * Reconciliation Events
     */
    public static final String RECONCILIATION_INITIATED = "ledger.reconciliation.initiated";
    public static final String RECONCILIATION_COMPLETED = "ledger.reconciliation.completed";
    public static final String RECONCILIATION_DISCREPANCY_FOUND = "ledger.reconciliation.discrepancy";

    // ========================================================================
    // FRAUD DETECTION DOMAIN
    // ========================================================================

    /**
     * Fraud Detection Lifecycle
     */
    public static final String FRAUD_CHECK_INITIATED = "fraud.check.initiated";
    public static final String FRAUD_RISK_SCORED = "fraud.risk.scored";
    public static final String FRAUD_DETECTED = "fraud.alert.detected";
    public static final String FRAUD_BLOCKED = "fraud.transaction.blocked";
    public static final String FRAUD_REVIEW_REQUIRED = "fraud.review.required";
    public static final String FRAUD_CASE_RESOLVED = "fraud.case.resolved";

    /**
     * Legacy Topics
     */
    public static final String FRAUD_ALERTS = "fraud-alerts";
    public static final String FRAUD_DETECTION_TRIGGER = "fraud-detection-trigger";
    public static final String FRAUD_DETECTION_RESULTS = "fraud-detection-results";

    // ========================================================================
    // COMPLIANCE DOMAIN - AML/KYC/SAR Events
    // ========================================================================

    /**
     * KYC Verification Events
     */
    public static final String KYC_INITIATED = "compliance.kyc.initiated";
    public static final String KYC_DOCUMENTS_UPLOADED = "compliance.kyc.documents-uploaded";
    public static final String KYC_VERIFIED = "compliance.kyc.verified";
    public static final String KYC_REJECTED = "compliance.kyc.rejected";
    public static final String KYC_EXPIRED = "compliance.kyc.expired";

    /**
     * AML Screening Events
     */
    public static final String AML_SCREENING_INITIATED = "compliance.aml.screening-initiated";
    public static final String AML_HIT_DETECTED = "compliance.aml.hit-detected";
    public static final String AML_CLEARED = "compliance.aml.cleared";

    /**
     * SAR (Suspicious Activity Report) Events
     */
    public static final String SAR_TRIGGERED = "compliance.sar.triggered";
    public static final String SAR_FILED = "compliance.sar.filed";
    public static final String SAR_ACKNOWLEDGED = "compliance.sar.acknowledged";

    /**
     * Sanctions Screening
     */
    public static final String SANCTIONS_CHECK_INITIATED = "compliance.sanctions.check-initiated";
    public static final String SANCTIONS_HIT_DETECTED = "compliance.sanctions.hit-detected";
    public static final String SANCTIONS_CLEARED = "compliance.sanctions.cleared";

    /**
     * Legacy Topics
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String COMPLIANCE_EVENTS = "compliance-events";
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String COMPLIANCE_ALERTS = "compliance-alerts";
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String AML_SCREENING = "aml-screening";
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String KYC_VERIFICATION = "kyc-verification";

    // ========================================================================
    // SECURITY DOMAIN
    // ========================================================================

    /**
     * Authentication Events
     */
    public static final String AUTH_LOGIN_SUCCESS = "security.auth.login-success";
    public static final String AUTH_LOGIN_FAILED = "security.auth.login-failed";
    public static final String AUTH_LOGOUT = "security.auth.logout";
    public static final String AUTH_MFA_REQUIRED = "security.auth.mfa-required";
    public static final String AUTH_MFA_VERIFIED = "security.auth.mfa-verified";
    public static final String AUTH_SESSION_EXPIRED = "security.auth.session-expired";

    /**
     * Security Alerts
     */
    public static final String SECURITY_ALERTS = "security-alerts";
    public static final String SQL_INJECTION_ALERTS = "sql-injection-alerts";
    public static final String SECURITY_BREACH_DETECTED = "security.breach.detected";
    public static final String SUSPICIOUS_ACTIVITY = "security.activity.suspicious";

    /**
     * Legacy Topics
     */
    public static final String AUTHENTICATION_EVENTS = "authentication-events";
    public static final String AUTHORIZATION_EVENTS = "authorization-events";

    // ========================================================================
    // AUDIT DOMAIN
    // ========================================================================

    /**
     * Audit Trail Events
     * CRITICAL: PCI DSS Requirement 10 - All user actions must be logged
     */
    public static final String AUDIT_USER_ACTION = "audit.user.action";
    public static final String AUDIT_ADMIN_ACTION = "audit.admin.action";
    public static final String AUDIT_SYSTEM_EVENT = "audit.system.event";
    public static final String AUDIT_DATA_ACCESS = "audit.data.access";
    public static final String AUDIT_DATA_MODIFICATION = "audit.data.modification";

    /**
     * Legacy Topics
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String AUDIT_EVENTS = "audit-events";
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String AUDIT_LOGS = "audit-logs";

    // ========================================================================
    // NOTIFICATION DOMAIN
    // ========================================================================

    /**
     * Notification Events
     */
    public static final String NOTIFICATION_EMAIL_QUEUED = "notification.email.queued";
    public static final String NOTIFICATION_SMS_QUEUED = "notification.sms.queued";
    public static final String NOTIFICATION_PUSH_QUEUED = "notification.push.queued";
    public static final String NOTIFICATION_SENT = "notification.message.sent";
    public static final String NOTIFICATION_FAILED = "notification.message.failed";

    /**
     * Legacy Topics
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String NOTIFICATION_EVENTS = "notification-events";
    public static final String EMAIL_NOTIFICATIONS = "email-notifications";
    public static final String SMS_NOTIFICATIONS = "sms-notifications";
    public static final String PUSH_NOTIFICATIONS = "push-notifications";

    // ========================================================================
    // USER DOMAIN
    // ========================================================================

    /**
     * User Lifecycle Events
     */
    public static final String USER_REGISTERED = "user.account.registered";
    public static final String USER_VERIFIED = "user.account.verified";
    public static final String USER_PROFILE_UPDATED = "user.profile.updated";
    public static final String USER_DEACTIVATED = "user.account.deactivated";
    public static final String USER_DELETED = "user.account.deleted";

    /**
     * Legacy Topics
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String USER_EVENTS = "user-events";
    public static final String USER_ACTIVITY = "user-activity";
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String USER_PROFILE_UPDATES = "user-profile-updates";

    // ========================================================================
    // ANALYTICS DOMAIN
    // ========================================================================

    /**
     * Real-time Analytics Events
     */
    public static final String ANALYTICS_TRANSACTION_RECORDED = "analytics.transaction.recorded";
    public static final String ANALYTICS_USER_BEHAVIOR = "analytics.user.behavior";
    public static final String ANALYTICS_PERFORMANCE_METRIC = "analytics.performance.metric";

    /**
     * Legacy Topics
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String ANALYTICS_EVENTS = "analytics-events";
    public static final String REAL_TIME_METRICS = "real-time-metrics";

    // ========================================================================
    // INVESTMENT DOMAIN
    // ========================================================================

    /**
     * Investment Events
     */
    public static final String INVESTMENT_ORDER_PLACED = "investment.order.placed";
    public static final String INVESTMENT_ORDER_EXECUTED = "investment.order.executed";
    public static final String INVESTMENT_ORDER_CANCELLED = "investment.order.cancelled";

    /**
     * Legacy Topics
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String INVESTMENT_EVENTS = "investment-events";
    public static final String PORTFOLIO_UPDATES = "portfolio-updates";
    public static final String MARKET_DATA_EVENTS = "market-data-events";

    // ========================================================================
    // BANKING INTEGRATION DOMAIN
    // ========================================================================

    /**
     * Bank Integration Events
     */
    public static final String BANK_ACCOUNT_LINKED = "bank.account.linked";
    public static final String BANK_ACCOUNT_VERIFIED = "bank.account.verified";
    public static final String BANK_TRANSFER_INITIATED = "bank.transfer.initiated";
    public static final String BANK_TRANSFER_COMPLETED = "bank.transfer.completed";
    public static final String BANK_TRANSFER_FAILED = "bank.transfer.failed";

    /**
     * Legacy Topics
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String BANK_INTEGRATION_EVENTS = "bank-integration-events";
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String BANK_TRANSFER_STATUS = "bank-transfer-status";

    // ========================================================================
    // SYSTEM DOMAIN
    // ========================================================================

    /**
     * System Events
     */
    public static final String SYSTEM_HEALTH_CHECK = "system.health.check";
    public static final String SYSTEM_ERROR = "system.error.occurred";

    /**
     * Security Topics
     * CRITICAL: Security incidents, alerts, and forensic data
     */
    public static final String SECURITY_INCIDENTS = "security.incidents";
    public static final String CRITICAL_SECURITY_ALERTS = "security.alerts.critical";
    public static final String EXECUTIVE_ALERTS = "security.alerts.executive";
    public static final String USER_SECURITY_ACTIONS = "security.user.actions";
    public static final String EMERGENCY_RESPONSE = "security.emergency.response";
    public static final String SECURITY_TEAM_ALERTS = "security.team.alerts";
    public static final String RATE_LIMITING_EVENTS = "security.rate-limiting.events";
    public static final String FORENSIC_CAPTURE = "security.forensic.capture";

    /**
     * Dead Letter Queue Topics
     * CRITICAL: All failed messages go here for manual review
     */
    public static final String DLQ_PAYMENT = "dlq.payment";
    public static final String DLQ_WALLET = "dlq.wallet";
    public static final String DLQ_TRANSACTION = "dlq.transaction";
    public static final String DLQ_FRAUD = "dlq.fraud";
    public static final String DLQ_COMPLIANCE = "dlq.compliance";
    public static final String DLQ_NOTIFICATION = "dlq.notification";

    /**
     * Legacy Topics
     */
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String SYSTEM_EVENTS = "system-events";
    public static final String ERROR_EVENTS = "error-events";
    @Deprecated(since = "2.0.0", forRemoval = false)
    public static final String DEAD_LETTER_QUEUE = "dead-letter-queue";

    // ========================================================================
    // PRIVATE CONSTRUCTOR
    // ========================================================================

    /**
     * Private constructor to prevent instantiation
     * This is a utility class and should only be used for constant access
     */
    private KafkaTopics() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}