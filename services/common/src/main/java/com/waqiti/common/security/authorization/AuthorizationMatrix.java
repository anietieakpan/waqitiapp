package com.waqiti.common.security.authorization;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * WAQITI AUTHORIZATION MATRIX
 *
 * Comprehensive role-based access control (RBAC) and permission matrix
 * for all financial operations across the Waqiti platform.
 *
 * SECURITY CRITICAL: This matrix defines WHO can do WHAT in the system.
 * Changes to this matrix must be reviewed by Security and Compliance teams.
 *
 * COMPLIANCE: Aligns with PCI DSS, SOX, and regulatory requirements for
 * separation of duties and least privilege access.
 *
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2025-01-01
 */
public class AuthorizationMatrix {

    /**
     * WAQITI ROLES
     * Hierarchical role structure with inheritance
     */
    @Getter
    @RequiredArgsConstructor
    public enum Role {
        // CUSTOMER ROLES
        CUSTOMER("Customer", RoleType.CUSTOMER, Set.of()),
        CUSTOMER_VERIFIED("Verified Customer", RoleType.CUSTOMER, Set.of(CUSTOMER)),
        CUSTOMER_PREMIUM("Premium Customer", RoleType.CUSTOMER, Set.of(CUSTOMER_VERIFIED)),

        // MERCHANT ROLES
        MERCHANT("Merchant", RoleType.MERCHANT, Set.of()),
        MERCHANT_VERIFIED("Verified Merchant", RoleType.MERCHANT, Set.of(MERCHANT)),
        MERCHANT_ENTERPRISE("Enterprise Merchant", RoleType.MERCHANT, Set.of(MERCHANT_VERIFIED)),

        // BUSINESS ROLES
        BUSINESS_USER("Business User", RoleType.BUSINESS, Set.of()),
        BUSINESS_ADMIN("Business Admin", RoleType.BUSINESS, Set.of(BUSINESS_USER)),

        // ADMIN ROLES
        SUPPORT_AGENT("Support Agent", RoleType.ADMIN, Set.of()),
        OPERATIONS_MANAGER("Operations Manager", RoleType.ADMIN, Set.of(SUPPORT_AGENT)),
        COMPLIANCE_OFFICER("Compliance Officer", RoleType.ADMIN, Set.of()),
        FINANCIAL_AUDITOR("Financial Auditor", RoleType.ADMIN, Set.of()),
        SYSTEM_ADMIN("System Admin", RoleType.ADMIN, Set.of(OPERATIONS_MANAGER)),
        SUPER_ADMIN("Super Admin", RoleType.ADMIN, Set.of(SYSTEM_ADMIN, COMPLIANCE_OFFICER));

        private final String displayName;
        private final RoleType type;
        private final Set<Role> inheritsFrom;

        public boolean inherits(Role role) {
            if (this == role) return true;
            return inheritsFrom.stream().anyMatch(r -> r.inherits(role));
        }
    }

    public enum RoleType {
        CUSTOMER, MERCHANT, BUSINESS, ADMIN
    }

    /**
     * WAQITI PERMISSIONS
     * Granular permissions for specific operations
     */
    @Getter
    @RequiredArgsConstructor
    public enum Permission {
        // WALLET PERMISSIONS
        WALLET_READ("wallet:read", "View wallet details and balance", PermissionCategory.WALLET),
        WALLET_CREATE("wallet:create", "Create new wallet", PermissionCategory.WALLET),
        WALLET_UPDATE("wallet:update", "Update wallet information", PermissionCategory.WALLET),
        WALLET_DELETE("wallet:delete", "Delete wallet", PermissionCategory.WALLET),
        WALLET_CREDIT("wallet:credit", "Add funds to wallet", PermissionCategory.WALLET),
        WALLET_DEBIT("wallet:debit", "Withdraw funds from wallet", PermissionCategory.WALLET),
        WALLET_FREEZE("wallet:freeze", "Freeze wallet (admin)", PermissionCategory.WALLET),
        WALLET_UNFREEZE("wallet:unfreeze", "Unfreeze wallet (admin)", PermissionCategory.WALLET),

        // PAYMENT PERMISSIONS
        PAYMENT_INITIATE("payment:initiate", "Initiate payment transaction", PermissionCategory.PAYMENT),
        PAYMENT_APPROVE("payment:approve", "Approve pending payment", PermissionCategory.PAYMENT),
        PAYMENT_CANCEL("payment:cancel", "Cancel pending payment", PermissionCategory.PAYMENT),
        PAYMENT_REFUND("payment:refund", "Process refund", PermissionCategory.PAYMENT),
        PAYMENT_REFUND_APPROVE("payment:refund:approve", "Approve refund request", PermissionCategory.PAYMENT),
        PAYMENT_CHARGEBACK("payment:chargeback", "Handle chargeback", PermissionCategory.PAYMENT),
        PAYMENT_VIEW_OWN("payment:view:own", "View own payment history", PermissionCategory.PAYMENT),
        PAYMENT_VIEW_ALL("payment:view:all", "View all payments (admin)", PermissionCategory.PAYMENT),

        // TRANSFER PERMISSIONS
        TRANSFER_P2P("transfer:p2p", "Peer-to-peer transfer", PermissionCategory.TRANSFER),
        TRANSFER_INTERNATIONAL("transfer:international", "International transfer", PermissionCategory.TRANSFER),
        TRANSFER_WIRE("transfer:wire", "Wire transfer", PermissionCategory.TRANSFER),
        TRANSFER_ACH("transfer:ach", "ACH transfer", PermissionCategory.TRANSFER),
        TRANSFER_BULK("transfer:bulk", "Bulk transfer", PermissionCategory.TRANSFER),

        // INVESTMENT PERMISSIONS
        INVESTMENT_VIEW("investment:view", "View investment portfolio", PermissionCategory.INVESTMENT),
        INVESTMENT_TRADE("investment:trade", "Execute trades", PermissionCategory.INVESTMENT),
        INVESTMENT_AUTOINVEST("investment:autoinvest", "Configure auto-invest", PermissionCategory.INVESTMENT),
        INVESTMENT_WITHDRAW("investment:withdraw", "Withdraw from investments", PermissionCategory.INVESTMENT),

        // CARD PERMISSIONS
        CARD_VIEW("card:view", "View card details", PermissionCategory.CARD),
        CARD_CREATE("card:create", "Create virtual/physical card", PermissionCategory.CARD),
        CARD_ACTIVATE("card:activate", "Activate card", PermissionCategory.CARD),
        CARD_FREEZE("card:freeze", "Freeze card", PermissionCategory.CARD),
        CARD_UNFREEZE("card:unfreeze", "Unfreeze card", PermissionCategory.CARD),
        CARD_CLOSE("card:close", "Close card", PermissionCategory.CARD),
        CARD_SET_PIN("card:set_pin", "Set/change card PIN", PermissionCategory.CARD),

        // MERCHANT PERMISSIONS
        MERCHANT_PAYMENT_ACCEPT("merchant:payment:accept", "Accept merchant payments", PermissionCategory.MERCHANT),
        MERCHANT_REFUND_ISSUE("merchant:refund:issue", "Issue merchant refund", PermissionCategory.MERCHANT),
        MERCHANT_SETTLEMENT_VIEW("merchant:settlement:view", "View settlement reports", PermissionCategory.MERCHANT),
        MERCHANT_ANALYTICS_VIEW("merchant:analytics:view", "View merchant analytics", PermissionCategory.MERCHANT),

        // LOAN PERMISSIONS
        LOAN_APPLY("loan:apply", "Apply for loan", PermissionCategory.LOAN),
        LOAN_VIEW("loan:view", "View loan details", PermissionCategory.LOAN),
        LOAN_REPAY("loan:repay", "Make loan payment", PermissionCategory.LOAN),
        LOAN_APPROVE("loan:approve", "Approve loan application", PermissionCategory.LOAN),
        LOAN_REJECT("loan:reject", "Reject loan application", PermissionCategory.LOAN),

        // COMPLIANCE PERMISSIONS
        COMPLIANCE_SAR_VIEW("compliance:sar:view", "View SARs", PermissionCategory.COMPLIANCE),
        COMPLIANCE_SAR_FILE("compliance:sar:file", "File SAR with FinCEN", PermissionCategory.COMPLIANCE),
        COMPLIANCE_KYC_VIEW("compliance:kyc:view", "View KYC documents", PermissionCategory.COMPLIANCE),
        COMPLIANCE_KYC_APPROVE("compliance:kyc:approve", "Approve KYC", PermissionCategory.COMPLIANCE),
        COMPLIANCE_SANCTIONS_SCREEN("compliance:sanctions:screen", "Screen against sanctions", PermissionCategory.COMPLIANCE),
        COMPLIANCE_RISK_PROFILE("compliance:risk:profile", "View customer risk profile", PermissionCategory.COMPLIANCE),

        // AUDIT PERMISSIONS
        AUDIT_VIEW("audit:view", "View audit logs", PermissionCategory.AUDIT),
        AUDIT_EXPORT("audit:export", "Export audit logs", PermissionCategory.AUDIT),

        // ADMIN PERMISSIONS
        ADMIN_USER_VIEW("admin:user:view", "View user details", PermissionCategory.ADMIN),
        ADMIN_USER_UPDATE("admin:user:update", "Update user details", PermissionCategory.ADMIN),
        ADMIN_USER_SUSPEND("admin:user:suspend", "Suspend user account", PermissionCategory.ADMIN),
        ADMIN_USER_RESTORE("admin:user:restore", "Restore user account", PermissionCategory.ADMIN),
        ADMIN_TRANSACTION_OVERRIDE("admin:transaction:override", "Override transaction limits", PermissionCategory.ADMIN),
        ADMIN_SYSTEM_CONFIG("admin:system:config", "Modify system configuration", PermissionCategory.ADMIN);

        private final String code;
        private final String description;
        private final PermissionCategory category;
    }

    public enum PermissionCategory {
        WALLET, PAYMENT, TRANSFER, INVESTMENT, CARD, MERCHANT, LOAN, COMPLIANCE, AUDIT, ADMIN
    }

    /**
     * ROLE-PERMISSION MAPPING
     * Defines which roles have which permissions
     */
    private static final Map<Role, Set<Permission>> ROLE_PERMISSIONS = new HashMap<>();

    static {
        // CUSTOMER PERMISSIONS
        ROLE_PERMISSIONS.put(Role.CUSTOMER, Set.of(
            Permission.WALLET_READ,
            Permission.WALLET_CREATE,
            Permission.PAYMENT_INITIATE,
            Permission.PAYMENT_VIEW_OWN,
            Permission.PAYMENT_CANCEL,
            Permission.TRANSFER_P2P,
            Permission.CARD_VIEW,
            Permission.CARD_CREATE,
            Permission.CARD_FREEZE,
            Permission.CARD_SET_PIN
        ));

        ROLE_PERMISSIONS.put(Role.CUSTOMER_VERIFIED, Set.of(
            Permission.WALLET_CREDIT,
            Permission.WALLET_DEBIT,
            Permission.TRANSFER_ACH,
            Permission.TRANSFER_INTERNATIONAL,
            Permission.CARD_ACTIVATE,
            Permission.INVESTMENT_VIEW,
            Permission.INVESTMENT_TRADE,
            Permission.LOAN_APPLY,
            Permission.LOAN_VIEW,
            Permission.LOAN_REPAY
        ));

        ROLE_PERMISSIONS.put(Role.CUSTOMER_PREMIUM, Set.of(
            Permission.TRANSFER_WIRE,
            Permission.TRANSFER_BULK,
            Permission.INVESTMENT_AUTOINVEST,
            Permission.INVESTMENT_WITHDRAW
        ));

        // MERCHANT PERMISSIONS
        ROLE_PERMISSIONS.put(Role.MERCHANT, Set.of(
            Permission.MERCHANT_PAYMENT_ACCEPT,
            Permission.MERCHANT_SETTLEMENT_VIEW,
            Permission.PAYMENT_REFUND
        ));

        ROLE_PERMISSIONS.put(Role.MERCHANT_VERIFIED, Set.of(
            Permission.MERCHANT_REFUND_ISSUE,
            Permission.MERCHANT_ANALYTICS_VIEW
        ));

        // BUSINESS PERMISSIONS
        ROLE_PERMISSIONS.put(Role.BUSINESS_USER, Set.of(
            Permission.PAYMENT_INITIATE,
            Permission.PAYMENT_VIEW_OWN,
            Permission.TRANSFER_P2P
        ));

        ROLE_PERMISSIONS.put(Role.BUSINESS_ADMIN, Set.of(
            Permission.PAYMENT_APPROVE,
            Permission.TRANSFER_BULK,
            Permission.ADMIN_USER_VIEW
        ));

        // COMPLIANCE OFFICER PERMISSIONS
        ROLE_PERMISSIONS.put(Role.COMPLIANCE_OFFICER, Set.of(
            Permission.COMPLIANCE_SAR_VIEW,
            Permission.COMPLIANCE_SAR_FILE,
            Permission.COMPLIANCE_KYC_VIEW,
            Permission.COMPLIANCE_KYC_APPROVE,
            Permission.COMPLIANCE_SANCTIONS_SCREEN,
            Permission.COMPLIANCE_RISK_PROFILE,
            Permission.AUDIT_VIEW,
            Permission.AUDIT_EXPORT,
            Permission.ADMIN_USER_VIEW,
            Permission.ADMIN_USER_SUSPEND,
            Permission.WALLET_FREEZE,
            Permission.WALLET_UNFREEZE,
            Permission.PAYMENT_VIEW_ALL
        ));

        // FINANCIAL AUDITOR PERMISSIONS
        ROLE_PERMISSIONS.put(Role.FINANCIAL_AUDITOR, Set.of(
            Permission.AUDIT_VIEW,
            Permission.AUDIT_EXPORT,
            Permission.PAYMENT_VIEW_ALL,
            Permission.COMPLIANCE_SAR_VIEW,
            Permission.ADMIN_USER_VIEW
        ));

        // SUPPORT AGENT PERMISSIONS
        ROLE_PERMISSIONS.put(Role.SUPPORT_AGENT, Set.of(
            Permission.ADMIN_USER_VIEW,
            Permission.WALLET_READ,
            Permission.PAYMENT_VIEW_OWN
        ));

        // OPERATIONS MANAGER PERMISSIONS
        ROLE_PERMISSIONS.put(Role.OPERATIONS_MANAGER, Set.of(
            Permission.ADMIN_USER_VIEW,
            Permission.ADMIN_USER_UPDATE,
            Permission.PAYMENT_VIEW_ALL,
            Permission.PAYMENT_REFUND_APPROVE,
            Permission.PAYMENT_CHARGEBACK,
            Permission.WALLET_FREEZE,
            Permission.LOAN_APPROVE,
            Permission.LOAN_REJECT
        ));

        // SYSTEM ADMIN PERMISSIONS
        ROLE_PERMISSIONS.put(Role.SYSTEM_ADMIN, Set.of(
            Permission.ADMIN_USER_VIEW,
            Permission.ADMIN_USER_UPDATE,
            Permission.ADMIN_USER_SUSPEND,
            Permission.ADMIN_USER_RESTORE,
            Permission.ADMIN_TRANSACTION_OVERRIDE,
            Permission.ADMIN_SYSTEM_CONFIG,
            Permission.WALLET_FREEZE,
            Permission.WALLET_UNFREEZE,
            Permission.PAYMENT_VIEW_ALL,
            Permission.AUDIT_VIEW,
            Permission.AUDIT_EXPORT
        ));

        // SUPER ADMIN HAS ALL PERMISSIONS
        ROLE_PERMISSIONS.put(Role.SUPER_ADMIN,
            Arrays.stream(Permission.values()).collect(Collectors.toSet()));
    }

    /**
     * Check if a role has a specific permission
     */
    public static boolean hasPermission(Role role, Permission permission) {
        // Check direct permissions
        Set<Permission> directPermissions = ROLE_PERMISSIONS.get(role);
        if (directPermissions != null && directPermissions.contains(permission)) {
            return true;
        }

        // Check inherited permissions
        for (Role inheritedRole : role.getInheritsFrom()) {
            if (hasPermission(inheritedRole, permission)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a set of roles has a specific permission
     */
    public static boolean hasPermission(Set<Role> roles, Permission permission) {
        return roles.stream().anyMatch(role -> hasPermission(role, permission));
    }

    /**
     * Get all permissions for a role (including inherited)
     */
    public static Set<Permission> getPermissions(Role role) {
        Set<Permission> permissions = new HashSet<>();

        // Add direct permissions
        Set<Permission> directPermissions = ROLE_PERMISSIONS.get(role);
        if (directPermissions != null) {
            permissions.addAll(directPermissions);
        }

        // Add inherited permissions
        for (Role inheritedRole : role.getInheritsFrom()) {
            permissions.addAll(getPermissions(inheritedRole));
        }

        return permissions;
    }

    /**
     * Get all permissions for multiple roles
     */
    public static Set<Permission> getPermissions(Set<Role> roles) {
        return roles.stream()
            .flatMap(role -> getPermissions(role).stream())
            .collect(Collectors.toSet());
    }

    /**
     * Validate role combination (detect conflicts)
     */
    public static List<String> validateRoleCombination(Set<Role> roles) {
        List<String> violations = new ArrayList<>();

        // SEPARATION OF DUTIES: Compliance officer cannot be system admin
        if (roles.contains(Role.COMPLIANCE_OFFICER) && roles.contains(Role.SYSTEM_ADMIN)) {
            violations.add("SOX VIOLATION: User cannot be both Compliance Officer and System Admin");
        }

        // SEPARATION OF DUTIES: Financial auditor cannot have transaction override
        if (roles.contains(Role.FINANCIAL_AUDITOR) &&
            getPermissions(roles).contains(Permission.ADMIN_TRANSACTION_OVERRIDE)) {
            violations.add("SOX VIOLATION: Financial Auditor cannot have transaction override permission");
        }

        return violations;
    }

    /**
     * Generate authorization report for audit
     */
    public static Map<String, Object> generateAuditReport(Role role) {
        Map<String, Object> report = new HashMap<>();
        report.put("role", role.name());
        report.put("displayName", role.getDisplayName());
        report.put("type", role.getType());
        report.put("inheritsFrom", role.getInheritsFrom().stream()
            .map(Role::name)
            .collect(Collectors.toList()));
        report.put("permissions", getPermissions(role).stream()
            .map(Permission::getCode)
            .sorted()
            .collect(Collectors.toList()));
        report.put("permissionCount", getPermissions(role).size());

        return report;
    }
}
