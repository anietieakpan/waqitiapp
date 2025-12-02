package com.waqiti.dispute.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * JWT User ID Validation Interceptor - PRODUCTION READY
 *
 * Validates that the JWT token's user ID matches the authenticated user
 * Prevents token substitution attacks and unauthorized access
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@Component
@Slf4j
public class JwtUserIdValidationInterceptor implements HandlerInterceptor {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Skip validation for public endpoints
        String path = request.getRequestURI();
        if (isPublicEndpoint(path)) {
            return true;
        }

        try {
            // Extract JWT token from Authorization header
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Missing or invalid Authorization header for path: {}", path);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"Missing or invalid Authorization header\"}");
                return false;
            }

            String token = authHeader.substring(7);

            // Parse JWT and extract claims
            Claims claims = Jwts.parser()
                    .setSigningKey(jwtSecret)
                    .parseClaimsJws(token)
                    .getBody();

            String jwtUserId = claims.getSubject();
            if (jwtUserId == null) {
                jwtUserId = claims.get("userId", String.class);
            }

            // Get authenticated user ID from SecurityContext
            String authenticatedUserId = org.springframework.security.core.context.SecurityContextHolder
                    .getContext()
                    .getAuthentication()
                    .getName();

            // Validate that JWT user ID matches authenticated user ID
            if (jwtUserId == null || !jwtUserId.equals(authenticatedUserId)) {
                log.error("JWT user ID mismatch! JWT userId: {}, Authenticated userId: {}, Path: {}",
                        jwtUserId, authenticatedUserId, path);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"Token user ID does not match authenticated user\"}");
                return false;
            }

            log.debug("JWT validation successful for user: {} on path: {}", authenticatedUserId, path);
            return true;

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("Expired JWT token for path: {}", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Token expired\"}");
            return false;

        } catch (io.jsonwebtoken.MalformedJwtException | io.jsonwebtoken.SignatureException e) {
            log.error("Invalid JWT token for path: {}", path, e);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Invalid token\"}");
            return false;

        } catch (Exception e) {
            log.error("JWT validation error for path: {}", path, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Internal server error during token validation\"}");
            return false;
        }
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/actuator") ||
               path.startsWith("/health") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs");
    }
}
