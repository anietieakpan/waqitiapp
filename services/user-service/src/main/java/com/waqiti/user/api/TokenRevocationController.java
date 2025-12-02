package com.waqiti.user.api;

import com.waqiti.common.security.jwt.JwtTokenRevocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * CRITICAL SECURITY: Token Revocation Management Controller
 *
 * Provides REST endpoints for token revocation operations.
 * Integrates with JwtTokenRevocationService for distributed token blacklisting.
 *
 * Security:
 * - All endpoints require authentication
 * - Admin-only endpoints for user-level revocation
 * - Comprehensive audit logging
 * - Rate limiting applied (configured in SecurityConfig)
 *
 * @author Waqiti Security Team
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Token revocation and authentication management")
public class TokenRevocationController {

    private final JwtTokenRevocationService revocationService;

    /**
     * Logout endpoint - revokes current user's token
     *
     * @param token Bearer token from Authorization header
     * @param authentication Current user's authentication
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Logout - Revoke current token",
        description = "Revokes the current user's JWT token, preventing further use until expiration",
        security = @SecurityRequirement(name = "bearer-jwt")
    )
    public ResponseEntity<?> logout(
            @RequestHeader("Authorization") String token,
            Authentication authentication) {

        try {
            // Extract token from "Bearer <token>" format
            String jwt = token.replace("Bearer ", "");
            String username = authentication.getName();

            // Revoke token with 24-hour TTL (adjust based on your token expiration)
            revocationService.revokeToken(
                jwt,
                Duration.ofHours(24),
                "User logout",
                username
            );

            log.info("SECURITY: User logged out successfully - Username: {}", username);

            return ResponseEntity.ok(new LogoutResponse(
                "Logout successful",
                "Your session has been terminated",
                java.time.Instant.now()
            ));

        } catch (Exception e) {
            log.error("SECURITY ERROR: Logout failed", e);
            return ResponseEntity.internalServerError()
                .body(new ErrorResponse("Logout failed", e.getMessage()));
        }
    }

    /**
     * Logout from all devices - revokes all tokens for current user
     *
     * @param authentication Current user's authentication
     */
    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Logout from all devices",
        description = "Revokes all JWT tokens for the current user across all devices",
        security = @SecurityRequirement(name = "bearer-jwt")
    )
    public ResponseEntity<?> logoutAllDevices(Authentication authentication) {

        try {
            String username = authentication.getName();

            // Get user ID from authentication (assuming it's in the principal)
            String userId = getUserIdFromAuthentication(authentication);

            // Revoke all user tokens with 30-day TTL (maximum token lifetime)
            revocationService.revokeAllUserTokens(
                userId,
                Duration.ofDays(30),
                "User-initiated logout from all devices",
                username
            );

            log.warn("SECURITY: All devices logged out for user - Username: {}, UserID: {}",
                    username, userId);

            return ResponseEntity.ok(new LogoutResponse(
                "Logout successful",
                "You have been logged out from all devices. Please log in again.",
                java.time.Instant.now()
            ));

        } catch (Exception e) {
            log.error("SECURITY ERROR: Logout all devices failed", e);
            return ResponseEntity.internalServerError()
                .body(new ErrorResponse("Logout failed", e.getMessage()));
        }
    }

    /**
     * Admin endpoint: Revoke all tokens for a specific user
     *
     * Use cases:
     * - Security incident response
     * - Account compromise
     * - Password reset enforcement
     *
     * @param userId User ID whose tokens should be revoked
     * @param authentication Admin user performing the action
     */
    @PostMapping("/admin/revoke-user-tokens/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Admin: Revoke all tokens for a user",
        description = "Revokes all JWT tokens for a specified user (admin only)",
        security = @SecurityRequirement(name = "bearer-jwt")
    )
    public ResponseEntity<?> revokeUserTokens(
            @PathVariable String userId,
            @RequestParam(required = false) String reason,
            Authentication authentication) {

        try {
            String adminUsername = authentication.getName();

            String revocationReason = reason != null
                ? reason
                : "Admin-initiated revocation";

            revocationService.revokeAllUserTokens(
                userId,
                Duration.ofDays(30),
                revocationReason,
                "admin:" + adminUsername
            );

            log.warn("SECURITY ADMIN: All tokens revoked for user - UserID: {}, Reason: {}, Admin: {}",
                    userId, revocationReason, adminUsername);

            return ResponseEntity.ok(new AdminRevocationResponse(
                "User tokens revoked",
                userId,
                revocationReason,
                adminUsername,
                java.time.Instant.now()
            ));

        } catch (Exception e) {
            log.error("SECURITY ERROR: Admin revocation failed - UserID: {}", userId, e);
            return ResponseEntity.internalServerError()
                .body(new ErrorResponse("Revocation failed", e.getMessage()));
        }
    }

    /**
     * Admin endpoint: Clear user revocation (restore access)
     *
     * @param userId User ID whose revocation should be cleared
     * @param authentication Admin user performing the action
     */
    @PostMapping("/admin/restore-user-tokens/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Admin: Restore user tokens",
        description = "Clears user-level token revocation (admin only)",
        security = @SecurityRequirement(name = "bearer-jwt")
    )
    public ResponseEntity<?> restoreUserTokens(
            @PathVariable String userId,
            Authentication authentication) {

        try {
            String adminUsername = authentication.getName();

            revocationService.clearUserRevocation(userId, "admin:" + adminUsername);

            log.info("SECURITY ADMIN: User revocation cleared - UserID: {}, Admin: {}",
                    userId, adminUsername);

            return ResponseEntity.ok(new AdminRevocationResponse(
                "User revocation cleared",
                userId,
                "Revocation cleared by admin",
                adminUsername,
                java.time.Instant.now()
            ));

        } catch (Exception e) {
            log.error("SECURITY ERROR: Failed to restore user tokens - UserID: {}", userId, e);
            return ResponseEntity.internalServerError()
                .body(new ErrorResponse("Restoration failed", e.getMessage()));
        }
    }

    /**
     * Admin endpoint: Get revocation statistics
     */
    @GetMapping("/admin/revocation-stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Admin: Get revocation statistics",
        description = "Returns current revocation metrics (admin only)",
        security = @SecurityRequirement(name = "bearer-jwt")
    )
    public ResponseEntity<?> getRevocationStatistics() {

        try {
            JwtTokenRevocationService.RevocationStatistics stats = revocationService.getStatistics();

            log.debug("SECURITY ADMIN: Revocation statistics retrieved - Tokens: {}, Users: {}",
                    stats.getRevokedTokenCount(), stats.getRevokedUserCount());

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("SECURITY ERROR: Failed to retrieve revocation statistics", e);
            return ResponseEntity.internalServerError()
                .body(new ErrorResponse("Statistics retrieval failed", e.getMessage()));
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Extract user ID from authentication object
     */
    private String getUserIdFromAuthentication(Authentication authentication) {
        // Implementation depends on your authentication setup
        // This is a placeholder - adjust based on your JWT claims structure

        if (authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails) {
            org.springframework.security.core.userdetails.UserDetails userDetails =
                (org.springframework.security.core.userdetails.UserDetails) authentication.getPrincipal();
            return userDetails.getUsername(); // Or extract from custom UserDetails implementation
        }

        return authentication.getName();
    }

    // ========== RESPONSE DTOs ==========

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class LogoutResponse {
        private String status;
        private String message;
        private java.time.Instant timestamp;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class AdminRevocationResponse {
        private String status;
        private String userId;
        private String reason;
        private String performedBy;
        private java.time.Instant timestamp;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ErrorResponse {
        private String error;
        private String message;
    }
}
