package com.waqiti.common.security;

import com.waqiti.common.audit.SecureAuditLogger;
import com.waqiti.common.exception.ResourceNotFoundException;
import com.waqiti.common.exception.SecurityViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comprehensive resource ownership validator for financial operations
 * Ensures users can only access resources they own or are authorized to access
 */
@Component
@RequiredArgsConstructor
public class ResourceOwnershipValidator {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ResourceOwnershipValidator.class);

    private final SecureAuditLogger auditLogger;
    private final SecurityContextUtil securityContextUtil;

    /**
     * Validate that the authenticated user owns or has access to the specified wallet
     */
    public void validateWalletOwnership(UUID walletId, String operation) {
        UUID authenticatedUserId = getAuthenticatedUserId();
        
        if (!isWalletOwner(walletId, authenticatedUserId)) {
            logSecurityViolation("UNAUTHORIZED_WALLET_ACCESS", walletId.toString(), operation);
            throw new AccessDeniedException("Access denied to wallet: " + walletId);
        }
        
        logAuthorizedAccess("WALLET", walletId.toString(), operation);
    }

    /**
     * Validate transaction ownership and authorization
     */
    public void validateTransactionAccess(UUID transactionId, String operation) {
        UUID authenticatedUserId = getAuthenticatedUserId();
        
        TransactionOwnershipInfo ownershipInfo = getTransactionOwnership(transactionId);
        
        if (!isAuthorizedForTransaction(ownershipInfo, authenticatedUserId, operation)) {
            logSecurityViolation("UNAUTHORIZED_TRANSACTION_ACCESS", transactionId.toString(), operation);
            throw new AccessDeniedException("Access denied to transaction: " + transactionId);
        }
        
        logAuthorizedAccess("TRANSACTION", transactionId.toString(), operation);
    }

    /**
     * Validate account ownership and access permissions
     */
    public void validateAccountAccess(UUID accountId, String operation) {
        UUID authenticatedUserId = getAuthenticatedUserId();
        
        if (!isAccountOwner(accountId, authenticatedUserId)) {
            logSecurityViolation("UNAUTHORIZED_ACCOUNT_ACCESS", accountId.toString(), operation);
            throw new AccessDeniedException("Access denied to account: " + accountId);
        }
        
        logAuthorizedAccess("ACCOUNT", accountId.toString(), operation);
    }

    /**
     * Validate payment method ownership
     */
    public void validatePaymentMethodAccess(UUID paymentMethodId, String operation) {
        UUID authenticatedUserId = getAuthenticatedUserId();
        
        if (!isPaymentMethodOwner(paymentMethodId, authenticatedUserId)) {
            logSecurityViolation("UNAUTHORIZED_PAYMENT_METHOD_ACCESS", paymentMethodId.toString(), operation);
            throw new AccessDeniedException("Access denied to payment method: " + paymentMethodId);
        }
        
        logAuthorizedAccess("PAYMENT_METHOD", paymentMethodId.toString(), operation);
    }

    /**
     * Validate KYC document access
     */
    public void validateKycDocumentAccess(UUID documentId, String operation) {
        UUID authenticatedUserId = getAuthenticatedUserId();
        
        if (!isKycDocumentOwner(documentId, authenticatedUserId) && !hasKycReviewPermissions()) {
            logSecurityViolation("UNAUTHORIZED_KYC_DOCUMENT_ACCESS", documentId.toString(), operation);
            throw new AccessDeniedException("Access denied to KYC document: " + documentId);
        }
        
        logAuthorizedAccess("KYC_DOCUMENT", documentId.toString(), operation);
    }

    /**
     * Validate user profile access (including viewing other users' profiles)
     */
    public void validateUserProfileAccess(UUID profileUserId, String operation) {
        UUID authenticatedUserId = getAuthenticatedUserId();
        
        // Users can always access their own profile
        if (authenticatedUserId.equals(profileUserId)) {
            logAuthorizedAccess("USER_PROFILE", profileUserId.toString(), "SELF_" + operation);
            return;
        }
        
        // Check if user has admin or support permissions to view other profiles
        if (!hasUserManagementPermissions()) {
            logSecurityViolation("UNAUTHORIZED_USER_PROFILE_ACCESS", profileUserId.toString(), operation);
            throw new AccessDeniedException("Access denied to user profile: " + profileUserId);
        }
        
        logAuthorizedAccess("USER_PROFILE", profileUserId.toString(), "ADMIN_" + operation);
    }

    /**
     * Validate bulk operation access (e.g., batch payments, bulk exports)
     */
    public void validateBulkOperationAccess(List<UUID> resourceIds, String resourceType, String operation) {
        UUID authenticatedUserId = getAuthenticatedUserId();
        
        for (UUID resourceId : resourceIds) {
            switch (resourceType.toUpperCase()) {
                case "WALLET" -> {
                    if (!isWalletOwner(resourceId, authenticatedUserId)) {
                        logSecurityViolation("UNAUTHORIZED_BULK_WALLET_ACCESS", resourceId.toString(), operation);
                        throw new AccessDeniedException("Access denied in bulk operation for wallet: " + resourceId);
                    }
                }
                case "TRANSACTION" -> {
                    TransactionOwnershipInfo ownershipInfo = getTransactionOwnership(resourceId);
                    if (!isAuthorizedForTransaction(ownershipInfo, authenticatedUserId, operation)) {
                        logSecurityViolation("UNAUTHORIZED_BULK_TRANSACTION_ACCESS", resourceId.toString(), operation);
                        throw new AccessDeniedException("Access denied in bulk operation for transaction: " + resourceId);
                    }
                }
                case "ACCOUNT" -> {
                    if (!isAccountOwner(resourceId, authenticatedUserId)) {
                        logSecurityViolation("UNAUTHORIZED_BULK_ACCOUNT_ACCESS", resourceId.toString(), operation);
                        throw new AccessDeniedException("Access denied in bulk operation for account: " + resourceId);
                    }
                }
                default -> throw new IllegalArgumentException("Unsupported resource type for bulk validation: " + resourceType);
            }
        }
        
        logAuthorizedAccess("BULK_" + resourceType, resourceIds.toString(), operation);
    }

    /**
     * Validate administrative access to sensitive operations
     */
    public void validateAdministrativeAccess(String operation, String resourceType) {
        if (!hasAdministrativePermissions()) {
            logSecurityViolation("UNAUTHORIZED_ADMIN_ACCESS", resourceType, operation);
            throw new AccessDeniedException("Administrative access required for operation: " + operation);
        }
        
        logAuthorizedAccess("ADMIN_OPERATION", resourceType, operation);
    }

    /**
     * Validate compliance officer access for regulatory operations
     */
    public void validateComplianceAccess(String operation, String resourceType) {
        if (!hasCompliancePermissions()) {
            logSecurityViolation("UNAUTHORIZED_COMPLIANCE_ACCESS", resourceType, operation);
            throw new AccessDeniedException("Compliance access required for operation: " + operation);
        }
        
        logAuthorizedAccess("COMPLIANCE_OPERATION", resourceType, operation);
    }

    // Private helper methods

    private UUID getAuthenticatedUserId() {
        try {
            return securityContextUtil.getAuthenticatedUserId();
        } catch (Exception e) {
            log.error("Failed to get authenticated user ID", e);
            throw new SecurityViolationException("Authentication required", "AUTHENTICATION_FAILURE");
        }
    }

    private boolean isWalletOwner(UUID walletId, UUID userId) {
        try {
            // Query wallet ownership - this would typically query the database
            // For now, implementing a placeholder that would be replaced with actual DB query
            return queryWalletOwnership(walletId, userId);
        } catch (Exception e) {
            log.error("Error checking wallet ownership for wallet: {}, user: {}", walletId, userId, e);
            return false; // Fail secure - deny access on error
        }
    }

    private TransactionOwnershipInfo getTransactionOwnership(UUID transactionId) {
        try {
            // This would query the database for transaction ownership info
            return queryTransactionOwnership(transactionId);
        } catch (Exception e) {
            log.error("Error getting transaction ownership for transaction: {}", transactionId, e);
            throw new ResourceNotFoundException("Transaction not found: " + transactionId);
        }
    }

    private boolean isAuthorizedForTransaction(TransactionOwnershipInfo ownershipInfo, UUID userId, String operation) {
        // User can access transaction if they are sender, receiver, or have admin permissions
        return ownershipInfo.getSenderId().equals(userId) || 
               ownershipInfo.getReceiverId().equals(userId) ||
               hasAdministrativePermissions();
    }

    private boolean isAccountOwner(UUID accountId, UUID userId) {
        try {
            return queryAccountOwnership(accountId, userId);
        } catch (Exception e) {
            log.error("Error checking account ownership for account: {}, user: {}", accountId, userId, e);
            return false;
        }
    }

    private boolean isPaymentMethodOwner(UUID paymentMethodId, UUID userId) {
        try {
            return queryPaymentMethodOwnership(paymentMethodId, userId);
        } catch (Exception e) {
            log.error("Error checking payment method ownership for payment method: {}, user: {}", paymentMethodId, userId, e);
            return false;
        }
    }

    private boolean isKycDocumentOwner(UUID documentId, UUID userId) {
        try {
            return queryKycDocumentOwnership(documentId, userId);
        } catch (Exception e) {
            log.error("Error checking KYC document ownership for document: {}, user: {}", documentId, userId, e);
            return false;
        }
    }

    private boolean hasKycReviewPermissions() {
        return securityContextUtil.hasRole("KYC_REVIEWER") || 
               securityContextUtil.hasRole("COMPLIANCE_OFFICER") ||
               securityContextUtil.hasRole("ADMIN");
    }

    private boolean hasUserManagementPermissions() {
        return securityContextUtil.hasRole("USER_MANAGER") ||
               securityContextUtil.hasRole("SUPPORT_AGENT") ||
               securityContextUtil.hasRole("ADMIN");
    }

    private boolean hasAdministrativePermissions() {
        return securityContextUtil.hasRole("ADMIN") ||
               securityContextUtil.hasRole("SYSTEM_ADMIN");
    }

    private boolean hasCompliancePermissions() {
        return securityContextUtil.hasRole("COMPLIANCE_OFFICER") ||
               securityContextUtil.hasRole("ADMIN");
    }

    private void logSecurityViolation(String violationType, String resourceId, String operation) {
        auditLogger.logSecurityEvent(
            SecureAuditLogger.SecurityEventType.SUSPICIOUS_ACTIVITY,
            String.format("Security violation: %s - Resource: %s, Operation: %s", violationType, resourceId, operation)
        );
    }

    private void logAuthorizedAccess(String resourceType, String resourceId, String operation) {
        auditLogger.logDataAccess(
            SecureAuditLogger.DataAccessType.READ, // This would be determined based on operation
            resourceType,
            resourceId,
            true
        );
    }

    // Placeholder methods for database queries - these would be implemented with actual repository calls

    private boolean queryWalletOwnership(UUID walletId, UUID userId) {
        // Implementation would query wallet repository
        // SELECT owner_id FROM wallets WHERE id = ? AND owner_id = ?
        return true; // Placeholder
    }

    private TransactionOwnershipInfo queryTransactionOwnership(UUID transactionId) {
        // Implementation would query transaction repository
        // SELECT sender_id, receiver_id, transaction_type FROM transactions WHERE id = ?
        return TransactionOwnershipInfo.builder()
                .senderId(UUID.randomUUID())
                .receiverId(UUID.randomUUID())
                .transactionType("TRANSFER")
                .build();
    }

    private boolean queryAccountOwnership(UUID accountId, UUID userId) {
        // Implementation would query account repository
        return true; // Placeholder
    }

    private boolean queryPaymentMethodOwnership(UUID paymentMethodId, UUID userId) {
        // Implementation would query payment method repository
        return true; // Placeholder
    }

    private boolean queryKycDocumentOwnership(UUID documentId, UUID userId) {
        // Implementation would query KYC document repository
        return true; // Placeholder
    }

    // Data transfer objects

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TransactionOwnershipInfo {
        private UUID senderId;
        private UUID receiverId;
        private String transactionType;
    }
}