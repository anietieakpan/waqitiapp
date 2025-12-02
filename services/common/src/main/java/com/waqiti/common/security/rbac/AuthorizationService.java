package com.waqiti.common.security.rbac;

import com.waqiti.common.exceptions.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authorization Service for Role-Based Access Control (RBAC)
 *
 * Provides centralized permission checking and enforcement across the platform.
 * Integrates with Spring Security and JWT tokens from Keycloak.
 *
 * Usage:
 * <pre>
 * {@code
 * @Autowired
 * private AuthorizationService authService;
 *
 * public void processPayment() {
 *     authService.enforcePermission(Permission.PAYMENT_WRITE);
 *     // Method implementation
 * }
 * }
 * </pre>
 *
 * @author Waqiti Platform Engineering
 */
@Service
public class AuthorizationService {
    private static final Logger logger = LoggerFactory.getLogger(AuthorizationService.class);
    private static final String ROLES_CLAIM = "roles";
    private static final String USER_ID_CLAIM = "sub";

    /**
     * Check if current user has a specific permission
     *
     * @param permission The permission to check
     * @return true if user has permission, false otherwise
     */
    public boolean hasPermission(Permission permission) {
        try {
            Set<Role> userRoles = getCurrentUserRoles();

            if (userRoles.isEmpty()) {
                logger.warn("User has no roles assigned");
                return false;
            }

            // Check if any of the user's roles has the required permission
            boolean hasPermission = userRoles.stream()
                .anyMatch(role -> role.hasPermission(permission));

            if (hasPermission) {
                logger.debug("User {} has permission: {}", getCurrentUserId(), permission.getCode());
            } else {
                logger.warn("User {} lacks permission: {}", getCurrentUserId(), permission.getCode());
            }

            return hasPermission;

        } catch (Exception e) {
            logger.error("Error checking permission: {}", permission.getCode(), e);
            return false;
        }
    }

    /**
     * Check if current user has ALL of the specified permissions
     *
     * @param permissions The permissions to check
     * @return true if user has all permissions, false otherwise
     */
    public boolean hasAllPermissions(Permission... permissions) {
        for (Permission permission : permissions) {
            if (!hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if current user has ANY of the specified permissions
     *
     * @param permissions The permissions to check
     * @return true if user has at least one permission, false otherwise
     */
    public boolean hasAnyPermission(Permission... permissions) {
        for (Permission permission : permissions) {
            if (hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enforce that current user has a specific permission
     * Throws AccessDeniedException if user lacks permission
     *
     * @param permission The permission to enforce
     * @throws AccessDeniedException if user lacks permission
     */
    public void enforcePermission(Permission permission) {
        if (!hasPermission(permission)) {
            String userId = getCurrentUserId();
            String message = String.format(
                "Access denied: User %s lacks required permission: %s (%s)",
                userId,
                permission.getCode(),
                permission.getDescription()
            );

            logger.error("Authorization failed - {}", message);

            // Audit log
            logAuthorizationFailure(userId, permission, message);

            throw new AccessDeniedException(message);
        }
    }

    /**
     * Enforce that current user has a specific permission with custom message
     *
     * @param permission The permission to enforce
     * @param customMessage Custom error message
     * @throws AccessDeniedException if user lacks permission
     */
    public void enforcePermission(Permission permission, String customMessage) {
        if (!hasPermission(permission)) {
            String userId = getCurrentUserId();
            logger.error("Authorization failed - User: {}, Permission: {}, Message: {}",
                userId, permission.getCode(), customMessage);

            logAuthorizationFailure(userId, permission, customMessage);

            throw new AccessDeniedException(customMessage);
        }
    }

    /**
     * Get current authenticated user's ID from JWT token
     *
     * @return User ID
     */
    public String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                logger.warn("No authenticated user found");
                return "anonymous";
            }

            if (authentication.getPrincipal() instanceof Jwt jwt) {
                return jwt.getClaimAsString(USER_ID_CLAIM);
            }

            return authentication.getName();

        } catch (Exception e) {
            logger.error("Error extracting user ID from authentication context", e);
            return "unknown";
        }
    }

    /**
     * Get current authenticated user's roles from JWT token
     *
     * @return Set of user's roles
     */
    public Set<Role> getCurrentUserRoles() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                logger.warn("No authenticated user found");
                return Set.of();
            }

            if (authentication.getPrincipal() instanceof Jwt jwt) {
                List<String> roleClaims = jwt.getClaimAsStringList(ROLES_CLAIM);

                if (roleClaims == null || roleClaims.isEmpty()) {
                    logger.warn("User {} has no roles in JWT token", getCurrentUserId());
                    return Set.of();
                }

                // Convert string role names to Role enum
                return roleClaims.stream()
                    .map(this::parseRole)
                    .filter(role -> role != null)
                    .collect(Collectors.toSet());
            }

            logger.warn("Authentication principal is not a JWT token");
            return Set.of();

        } catch (Exception e) {
            logger.error("Error extracting roles from JWT token", e);
            return Set.of();
        }
    }

    /**
     * Get all permissions for current user (aggregated from all roles)
     *
     * @return Set of all user's permissions
     */
    public Set<Permission> getCurrentUserPermissions() {
        Set<Role> roles = getCurrentUserRoles();

        return roles.stream()
            .flatMap(role -> role.getPermissions().stream())
            .collect(Collectors.toSet());
    }

    /**
     * Check if current user has a specific role
     *
     * @param role The role to check
     * @return true if user has role, false otherwise
     */
    public boolean hasRole(Role role) {
        return getCurrentUserRoles().contains(role);
    }

    /**
     * Check if current user is an admin
     *
     * @return true if user has ADMIN role
     */
    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }

    /**
     * Check if current user is a compliance officer
     *
     * @return true if user has COMPLIANCE_OFFICER role
     */
    public boolean isComplianceOfficer() {
        return hasRole(Role.COMPLIANCE_OFFICER);
    }

    /**
     * Check if current user is a fraud analyst
     *
     * @return true if user has FRAUD_ANALYST role
     */
    public boolean isFraudAnalyst() {
        return hasRole(Role.FRAUD_ANALYST);
    }

    /**
     * Validate that current user owns a resource
     * Common pattern for preventing IDOR vulnerabilities
     *
     * @param resourceOwnerId The owner ID of the resource
     * @throws AccessDeniedException if user doesn't own resource and isn't admin
     */
    public void enforceOwnership(String resourceOwnerId) {
        String currentUserId = getCurrentUserId();

        // Admins can access any resource
        if (isAdmin()) {
            logger.debug("Admin user {} accessing resource owned by {}", currentUserId, resourceOwnerId);
            return;
        }

        if (!currentUserId.equals(resourceOwnerId)) {
            String message = String.format(
                "Access denied: User %s cannot access resource owned by %s",
                currentUserId,
                resourceOwnerId
            );

            logger.error("Ownership validation failed - {}", message);

            throw new AccessDeniedException(message);
        }
    }

    /**
     * Parse role string to Role enum
     *
     * @param roleString Role name from JWT
     * @return Role enum or null if invalid
     */
    private Role parseRole(String roleString) {
        try {
            // Handle both "ROLE_USER" and "USER" formats from Keycloak
            String normalizedRole = roleString.toUpperCase()
                .replace("ROLE_", "");

            return Role.valueOf(normalizedRole);
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown role in JWT token: {}", roleString);
            return null;
        }
    }

    /**
     * Log authorization failure for audit purposes
     *
     * @param userId User ID
     * @param permission Permission that was denied
     * @param message Error message
     */
    private void logAuthorizationFailure(String userId, Permission permission, String message) {
        // This will be picked up by audit logging framework
        logger.error(
            "AUDIT_AUTHORIZATION_FAILURE | user={} | permission={} | code={} | message={}",
            userId,
            permission.name(),
            permission.getCode(),
            message
        );
    }
}
