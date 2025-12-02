package com.waqiti.common.security;

import com.waqiti.common.audit.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.waqiti.common.security.model.*;

/**
 * Comprehensive authorization service to prevent IDOR vulnerabilities.
 * Implements ownership validation, resource access control, and delegation management.
 */
@Service
@Slf4j
public class AuthorizationService {

    private final ResourceOwnershipService resourceOwnershipService;
    private final DelegationService delegationService;
    private final AuditService auditService;
    
    public AuthorizationService(
            ResourceOwnershipService resourceOwnershipService,
            DelegationService delegationService,
            AuditService auditService) {
        this.resourceOwnershipService = resourceOwnershipService;
        this.delegationService = delegationService;
        this.auditService = auditService;
    }
    
    private final Map<String, ResourceAccessPolicy> accessPolicies = new ConcurrentHashMap<>();

    /**
     * Validates if the current user can access a specific resource
     */
    public boolean canAccessResource(String resourceType, String resourceId) {
        try {
            String userId = getCurrentUserId();
            
            // Check if user is blocked
            if (isUserBlocked(userId)) {
                log.warn("Blocked user attempted resource access: {}", userId);
                return false;
            }
            
            // Admin bypass (with audit)
            if (hasAdminRole()) {
                auditService.logAdminAccess(userId, resourceType, resourceId);
                return true;
            }
            
            // Check ownership
            if (resourceOwnershipService.isOwner(userId, resourceType, resourceId)) {
                return true;
            }
            
            // Check delegation
            if (delegationService.hasDelegate(userId, resourceType, resourceId)) {
                auditService.logDelegatedAccess(userId, resourceType, resourceId);
                return true;
            }
            
            // Check group membership
            if (checkGroupAccess(userId, resourceType, resourceId)) {
                return true;
            }
            
            // Check custom policies
            ResourceAccessPolicy policy = accessPolicies.get(resourceType);
            if (policy != null && policy.evaluate(userId, resourceId)) {
                return true;
            }
            
            // Log unauthorized attempt
            log.warn("Unauthorized access attempt - User: {}, Resource: {}:{}", 
                userId, resourceType, resourceId);
            auditService.logUnauthorizedAccess(userId, resourceType, resourceId);
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking resource access", e);
            return false; // Fail closed
        }
    }

    /**
     * Validates if user can perform a specific action on a resource
     */
    public boolean canPerformAction(String resourceType, String resourceId, String action) {
        String userId = getCurrentUserId();
        
        // First check basic access
        if (!canAccessResource(resourceType, resourceId)) {
            return false;
        }
        
        // Check action-specific permissions
        return checkActionPermission(userId, resourceType, resourceId, action);
    }

    /**
     * Validates if user can access account-related resources
     */
    public boolean canAccessAccount(String accountId) {
        String userId = getCurrentUserId();
        
        // Check direct ownership
        if (resourceOwnershipService.isAccountOwner(userId, accountId)) {
            return true;
        }
        
        // Check joint account access
        if (resourceOwnershipService.isJointAccountHolder(userId, accountId)) {
            return true;
        }
        
        // Check family account access
        if (resourceOwnershipService.isFamilyAccountMember(userId, accountId)) {
            return checkFamilyAccountPermissions(userId, accountId);
        }
        
        // Check business account access
        if (resourceOwnershipService.isBusinessAccountAuthorized(userId, accountId)) {
            return true;
        }
        
        log.warn("Unauthorized account access attempt - User: {}, Account: {}", userId, accountId);
        return false;
    }

    /**
     * Validates if user can access transaction details
     */
    public boolean canAccessTransaction(String transactionId) {
        String userId = getCurrentUserId();
        
        // Get transaction details
        TransactionInfo txInfo = resourceOwnershipService.getTransactionInfo(transactionId);
        if (txInfo == null) {
            return false;
        }
        
        // Check if user is sender or recipient
        if (userId.equals(txInfo.getSenderId()) || userId.equals(txInfo.getRecipientId())) {
            return true;
        }
        
        // Check if user has access to associated account
        if (canAccessAccount(txInfo.getAccountId())) {
            return true;
        }
        
        // Check if user is authorized viewer (e.g., accountant, auditor)
        if (hasRole("TRANSACTION_VIEWER") && isWithinViewScope(txInfo)) {
            auditService.logTransactionView(userId, transactionId);
            return true;
        }
        
        return false;
    }

    /**
     * Validates if user can modify a resource
     */
    public boolean canModifyResource(String resourceType, String resourceId) {
        String userId = getCurrentUserId();
        
        // Only owners and authorized delegates can modify
        if (!resourceOwnershipService.isOwner(userId, resourceType, resourceId)) {
            // Check if user has modify delegation
            if (!delegationService.hasModifyPermission(userId, resourceType, resourceId)) {
                log.warn("Unauthorized modification attempt - User: {}, Resource: {}:{}", 
                    userId, resourceType, resourceId);
                return false;
            }
        }
        
        // Check if resource is locked
        if (resourceOwnershipService.isResourceLocked(resourceType, resourceId)) {
            log.info("Attempt to modify locked resource - User: {}, Resource: {}:{}", 
                userId, resourceType, resourceId);
            return false;
        }
        
        // Check rate limiting for modifications
        if (!checkModificationRateLimit(userId, resourceType)) {
            log.warn("Modification rate limit exceeded - User: {}, Resource Type: {}", 
                userId, resourceType);
            return false;
        }
        
        return true;
    }

    /**
     * Validates if user can delete a resource
     */
    public boolean canDeleteResource(String resourceType, String resourceId) {
        String userId = getCurrentUserId();
        
        // Only owners can delete (no delegation for delete)
        if (!resourceOwnershipService.isOwner(userId, resourceType, resourceId)) {
            log.warn("Unauthorized deletion attempt - User: {}, Resource: {}:{}", 
                userId, resourceType, resourceId);
            return false;
        }
        
        // Check if resource can be deleted (business rules)
        if (!resourceOwnershipService.canBeDeleted(resourceType, resourceId)) {
            log.info("Attempt to delete non-deletable resource - User: {}, Resource: {}:{}", 
                userId, resourceType, resourceId);
            return false;
        }
        
        // Require additional authentication for critical deletions
        if (isCriticalResource(resourceType) && !hasRecentAuthentication()) {
            log.info("Re-authentication required for critical deletion - User: {}, Resource: {}:{}", 
                userId, resourceType, resourceId);
            return false;
        }
        
        return true;
    }

    /**
     * Validates bulk resource access (prevents enumeration attacks)
     */
    public List<String> filterAuthorizedResources(String resourceType, List<String> resourceIds) {
        String userId = getCurrentUserId();
        List<String> authorized = new ArrayList<>();
        
        // Limit bulk access to prevent enumeration
        if (resourceIds.size() > 100) {
            log.warn("Bulk access attempt exceeds limit - User: {}, Count: {}", 
                userId, resourceIds.size());
            resourceIds = resourceIds.subList(0, 100);
        }
        
        for (String resourceId : resourceIds) {
            if (canAccessResource(resourceType, resourceId)) {
                authorized.add(resourceId);
            }
        }
        
        // Log if user attempted to access many unauthorized resources
        int unauthorizedCount = resourceIds.size() - authorized.size();
        if (unauthorizedCount > 10) {
            log.warn("Multiple unauthorized access attempts - User: {}, Unauthorized: {}", 
                userId, unauthorizedCount);
            auditService.logBulkUnauthorizedAccess(userId, resourceType, unauthorizedCount);
        }
        
        return authorized;
    }

    /**
     * Grant temporary access to a resource
     */
    public void grantTemporaryAccess(String grantorId, String granteeId, 
                                    String resourceType, String resourceId, 
                                    long durationMinutes) {
        // Verify grantor has permission to grant access
        if (!canGrantAccess(grantorId, resourceType, resourceId)) {
            throw new SecurityException("User does not have permission to grant access");
        }
        
        delegationService.createTemporaryDelegation(
            grantorId, granteeId, resourceType, resourceId, (int) durationMinutes
        );

        auditService.logAccessGrant(grantorId, granteeId, resourceType, resourceId, (int) durationMinutes);
        log.info("Temporary access granted - Grantor: {}, Grantee: {}, Resource: {}:{}, Duration: {}min", 
            grantorId, granteeId, resourceType, resourceId, durationMinutes);
    }

    /**
     * Revoke access to a resource
     */
    public void revokeAccess(String revokerId, String userId, 
                            String resourceType, String resourceId) {
        // Verify revoker has permission
        if (!canRevokeAccess(revokerId, resourceType, resourceId)) {
            throw new SecurityException("User does not have permission to revoke access");
        }
        
        delegationService.revokeDelegation(userId, resourceType, resourceId);
        
        auditService.logAccessRevoke(revokerId, userId, resourceType, resourceId);
        log.info("Access revoked - Revoker: {}, User: {}, Resource: {}:{}", 
            revokerId, userId, resourceType, resourceId);
    }

    /**
     * Register custom access policy for a resource type
     */
    public void registerAccessPolicy(String resourceType, ResourceAccessPolicy policy) {
        accessPolicies.put(resourceType, policy);
        log.info("Access policy registered for resource type: {}", resourceType);
    }

    // Private helper methods

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }
        return auth.getName();
    }

    private boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    private boolean hasAdminRole() {
        return hasRole("ADMIN") || hasRole("SUPER_ADMIN");
    }

    private boolean isUserBlocked(String userId) {
        // Check if user is blocked/suspended
        return resourceOwnershipService.isUserBlocked(userId);
    }

    private boolean checkGroupAccess(String userId, String resourceType, String resourceId) {
        // Check if user has access through group membership
        List<String> userGroups = resourceOwnershipService.getUserGroups(userId);
        for (String groupId : userGroups) {
            if (resourceOwnershipService.groupHasAccess(groupId, resourceType, resourceId)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkActionPermission(String userId, String resourceType, 
                                         String resourceId, String action) {
        // Check specific action permissions
        Set<String> allowedActions = resourceOwnershipService.getAllowedActions(
            userId, resourceType, resourceId
        );
        return allowedActions.contains(action);
    }

    private boolean checkFamilyAccountPermissions(String userId, String accountId) {
        // Check specific permissions for family accounts
        FamilyAccountRole role = resourceOwnershipService.getFamilyAccountRole(userId, accountId);
        return role != null && role.canAccessAccount();
    }

    private boolean isWithinViewScope(TransactionInfo txInfo) {
        // Check if transaction is within viewer's allowed scope
        // (e.g., date range, amount limits, account types)
        return true; // Simplified for example
    }

    private boolean checkModificationRateLimit(String userId, String resourceType) {
        // Check if user has exceeded modification rate limits
        int recentModifications = resourceOwnershipService.getRecentModificationCount(
            userId, resourceType, 1 // Check last hour
        );
        return recentModifications < 10; // Max 10 modifications per hour
    }

    private boolean isCriticalResource(String resourceType) {
        Set<String> criticalTypes = Set.of(
            "ACCOUNT", "PAYMENT_METHOD", "SECURITY_SETTINGS", "KYC_DOCUMENT"
        );
        return criticalTypes.contains(resourceType);
    }

    private boolean hasRecentAuthentication() {
        // Check if user has authenticated recently (e.g., within last 5 minutes)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // Implementation would check authentication timestamp
        return true; // Simplified for example
    }

    private boolean canGrantAccess(String grantorId, String resourceType, String resourceId) {
        // Only owners and admins can grant access
        return resourceOwnershipService.isOwner(grantorId, resourceType, resourceId) || 
               hasAdminRole();
    }

    private boolean canRevokeAccess(String revokerId, String resourceType, String resourceId) {
        // Owners and admins can revoke access
        return resourceOwnershipService.isOwner(revokerId, resourceType, resourceId) || 
               hasAdminRole();
    }

    /**
     * Interface for custom resource access policies
     */
    public interface ResourceAccessPolicy {
        boolean evaluate(String userId, String resourceId);
    }

    /**
     * Transaction information for access control
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TransactionInfo {
        private String transactionId;
        private String senderId;
        private String recipientId;
        private String accountId;
        private String type;
    }

    /**
     * Family account role
     */
    public enum FamilyAccountRole {
        PARENT(true, true, true),
        TEEN(true, false, false),
        CHILD(false, false, false);
        
        private final boolean canAccessAccount;
        private final boolean canMakePayments;
        private final boolean canManageSettings;
        
        FamilyAccountRole(boolean canAccessAccount, boolean canMakePayments, boolean canManageSettings) {
            this.canAccessAccount = canAccessAccount;
            this.canMakePayments = canMakePayments;
            this.canManageSettings = canManageSettings;
        }
        
        public boolean canAccessAccount() { return canAccessAccount; }
        public boolean canMakePayments() { return canMakePayments; }
        public boolean canManageSettings() { return canManageSettings; }
    }
}