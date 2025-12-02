package com.waqiti.common.security;

/**
 * System Roles for Waqiti Platform
 *
 * Defines standard roles used across the application for:
 * - SOX Compliance (Segregation of Duties)
 * - RBAC (Role-Based Access Control)
 * - Financial Services Authorization
 */
public final class Roles {

    private Roles() {
        // Utility class
    }

    // System Roles
    public static final String SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";
    public static final String ADMIN = "ROLE_ADMIN";
    public static final String USER = "ROLE_USER";

    // Financial Roles
    public static final String FINANCIAL_ANALYST = "ROLE_FINANCIAL_ANALYST";
    public static final String ACCOUNTANT = "ROLE_ACCOUNTANT";
    public static final String AUDITOR = "ROLE_AUDITOR";

    // Transaction Roles
    public static final String TRANSACTION_INITIATOR = "ROLE_TRANSACTION_INITIATOR";
    public static final String TRANSACTION_APPROVER = "ROLE_TRANSACTION_APPROVER";
    public static final String PAYMENT_PROCESSOR = "ROLE_PAYMENT_PROCESSOR";

    // Compliance Roles
    public static final String COMPLIANCE_OFFICER = "ROLE_COMPLIANCE_OFFICER";
    public static final String KYC_ANALYST = "ROLE_KYC_ANALYST";
    public static final String AML_SPECIALIST = "ROLE_AML_SPECIALIST";

    // Support Roles
    public static final String CUSTOMER_SUPPORT = "ROLE_CUSTOMER_SUPPORT";
    public static final String SUPPORT_MANAGER = "ROLE_SUPPORT_MANAGER";

    // Security Roles
    public static final String SECURITY_ANALYST = "ROLE_SECURITY_ANALYST";
    public static final String FRAUD_INVESTIGATOR = "ROLE_FRAUD_INVESTIGATOR";

    // Developer Roles
    public static final String DEVELOPER = "ROLE_DEVELOPER";
    public static final String DEVOPS = "ROLE_DEVOPS";

    /**
     * Check if a role requires SOX segregation of duties
     */
    public static boolean requiresSegregation(String role) {
        return role.equals(TRANSACTION_INITIATOR) ||
               role.equals(TRANSACTION_APPROVER) ||
               role.equals(PAYMENT_PROCESSOR) ||
               role.equals(ACCOUNTANT) ||
               role.equals(AUDITOR);
    }
}
