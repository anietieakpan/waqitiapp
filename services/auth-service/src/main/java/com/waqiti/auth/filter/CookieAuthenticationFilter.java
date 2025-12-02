package com.waqiti.auth.filter;

import com.waqiti.auth.config.CookieAuthenticationConfig;
import com.waqiti.auth.service.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Enterprise-grade JWT authentication filter using HTTP-only cookies.
 *
 * Security Features:
 * - Extracts JWT from HTTP-only cookies (not Authorization header)
 * - Validates token signature and expiration
 * - Loads user details and authorities
 * - Sets Spring Security authentication context
 * - Comprehensive error handling and logging
 *
 * Process Flow:
 * 1. Extract access token from HTTP-only cookie
 * 2. Validate token (signature, expiration, not blacklisted)
 * 3. Extract username from token
 * 4. Load user details from database
 * 5. Create authentication object
 * 6. Set security context
 *
 * Compliance:
 * - OWASP Authentication Cheat Sheet
 * - PCI-DSS Requirement 8
 *
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2025-10-11
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CookieAuthenticationFilter extends OncePerRequestFilter {

    private final CookieAuthenticationConfig cookieConfig;
    private final JwtTokenService jwtTokenService;
    private final UserDetailsService userDetailsService;

    /**
     * Filter logic for cookie-based JWT authentication.
     *
     * @param request HTTP request
     * @param response HTTP response
     * @param filterChain Filter chain
     * @throws ServletException on servlet errors
     * @throws IOException on I/O errors
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // Extract JWT from HTTP-only cookie
            Optional<String> jwtToken = cookieConfig.getAccessTokenFromCookie(request);

            if (jwtToken.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
                String token = jwtToken.get();

                // Validate token and extract username
                if (jwtTokenService.validateToken(token)) {
                    String username = jwtTokenService.getUsernameFromToken(token);

                    // Load user details
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    // Verify token is valid for this user
                    if (jwtTokenService.validateTokenForUser(token, userDetails)) {
                        // Create authentication token
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );

                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request)
                        );

                        // Set authentication in security context
                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        log.debug("Successfully authenticated user: {} from cookie", username);

                        // Add user context headers for downstream services
                        response.setHeader("X-Authenticated-User", username);
                        response.setHeader("X-Auth-Method", "cookie");
                    } else {
                        log.warn("Token validation failed for user: {}", username);
                        handleInvalidToken(response, "Token validation failed");
                    }
                } else {
                    log.debug("Invalid or expired token in cookie");
                    handleInvalidToken(response, "Invalid or expired token");
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication from cookie: {}", e.getMessage(), e);
            handleAuthenticationError(response, e);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Determines if this filter should be applied to the request.
     *
     * @param request HTTP request
     * @return false for public endpoints, true otherwise
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();

        // Skip filter for public endpoints
        return path.startsWith("/api/auth/login") ||
               path.startsWith("/api/auth/register") ||
               path.startsWith("/api/auth/refresh") ||
               path.startsWith("/api/public/") ||
               path.startsWith("/actuator/health") ||
               path.startsWith("/actuator/info") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs");
    }

    /**
     * Handles invalid token scenario.
     *
     * @param response HTTP response
     * @param message Error message
     */
    private void handleInvalidToken(HttpServletResponse response, String message) {
        response.setHeader("X-Auth-Error", message);
        response.setHeader("X-Auth-Status", "invalid-token");
        // Don't clear cookies here - let refresh token flow handle it
    }

    /**
     * Handles authentication errors.
     *
     * @param response HTTP response
     * @param exception Exception that occurred
     */
    private void handleAuthenticationError(HttpServletResponse response, Exception exception) {
        response.setHeader("X-Auth-Error", "Authentication processing failed");
        response.setHeader("X-Auth-Exception", exception.getClass().getSimpleName());
    }
}
