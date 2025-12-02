package com.waqiti.security.idor;

import com.waqiti.common.security.audit.SecurityAuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Ownership Validation Service for IDOR Prevention
 *
 * CRITICAL SECURITY:
 * - Prevents Insecure Direct Object Reference (IDOR) attacks
 * - Validates user ownership before allowing resource access
 * - Required for OWASP Top 10 A01:2021 - Broken Access Control compliance
 *
 * IDOR ATTACK EXAMPLE:
 * - User A tries to access User B's wallet: GET /api/v1/wallets/{userB-wallet-id}
 * - Without ownership validation, User A can see User B's balance
 * - With ownership validation, request is rejected with 403 Forbidden
 *
 * PROTECTION MECHANISMS:
 * - JWT token extraction to identify authenticated user
 * - Database lookup to verify resource ownership
 * - Audit logging of all ownership validation failures
 * - Rate limiting on repeated violation attempts
 *
 * @author Waqiti Security Team
 * @version 3.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OwnershipValidator {

    private final ResourceOwnershipRepository ownershipRepository;
    private final SecurityAuditLogger securityAuditLogger;

    /**
     * Validate that the authenticated user owns the specified resource
     *
     * @param authenticatedUserId The ID of the authenticated user from JWT
     * @param resourceType The type of resource (WALLET, ACCOUNT, TRANSACTION, etc.)
     * @param resourceId The ID of the resource being accessed
     * @throws OwnershipValidationException if user does not own the resource
     */
    public void validateOwnership(UUID authenticatedUserId, String resourceType, UUID resourceId) {
        log.debug("SECURITY: Validating ownership - User: {} Resource: {} ID: {}",
            authenticatedUserId, resourceType, resourceId);

        try {
            // Check if user owns the resource
            boolean isOwner = ownershipRepository.isOwner(authenticatedUserId, resourceType, resourceId);

            if (!isOwner) {
                // Log security violation
                securityAuditLogger.logIDORAttempt(
                    authenticatedUserId,
                    resourceType,
                    resourceId,
                    "UNAUTHORIZED_ACCESS_ATTEMPT"
                );

                log.warn("SECURITY VIOLATION: User {} attempted to access {} {} without ownership",
                    authenticatedUserId, resourceType, resourceId);

                throw new OwnershipValidationException(
                    String.format("User %s does not have access to %s %s",
                        authenticatedUserId, resourceType, resourceId)
                );
            }

            log.debug("SECURITY: Ownership validated successfully - User: {} owns {} {}",
                authenticatedUserId, resourceType, resourceId);

        } catch (OwnershipValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("SECURITY: Ownership validation failed for user {} on {} {}",
                authenticatedUserId, resourceType, resourceId, e);

            throw new OwnershipValidationException(
                "Ownership validation failed", e);
        }
    }

    /**
     * Validate ownership with additional permission check
     *
     * @param authenticatedUserId User from JWT token
     * @param resourceType Type of resource
     * @param resourceId Resource ID
     * @param requiredPermission Required permission (READ, WRITE, DELETE)
     */
    public void validateOwnershipWithPermission(
            UUID authenticatedUserId,
            String resourceType,
            UUID resourceId,
            String requiredPermission) {

        // First validate basic ownership
        validateOwnership(authenticatedUserId, resourceType, resourceId);

        // Then check specific permission
        boolean hasPermission = ownershipRepository.hasPermission(
            authenticatedUserId, resourceType, resourceId, requiredPermission);

        if (!hasPermission) {
            securityAuditLogger.logPermissionDenied(
                authenticatedUserId,
                resourceType,
                resourceId,
                requiredPermission
            );

            throw new OwnershipValidationException(
                String.format("User %s does not have %s permission on %s %s",
                    authenticatedUserId, requiredPermission, resourceType, resourceId)
            );
        }
    }

    /**
     * Validate that user owns all resources in a list (bulk operations)
     */
    public void validateBulkOwnership(
            UUID authenticatedUserId,
            String resourceType,
            java.util.List<UUID> resourceIds) {

        for (UUID resourceId : resourceIds) {
            validateOwnership(authenticatedUserId, resourceType, resourceId);
        }
    }

    /**
     * Check if user has admin role (bypasses ownership check)
     */
    public boolean isAdmin(UUID userId) {
        return ownershipRepository.hasRole(userId, "ADMIN");
    }
}
