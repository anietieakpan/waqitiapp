package com.waqiti.common.security;

/**
 * Centralized role definitions for the Waqiti platform.
 * These roles are used across all microservices for authorization.
 */
public final class Roles {
    
    // User Roles
    public static final String USER = "ROLE_USER";
    public static final String PREMIUM_USER = "ROLE_PREMIUM_USER";
    public static final String VIP_USER = "ROLE_VIP_USER";
    
    // Business Roles
    public static final String MERCHANT = "ROLE_MERCHANT";
    public static final String MERCHANT_ADMIN = "ROLE_MERCHANT_ADMIN";
    public static final String BUSINESS_OWNER = "ROLE_BUSINESS_OWNER";
    
    // Administrative Roles
    public static final String ADMIN = "ROLE_ADMIN";
    public static final String SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    public static final String SYSTEM = "ROLE_SYSTEM";
    
    // Operational Roles
    public static final String SUPPORT = "ROLE_SUPPORT";
    public static final String SUPPORT_MANAGER = "ROLE_SUPPORT_MANAGER";
    public static final String OPERATIONS = "ROLE_OPERATIONS";
    
    // Compliance & Risk Roles
    public static final String COMPLIANCE_OFFICER = "ROLE_COMPLIANCE_OFFICER";
    public static final String COMPLIANCE_MANAGER = "ROLE_COMPLIANCE_MANAGER";
    public static final String RISK_ANALYST = "ROLE_RISK_ANALYST";
    public static final String FRAUD_ANALYST = "ROLE_FRAUD_ANALYST";
    
    // Financial Roles
    public static final String ACCOUNTANT = "ROLE_ACCOUNTANT";
    public static final String FINANCE_MANAGER = "ROLE_FINANCE_MANAGER";
    public static final String TREASURY = "ROLE_TREASURY";
    
    // Audit Roles
    public static final String AUDITOR = "ROLE_AUDITOR";
    public static final String INTERNAL_AUDIT = "ROLE_INTERNAL_AUDIT";
    public static final String EXTERNAL_AUDIT = "ROLE_EXTERNAL_AUDIT";
    
    // API & Integration Roles
    public static final String API_USER = "ROLE_API_USER";
    public static final String INTEGRATION_PARTNER = "ROLE_INTEGRATION_PARTNER";
    public static final String WEBHOOK_SYSTEM = "ROLE_WEBHOOK_SYSTEM";
    
    // Family Account Roles
    public static final String FAMILY_ADMIN = "ROLE_FAMILY_ADMIN";
    public static final String FAMILY_MEMBER = "ROLE_FAMILY_MEMBER";
    public static final String FAMILY_CHILD = "ROLE_FAMILY_CHILD";
    
    // Temporary & Limited Roles
    public static final String GUEST = "ROLE_GUEST";
    public static final String LIMITED_ACCESS = "ROLE_LIMITED_ACCESS";
    public static final String READ_ONLY = "ROLE_READ_ONLY";
    
    private Roles() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Check if a role has administrative privileges
     */
    public static boolean isAdminRole(String role) {
        return ADMIN.equals(role) || 
               SUPER_ADMIN.equals(role) || 
               SYSTEM.equals(role);
    }
    
    /**
     * Check if a role has compliance privileges
     */
    public static boolean isComplianceRole(String role) {
        return COMPLIANCE_OFFICER.equals(role) || 
               COMPLIANCE_MANAGER.equals(role) || 
               RISK_ANALYST.equals(role) || 
               FRAUD_ANALYST.equals(role);
    }
    
    /**
     * Check if a role has financial privileges
     */
    public static boolean isFinancialRole(String role) {
        return ACCOUNTANT.equals(role) || 
               FINANCE_MANAGER.equals(role) || 
               TREASURY.equals(role);
    }
    
    /**
     * Check if a role has support privileges
     */
    public static boolean isSupportRole(String role) {
        return SUPPORT.equals(role) || 
               SUPPORT_MANAGER.equals(role) || 
               OPERATIONS.equals(role);
    }
}