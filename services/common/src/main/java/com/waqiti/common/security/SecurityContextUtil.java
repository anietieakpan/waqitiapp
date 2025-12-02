package com.waqiti.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class for extracting and validating security context information
 * Provides secure access to authenticated user data from Keycloak JWT tokens
 */
@Component
public class SecurityContextUtil {
    
    // SECURITY FIX: Track active sessions for security monitoring
    private static final ConcurrentHashMap<UUID, AtomicInteger> activeSessionCounts = new ConcurrentHashMap<>();

    /**
     * Gets the authenticated user's ID from the security context
     * @return The user's UUID from Keycloak JWT token
     * @throws SecurityException if no authenticated user or invalid token
     */
    public static UUID getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("No authenticated user found");
        }

        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            Jwt jwt = jwtAuth.getToken();
            
            // Extract user ID from 'sub' claim (Keycloak standard)
            String subject = jwt.getSubject();
            if (subject != null) {
                try {
                    return UUID.fromString(subject);
                } catch (IllegalArgumentException e) {
                    // Try alternative claim names for backward compatibility
                    Object userId = jwt.getClaim("user_id");
                    if (userId != null) {
                        return UUID.fromString(userId.toString());
                    }
                }
            }
        }
        
        throw new SecurityException("Unable to extract user ID from authentication token");
    }

    /**
     * Checks if the authenticated user can access data for the specified user
     * @param targetUserId The user ID to check access for
     * @return true if access is allowed, false otherwise
     */
    public static boolean canAccessUserData(UUID targetUserId) {
        try {
            UUID authenticatedUserId = getAuthenticatedUserId();
            
            // User can access their own data
            if (authenticatedUserId.equals(targetUserId)) {
                return true;
            }
            
            // Check for admin or system roles
            return hasRole("ADMIN") || hasRole("SYSTEM") || hasRole("COMPLIANCE_OFFICER");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if the authenticated user has a specific role
     * @param role The role to check (without ROLE_ prefix)
     * @return true if user has the role
     */
    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
            .anyMatch(authority -> 
                authority.getAuthority().equals("ROLE_" + role) ||
                authority.getAuthority().equals(role)
            );
    }

    /**
     * Checks if the authenticated user has any of the specified roles
     * @param roles The roles to check
     * @return true if user has any of the roles
     */
    public static boolean hasAnyRole(String... roles) {
        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets all roles for the authenticated user
     * @return Set of role names (without ROLE_ prefix)
     */
    public static Set<String> getUserRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null) {
            return Set.of();
        }
        
        return authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .filter(auth -> auth.startsWith("ROLE_"))
            .map(auth -> auth.substring(5))
            .collect(Collectors.toSet());
    }

    /**
     * Validates that the authenticated user owns the specified resource
     * @param resourceOwnerId The owner ID of the resource
     * @throws SecurityException if access is not allowed
     */
    public static void validateResourceOwnership(UUID resourceOwnerId) {
        if (!canAccessUserData(resourceOwnerId)) {
            throw new SecurityException("Unauthorized access to resource");
        }
    }

    /**
     * Gets the authenticated user's email from the security context
     * @return Optional containing the user's email
     */
    public static Optional<String> getAuthenticatedUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            Jwt jwt = jwtAuth.getToken();
            
            Object email = jwt.getClaim("email");
            if (email != null) {
                return Optional.of(email.toString());
            }
        }
        
        return Optional.empty();
    }

    /**
     * Gets the client ID from the security context (for service-to-service calls)
     * @return Optional containing the client ID
     */
    public static Optional<String> getClientId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            Jwt jwt = jwtAuth.getToken();
            
            Object clientId = jwt.getClaim("azp");
            if (clientId != null) {
                return Optional.of(clientId.toString());
            }
        }
        
        return Optional.empty();
    }

    /**
     * Checks if the current authentication is a service-to-service call
     * @return true if this is a service call (client credentials flow)
     */
    public static boolean isServiceCall() {
        return getClientId().isPresent() && 
               getClientId().get().endsWith("-service");
    }
    
    /**
     * Gets the current user's ID as a UUID (null-safe version)
     * @return The user ID or null if not authenticated
     */
    public static UUID getCurrentUserId() {
        try {
            return getAuthenticatedUserId();
        } catch (SecurityException e) {
            return null;
        }
    }
    
    /**
     * Gets the current user's primary role
     * @return The primary role or null if not authenticated
     */
    public static String getCurrentUserRole() {
        Set<String> roles = getUserRoles();
        if (roles.isEmpty()) {
            return null;
        }
        
        // Return the highest priority role
        if (roles.contains("SUPER_ADMIN")) return "SUPER_ADMIN";
        if (roles.contains("ADMIN")) return "ADMIN";
        if (roles.contains("SYSTEM_ADMIN")) return "SYSTEM_ADMIN";
        if (roles.contains("COMPLIANCE_OFFICER")) return "COMPLIANCE_OFFICER";
        if (roles.contains("FRAUD_ANALYST")) return "FRAUD_ANALYST";
        if (roles.contains("FINANCIAL_ADMIN")) return "FINANCIAL_ADMIN";
        if (roles.contains("AUDIT_ADMIN")) return "AUDIT_ADMIN";
        if (roles.contains("USER")) return "USER";
        
        // Return the first role if none of the priority roles match
        return roles.iterator().next();
    }
    
    /**
     * Gets the current session ID from JWT token
     * @return Session ID or null if not available
     */
    public static String getCurrentSessionId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            Jwt jwt = jwtAuth.getToken();
            
            Object sessionId = jwt.getClaim("session_state");
            if (sessionId != null) {
                return sessionId.toString();
            }
            
            // Fallback to JTI (JWT ID)
            Object jti = jwt.getClaim("jti");
            if (jti != null) {
                return jti.toString();
            }
        }
        
        return null;
    }
    
    /**
     * Gets the current principal
     * @return Principal or null if not authenticated
     */
    public static java.security.Principal getCurrentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() ? authentication : null;
    }

    /**
     * Gets the current user's ID as a string
     * @return The user ID or null if not authenticated
     */
    public static String getCurrentUserIdString() {
        try {
            UUID userId = getAuthenticatedUserId();
            return userId != null ? userId.toString() : null;
        } catch (SecurityException e) {
            return null;
        }
    }
    
    /**
     * Gets the current user's username
     * @return The username or "system" if not authenticated
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null) {
            return "system";
        }
        
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            Jwt jwt = jwtAuth.getToken();
            
            // Try preferred_username first (Keycloak standard)
            Object preferredUsername = jwt.getClaim("preferred_username");
            if (preferredUsername != null) {
                return preferredUsername.toString();
            }
            
            // Try username claim
            Object username = jwt.getClaim("username");
            if (username != null) {
                return username.toString();
            }
            
            // Fallback to email
            Object email = jwt.getClaim("email");
            if (email != null) {
                return email.toString();
            }
        }
        
        // Fallback to principal name
        return authentication.getName();
    }
    
    /**
     * SECURITY FIX: Get highest role for enhanced authorization
     */
    public static String getHighestRole() {
        Set<String> roles = getUserRoles();
        
        // Return the highest priority role
        if (roles.contains("SUPER_ADMIN")) return "SUPER_ADMIN";
        if (roles.contains("ADMIN")) return "ADMIN";
        if (roles.contains("SYSTEM_ADMIN")) return "SYSTEM_ADMIN";
        if (roles.contains("COMPLIANCE_OFFICER")) return "COMPLIANCE_OFFICER";
        if (roles.contains("AUDIT_ADMIN")) return "AUDIT_ADMIN";
        if (roles.contains("FRAUD_ANALYST")) return "FRAUD_ANALYST";
        if (roles.contains("FINANCIAL_ADMIN")) return "FINANCIAL_ADMIN";
        if (roles.contains("RECONCILER")) return "RECONCILER";
        if (roles.contains("AUDITOR")) return "AUDITOR";
        if (roles.contains("USER")) return "USER";
        
        return roles.isEmpty() ? "ANONYMOUS" : roles.iterator().next();
    }
    
    /**
     * SECURITY FIX: Get client IP address from request
     */
    public static String getClientIp() {
        try {
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attr.getRequest();
            
            // Check for X-Forwarded-For header (proxy/load balancer)
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                // Take the first IP if multiple are present
                return xForwardedFor.split(",")[0].trim();
            }
            
            // Check for X-Real-IP header (nginx)
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }
            
            // Fallback to remote address
            return request.getRemoteAddr();
            
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * SECURITY FIX: Get user agent from request
     */
    public static String getUserAgent() {
        try {
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attr.getRequest();
            
            String userAgent = request.getHeader("User-Agent");
            return userAgent != null ? userAgent : "unknown";
            
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * SECURITY FIX: Get active session count for a user (simplified implementation)
     */
    public static int getActiveSessionCount(UUID userId) {
        if (userId == null) {
            return 0;
        }
        
        AtomicInteger count = activeSessionCounts.get(userId);
        return count != null ? count.get() : 1; // Assume at least current session
    }
    
    /**
     * SECURITY FIX: Register user session (called during authentication)
     */
    public static void registerUserSession(UUID userId) {
        if (userId != null) {
            activeSessionCounts.computeIfAbsent(userId, k -> new AtomicInteger(0)).incrementAndGet();
        }
    }
    
    /**
     * SECURITY FIX: Unregister user session (called during logout)
     */
    public static void unregisterUserSession(UUID userId) {
        if (userId != null) {
            AtomicInteger count = activeSessionCounts.get(userId);
            if (count != null) {
                int newCount = count.decrementAndGet();
                if (newCount <= 0) {
                    activeSessionCounts.remove(userId);
                }
            }
        }
    }
}