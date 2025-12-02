package com.waqiti.common.security.rbac;

import java.util.Set;
import static com.waqiti.common.security.rbac.Permission.*;

/**
 * System-wide Roles
 *
 * Defines all roles available in the Waqiti platform.
 * Each role has a set of permissions.
 *
 * @author Waqiti Platform Engineering
 */
public enum Role {
    /**
     * Regular user with basic permissions
     */
    USER(Set.of(
        PAYMENT_READ, PAYMENT_WRITE, PAYMENT_CANCEL,
        WALLET_READ, WALLET_WRITE, WALLET_TRANSFER, WALLET_WITHDRAW,
        TRANSACTION_READ, TRANSACTION_EXPORT,
        ACCOUNT_READ, ACCOUNT_WRITE,
        PROFILE_READ, PROFILE_WRITE,
        USER_READ
    )),

    /**
     * Premium user with additional features
     */
    PREMIUM(Set.of(
        // Inherits all USER permissions
        PAYMENT_READ, PAYMENT_WRITE, PAYMENT_CANCEL,
        WALLET_READ, WALLET_WRITE, WALLET_TRANSFER, WALLET_WITHDRAW,
        TRANSACTION_READ, TRANSACTION_EXPORT,
        ACCOUNT_READ, ACCOUNT_WRITE,
        PROFILE_READ, PROFILE_WRITE,
        USER_READ,
        // Additional premium permissions
        INTERNATIONAL_TRANSFER, INTERNATIONAL_VIEW,
        CRYPTO_TRADE, CRYPTO_TRANSFER, CRYPTO_VIEW,
        INVESTMENT_TRADE, INVESTMENT_VIEW
    )),

    /**
     * Merchant with business features
     */
    MERCHANT(Set.of(
        // Basic user permissions
        PAYMENT_READ, PAYMENT_WRITE,
        WALLET_READ, WALLET_WRITE,
        TRANSACTION_READ, TRANSACTION_EXPORT,
        ACCOUNT_READ, ACCOUNT_WRITE,
        PROFILE_READ, PROFILE_WRITE,
        // Merchant specific
        MERCHANT_DASHBOARD, MERCHANT_ANALYTICS, MERCHANT_REFUND
    )),

    /**
     * Compliance officer with regulatory powers
     */
    COMPLIANCE_OFFICER(Set.of(
        // View permissions
        USER_READ, ACCOUNT_READ, TRANSACTION_READ,
        PAYMENT_READ, WALLET_READ,
        // Compliance permissions
        COMPLIANCE_READ, COMPLIANCE_WRITE,
        COMPLIANCE_SAR_FILE, COMPLIANCE_FREEZE_ACCOUNT,
        COMPLIANCE_ENHANCED_MONITORING,
        // Audit permissions
        AUDIT_READ, AUDIT_EXPORT
    )),

    /**
     * Fraud analyst with investigation powers
     */
    FRAUD_ANALYST(Set.of(
        // View permissions
        USER_READ, ACCOUNT_READ, TRANSACTION_READ,
        PAYMENT_READ, WALLET_READ,
        // Fraud permissions
        FRAUD_READ, FRAUD_WRITE,
        FRAUD_BLOCK_ACCOUNT, FRAUD_REVIEW_TRANSACTION,
        // Audit permissions
        AUDIT_READ
    )),

    /**
     * Customer support agent
     */
    SUPPORT(Set.of(
        // View permissions
        SUPPORT_VIEW_USER, SUPPORT_VIEW_TRANSACTION,
        // Ticket management
        SUPPORT_CREATE_TICKET, SUPPORT_RESOLVE_TICKET,
        // Limited user info
        USER_READ, ACCOUNT_READ, TRANSACTION_READ
    )),

    /**
     * System administrator with full access
     */
    ADMIN(Set.of(
        // Admin has all permissions
        ADMIN_ALL, ADMIN_USER_MANAGEMENT, ADMIN_SYSTEM_CONFIG, ADMIN_VIEW_LOGS,
        // Plus all other permissions for completeness
        PAYMENT_READ, PAYMENT_WRITE, PAYMENT_REFUND, PAYMENT_CANCEL,
        WALLET_READ, WALLET_WRITE, WALLET_TRANSFER, WALLET_WITHDRAW,
        USER_READ, USER_WRITE, USER_DELETE,
        TRANSACTION_READ, TRANSACTION_EXPORT,
        ACCOUNT_READ, ACCOUNT_WRITE, ACCOUNT_CLOSE,
        INTERNATIONAL_TRANSFER, INTERNATIONAL_VIEW,
        CRYPTO_TRADE, CRYPTO_TRANSFER, CRYPTO_VIEW,
        INVESTMENT_TRADE, INVESTMENT_VIEW,
        MERCHANT_DASHBOARD, MERCHANT_ANALYTICS, MERCHANT_REFUND,
        COMPLIANCE_READ, COMPLIANCE_WRITE, COMPLIANCE_SAR_FILE,
        COMPLIANCE_FREEZE_ACCOUNT, COMPLIANCE_ENHANCED_MONITORING,
        FRAUD_READ, FRAUD_WRITE, FRAUD_BLOCK_ACCOUNT, FRAUD_REVIEW_TRANSACTION,
        SUPPORT_VIEW_USER, SUPPORT_VIEW_TRANSACTION,
        SUPPORT_CREATE_TICKET, SUPPORT_RESOLVE_TICKET,
        AUDIT_READ, AUDIT_EXPORT,
        PROFILE_READ, PROFILE_WRITE
    ));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(Permission permission) {
        // ADMIN_ALL is wildcard permission
        if (permissions.contains(ADMIN_ALL)) {
            return true;
        }
        return permissions.contains(permission);
    }
}
