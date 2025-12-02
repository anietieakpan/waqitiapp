package com.waqiti.voice.security.access;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Security Context Service
 *
 * CRITICAL SECURITY: Extracts authenticated user information from Spring Security context
 *
 * Used for:
 * - Row-level security enforcement
 * - Audit logging
 * - Authorization checks
 * - User data isolation
 *
 * Security Model:
 * - JWT-based authentication (Keycloak)
 * - User ID extracted from JWT "sub" claim
 * - All data access must validate against authenticated user
 *
 * Prevents:
 * - Horizontal privilege escalation (User A accessing User B's data)
 * - Unauthorized data access
 * - IDOR (Insecure Direct Object Reference) vulnerabilities
 *
 * Compliance:
 * - GDPR Article 32 (Access control)
 * - PCI-DSS Requirement 7 (Restrict access to cardholder data)
 * - SOC 2 (Access control)
 */
@Slf4j
@Service
public class SecurityContextService {

    /**
     * Get authenticated user ID from security context
     *
     * @return User UUID from JWT "sub" claim
     * @throws UnauthorizedException if no authenticated user
     */
    public UUID getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("SECURITY: No authenticated user in security context");
            throw new UnauthorizedException("Authentication required");
        }

        // Handle JWT authentication (Keycloak)
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            Jwt jwt = jwtAuth.getToken();

            // Extract user ID from "sub" claim
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) {
                log.error("SECURITY: JWT missing 'sub' claim");
                throw new UnauthorizedException("Invalid token: missing user ID");
            }

            try {
                UUID userId = UUID.fromString(subject);
                log.debug("Authenticated user ID: {}", userId);
                return userId;
            } catch (IllegalArgumentException e) {
                log.error("SECURITY: Invalid user ID format in JWT: {}", subject);
                throw new UnauthorizedException("Invalid token: malformed user ID");
            }
        }

        // Fallback: Try to extract from principal
        Object principal = authentication.getPrincipal();
        if (principal instanceof String) {
            try {
                return UUID.fromString((String) principal);
            } catch (IllegalArgumentException e) {
                log.error("SECURITY: Cannot parse user ID from principal: {}", principal);
                throw new UnauthorizedException("Invalid authentication principal");
            }
        }

        log.error("SECURITY: Unsupported authentication type: {}", authentication.getClass().getName());
        throw new UnauthorizedException("Unsupported authentication type");
    }

    /**
     * Get authenticated user ID as string
     */
    public String getAuthenticatedUserIdAsString() {
        return getAuthenticatedUserId().toString();
    }

    /**
     * Validate that the requested user ID matches the authenticated user
     *
     * CRITICAL: Prevents horizontal privilege escalation
     *
     * @param requestedUserId The user ID being accessed
     * @throws UnauthorizedException if user IDs don't match
     */
    public void validateUserAccess(UUID requestedUserId) {
        UUID authenticatedUserId = getAuthenticatedUserId();

        if (!authenticatedUserId.equals(requestedUserId)) {
            log.error("SECURITY VIOLATION: User {} attempted to access data for user {}",
                    authenticatedUserId, requestedUserId);
            throw new UnauthorizedException(
                    "Access denied: You can only access your own data"
            );
        }

        log.debug("User access validated: {} accessing own data", authenticatedUserId);
    }

    /**
     * Check if user has specific role
     *
     * @param role Role name (e.g., "ADMIN", "USER")
     * @return true if user has role
     */
    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }

    /**
     * Check if user has admin role
     */
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * Get JWT token from security context
     *
     * @return JWT token or null
     */
    public Jwt getJwtToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken) {
            return ((JwtAuthenticationToken) authentication).getToken();
        }

        return null;
    }

    /**
     * Get claim from JWT token
     *
     * @param claimName Claim name
     * @return Claim value or null
     */
    public Object getJwtClaim(String claimName) {
        Jwt jwt = getJwtToken();
        if (jwt == null) {
            return null;
        }

        return jwt.getClaim(claimName);
    }

    /**
     * Check if request is authenticated
     */
    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }

    /**
     * Validate user access or allow admin override
     *
     * Admins can access any user's data for support purposes
     *
     * @param requestedUserId The user ID being accessed
     * @throws UnauthorizedException if access denied
     */
    public void validateUserAccessOrAdmin(UUID requestedUserId) {
        if (isAdmin()) {
            UUID adminUserId = getAuthenticatedUserId();
            log.info("ADMIN ACCESS: User {} (admin) accessing data for user {}",
                    adminUserId, requestedUserId);
            return;
        }

        validateUserAccess(requestedUserId);
    }

    /**
     * Unauthorized access exception
     */
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}
