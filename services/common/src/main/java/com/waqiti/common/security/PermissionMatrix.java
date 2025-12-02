package com.waqiti.common.security;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Centralized permission matrix defining what each role can do.
 * This includes transaction limits, feature access, and operational permissions.
 */
@Component
@Slf4j
@Getter
public class PermissionMatrix {
    
    // Transaction Limits by Role (Daily)
    private static final Map<String, TransactionLimits> TRANSACTION_LIMITS = new HashMap<>();
    
    // Feature Permissions by Role
    private static final Map<String, Set<Permission>> ROLE_PERMISSIONS = new HashMap<>();
    
    static {
        initializeTransactionLimits();
        initializePermissions();
    }
    
    /**
     * Transaction limit configuration for each role
     */
    @Getter
    public static class TransactionLimits {
        private final BigDecimal dailyTransferLimit;
        private final BigDecimal singleTransferLimit;
        private final BigDecimal dailyWithdrawalLimit;
        private final BigDecimal singleWithdrawalLimit;
        private final BigDecimal dailyDepositLimit;
        private final BigDecimal monthlyLimit;
        private final int maxTransactionsPerDay;
        private final boolean requiresTwoFactorAbove;
        private final BigDecimal twoFactorThreshold;
        
        public TransactionLimits(
                BigDecimal dailyTransferLimit,
                BigDecimal singleTransferLimit,
                BigDecimal dailyWithdrawalLimit,
                BigDecimal singleWithdrawalLimit,
                BigDecimal dailyDepositLimit,
                BigDecimal monthlyLimit,
                int maxTransactionsPerDay,
                BigDecimal twoFactorThreshold) {
            this.dailyTransferLimit = dailyTransferLimit;
            this.singleTransferLimit = singleTransferLimit;
            this.dailyWithdrawalLimit = dailyWithdrawalLimit;
            this.singleWithdrawalLimit = singleWithdrawalLimit;
            this.dailyDepositLimit = dailyDepositLimit;
            this.monthlyLimit = monthlyLimit;
            this.maxTransactionsPerDay = maxTransactionsPerDay;
            this.requiresTwoFactorAbove = twoFactorThreshold != null;
            this.twoFactorThreshold = twoFactorThreshold;
        }
    }
    
    /**
     * Permission enumeration
     */
    public enum Permission {
        // Wallet Permissions
        CREATE_WALLET,
        VIEW_WALLET,
        TRANSFER_FUNDS,
        DEPOSIT_FUNDS,
        WITHDRAW_FUNDS,
        FREEZE_WALLET,
        CLOSE_WALLET,
        
        // Payment Permissions
        MAKE_PAYMENT,
        REQUEST_PAYMENT,
        SCHEDULE_PAYMENT,
        CANCEL_PAYMENT,
        REFUND_PAYMENT,
        
        // Account Permissions
        VIEW_ACCOUNT,
        MODIFY_ACCOUNT,
        CLOSE_ACCOUNT,
        VIEW_STATEMENTS,
        DOWNLOAD_STATEMENTS,
        
        // Card Permissions
        CREATE_VIRTUAL_CARD,
        CREATE_PHYSICAL_CARD,
        FREEZE_CARD,
        SET_CARD_LIMITS,
        VIEW_CARD_DETAILS,
        
        // International Permissions
        INTERNATIONAL_TRANSFER,
        CURRENCY_EXCHANGE,
        SWIFT_TRANSFER,
        
        // Business Permissions
        MANAGE_EMPLOYEES,
        BULK_PAYMENTS,
        PAYROLL_PROCESSING,
        INVOICE_MANAGEMENT,
        
        // Compliance Permissions
        VIEW_KYC_DATA,
        UPDATE_KYC_DATA,
        APPROVE_KYC,
        VIEW_COMPLIANCE_REPORTS,
        FILE_SAR,
        FILE_CTR,
        
        // Administrative Permissions
        VIEW_ALL_USERS,
        MODIFY_USER_ROLES,
        SYSTEM_CONFIGURATION,
        VIEW_AUDIT_LOGS,
        EXPORT_DATA,
        
        // Support Permissions
        VIEW_USER_DETAILS,
        RESET_USER_PASSWORD,
        UNLOCK_ACCOUNT,
        VIEW_TRANSACTION_HISTORY,
        REVERSE_TRANSACTION,
        
        // Risk Management
        OVERRIDE_RISK_RULES,
        APPROVE_HIGH_RISK_TRANSACTION,
        MODIFY_RISK_PARAMETERS,
        VIEW_FRAUD_ALERTS,
        
        // Reporting
        GENERATE_REPORTS,
        VIEW_ANALYTICS,
        EXPORT_REPORTS,
        VIEW_FINANCIAL_REPORTS
    }
    
    private static void initializeTransactionLimits() {
        // Basic User
        TRANSACTION_LIMITS.put(Roles.USER, new TransactionLimits(
            new BigDecimal("5000"),    // Daily transfer
            new BigDecimal("1000"),    // Single transfer
            new BigDecimal("2000"),    // Daily withdrawal
            new BigDecimal("500"),     // Single withdrawal
            new BigDecimal("10000"),   // Daily deposit
            new BigDecimal("50000"),   // Monthly limit
            50,                         // Max transactions per day
            new BigDecimal("500")      // 2FA threshold
        ));
        
        // Premium User
        TRANSACTION_LIMITS.put(Roles.PREMIUM_USER, new TransactionLimits(
            new BigDecimal("25000"),   // Daily transfer
            new BigDecimal("10000"),   // Single transfer
            new BigDecimal("10000"),   // Daily withdrawal
            new BigDecimal("5000"),    // Single withdrawal
            new BigDecimal("50000"),   // Daily deposit
            new BigDecimal("250000"),  // Monthly limit
            200,                        // Max transactions per day
            new BigDecimal("2000")     // 2FA threshold
        ));
        
        // VIP User
        TRANSACTION_LIMITS.put(Roles.VIP_USER, new TransactionLimits(
            new BigDecimal("100000"),  // Daily transfer
            new BigDecimal("50000"),   // Single transfer
            new BigDecimal("50000"),   // Daily withdrawal
            new BigDecimal("25000"),   // Single withdrawal
            new BigDecimal("500000"),  // Daily deposit
            new BigDecimal("2000000"), // Monthly limit
            500,                        // Max transactions per day
            new BigDecimal("10000")    // 2FA threshold
        ));
        
        // Merchant
        TRANSACTION_LIMITS.put(Roles.MERCHANT, new TransactionLimits(
            new BigDecimal("50000"),   // Daily transfer
            new BigDecimal("25000"),   // Single transfer
            new BigDecimal("25000"),   // Daily withdrawal
            new BigDecimal("10000"),   // Single withdrawal
            new BigDecimal("100000"),  // Daily deposit
            new BigDecimal("1000000"), // Monthly limit
            1000,                       // Max transactions per day
            new BigDecimal("5000")     // 2FA threshold
        ));
        
        // Business Owner
        TRANSACTION_LIMITS.put(Roles.BUSINESS_OWNER, new TransactionLimits(
            new BigDecimal("500000"),  // Daily transfer
            new BigDecimal("100000"),  // Single transfer
            new BigDecimal("100000"),  // Daily withdrawal
            new BigDecimal("50000"),   // Single withdrawal
            new BigDecimal("1000000"), // Daily deposit
            new BigDecimal("10000000"),// Monthly limit
            5000,                       // Max transactions per day
            new BigDecimal("25000")    // 2FA threshold
        ));
        
        // Family Admin
        TRANSACTION_LIMITS.put(Roles.FAMILY_ADMIN, new TransactionLimits(
            new BigDecimal("10000"),   // Daily transfer
            new BigDecimal("2000"),    // Single transfer
            new BigDecimal("5000"),    // Daily withdrawal
            new BigDecimal("1000"),    // Single withdrawal
            new BigDecimal("20000"),   // Daily deposit
            new BigDecimal("100000"),  // Monthly limit
            100,                        // Max transactions per day
            new BigDecimal("1000")     // 2FA threshold
        ));
        
        // Family Child (restricted)
        TRANSACTION_LIMITS.put(Roles.FAMILY_CHILD, new TransactionLimits(
            new BigDecimal("100"),     // Daily transfer
            new BigDecimal("50"),      // Single transfer
            new BigDecimal("50"),      // Daily withdrawal
            new BigDecimal("20"),      // Single withdrawal
            new BigDecimal("200"),     // Daily deposit
            new BigDecimal("1000"),    // Monthly limit
            10,                         // Max transactions per day
            new BigDecimal("50")       // 2FA threshold
        ));
        
        // Admin (no limits for operations)
        TRANSACTION_LIMITS.put(Roles.ADMIN, new TransactionLimits(
            new BigDecimal("999999999"),
            new BigDecimal("999999999"),
            new BigDecimal("999999999"),
            new BigDecimal("999999999"),
            new BigDecimal("999999999"),
            new BigDecimal("999999999"),
            99999,
            // BEST PRACTICE: Use BigDecimal.ZERO instead of new BigDecimal("0")
            BigDecimal.ZERO        // Always require 2FA
        ));
    }
    
    private static void initializePermissions() {
        // Basic User Permissions
        ROLE_PERMISSIONS.put(Roles.USER, EnumSet.of(
            Permission.CREATE_WALLET,
            Permission.VIEW_WALLET,
            Permission.TRANSFER_FUNDS,
            Permission.DEPOSIT_FUNDS,
            Permission.WITHDRAW_FUNDS,
            Permission.MAKE_PAYMENT,
            Permission.REQUEST_PAYMENT,
            Permission.VIEW_ACCOUNT,
            Permission.VIEW_STATEMENTS,
            Permission.CREATE_VIRTUAL_CARD
        ));
        
        // Premium User (includes all basic + additional)
        Set<Permission> premiumPermissions = EnumSet.copyOf(ROLE_PERMISSIONS.get(Roles.USER));
        premiumPermissions.addAll(EnumSet.of(
            Permission.SCHEDULE_PAYMENT,
            Permission.DOWNLOAD_STATEMENTS,
            Permission.CREATE_PHYSICAL_CARD,
            Permission.SET_CARD_LIMITS,
            Permission.CURRENCY_EXCHANGE,
            Permission.INTERNATIONAL_TRANSFER
        ));
        ROLE_PERMISSIONS.put(Roles.PREMIUM_USER, premiumPermissions);
        
        // VIP User (includes all premium + additional)
        Set<Permission> vipPermissions = EnumSet.copyOf(premiumPermissions);
        vipPermissions.addAll(EnumSet.of(
            Permission.SWIFT_TRANSFER,
            Permission.VIEW_FINANCIAL_REPORTS,
            Permission.EXPORT_REPORTS
        ));
        ROLE_PERMISSIONS.put(Roles.VIP_USER, vipPermissions);
        
        // Merchant Permissions
        ROLE_PERMISSIONS.put(Roles.MERCHANT, EnumSet.of(
            Permission.VIEW_WALLET,
            Permission.TRANSFER_FUNDS,
            Permission.DEPOSIT_FUNDS,
            Permission.WITHDRAW_FUNDS,
            Permission.MAKE_PAYMENT,
            Permission.REQUEST_PAYMENT,
            Permission.SCHEDULE_PAYMENT,
            Permission.REFUND_PAYMENT,
            Permission.VIEW_ACCOUNT,
            Permission.VIEW_STATEMENTS,
            Permission.DOWNLOAD_STATEMENTS,
            Permission.INVOICE_MANAGEMENT,
            Permission.VIEW_ANALYTICS,
            Permission.GENERATE_REPORTS
        ));
        
        // Business Owner
        Set<Permission> businessPermissions = EnumSet.copyOf(ROLE_PERMISSIONS.get(Roles.MERCHANT));
        businessPermissions.addAll(EnumSet.of(
            Permission.MANAGE_EMPLOYEES,
            Permission.BULK_PAYMENTS,
            Permission.PAYROLL_PROCESSING,
            Permission.INTERNATIONAL_TRANSFER,
            Permission.CURRENCY_EXCHANGE,
            Permission.SWIFT_TRANSFER,
            Permission.VIEW_FINANCIAL_REPORTS,
            Permission.EXPORT_REPORTS
        ));
        ROLE_PERMISSIONS.put(Roles.BUSINESS_OWNER, businessPermissions);
        
        // Compliance Officer
        ROLE_PERMISSIONS.put(Roles.COMPLIANCE_OFFICER, EnumSet.of(
            Permission.VIEW_KYC_DATA,
            Permission.UPDATE_KYC_DATA,
            Permission.APPROVE_KYC,
            Permission.VIEW_COMPLIANCE_REPORTS,
            Permission.FILE_SAR,
            Permission.FILE_CTR,
            Permission.VIEW_USER_DETAILS,
            Permission.VIEW_TRANSACTION_HISTORY,
            Permission.VIEW_FRAUD_ALERTS,
            Permission.GENERATE_REPORTS,
            Permission.VIEW_AUDIT_LOGS
        ));
        
        // Support Staff
        ROLE_PERMISSIONS.put(Roles.SUPPORT, EnumSet.of(
            Permission.VIEW_USER_DETAILS,
            Permission.VIEW_WALLET,
            Permission.VIEW_ACCOUNT,
            Permission.VIEW_TRANSACTION_HISTORY,
            Permission.RESET_USER_PASSWORD,
            Permission.UNLOCK_ACCOUNT,
            Permission.VIEW_STATEMENTS
        ));
        
        // Admin (all permissions)
        ROLE_PERMISSIONS.put(Roles.ADMIN, EnumSet.allOf(Permission.class));
        
        // System (all permissions)
        ROLE_PERMISSIONS.put(Roles.SYSTEM, EnumSet.allOf(Permission.class));
    }
    
    /**
     * Get transaction limits for a role
     */
    public TransactionLimits getTransactionLimits(String role) {
        return TRANSACTION_LIMITS.getOrDefault(role, 
            TRANSACTION_LIMITS.get(Roles.USER)); // Default to basic user limits
    }
    
    /**
     * Check if a role has a specific permission
     */
    public boolean hasPermission(String role, Permission permission) {
        Set<Permission> permissions = ROLE_PERMISSIONS.get(role);
        return permissions != null && permissions.contains(permission);
    }
    
    /**
     * Get all permissions for a role
     */
    public Set<Permission> getPermissions(String role) {
        return ROLE_PERMISSIONS.getOrDefault(role, EnumSet.noneOf(Permission.class));
    }
    
    /**
     * Check if amount requires two-factor authentication for a role
     */
    public boolean requiresTwoFactor(String role, BigDecimal amount) {
        TransactionLimits limits = getTransactionLimits(role);
        return limits.requiresTwoFactorAbove && 
               amount.compareTo(limits.twoFactorThreshold) > 0;
    }
    
    /**
     * Validate transaction against role limits
     */
    public boolean isWithinLimit(String role, String limitType, BigDecimal amount) {
        TransactionLimits limits = getTransactionLimits(role);
        
        switch (limitType) {
            case "SINGLE_TRANSFER":
                return amount.compareTo(limits.singleTransferLimit) <= 0;
            case "DAILY_TRANSFER":
                return amount.compareTo(limits.dailyTransferLimit) <= 0;
            case "SINGLE_WITHDRAWAL":
                return amount.compareTo(limits.singleWithdrawalLimit) <= 0;
            case "DAILY_WITHDRAWAL":
                return amount.compareTo(limits.dailyWithdrawalLimit) <= 0;
            case "DAILY_DEPOSIT":
                return amount.compareTo(limits.dailyDepositLimit) <= 0;
            case "MONTHLY":
                return amount.compareTo(limits.monthlyLimit) <= 0;
            default:
                return false;
        }
    }
}