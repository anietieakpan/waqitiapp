package com.waqiti.investment.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Security Utilities - User access validation
 *
 * CRITICAL SECURITY: Ensures users can only access their own resources.
 *
 * Security Controls:
 * - Validates authenticated user matches requested resource owner
 * - Prevents horizontal privilege escalation
 * - Logs all access attempts for audit
 * - Supports admin override for compliance purposes
 *
 * Compliance:
 * - GDPR Article 32 (Security of processing)
 * - SOC 2 Type II access control requirements
 * - NIST SP 800-53 AC-3 (Access Enforcement)
 * - IRS Publication 1075 (Safeguarding Tax Information)
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-02
 */
@Component
@Slf4j
public class SecurityUtils {

    /**
     * Validate that the authenticated user has access to the requested user's resources.
     *
     * CRITICAL SECURITY: Prevents horizontal privilege escalation.
     * - Users can access their own resources
     * - Admins with ROLE_ADMIN or ROLE_TAX_ADMIN can access any user's resources
     *
     * @param requestedUserId UUID of the user whose resources are being accessed
     * @throws AccessDeniedException if authenticated user does not have access
     */
    public static void validateUserAccess(UUID requestedUserId) {
        if (requestedUserId == null) {
            log.error("SECURITY: Null userId in access validation");
            throw new AccessDeniedException("Invalid user ID");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("SECURITY VIOLATION: Unauthenticated access attempt for user: {}", requestedUserId);
            throw new AccessDeniedException("Authentication required");
        }

        // Check if user is admin (can access any user's data for compliance purposes)
        boolean isAdmin = authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN") ||
                            auth.getAuthority().equals("ROLE_TAX_ADMIN") ||
                            auth.getAuthority().equals("ROLE_COMPLIANCE_OFFICER"));

        if (isAdmin) {
            log.info("SECURITY AUDIT: Admin access granted to user {} resources by admin: {}",
                    requestedUserId, authentication.getName());
            return;
        }

        // Extract authenticated user ID from principal
        String authenticatedUserIdStr = extractUserIdFromAuthentication(authentication);

        if (authenticatedUserIdStr == null) {
            log.error("SECURITY VIOLATION: Could not extract user ID from authentication");
            throw new AccessDeniedException("Invalid authentication principal");
        }

        UUID authenticatedUserId;
        try {
            authenticatedUserId = UUID.fromString(authenticatedUserIdStr);
        } catch (IllegalArgumentException e) {
            log.error("SECURITY VIOLATION: Invalid UUID format in authentication: {}", authenticatedUserIdStr);
            throw new AccessDeniedException("Invalid user ID format");
        }

        // Verify authenticated user matches requested user
        if (!authenticatedUserId.equals(requestedUserId)) {
            log.error("SECURITY VIOLATION: User {} attempted to access resources of user {}",
                    authenticatedUserId, requestedUserId);
            throw new AccessDeniedException("Access denied: cannot access another user's resources");
        }

        log.debug("SECURITY: Access validated for user: {}", authenticatedUserId);
    }

    /**
     * Check if the authenticated user is the owner of the resource.
     *
     * @param resourceOwnerId UUID of the resource owner
     * @return true if authenticated user is the owner or admin, false otherwise
     */
    public static boolean isResourceOwner(UUID resourceOwnerId) {
        try {
            validateUserAccess(resourceOwnerId);
            return true;
        } catch (AccessDeniedException e) {
            return false;
        }
    }

    /**
     * Get the authenticated user's ID.
     *
     * @return UUID of authenticated user
     * @throws AccessDeniedException if no authentication or invalid principal
     */
    public static UUID getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }

        String userIdStr = extractUserIdFromAuthentication(authentication);

        if (userIdStr == null) {
            throw new AccessDeniedException("Could not extract user ID from authentication");
        }

        try {
            return UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("Invalid user ID format in authentication");
        }
    }

    /**
     * Extract user ID from authentication principal.
     *
     * Supports multiple authentication types:
     * - JWT tokens with "sub" claim containing UUID
     * - OAuth2 authentication with user details
     * - Custom UserPrincipal objects
     *
     * @param authentication Spring Security authentication object
     * @return User ID string, or null if not found
     */
    private static String extractUserIdFromAuthentication(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        // JWT token with sub claim
        if (principal instanceof org.springframework.security.oauth2.jwt.Jwt) {
            org.springframework.security.oauth2.jwt.Jwt jwt = (org.springframework.security.oauth2.jwt.Jwt) principal;
            return jwt.getSubject(); // Assumes sub claim contains UUID
        }

        // OAuth2 user
        if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
            org.springframework.security.oauth2.core.user.OAuth2User oauth2User =
                (org.springframework.security.oauth2.core.user.OAuth2User) principal;
            Object userId = oauth2User.getAttribute("user_id");
            if (userId != null) {
                return userId.toString();
            }
        }

        // Custom UserDetails with userId
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            // Assumes username is the UUID
            return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
        }

        // Fallback: use name
        if (principal instanceof String) {
            return (String) principal;
        }

        return authentication.getName();
    }

    /**
     * Check if the authenticated user has admin role.
     *
     * @return true if user has ROLE_ADMIN or ROLE_TAX_ADMIN
     */
    public static boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN") ||
                            auth.getAuthority().equals("ROLE_TAX_ADMIN") ||
                            auth.getAuthority().equals("ROLE_COMPLIANCE_OFFICER"));
    }
}
