package com.waqiti.auth.controller;

import com.waqiti.auth.config.CookieAuthenticationConfig;
import com.waqiti.auth.dto.LoginRequest;
import com.waqiti.auth.dto.LoginResponse;
import com.waqiti.auth.dto.RefreshTokenRequest;
import com.waqiti.auth.service.AuthenticationService;
import com.waqiti.auth.service.CsrfTokenService;
import com.waqiti.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Enterprise-grade authentication controller with HTTP-only cookie support.
 *
 * Security Implementation:
 * - HTTP-only cookies for token storage (XSS protection)
 * - CSRF tokens for state-changing operations
 * - Secure cookie attributes (Secure, SameSite=Strict)
 * - Token refresh mechanism
 * - Comprehensive audit logging
 * - Rate limiting applied
 *
 * Migration from localStorage to cookies:
 * - Old: Frontend stores tokens in localStorage (XSS vulnerable)
 * - New: Tokens stored in HTTP-only cookies (XSS immune)
 * - Frontend only needs to handle CSRF tokens
 *
 * API Endpoints:
 * - POST /api/auth/login - Login with credentials, returns cookies
 * - POST /api/auth/refresh - Refresh access token using refresh cookie
 * - POST /api/auth/logout - Logout and clear all cookies
 * - GET /api/auth/csrf-token - Get CSRF token for forms
 *
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2025-10-11
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Secure cookie-based authentication APIs")
public class SecureCookieAuthController {

    private final AuthenticationService authenticationService;
    private final CookieAuthenticationConfig cookieConfig;
    private final CsrfTokenService csrfTokenService;

    /**
     * Login endpoint - Sets HTTP-only cookies for tokens.
     *
     * Security Flow:
     * 1. Validate credentials
     * 2. Generate JWT access token (15min) and refresh token (7 days)
     * 3. Set tokens in HTTP-only cookies
     * 4. Generate and return CSRF token (readable by frontend)
     * 5. Log authentication event
     *
     * Response:
     * - Cookies: access_token, refresh_token (HTTP-only, Secure, SameSite=Strict)
     * - Body: User info, CSRF token, expiration times
     * - No tokens in response body (security)
     *
     * @param loginRequest Login credentials
     * @param request HTTP request
     * @param response HTTP response (for setting cookies)
     * @return Login response with user info and CSRF token
     */
    @PostMapping("/login")
    @Operation(
        summary = "Login with credentials",
        description = "Authenticates user and sets HTTP-only cookies for secure token storage"
    )
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        log.info("Login attempt for user: {} from IP: {}",
                loginRequest.getUsername(), request.getRemoteAddr());

        try {
            // Authenticate user and generate tokens
            LoginResponse loginResponse = authenticationService.authenticate(loginRequest);

            // Set access token in HTTP-only cookie (15 minutes)
            cookieConfig.setAccessTokenCookie(loginResponse.getAccessToken(), response);

            // Set refresh token in HTTP-only cookie (7 days)
            cookieConfig.setRefreshTokenCookie(loginResponse.getRefreshToken(), response);

            // Generate CSRF token and set in readable cookie
            String csrfToken = csrfTokenService.generateCsrfToken(loginResponse.getUserId());
            cookieConfig.setCsrfTokenCookie(csrfToken, response);

            // Remove tokens from response body (security - already in cookies)
            loginResponse.setAccessToken(null);
            loginResponse.setRefreshToken(null);

            // Add CSRF token to response for frontend to use in headers
            loginResponse.setCsrfToken(csrfToken);

            log.info("User {} authenticated successfully. Tokens set in HTTP-only cookies",
                    loginRequest.getUsername());

            return ResponseEntity.ok(ApiResponse.success(
                loginResponse,
                "Login successful. Authentication cookies have been set."
            ));

        } catch (Exception e) {
            log.error("Login failed for user {}: {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication failed: " + e.getMessage()));
        }
    }

    /**
     * Token refresh endpoint - Uses refresh token from HTTP-only cookie.
     *
     * Security Flow:
     * 1. Extract refresh token from HTTP-only cookie
     * 2. Validate refresh token
     * 3. Generate new access token
     * 4. Set new access token in HTTP-only cookie
     * 5. Optionally rotate refresh token
     *
     * Note: Refresh token cookie is restricted to path /api/auth/refresh
     *
     * @param request HTTP request (contains refresh token cookie)
     * @param response HTTP response (for setting new token cookie)
     * @return Success response if refresh succeeds
     */
    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh access token",
        description = "Uses refresh token from HTTP-only cookie to issue new access token"
    )
    public ResponseEntity<ApiResponse<Void>> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        log.debug("Token refresh requested from IP: {}", request.getRemoteAddr());

        try {
            // Extract refresh token from cookie
            String refreshToken = cookieConfig.getRefreshTokenFromCookie(request)
                    .orElseThrow(() -> new IllegalStateException("Refresh token cookie not found"));

            // Validate and refresh tokens
            String newAccessToken = authenticationService.refreshAccessToken(refreshToken);

            // Set new access token in HTTP-only cookie
            cookieConfig.setAccessTokenCookie(newAccessToken, response);

            // Optionally rotate refresh token (for enhanced security)
            // String newRefreshToken = authenticationService.rotateRefreshToken(refreshToken);
            // cookieConfig.setRefreshTokenCookie(newRefreshToken, response);

            log.info("Access token refreshed successfully");

            return ResponseEntity.ok(ApiResponse.success(
                null,
                "Access token refreshed successfully"
            ));

        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());

            // Clear invalid cookies
            cookieConfig.clearAuthenticationCookies(response);

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Token refresh failed. Please login again."));
        }
    }

    /**
     * Logout endpoint - Clears all authentication cookies.
     *
     * Security Flow:
     * 1. Invalidate tokens on server (add to blacklist)
     * 2. Clear all authentication cookies
     * 3. Log logout event
     *
     * @param request HTTP request
     * @param response HTTP response (for clearing cookies)
     * @return Success response
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "cookieAuth")
    @Operation(
        summary = "Logout user",
        description = "Invalidates tokens and clears all authentication cookies"
    )
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        log.info("Logout requested");

        try {
            // Extract tokens from cookies before clearing
            cookieConfig.getAccessTokenFromCookie(request).ifPresent(token -> {
                // Add to blacklist to prevent reuse
                authenticationService.invalidateToken(token);
            });

            cookieConfig.getRefreshTokenFromCookie(request).ifPresent(token -> {
                authenticationService.invalidateToken(token);
            });

            // Clear all authentication cookies
            cookieConfig.clearAuthenticationCookies(response);

            log.info("User logged out successfully. Cookies cleared.");

            return ResponseEntity.ok(ApiResponse.success(
                null,
                "Logout successful. Authentication cookies have been cleared."
            ));

        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage());

            // Still clear cookies even if server-side invalidation fails
            cookieConfig.clearAuthenticationCookies(response);

            return ResponseEntity.ok(ApiResponse.success(
                null,
                "Logged out (with warnings)"
            ));
        }
    }

    /**
     * Get CSRF token endpoint - Returns CSRF token for frontend forms.
     *
     * This endpoint is called by frontend to get CSRF token for
     * state-changing requests (POST, PUT, DELETE).
     *
     * @param request HTTP request
     * @param response HTTP response
     * @return CSRF token
     */
    @GetMapping("/csrf-token")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "cookieAuth")
    @Operation(
        summary = "Get CSRF token",
        description = "Returns CSRF token for frontend to include in request headers"
    )
    public ResponseEntity<ApiResponse<String>> getCsrfToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            // Get or generate CSRF token
            String csrfToken = cookieConfig.getCsrfTokenFromCookie(request)
                    .orElseGet(() -> {
                        String newToken = csrfTokenService.generateCsrfToken(
                            authenticationService.getCurrentUserId()
                        );
                        cookieConfig.setCsrfTokenCookie(newToken, response);
                        return newToken;
                    });

            return ResponseEntity.ok(ApiResponse.success(
                csrfToken,
                "CSRF token retrieved successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to get CSRF token: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to generate CSRF token"));
        }
    }

    /**
     * Validates the current session (useful for frontend auth checks).
     *
     * @return User session info if authenticated
     */
    @GetMapping("/validate-session")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "cookieAuth")
    @Operation(
        summary = "Validate current session",
        description = "Checks if current authentication cookies are valid"
    )
    public ResponseEntity<ApiResponse<Void>> validateSession() {
        // If this endpoint is reached, authentication filter has validated the cookie
        log.debug("Session validated successfully");

        return ResponseEntity.ok(ApiResponse.success(
            null,
            "Session is valid"
        ));
    }

    /**
     * Health check endpoint for authentication service.
     *
     * @return Service health status
     */
    @GetMapping("/health")
    @Operation(
        summary = "Authentication service health",
        description = "Returns health status of authentication service"
    )
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success(
            "healthy",
            "Authentication service is operational"
        ));
    }
}
