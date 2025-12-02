package com.waqiti.common.security.rbac;

/**
 * System-wide Permissions
 *
 * Defines all permissions available in the Waqiti platform.
 * Permissions are fine-grained access controls that can be assigned to roles.
 *
 * @author Waqiti Platform Engineering
 */
public enum Permission {
    // Payment Permissions
    PAYMENT_READ("payment:read", "View payment information"),
    PAYMENT_WRITE("payment:write", "Create and modify payments"),
    PAYMENT_REFUND("payment:refund", "Issue payment refunds"),
    PAYMENT_CANCEL("payment:cancel", "Cancel payments"),

    // Wallet Permissions
    WALLET_READ("wallet:read", "View wallet information"),
    WALLET_WRITE("wallet:write", "Modify wallet balance"),
    WALLET_TRANSFER("wallet:transfer", "Transfer funds between wallets"),
    WALLET_WITHDRAW("wallet:withdraw", "Withdraw funds from wallet"),

    // User Permissions
    USER_READ("user:read", "View user information"),
    USER_WRITE("user:write", "Create and modify users"),
    USER_DELETE("user:delete", "Delete users"),

    // Transaction Permissions
    TRANSACTION_READ("transaction:read", "View transaction history"),
    TRANSACTION_EXPORT("transaction:export", "Export transaction data"),

    // Account Permissions
    ACCOUNT_READ("account:read", "View account information"),
    ACCOUNT_WRITE("account:write", "Modify account settings"),
    ACCOUNT_CLOSE("account:close", "Close accounts"),

    // International Transfer Permissions
    INTERNATIONAL_TRANSFER("international:transfer", "Make international transfers"),
    INTERNATIONAL_VIEW("international:view", "View international transfer limits"),

    // Crypto Permissions
    CRYPTO_TRADE("crypto:trade", "Trade cryptocurrencies"),
    CRYPTO_TRANSFER("crypto:transfer", "Transfer crypto assets"),
    CRYPTO_VIEW("crypto:view", "View crypto portfolio"),

    // Investment Permissions
    INVESTMENT_TRADE("investment:trade", "Trade investments"),
    INVESTMENT_VIEW("investment:view", "View investment portfolio"),

    // Merchant Permissions
    MERCHANT_DASHBOARD("merchant:dashboard", "Access merchant dashboard"),
    MERCHANT_ANALYTICS("merchant:analytics", "View merchant analytics"),
    MERCHANT_REFUND("merchant:refund", "Issue merchant refunds"),

    // Compliance Permissions
    COMPLIANCE_READ("compliance:read", "View compliance data"),
    COMPLIANCE_WRITE("compliance:write", "Modify compliance settings"),
    COMPLIANCE_SAR_FILE("compliance:sar:file", "File Suspicious Activity Reports"),
    COMPLIANCE_FREEZE_ACCOUNT("compliance:account:freeze", "Freeze user accounts"),
    COMPLIANCE_ENHANCED_MONITORING("compliance:monitoring:enhanced", "Enable enhanced monitoring"),

    // Fraud Permissions
    FRAUD_READ("fraud:read", "View fraud alerts"),
    FRAUD_WRITE("fraud:write", "Manage fraud cases"),
    FRAUD_BLOCK_ACCOUNT("fraud:account:block", "Block accounts for fraud"),
    FRAUD_REVIEW_TRANSACTION("fraud:transaction:review", "Review flagged transactions"),

    // Support Permissions
    SUPPORT_VIEW_USER("support:user:view", "View user details"),
    SUPPORT_VIEW_TRANSACTION("support:transaction:view", "View transaction details"),
    SUPPORT_CREATE_TICKET("support:ticket:create", "Create support tickets"),
    SUPPORT_RESOLVE_TICKET("support:ticket:resolve", "Resolve support tickets"),

    // Admin Permissions
    ADMIN_ALL("admin:*", "Full administrative access"),
    ADMIN_USER_MANAGEMENT("admin:users", "Manage all users"),
    ADMIN_SYSTEM_CONFIG("admin:config", "Modify system configuration"),
    ADMIN_VIEW_LOGS("admin:logs", "View system logs"),

    // Audit Permissions
    AUDIT_READ("audit:read", "View audit logs"),
    AUDIT_EXPORT("audit:export", "Export audit data"),

    // Profile Permissions
    PROFILE_READ("profile:read", "View own profile"),
    PROFILE_WRITE("profile:write", "Modify own profile");

    private final String code;
    private final String description;

    Permission(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static Permission fromCode(String code) {
        for (Permission permission : values()) {
            if (permission.code.equals(code)) {
                return permission;
            }
        }
        throw new IllegalArgumentException("Unknown permission code: " + code);
    }
}
