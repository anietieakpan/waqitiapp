package com.waqiti.user.security;

import com.waqiti.user.service.KeycloakAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * CRITICAL SECURITY COMPONENT: Ownership Validation for IDOR Prevention
 *
 * PURPOSE: This validator provides defense-in-depth protection against Insecure Direct Object
 * Reference (IDOR) vulnerabilities by explicitly validating that authenticated users can only
 * access their own resources unless they have admin privileges.
 *
 * SECURITY ARCHITECTURE:
 * - Layer 1: @PreAuthorize annotation (Spring Security SpEL)
 * - Layer 2: THIS VALIDATOR (explicit runtime validation)
 * - Layer 3: Service layer validation (business logic)
 *
 * Why Multi-Layer?
 * - If @PreAuthorize bean fails to load → this validator catches it
 * - If configuration error disables Spring Security → this validator catches it
 * - If developer forgets @PreAuthorize → this validator catches it
 *
 * COMPLIANCE:
 * - OWASP Top 10 A01:2021 - Broken Access Control (IDOR is #1 web vulnerability)
 * - GDPR Article 32 - Protection of personal data
 * - PCI DSS 7.1 - Limit access to cardholder data by business need
 * - SOX 404 - Access controls for financial data
 * - CCPA Section 1798.150 - Unauthorized access to personal information
 *
 * USAGE:
 * <pre>
 * {@code
 * @GetMapping("/{userId}")
 * public ResponseEntity<UserResponse> getUserById(@PathVariable UUID userId) {
 *     // Validate ownership BEFORE accessing data
 *     ownershipValidator.validateUserOwnership(userId, "getUserById");
 *     return ResponseEntity.ok(userService.getUserById(userId));
 * }
 * }
 * </pre>
 *
 * FINANCIAL IMPACT:
 * - Prevents €20M GDPR fines for unauthorized PII access
 * - Prevents class-action lawsuits for data breaches
 * - Prevents SOX compliance violations
 * - Prevents customer trust loss
 *
 * @author Waqiti Security Team
 * @since 2025-11-08 (CRITICAL-002 Fix)
 * @see <a href="https://owasp.org/Top10/A01_2021-Broken_Access_Control/">OWASP A01</a>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
public class OwnershipValidator {

    private final KeycloakAuthService keycloakAuthService;

    /**
     * Validates that the current authenticated user owns the requested resource or is an admin.
     *
     * @param requestedUserId The user ID being accessed
     * @param operation The operation name (for logging)
     * @throws ResponseStatusException with 403 Forbidden if unauthorized
     * @throws ResponseStatusException with 401 Unauthorized if not authenticated
     */
    public void validateUserOwnership(UUID requestedUserId, String operation) {
        try {
            UUID currentUserId = keycloakAuthService.getCurrentUserId();
            boolean isAdmin = keycloakAuthService.isAdmin();

            // Allow if user owns the resource OR is admin
            if (requestedUserId.equals(currentUserId) || isAdmin) {
                log.debug("SECURITY_CHECK_PASSED | operation={} | requestedUserId={} | currentUserId={} | isAdmin={} | result=AUTHORIZED",
                        operation, requestedUserId, currentUserId, isAdmin);
                return;
            }

            // SECURITY VIOLATION: User attempting to access another user's data
            log.warn("SECURITY_VIOLATION | event=IDOR_ATTEMPT | operation={} | requestedUserId={} | currentUserId={} | " +
                            "isAdmin={} | violation_type=UNAUTHORIZED_USER_ACCESS | severity=CRITICAL",
                    operation, requestedUserId, currentUserId, isAdmin);

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied: You do not have permission to access this resource"
            );

        } catch (ResponseStatusException e) {
            throw e; // Re-throw our own exception
        } catch (Exception e) {
            log.error("SECURITY_ERROR | operation={} | error=authentication_failure | message={}",
                    operation, e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }
    }

    /**
     * Validates user ownership (string version).
     */
    public void validateUserOwnership(String requestedUserId, String operation) {
        try {
            validateUserOwnership(UUID.fromString(requestedUserId), operation);
        } catch (IllegalArgumentException e) {
            log.warn("SECURITY_VIOLATION | event=INVALID_USER_ID | operation={} | requestedUserId={} | error={}",
                    operation, requestedUserId, e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid user ID format"
            );
        }
    }

    /**
     * Checks if the current user owns the resource OR is an admin (non-throwing version).
     *
     * @param requestedUserId The user ID being checked
     * @return true if authorized, false otherwise
     */
    public boolean isAuthorized(UUID requestedUserId) {
        try {
            UUID currentUserId = keycloakAuthService.getCurrentUserId();
            boolean isAdmin = keycloakAuthService.isAdmin();
            return requestedUserId.equals(currentUserId) || isAdmin;
        } catch (Exception e) {
            log.warn("SECURITY_CHECK_FAILED | requestedUserId={} | error={}", requestedUserId, e.getMessage());
            return false;
        }
    }

    /**
     * Validates that the current user is an admin.
     *
     * @param operation The operation name (for logging)
     * @throws ResponseStatusException with 403 Forbidden if not admin
     */
    public void requireAdmin(String operation) {
        try {
            boolean isAdmin = keycloakAuthService.isAdmin();

            if (isAdmin) {
                UUID currentUserId = keycloakAuthService.getCurrentUserId();
                log.debug("ADMIN_CHECK_PASSED | operation={} | currentUserId={} | result=AUTHORIZED",
                        operation, currentUserId);
                return;
            }

            UUID currentUserId = keycloakAuthService.getCurrentUserId();
            log.warn("SECURITY_VIOLATION | event=UNAUTHORIZED_ADMIN_ACCESS | operation={} | currentUserId={} | " +
                    "violation_type=NON_ADMIN_ACCESS_ATTEMPT | severity=HIGH", operation, currentUserId);

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin access required"
            );

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("SECURITY_ERROR | operation={} | error=authentication_failure | message={}",
                    operation, e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }
    }

    /**
     * Validates that the current user has a specific role.
     *
     * @param requiredRole The required role
     * @param operation The operation name (for logging)
     * @throws ResponseStatusException with 403 Forbidden if role not present
     */
    public void requireRole(String requiredRole, String operation) {
        try {
            boolean hasRole = keycloakAuthService.hasRole(requiredRole);

            if (hasRole) {
                UUID currentUserId = keycloakAuthService.getCurrentUserId();
                log.debug("ROLE_CHECK_PASSED | operation={} | currentUserId={} | requiredRole={} | result=AUTHORIZED",
                        operation, currentUserId, requiredRole);
                return;
            }

            UUID currentUserId = keycloakAuthService.getCurrentUserId();
            log.warn("SECURITY_VIOLATION | event=UNAUTHORIZED_ROLE_ACCESS | operation={} | currentUserId={} | " +
                            "requiredRole={} | violation_type=INSUFFICIENT_PRIVILEGES | severity=HIGH",
                    operation, currentUserId, requiredRole);

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Required role: " + requiredRole
            );

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("SECURITY_ERROR | operation={} | requiredRole={} | error=authentication_failure | message={}",
                    operation, requiredRole, e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }
    }

    /**
     * Gets the current authenticated user ID (convenience method).
     *
     * @return Current user ID
     * @throws ResponseStatusException with 401 Unauthorized if not authenticated
     */
    public UUID getCurrentUserId() {
        try {
            return keycloakAuthService.getCurrentUserId();
        } catch (Exception e) {
            log.error("SECURITY_ERROR | error=get_current_user_failed | message={}", e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }
    }

    /**
     * Checks if the current user is an admin (convenience method).
     *
     * @return true if admin, false otherwise
     */
    public boolean isAdmin() {
        try {
            return keycloakAuthService.isAdmin();
        } catch (Exception e) {
            log.warn("SECURITY_CHECK_FAILED | check=isAdmin | error={}", e.getMessage());
            return false;
        }
    }
}
