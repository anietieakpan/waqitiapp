package com.waqiti.common.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Helper class for extracting security context information
 */
public class SecurityContextHolder {
    
    /**
     * Get session ID from security context
     */
    public static String getSessionId() {
        SecurityContext context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        
        if (context != null && context.getAuthentication() != null) {
            Authentication auth = context.getAuthentication();
            
            // Try to get session ID from JWT claims
            if (auth.getPrincipal() instanceof Jwt) {
                Jwt jwt = (Jwt) auth.getPrincipal();
                Object sessionId = jwt.getClaim("session_id");
                if (sessionId != null) {
                    return sessionId.toString();
                }
            }
            
            // Try to get from authentication details
            if (auth.getDetails() != null) {
                Object details = auth.getDetails();
                if (details instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> detailsMap = (java.util.Map<String, Object>) details;
                    Object sessionId = detailsMap.get("sessionId");
                    if (sessionId != null) {
                        return sessionId.toString();
                    }
                }
            }
        }
        
        // Fall back to request context session ID
        return RequestContextHolder.getSessionId();
    }
    
    /**
     * Get current user ID from security context
     */
    public static String getCurrentUserId() {
        SecurityContext context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        
        if (context != null && context.getAuthentication() != null) {
            Authentication auth = context.getAuthentication();
            
            // Try to get user ID from JWT claims
            if (auth.getPrincipal() instanceof Jwt) {
                Jwt jwt = (Jwt) auth.getPrincipal();
                Object userId = jwt.getClaim("sub");
                if (userId != null) {
                    return userId.toString();
                }
            }
            
            // Fall back to principal name
            return auth.getName();
        }
        
        return null;
    }
    
    /**
     * Check if current user is authenticated
     */
    public static boolean isAuthenticated() {
        SecurityContext context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        
        if (context != null && context.getAuthentication() != null) {
            Authentication auth = context.getAuthentication();
            return auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
        }
        
        return false;
    }
}