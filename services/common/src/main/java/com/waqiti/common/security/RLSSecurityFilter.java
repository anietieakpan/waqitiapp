package com.waqiti.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * CRITICAL SECURITY COMPONENT: RLS Context Injection Filter
 *
 * PURPOSE:
 * Automatically extracts user context from JWT authentication and sets RLS context
 * for EVERY authenticated request. This ensures database-level security is enforced.
 *
 * FILTER CHAIN POSITION:
 * Must run AFTER Spring Security authentication filter but BEFORE any business logic.
 * Order = 10 ensures it runs early in the filter chain.
 *
 * INTEGRATION:
 * 1. Spring Security authenticates request (JWT validation)
 * 2. RLSSecurityFilter extracts user ID + role from JWT
 * 3. Sets RLS context via RLSContextValidator
 * 4. Business logic executes
 * 5. RLS context auto-cleared after request
 *
 * JWT CLAIMS REQUIRED:
 * - sub: User UUID (subject claim)
 * - role: User role (custom claim)
 * - session_id: Session identifier (custom claim, optional)
 *
 * @author Waqiti Security Team
 * @since 2025-10-31
 * @version 1.0.0
 */
@Slf4j
@Component
@Order(10) // Run early in filter chain, after authentication
@RequiredArgsConstructor
public class RLSSecurityFilter extends OncePerRequestFilter {

    private final RLSContextValidator contextValidator;

    /**
     * Processes each request to set RLS context for authenticated users.
     *
     * EXECUTION FLOW:
     * 1. Check if user is authenticated (Spring Security)
     * 2. Extract JWT from authentication
     * 3. Parse user ID and role from JWT claims
     * 4. Set RLS context via contextValidator
     * 5. Proceed with request
     * 6. Clear RLS context in finally block
     *
     * @param request HTTP request
     * @param response HTTP response
     * @param filterChain filter chain to continue
     */
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // Get authentication from Spring Security context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()) {
                // Check if this is a JWT-based authentication (Keycloak/OAuth2)
                if (authentication instanceof JwtAuthenticationToken) {
                    JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
                    Jwt jwt = jwtAuth.getToken();

                    // Extract user context from JWT claims
                    String userIdStr = jwt.getSubject(); // 'sub' claim = user UUID
                    String userRole = jwt.getClaimAsString("role"); // Custom 'role' claim
                    String sessionId = jwt.getClaimAsString("session_id"); // Custom claim

                    // Fallback: try 'realm_access.roles' for Keycloak
                    if (userRole == null) {
                        userRole = extractKeycloakRole(jwt);
                    }

                    // Validate and parse user ID
                    UUID userId;
                    try {
                        userId = UUID.fromString(userIdStr);
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid user ID in JWT token: {}", userIdStr);
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                            "Invalid user ID in authentication token");
                        return;
                    }

                    // Set RLS context for this request
                    contextValidator.setContext(userId, userRole, sessionId);

                    log.debug("RLS context set from JWT for user: {}, role: {}, endpoint: {} {}",
                        userId, userRole, request.getMethod(), request.getRequestURI());

                } else {
                    // Non-JWT authentication (e.g., API key, basic auth)
                    // Try to extract user ID from principal
                    String principalName = authentication.getName();

                    try {
                        UUID userId = UUID.fromString(principalName);
                        String userRole = extractRoleFromAuthorities(authentication);

                        contextValidator.setContext(userId, userRole, null);

                        log.debug("RLS context set from non-JWT auth for user: {}", userId);

                    } catch (IllegalArgumentException e) {
                        log.warn("Could not parse user ID from principal: {}", principalName);
                        // Continue without RLS context (read-only endpoints may not need it)
                    }
                }
            } else {
                // Unauthenticated request - no RLS context set
                // RLS policies will deny access to protected data
                log.trace("No authentication found for request: {} {}",
                    request.getMethod(), request.getRequestURI());
            }

            // Continue filter chain
            filterChain.doFilter(request, response);

        } catch (RLSContextValidator.RLSContextValidationException e) {
            log.error("RLS context validation failed: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Security context validation failed");

        } finally {
            // CRITICAL: Clear RLS context to prevent leakage to next request
            contextValidator.clearContext();
            log.trace("RLS context cleared after request completion");
        }
    }

    /**
     * Extracts role from Keycloak JWT token.
     *
     * Keycloak stores roles in:
     * - realm_access.roles[] array
     * - resource_access.{client_id}.roles[] array
     *
     * We prioritize realm roles and take the first role found.
     *
     * @param jwt JWT token
     * @return user role or "USER" as default
     */
    private String extractKeycloakRole(Jwt jwt) {
        try {
            // Try realm_access.roles
            Object realmAccess = jwt.getClaim("realm_access");
            if (realmAccess instanceof java.util.Map) {
                java.util.Map<String, Object> realmAccessMap = (java.util.Map<String, Object>) realmAccess;
                Object roles = realmAccessMap.get("roles");
                if (roles instanceof java.util.List) {
                    java.util.List<String> rolesList = (java.util.List<String>) roles;
                    if (!rolesList.isEmpty()) {
                        // Return highest priority role
                        if (rolesList.contains("ADMIN")) return "ADMIN";
                        if (rolesList.contains("COMPLIANCE_OFFICER")) return "COMPLIANCE_OFFICER";
                        if (rolesList.contains("MERCHANT")) return "MERCHANT";
                        return rolesList.get(0); // Default to first role
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract Keycloak roles from JWT", e);
        }

        return "USER"; // Default role if extraction fails
    }

    /**
     * Extracts role from Spring Security GrantedAuthority.
     *
     * @param authentication Spring Security authentication
     * @return user role or "USER" as default
     */
    private String extractRoleFromAuthorities(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(auth -> auth.getAuthority())
            .filter(auth -> auth.startsWith("ROLE_"))
            .map(auth -> auth.substring(5)) // Remove "ROLE_" prefix
            .findFirst()
            .orElse("USER");
    }

    /**
     * Determines if this filter should run for the given request.
     *
     * Skip filter for:
     * - Static resources (/static/**, /public/**)
     * - Health check endpoints (/actuator/health)
     * - Swagger UI (/swagger-ui/**)
     *
     * @param request HTTP request
     * @return true if filter should NOT run
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.startsWith("/static/")
            || path.startsWith("/public/")
            || path.equals("/actuator/health")
            || path.startsWith("/swagger-ui/")
            || path.startsWith("/v3/api-docs/");
    }
}
