package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Custom security expressions for method-level security.
 * Provides additional security checks beyond basic role-based access.
 */
@Slf4j
public class CustomMethodSecurityExpressionRoot extends SecurityExpressionRoot 
        implements MethodSecurityExpressionOperations {
    
    private final PermissionMatrix permissionMatrix;
    private Object filterObject;
    private Object returnObject;
    
    public CustomMethodSecurityExpressionRoot(Authentication authentication, 
                                              PermissionMatrix permissionMatrix) {
        super(authentication);
        this.permissionMatrix = permissionMatrix;
    }

    /**
     * Note: hasAnyRole() is provided by parent class SecurityExpressionRoot (marked final).
     * We use the parent's implementation which handles role checking with "ROLE_" prefix.
     * All internal methods (isAdmin, isComplianceOfficer, etc.) call the parent's method.
     */

    /**
     * Check if user has all specified roles
     */
    public boolean hasAllRoles(String... roles) {
        Set<String> userRoles = getUserRoles();
        for (String role : roles) {
            String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
            if (!userRoles.contains(roleWithPrefix)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check if user has a specific permission
     */
    public boolean hasPermission(String permission) {
        try {
            PermissionMatrix.Permission perm = PermissionMatrix.Permission.valueOf(permission);
            Set<String> userRoles = getUserRoles();
            
            for (String role : userRoles) {
                if (permissionMatrix.hasPermission(role, perm)) {
                    return true;
                }
            }
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid permission requested: {}", permission);
            return false;
        }
    }
    
    /**
     * Check if transaction amount is within user's limit
     */
    public boolean isWithinTransactionLimit(BigDecimal amount, String transactionType) {
        if (amount == null || transactionType == null) {
            return false;
        }
        
        String highestRole = getHighestRole();
        return permissionMatrix.isWithinLimit(highestRole, transactionType, amount);
    }
    
    /**
     * Check if transaction requires two-factor authentication
     */
    public boolean requiresTwoFactor(BigDecimal amount) {
        if (amount == null) {
            return true; // Require 2FA for safety if amount is unknown
        }
        
        String highestRole = getHighestRole();
        return permissionMatrix.requiresTwoFactor(highestRole, amount);
    }
    
    /**
     * Check if operation is within business hours
     */
    public boolean isWithinBusinessHours() {
        LocalTime now = LocalTime.now();
        LocalTime businessStart = LocalTime.of(6, 0); // 6 AM
        LocalTime businessEnd = LocalTime.of(22, 0);  // 10 PM
        
        return !now.isBefore(businessStart) && !now.isAfter(businessEnd);
    }
    
    /**
     * Check if user has admin privileges
     */
    public boolean isAdmin() {
        return hasAnyRole(Roles.ADMIN, Roles.SUPER_ADMIN, Roles.SYSTEM);
    }
    
    /**
     * Check if user has compliance privileges
     */
    public boolean isComplianceOfficer() {
        return hasAnyRole(Roles.COMPLIANCE_OFFICER, Roles.COMPLIANCE_MANAGER);
    }
    
    /**
     * Check if user has support privileges
     */
    public boolean isSupport() {
        return hasAnyRole(Roles.SUPPORT, Roles.SUPPORT_MANAGER, Roles.OPERATIONS);
    }
    
    /**
     * Check if user is a premium member (any premium tier)
     */
    public boolean isPremiumMember() {
        return hasAnyRole(Roles.PREMIUM_USER, Roles.VIP_USER);
    }
    
    /**
     * Check if user has business account privileges
     */
    public boolean isBusinessAccount() {
        return hasAnyRole(Roles.MERCHANT, Roles.MERCHANT_ADMIN, Roles.BUSINESS_OWNER);
    }
    
    /**
     * Check daily transaction count limit
     */
    public boolean isWithinDailyTransactionLimit(int currentCount) {
        String highestRole = getHighestRole();
        PermissionMatrix.TransactionLimits limits = permissionMatrix.getTransactionLimits(highestRole);
        return currentCount < limits.getMaxTransactionsPerDay();
    }
    
    /**
     * Check if high-value transaction needs approval
     */
    public boolean needsApproval(BigDecimal amount) {
        // Transactions over 50,000 need approval unless admin
        BigDecimal approvalThreshold = new BigDecimal("50000");
        return amount.compareTo(approvalThreshold) > 0 && !isAdmin();
    }
    
    /**
     * Check if user can perform international transactions
     */
    public boolean canPerformInternationalTransfer() {
        return hasPermission("INTERNATIONAL_TRANSFER");
    }
    
    /**
     * Check if user can access sensitive data
     */
    public boolean canAccessSensitiveData() {
        return isAdmin() || isComplianceOfficer() || 
               hasAnyRole(Roles.AUDITOR, Roles.INTERNAL_AUDIT);
    }
    
    /**
     * Check if user can reverse transactions
     */
    public boolean canReverseTransaction() {
        return hasPermission("REVERSE_TRANSACTION") || isAdmin();
    }
    
    /**
     * Check if user can override risk rules
     */
    public boolean canOverrideRiskRules() {
        return hasPermission("OVERRIDE_RISK_RULES") || 
               hasAnyRole(Roles.RISK_ANALYST, Roles.COMPLIANCE_MANAGER);
    }
    
    /**
     * Helper method to get user's roles
     */
    private Set<String> getUserRoles() {
        Collection<? extends GrantedAuthority> authorities = getAuthentication().getAuthorities();
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
    
    /**
     * Get the highest privileged role for limit calculations
     */
    private String getHighestRole() {
        Set<String> userRoles = getUserRoles();
        
        // Check roles in order of privilege
        if (userRoles.contains(Roles.SYSTEM)) return Roles.SYSTEM;
        if (userRoles.contains(Roles.SUPER_ADMIN)) return Roles.SUPER_ADMIN;
        if (userRoles.contains(Roles.ADMIN)) return Roles.ADMIN;
        if (userRoles.contains(Roles.BUSINESS_OWNER)) return Roles.BUSINESS_OWNER;
        if (userRoles.contains(Roles.VIP_USER)) return Roles.VIP_USER;
        if (userRoles.contains(Roles.MERCHANT)) return Roles.MERCHANT;
        if (userRoles.contains(Roles.PREMIUM_USER)) return Roles.PREMIUM_USER;
        if (userRoles.contains(Roles.FAMILY_ADMIN)) return Roles.FAMILY_ADMIN;
        if (userRoles.contains(Roles.USER)) return Roles.USER;
        if (userRoles.contains(Roles.FAMILY_CHILD)) return Roles.FAMILY_CHILD;
        
        // Default to most restrictive
        return Roles.GUEST;
    }
    
    // MethodSecurityExpressionOperations implementation
    @Override
    public void setFilterObject(Object filterObject) {
        this.filterObject = filterObject;
    }
    
    @Override
    public Object getFilterObject() {
        return filterObject;
    }
    
    @Override
    public void setReturnObject(Object returnObject) {
        this.returnObject = returnObject;
    }
    
    @Override
    public Object getReturnObject() {
        return returnObject;
    }
    
    @Override
    public Object getThis() {
        return this;
    }
}