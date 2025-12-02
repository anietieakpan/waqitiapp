package com.waqiti.payment.security;

import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.InstantDepositRepository;
import com.waqiti.payment.repository.ACHTransactionRepository;
import com.waqiti.payment.entity.Payment;
import com.waqiti.payment.entity.InstantDeposit;
import com.waqiti.payment.entity.ACHTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Production-grade ownership validator to prevent IDOR (Insecure Direct Object Reference) vulnerabilities.
 *
 * Security Pattern:
 * - Validates that the authenticated user owns the resource they're accessing
 * - Used in @PreAuthorize annotations for authorization
 * - Comprehensive audit logging for all validation attempts
 * - Fails closed (denies access if ownership cannot be verified)
 *
 * IDOR Prevention:
 * - User cannot access other users' payments
 * - User cannot deposit to other users' accounts
 * - User cannot view/modify other users' ACH transactions
 * - User cannot access other users' tokens
 *
 * @author Waqiti Security Team
 * @version 2.0 - Production Ready
 * @since 2025-10-07
 */
@Component("accountOwnershipValidator")
@RequiredArgsConstructor
@Slf4j
public class AccountOwnershipValidator {

    private final PaymentRepository paymentRepository;
    private final InstantDepositRepository instantDepositRepository;
    private final ACHTransactionRepository achTransactionRepository;
    private final TokenizationService tokenizationService;
    private final RBACService rbacService;

    /**
     * Validates that the user can deposit to the specified account/wallet.
     *
     * @param username Authenticated username (UUID format)
     * @param accountId Account/wallet ID to deposit to
     * @return true if user owns the account, false otherwise
     */
    public boolean canDepositToAccount(String username, UUID accountId) {
        try {
            UUID userId = parseUserId(username);

            // Verify the account belongs to the authenticated user
            boolean isOwner = achTransactionRepository.existsByAccountIdAndUserId(accountId, userId);

            if (!isOwner) {
                log.warn("SECURITY: IDOR attempt detected - User {} attempted to deposit to account {} (not owned)",
                    userId, accountId);
            } else {
                log.debug("Ownership validated: User {} owns account {}", userId, accountId);
            }

            return isOwner;

        } catch (Exception e) {
            log.error("SECURITY: Ownership validation failed for account {}", accountId, e);
            return false; // Fail closed for security
        }
    }

    /**
     * Validates that the user can access the specified payment.
     *
     * @param username Authenticated username (UUID format)
     * @param paymentId Payment ID to access
     * @return true if user is the payer or payee, false otherwise
     */
    public boolean canAccessPayment(String username, UUID paymentId) {
        try {
            UUID userId = parseUserId(username);

            Payment payment = paymentRepository.findById(paymentId).orElse(null);

            if (payment == null) {
                log.warn("SECURITY: Payment not found: {}", paymentId);
                return false;
            }

            // User can access if they are the payer OR the merchant (payee)
            boolean isOwner = payment.getUserId().equals(userId) ||
                            (payment.getMerchantId() != null && payment.getMerchantId().equals(userId));

            if (!isOwner) {
                log.warn("SECURITY: IDOR attempt detected - User {} attempted to access payment {} (not authorized)",
                    userId, paymentId);
            } else {
                log.debug("Payment access validated: User {} authorized for payment {}", userId, paymentId);
            }

            return isOwner;

        } catch (Exception e) {
            log.error("SECURITY: Payment ownership validation failed for payment {}", paymentId, e);
            return false; // Fail closed for security
        }
    }

    /**
     * Validates that the user can access the specified token.
     *
     * PRODUCTION-READY: Integrated with TokenizationService for complete validation
     *
     * SECURITY CHECKS:
     * - Token format validation
     * - Token ownership verification
     * - Token expiration check
     * - Token revocation check
     * - Comprehensive audit logging
     *
     * @param username Authenticated username (UUID format)
     * @param token Tokenized payment method identifier
     * @return true if user owns the token and it's valid, false otherwise
     */
    public boolean canAccessToken(String username, String token) {
        try {
            UUID userId = parseUserId(username);

            log.debug("SECURITY: Token access validation starting - User: {}, Token: {}",
                userId, maskToken(token));

            // PRODUCTION: Verify token ownership via TokenizationService
            boolean isOwner = tokenizationService.verifyTokenOwnership(userId, token);

            if (!isOwner) {
                log.warn("SECURITY: IDOR attempt detected - User {} attempted to access token {} (not owned or invalid)",
                    userId, maskToken(token));
            } else {
                log.info("AUDIT: Token access validated - User {} owns token {}", userId, maskToken(token));
            }

            return isOwner;

        } catch (Exception e) {
            log.error("SECURITY: Token ownership validation failed for user {}", username, e);
            return false; // Fail closed for security
        }
    }

    /**
     * Masks token for logging (PCI-DSS compliance).
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }

    /**
     * Validates that the user can access the specified ACH transfer.
     *
     * @param username Authenticated username (UUID format)
     * @param achTransferId ACH transaction ID
     * @return true if user owns the ACH transfer, false otherwise
     */
    public boolean canAccessAchTransfer(String username, UUID achTransferId) {
        try {
            UUID userId = parseUserId(username);

            ACHTransaction achTransaction = achTransactionRepository.findById(achTransferId).orElse(null);

            if (achTransaction == null) {
                log.warn("SECURITY: ACH transaction not found: {}", achTransferId);
                return false;
            }

            boolean isOwner = achTransaction.getUserId().equals(userId);

            if (!isOwner) {
                log.warn("SECURITY: IDOR attempt detected - User {} attempted to access ACH transfer {} (not owned)",
                    userId, achTransferId);
            } else {
                log.debug("ACH access validated: User {} owns ACH transfer {}", userId, achTransferId);
            }

            return isOwner;

        } catch (Exception e) {
            log.error("SECURITY: ACH ownership validation failed for transfer {}", achTransferId, e);
            return false; // Fail closed for security
        }
    }

    /**
     * Validates that the user can access the specified instant deposit.
     *
     * @param username Authenticated username (UUID format)
     * @param instantDepositId Instant deposit ID
     * @return true if user owns the instant deposit, false otherwise
     */
    public boolean canAccessInstantDeposit(String username, UUID instantDepositId) {
        try {
            UUID userId = parseUserId(username);

            InstantDeposit deposit = instantDepositRepository.findById(instantDepositId).orElse(null);

            if (deposit == null) {
                log.warn("SECURITY: Instant deposit not found: {}", instantDepositId);
                return false;
            }

            boolean isOwner = deposit.getUserId().equals(userId);

            if (!isOwner) {
                log.warn("SECURITY: IDOR attempt detected - User {} attempted to access instant deposit {} (not owned)",
                    userId, instantDepositId);
            } else {
                log.debug("Instant deposit access validated: User {} owns deposit {}", userId, instantDepositId);
            }

            return isOwner;

        } catch (Exception e) {
            log.error("SECURITY: Instant deposit ownership validation failed for deposit {}", instantDepositId, e);
            return false; // Fail closed for security
        }
    }

    /**
     * Validates that two users can perform a peer-to-peer transaction.
     *
     * @param username Authenticated username (UUID format)
     * @param fromUserId Source user ID
     * @param toUserId Destination user ID
     * @return true if authenticated user is the source, false otherwise
     */
    public boolean canTransferBetweenUsers(String username, UUID fromUserId, UUID toUserId) {
        try {
            UUID userId = parseUserId(username);

            // User must be the source of the transfer
            boolean isAuthorized = userId.equals(fromUserId);

            if (!isAuthorized) {
                log.warn("SECURITY: IDOR attempt detected - User {} attempted transfer from user {} to {}",
                    userId, fromUserId, toUserId);
            } else {
                log.debug("Transfer authorization validated: User {} authorized to transfer to {}", userId, toUserId);
            }

            return isAuthorized;

        } catch (Exception e) {
            log.error("SECURITY: Transfer authorization validation failed", e);
            return false; // Fail closed for security
        }
    }

    /**
     * Parses username (expected to be UUID) to user ID.
     *
     * @param username Username from authentication principal
     * @return Parsed UUID
     * @throws IllegalArgumentException if username is not a valid UUID
     */
    private UUID parseUserId(String username) {
        try {
            return UUID.fromString(username);
        } catch (IllegalArgumentException e) {
            log.error("SECURITY: Invalid username format - expected UUID, got: {}", username);
            throw new AccessDeniedException("Invalid user authentication format");
        }
    }

    /**
     * Validates that a user can perform administrative actions on a resource.
     * Only for use by admin endpoints.
     *
     * PRODUCTION-READY: Integrated with RBACService for role-based access control
     *
     * SUPPORTED ADMIN ROLES:
     * - ROLE_ADMIN: Full access to all resources
     * - ROLE_SUPPORT: Read-only access for customer support
     * - ROLE_COMPLIANCE: Access for compliance investigations
     * - ROLE_FINANCE: Access for financial operations (refunds, chargebacks)
     *
     * SUPPORTED PERMISSIONS:
     * - payment:refund: Can process refunds
     * - payment:chargeback: Can handle chargebacks
     * - payment:read: Can view payment details
     * - user:support: Can access user data for support
     *
     * @param username Authenticated username
     * @param resourceId Resource being accessed
     * @param resourceType Type of resource (for logging)
     * @return true if user has admin privileges, false otherwise
     */
    public boolean hasAdminAccess(String username, UUID resourceId, String resourceType) {
        try {
            UUID userId = parseUserId(username);

            log.info("SECURITY AUDIT: Admin access check - User: {}, Resource: {} {}, Timestamp: {}",
                userId, resourceType, resourceId, java.time.LocalDateTime.now());

            // PRODUCTION: Check RBAC via RBACService
            boolean hasAccess = rbacService.hasAdminRole(userId) ||
                               rbacService.hasPermission(userId, resourceType + ":admin");

            if (!hasAccess) {
                log.warn("SECURITY: Unauthorized admin access attempt - User {} attempted to access {} {} without privileges",
                    userId, resourceType, resourceId);

                // Audit the security violation
                rbacService.auditUnauthorizedAccess(userId, resourceType, resourceId, "ADMIN_ACCESS_DENIED");
            } else {
                log.info("AUDIT: Admin access granted - User {} accessing {} {}",
                    userId, resourceType, resourceId);

                // Audit successful admin access
                rbacService.auditAdminAccess(userId, resourceType, resourceId, "ADMIN_ACCESS_GRANTED");
            }

            return hasAccess;

        } catch (Exception e) {
            log.error("SECURITY: Admin access validation failed - username: {}, resourceType: {}, resourceId: {}",
                username, resourceType, resourceId, e);
            return false; // Fail closed for security
        }
    }

    /**
     * Validates that a user has a specific permission.
     *
     * @param username Authenticated username
     * @param permission Permission to check (e.g., "payment:refund")
     * @return true if user has the permission
     */
    public boolean hasPermission(String username, String permission) {
        try {
            UUID userId = parseUserId(username);
            return rbacService.hasPermission(userId, permission);
        } catch (Exception e) {
            log.error("SECURITY: Permission check failed", e);
            return false;
        }
    }

    /**
     * Validates that a user has any of the specified roles.
     *
     * @param username Authenticated username
     * @param roles Roles to check
     * @return true if user has at least one of the roles
     */
    public boolean hasAnyRole(String username, String... roles) {
        try {
            UUID userId = parseUserId(username);
            return rbacService.hasAnyRole(userId, roles);
        } catch (Exception e) {
            log.error("SECURITY: Role check failed", e);
            return false;
        }
    }
}
