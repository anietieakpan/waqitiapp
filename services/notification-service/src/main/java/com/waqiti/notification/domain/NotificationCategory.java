package com.waqiti.notification.domain;

/**
 * Comprehensive notification categories for the Waqiti financial platform
 */
public enum NotificationCategory {
    // Transaction-related notifications
    PAYMENT_RECEIVED("payment_received", "Payment Received", "Notifications when you receive money", true),
    PAYMENT_SENT("payment_sent", "Payment Sent", "Confirmations when you send money", true),
    PAYMENT_FAILED("payment_failed", "Payment Failed", "Notifications when payments fail", true),
    PAYMENT_PENDING("payment_pending", "Payment Pending", "Updates on pending payments", true),
    PAYMENT_CANCELLED("payment_cancelled", "Payment Cancelled", "Notifications when payments are cancelled", true),
    
    // Money request notifications
    MONEY_REQUEST_RECEIVED("money_request_received", "Money Request Received", "When someone requests money from you", true),
    MONEY_REQUEST_SENT("money_request_sent", "Money Request Sent", "Confirmations when you request money", true),
    MONEY_REQUEST_ACCEPTED("money_request_accepted", "Money Request Accepted", "When someone accepts your money request", true),
    MONEY_REQUEST_DECLINED("money_request_declined", "Money Request Declined", "When someone declines your money request", true),
    MONEY_REQUEST_EXPIRED("money_request_expired", "Money Request Expired", "When money requests expire", true),
    
    // Account and security notifications
    ACCOUNT_VERIFICATION("account_verification", "Account Verification", "Account verification status updates", true),
    ACCOUNT_LOCKED("account_locked", "Account Locked", "When your account is locked", true),
    ACCOUNT_UNLOCKED("account_unlocked", "Account Unlocked", "When your account is unlocked", true),
    ACCOUNT_SUSPENDED("account_suspended", "Account Suspended", "When your account is suspended", true),
    PASSWORD_CHANGED("password_changed", "Password Changed", "When your password is changed", true),
    LOGIN_ATTEMPT("login_attempt", "Login Attempt", "Suspicious login attempts", true),
    NEW_DEVICE_LOGIN("new_device_login", "New Device Login", "Logins from new devices", true),
    SECURITY_ALERT("security_alert", "Security Alert", "Important security notifications", true),
    TWO_FACTOR_ENABLED("two_factor_enabled", "Two-Factor Enabled", "When 2FA is enabled", true),
    TWO_FACTOR_DISABLED("two_factor_disabled", "Two-Factor Disabled", "When 2FA is disabled", true),
    
    // Card-related notifications
    CARD_TRANSACTION("card_transaction", "Card Transaction", "Virtual card transaction notifications", true),
    CARD_BLOCKED("card_blocked", "Card Blocked", "When your card is blocked", true),
    CARD_UNBLOCKED("card_unblocked", "Card Unblocked", "When your card is unblocked", true),
    CARD_EXPIRED("card_expired", "Card Expired", "When your card expires", true),
    CARD_CREATED("card_created", "Card Created", "When a new card is created", true),
    CARD_DELETED("card_deleted", "Card Deleted", "When a card is deleted", true),
    
    // Balance and limits
    LOW_BALANCE("low_balance", "Low Balance", "When your balance is low", true),
    HIGH_BALANCE("high_balance", "High Balance", "When your balance exceeds a threshold", false),
    SPENDING_LIMIT_REACHED("spending_limit_reached", "Spending Limit Reached", "When you reach spending limits", true),
    DAILY_LIMIT_REACHED("daily_limit_reached", "Daily Limit Reached", "When you reach daily transaction limits", true),
    MONTHLY_LIMIT_REACHED("monthly_limit_reached", "Monthly Limit Reached", "When you reach monthly limits", true),
    
    // Social and contacts
    CONTACT_REQUEST("contact_request", "Contact Request", "When someone wants to add you as a contact", true),
    CONTACT_ACCEPTED("contact_accepted", "Contact Accepted", "When someone accepts your contact request", true),
    FRIEND_ACTIVITY("friend_activity", "Friend Activity", "Updates from your contacts", false),
    SOCIAL_PAYMENT("social_payment", "Social Payment", "Social payment notifications", true),
    
    // Investment and savings (for future features)
    INVESTMENT_UPDATE("investment_update", "Investment Update", "Updates on your investments", false),
    SAVINGS_GOAL_REACHED("savings_goal_reached", "Savings Goal Reached", "When you reach savings goals", true),
    SAVINGS_REMINDER("savings_reminder", "Savings Reminder", "Reminders to save money", false),
    INVESTMENT_OPPORTUNITY("investment_opportunity", "Investment Opportunity", "New investment opportunities", false),
    
    // BNPL (Buy Now Pay Later)
    BNPL_PAYMENT_DUE("bnpl_payment_due", "BNPL Payment Due", "When BNPL payments are due", true),
    BNPL_PAYMENT_OVERDUE("bnpl_payment_overdue", "BNPL Payment Overdue", "When BNPL payments are overdue", true),
    BNPL_APPROVED("bnpl_approved", "BNPL Approved", "When BNPL applications are approved", true),
    BNPL_DECLINED("bnpl_declined", "BNPL Declined", "When BNPL applications are declined", true),
    
    // Merchant and business
    MERCHANT_PAYMENT("merchant_payment", "Merchant Payment", "Payments from merchants", true),
    MERCHANT_REFUND("merchant_refund", "Merchant Refund", "Refunds from merchants", true),
    BUSINESS_VERIFICATION("business_verification", "Business Verification", "Business account verification", true),
    
    // International transfers
    INTERNATIONAL_TRANSFER_SENT("international_transfer_sent", "International Transfer Sent", "International transfer confirmations", true),
    INTERNATIONAL_TRANSFER_RECEIVED("international_transfer_received", "International Transfer Received", "International transfer receipts", true),
    EXCHANGE_RATE_ALERT("exchange_rate_alert", "Exchange Rate Alert", "Favorable exchange rate notifications", false),
    
    // Recurring payments
    RECURRING_PAYMENT_EXECUTED("recurring_payment_executed", "Recurring Payment Executed", "When recurring payments are processed", true),
    RECURRING_PAYMENT_FAILED("recurring_payment_failed", "Recurring Payment Failed", "When recurring payments fail", true),
    RECURRING_PAYMENT_CANCELLED("recurring_payment_cancelled", "Recurring Payment Cancelled", "When recurring payments are cancelled", true),
    
    // Compliance and regulatory
    KYC_REQUIRED("kyc_required", "KYC Required", "When KYC verification is required", true),
    KYC_APPROVED("kyc_approved", "KYC Approved", "When KYC is approved", true),
    KYC_REJECTED("kyc_rejected", "KYC Rejected", "When KYC is rejected", true),
    COMPLIANCE_UPDATE("compliance_update", "Compliance Update", "Regulatory compliance updates", true),
    TAX_DOCUMENT_READY("tax_document_ready", "Tax Document Ready", "When tax documents are available", true),
    
    // System and maintenance
    SYSTEM_MAINTENANCE("system_maintenance", "System Maintenance", "Scheduled maintenance notifications", true),
    SYSTEM_UPDATE("system_update", "System Update", "System and feature updates", false),
    SERVICE_DISRUPTION("service_disruption", "Service Disruption", "Service outage notifications", true),
    FEATURE_ANNOUNCEMENT("feature_announcement", "Feature Announcement", "New feature announcements", false),
    
    // Marketing and promotional
    PROMOTIONAL_OFFER("promotional_offer", "Promotional Offer", "Special offers and promotions", false),
    CASHBACK_EARNED("cashback_earned", "Cashback Earned", "When you earn cashback", true),
    REWARDS_EARNED("rewards_earned", "Rewards Earned", "When you earn rewards", true),
    LOYALTY_UPDATE("loyalty_update", "Loyalty Update", "Loyalty program updates", false),
    REFERRAL_BONUS("referral_bonus", "Referral Bonus", "When you earn referral bonuses", true),
    
    // Customer support
    SUPPORT_TICKET_CREATED("support_ticket_created", "Support Ticket Created", "When support tickets are created", true),
    SUPPORT_TICKET_UPDATED("support_ticket_updated", "Support Ticket Updated", "When support tickets are updated", true),
    SUPPORT_TICKET_RESOLVED("support_ticket_resolved", "Support Ticket Resolved", "When support tickets are resolved", true),
    
    // Gamification and achievements
    ACHIEVEMENT_UNLOCKED("achievement_unlocked", "Achievement Unlocked", "When you unlock achievements", false),
    LEVEL_UP("level_up", "Level Up", "When you level up in gamification", false),
    CHALLENGE_COMPLETED("challenge_completed", "Challenge Completed", "When you complete challenges", false),
    
    // Legacy categories for backward compatibility
    ACCOUNT("account", "Account", "Account-related notifications", true),
    TRANSACTION("transaction", "Transaction", "Transaction-related notifications", true),
    PAYMENT_REQUEST("payment_request", "Payment Request", "Payment request notifications", true),
    SCHEDULED_PAYMENT("scheduled_payment", "Scheduled Payment", "Scheduled payment notifications", true),
    SPLIT_PAYMENT("split_payment", "Split Payment", "Split payment notifications", true),
    SECURITY("security", "Security", "Security-related notifications", true),
    
    // Generic categories
    GENERAL("general", "General", "General notifications", true),
    IMPORTANT("important", "Important", "Important notifications that cannot be disabled", true),
    MARKETING("marketing", "Marketing", "Marketing and promotional content", false);

    private final String code;
    private final String displayName;
    private final String description;
    private final boolean defaultEnabled;

    NotificationCategory(String code, String displayName, String description, boolean defaultEnabled) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.defaultEnabled = defaultEnabled;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    /**
     * Get category by code
     */
    public static NotificationCategory fromCode(String code) {
        for (NotificationCategory category : values()) {
            if (category.getCode().equals(code)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown notification category code: " + code);
    }

    /**
     * Check if this is a security-related category that cannot be disabled
     */
    public boolean isSecurityCategory() {
        return this == SECURITY_ALERT || 
               this == LOGIN_ATTEMPT || 
               this == NEW_DEVICE_LOGIN || 
               this == ACCOUNT_LOCKED || 
               this == ACCOUNT_SUSPENDED || 
               this == PASSWORD_CHANGED ||
               this == IMPORTANT ||
               this == SECURITY;
    }

    /**
     * Check if this is a payment-related category
     */
    public boolean isPaymentCategory() {
        return this == PAYMENT_RECEIVED || 
               this == PAYMENT_SENT || 
               this == PAYMENT_FAILED || 
               this == PAYMENT_PENDING || 
               this == PAYMENT_CANCELLED ||
               this == MONEY_REQUEST_RECEIVED ||
               this == MONEY_REQUEST_SENT ||
               this == MONEY_REQUEST_ACCEPTED ||
               this == MONEY_REQUEST_DECLINED ||
               this == TRANSACTION ||
               this == PAYMENT_REQUEST;
    }

    /**
     * Check if this is a marketing-related category
     */
    public boolean isMarketingCategory() {
        return this == PROMOTIONAL_OFFER || 
               this == LOYALTY_UPDATE || 
               this == MARKETING ||
               this == FEATURE_ANNOUNCEMENT ||
               this == INVESTMENT_OPPORTUNITY;
    }

    /**
     * Get the priority level for this category
     */
    public NotificationPriority getDefaultPriority() {
        if (isSecurityCategory()) {
            return NotificationPriority.HIGH;
        } else if (isPaymentCategory()) {
            return NotificationPriority.HIGH;
        } else if (isMarketingCategory()) {
            return NotificationPriority.LOW;
        } else {
            return NotificationPriority.MEDIUM;
        }
    }
}
