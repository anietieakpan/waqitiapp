package com.waqiti.security.filter;

import com.waqiti.security.config.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * JWT Authentication Filter for additional JWT processing
 * 
 * Handles request correlation, user context, and security logging
 */
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final KeycloakProperties keycloakProperties;
    
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String USER_ID_HEADER = "X-User-ID";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        
        try {
            // Add correlation ID if not present
            addCorrelationId(request, response);
            
            // Add request ID
            addRequestId(request, response);
            
            // Extract and validate JWT token if present
            String token = extractToken(request);
            if (!token.isEmpty()) {
                processJwtToken(request, response, token);
            }
            
            // Log security events if enabled
            if (keycloakProperties.getSecurity().isLogSecurityEvents()) {
                logSecurityEvent(request, !token.isEmpty());
            }
            
        } catch (Exception e) {
            log.error("Error in JWT authentication filter", e);
            // Don't block the request, let Spring Security handle authentication
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * Add correlation ID for request tracing
     */
    private void addCorrelationId(HttpServletRequest request, HttpServletResponse response) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        
        // Add to MDC for logging
        org.slf4j.MDC.put("correlationId", correlationId);
    }
    
    /**
     * Add request ID for individual request tracking
     */
    private void addRequestId(HttpServletRequest request, HttpServletResponse response) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        
        response.setHeader(REQUEST_ID_HEADER, requestId);
        
        // Add to MDC for logging
        org.slf4j.MDC.put("requestId", requestId);
    }
    
    /**
     * Extract JWT token from Authorization header
     */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        // Return empty string instead of null to indicate no token present
        return "";
    }
    
    /**
     * Process JWT token and add user context
     */
    private void processJwtToken(HttpServletRequest request, HttpServletResponse response, String token) {
        try {
            // Add user ID to response headers if authentication is successful
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                String userId = authentication.getName();
                if (StringUtils.hasText(userId)) {
                    response.setHeader(USER_ID_HEADER, userId);
                    
                    // Add to MDC for logging
                    org.slf4j.MDC.put("userId", userId);
                }
            }
            
        } catch (Exception e) {
            log.debug("Error processing JWT token in filter", e);
        }
    }
    
    /**
     * Log security events for audit purposes
     */
    private void logSecurityEvent(HttpServletRequest request, boolean hasToken) {
        if (shouldLogRequest(request)) {
            log.info("Security event: {} {} - Token present: {}, IP: {}, User-Agent: {}", 
                    request.getMethod(),
                    request.getRequestURI(),
                    hasToken,
                    getClientIpAddress(request),
                    request.getHeader("User-Agent"));
        }
    }
    
    /**
     * Determine if request should be logged
     */
    private boolean shouldLogRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        
        // Don't log health checks and other noise
        return !uri.contains("/actuator/health") &&
               !uri.contains("/health") &&
               !uri.contains("/metrics") &&
               !uri.contains("/prometheus");
    }
    
    /**
     * Get client IP address considering proxy headers.
     *
     * SECURITY NOTE: X-Forwarded-For headers can be spoofed by clients.
     * This implementation trusts proxy headers only for logging purposes.
     * For security-critical decisions (rate limiting, fraud detection),
     * use the remote address or configure trusted proxy IP ranges.
     *
     * In production:
     * - Configure your load balancer to overwrite X-Forwarded-For
     * - Use X-Real-IP from a trusted reverse proxy only
     * - Validate that requests come from known proxy IPs
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // Log both claimed and actual IP for audit purposes
        String remoteAddr = request.getRemoteAddr();

        // Only trust proxy headers for informational/logging purposes
        // Security-critical operations should use remoteAddr or validate proxy chain
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            // Take first IP in chain (original client)
            String claimedIp = xForwardedFor.split(",")[0].trim();
            // Log discrepancy for security monitoring
            if (!claimedIp.equals(remoteAddr)) {
                log.debug("IP discrepancy - X-Forwarded-For: {}, RemoteAddr: {}", claimedIp, remoteAddr);
            }
            return claimedIp;
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp;
        }

        return remoteAddr;
    }
    
    @Override
    public void destroy() {
        // Clear MDC on filter destruction
        org.slf4j.MDC.clear();
        super.destroy();
    }
}