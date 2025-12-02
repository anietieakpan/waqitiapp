package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Enhanced utility class to access security context information with JWT support
 */
@Component
@Slf4j
public class SecurityContext {
    
    private static final String CLAIM_USER_ID = "user_id";
    private static final String CLAIM_SUB = "sub";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_SCOPES = "scope";
    
    /**
     * Get the current authenticated user ID with JWT support
     * @return the user ID of the currently authenticated user, or null if not authenticated
     */
    public String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("No authenticated user found");
            return null;
        }

        // Handle JWT authentication
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            Jwt jwt = jwtAuth.getToken();
            
            // Try user_id claim first, then sub as fallback
            String userId = jwt.getClaimAsString(CLAIM_USER_ID);
            if (userId == null) {
                userId = jwt.getClaimAsString(CLAIM_SUB);
            }
            
            if (userId != null) {
                return userId;
            }
        }
        
        // Extract user ID from the authentication principal
        Object principal = authentication.getPrincipal();
        
        if (principal instanceof String) {
            return (String) principal;
        }
        
        // If principal is a UserDetails object, extract username
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
        }
        
        // Try to extract from custom principal object
        try {
            return (String) principal.getClass().getMethod("getId").invoke(principal);
        } catch (Exception e) {
            // Fallback to toString() if getId() method doesn't exist
            return principal.toString();
        }
    }
    
    /**
     * Get the current authenticated user's ID as UUID
     */
    public UUID getCurrentUserIdAsUuid() {
        String userId = getCurrentUserId();
        if (userId == null) {
            return null;
        }
        
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            log.warn("User ID is not a valid UUID: {}", userId);
            return null;
        }
    }
    
    /**
     * Alias for getCurrentUserIdAsUuid() to maintain compatibility
     */
    public UUID getUserId() {
        return getCurrentUserIdAsUuid();
    }
    
    /**
     * Get the current authenticated user's email from JWT
     */
    public String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            Jwt jwt = jwtAuth.getToken();
            
            return jwt.getClaimAsString(CLAIM_EMAIL);
        }
        
        return null;
    }
    
    /**
     * Get the current authenticated user's scopes
     */
    @SuppressWarnings("unchecked")
    public Set<String> getCurrentUserScopes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            Jwt jwt = jwtAuth.getToken();
            
            // Try to get scopes from JWT claims
            Object scopesClaim = jwt.getClaim(CLAIM_SCOPES);
            if (scopesClaim instanceof String) {
                String scopesStr = (String) scopesClaim;
                return Set.of(scopesStr.split("\\s+"));
            }
        }

        // Fallback to Spring Security authorities
        if (authentication != null) {
            return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("SCOPE_"))
                .map(authority -> authority.substring(6)) // Remove "SCOPE_" prefix
                .collect(java.util.stream.Collectors.toSet());
        }
        
        return Set.of();
    }
    
    /**
     * Get the current authenticated username
     * @return the username of the currently authenticated user
     * @throws IllegalStateException if no user is authenticated
     */
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }
        
        return authentication.getName();
    }
    
    /**
     * Get the current authentication object
     * @return the current Authentication object
     */
    public Authentication getCurrentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
    
    /**
     * Check if a user is currently authenticated
     * @return true if a user is authenticated, false otherwise
     */
    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() && 
               !"anonymousUser".equals(authentication.getPrincipal());
    }
    
    /**
     * Check if the current user has a specific role
     * @param role the role to check for
     * @return true if the user has the role, false otherwise
     */
    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role) || 
                                 authority.getAuthority().equals(role));
    }
    
    /**
     * Check if the current user has a specific authority
     * @param authority the authority to check for
     * @return true if the user has the authority, false otherwise
     */
    public boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals(authority));
    }
    
    /**
     * Get the current user's name (alias for getCurrentUsername)
     * @return the username of the currently authenticated user
     */
    public String getCurrentUserName() {
        return getCurrentUsername();
    }
    
    /**
     * Get the current user's role
     * @return the primary role of the currently authenticated user
     */
    public String getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        
        // Return the first role found (primary role)
        return authentication.getAuthorities().stream()
            .map(auth -> auth.getAuthority())
            .filter(auth -> auth.startsWith("ROLE_"))
            .map(auth -> auth.substring(5)) // Remove "ROLE_" prefix
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Get the correlation ID for the current request
     * @return the correlation ID if available
     */
    public String getCorrelationId() {
        // This would typically be stored in MDC or request attributes
        return org.slf4j.MDC.get("correlationId");
    }
}