package com.waqiti.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Service for managing and accessing security context information
 */
@Service
public class SecurityContextService {
    
    /**
     * Get current authenticated user ID
     */
    public String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            Object principal = auth.getPrincipal();
            if (principal instanceof UserDetails) {
                return ((UserDetails) principal).getUsername();
            } else if (principal instanceof String) {
                return (String) principal;
            }
        }
        return "anonymous";
    }
    
    /**
     * Get current authentication object
     */
    public Authentication getCurrentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
    
    /**
     * Check if current user is authenticated
     */
    public boolean isAuthenticated() {
        Authentication auth = getCurrentAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymous".equals(auth.getPrincipal());
    }
    
    /**
     * Get current user roles
     */
    public String[] getCurrentUserRoles() {
        Authentication auth = getCurrentAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            return auth.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .toArray(String[]::new);
        }
        return new String[0];
    }
    
    /**
     * Get current session ID
     */
    public String getCurrentSessionId() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null && request.getSession(false) != null) {
                return request.getSession(false).getId();
            }
        } catch (Exception e) {
            // Ignore - not in request context
        }
        return null;
    }
    
    /**
     * Get correlation ID from request headers or generate one
     */
    public String getCorrelationId() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                String correlationId = request.getHeader("X-Correlation-ID");
                if (correlationId != null) {
                    return correlationId;
                }
                correlationId = request.getHeader("X-Request-ID");
                if (correlationId != null) {
                    return correlationId;
                }
            }
        } catch (Exception e) {
            // Ignore - not in request context
        }
        return java.util.UUID.randomUUID().toString();
    }
    
    /**
     * Get client IP address from request
     */
    public String getClientIpAddress() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                // Check X-Forwarded-For header first
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                
                // Check X-Real-IP header
                String xRealIp = request.getHeader("X-Real-IP");
                if (xRealIp != null && !xRealIp.isEmpty()) {
                    return xRealIp;
                }
                
                // Fall back to remote address
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            // Ignore - not in request context
        }
        return "unknown";
    }
    
    /**
     * Get user agent from request
     */
    public String getUserAgent() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                return request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            // Ignore - not in request context
        }
        return "unknown";
    }
    
    /**
     * Get current HTTP request
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest();
        } catch (IllegalStateException e) {
            // Not in request context
            return null;
        }
    }
}