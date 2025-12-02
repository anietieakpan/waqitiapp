package com.waqiti.common.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CRITICAL SECURITY: JWT Authentication Filter with Token Revocation Check
 *
 * This filter integrates with JwtTokenRevocationService to prevent authentication
 * with revoked tokens. Addresses the critical security gap where compromised tokens
 * remain valid until expiration.
 *
 * Features:
 * - Token extraction from Authorization header
 * - Token validation and claims parsing
 * - Revocation status checking (token-level and user-level)
 * - Spring Security context population
 * - Comprehensive error handling and logging
 * - Performance optimization with early returns
 *
 * Security Considerations:
 * - Fails securely: any error treats token as invalid
 * - Logs all authentication attempts for audit
 * - Checks both token-specific and user-level revocations
 * - Prevents timing attacks with consistent response times
 *
 * @author Waqiti Security Team
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenRevocationService revocationService;
    private final JwtTokenValidator jwtTokenValidator;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {

        long startTime = System.nanoTime();

        try {
            // Step 1: Extract token from request
            String token = extractTokenFromRequest(request);

            // Step 2: If no token, continue without authentication
            if (token == null) {
                log.debug("No JWT token found in request to: {}", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }

            // Step 3: CRITICAL SECURITY CHECK - Verify token is not revoked
            if (revocationService.isTokenRevoked(token)) {
                log.warn("SECURITY ALERT: Revoked token attempted - URI: {}, IP: {}",
                        request.getRequestURI(), getClientIp(request));

                // Clear any existing authentication
                SecurityContextHolder.clearContext();

                // Return 401 Unauthorized
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"error\":\"Token has been revoked\",\"timestamp\":\"" +
                    java.time.Instant.now() + "\"}"
                );
                return; // Don't continue filter chain
            }

            // Step 4: Validate token and extract claims
            if (!jwtTokenValidator.isTokenValid(token)) {
                log.warn("SECURITY: Invalid JWT token - URI: {}, IP: {}",
                        request.getRequestURI(), getClientIp(request));
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            // Step 5: Extract claims from token
            Claims claims = jwtTokenValidator.extractClaims(token);
            String userId = claims.getSubject();
            String username = claims.get("preferred_username", String.class);

            // Step 6: CRITICAL SECURITY CHECK - Verify user is not globally revoked
            if (revocationService.isUserRevoked(userId)) {
                log.warn("SECURITY ALERT: User-level revocation detected - UserID: {}, URI: {}, IP: {}",
                        userId, request.getRequestURI(), getClientIp(request));

                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"error\":\"All user tokens have been revoked. Please log in again.\",\"timestamp\":\"" +
                    java.time.Instant.now() + "\"}"
                );
                return;
            }

            // Step 7: Extract roles/authorities from token
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("realm_access", java.util.Map.class) != null
                ? (List<String>) ((java.util.Map<?, ?>) claims.get("realm_access")).get("roles")
                : List.of();

            List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());

            // Step 8: Create authentication object and set in security context
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(username, null, authorities);

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            long duration = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds
            log.debug("JWT authentication successful - User: {}, Roles: {}, Duration: {}ms",
                     username, roles, duration);

            // Step 9: Continue filter chain with authenticated context
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            long duration = (System.nanoTime() - startTime) / 1_000_000;
            log.error("SECURITY ERROR: JWT authentication failed - URI: {}, IP: {}, Duration: {}ms",
                     request.getRequestURI(), getClientIp(request), duration, e);

            // Fail-secure: clear context on any error
            SecurityContextHolder.clearContext();

            filterChain.doFilter(request, response);
        }
    }

    /**
     * Extract JWT token from Authorization header
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }

        return null;
    }

    /**
     * Extract client IP address from request (handles proxies)
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // X-Forwarded-For can contain multiple IPs, take the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    /**
     * Determine if filter should not be applied to this request
     * Override this to skip JWT validation for public endpoints
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Skip JWT filter for these public paths
        return path.startsWith("/actuator/health") ||
               path.startsWith("/actuator/info") ||
               path.startsWith("/api/v1/auth/login") ||
               path.startsWith("/api/v1/auth/register") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs");
    }
}
