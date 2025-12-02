package com.waqiti.common.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Resource Authorization Service
 * 
 * Prevents Insecure Direct Object Reference (IDOR) vulnerabilities by verifying
 * that authenticated users can only access their own resources.
 * 
 * CRITICAL for PCI DSS 6.5.4 compliance and preventing unauthorized data access.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ResourceAuthorizationService {

    private final MeterRegistry meterRegistry;
    private Counter idorAttemptCounter;
    private Counter authorizationDeniedCounter;

    public void initMetrics() {
        idorAttemptCounter = Counter.builder("security.idor.attempts")
            .description("Potential IDOR attack attempts detected")
            .register(meterRegistry);
            
        authorizationDeniedCounter = Counter.builder("security.authorization.denied")
            .description("Resource authorization denied")
            .register(meterRegistry);
    }

    /**
     * Verify that the authenticated user owns the resource
     */
    public void verifyOwnership(UUID resourceOwnerId, String resourceType) {
        UUID authenticatedUserId = getAuthenticatedUserId();
        
        if (!authenticatedUserId.equals(resourceOwnerId)) {
            idorAttemptCounter.increment();
            log.warn("SECURITY: IDOR attempt detected - User {} tried to access {} owned by {}",
                authenticatedUserId, resourceType, resourceOwnerId);
            throw new UnauthorizedAccessException(
                String.format("Access denied to %s", resourceType));
        }
        
        log.debug("Authorization granted for user {} to access {}", authenticatedUserId, resourceType);
    }

    /**
     * Verify resource ownership with custom error message
     */
    public void verifyOwnership(UUID resourceOwnerId, String resourceType, String customMessage) {
        UUID authenticatedUserId = getAuthenticatedUserId();
        
        if (!authenticatedUserId.equals(resourceOwnerId)) {
            idorAttemptCounter.increment();
            log.warn("SECURITY: IDOR attempt detected - User {} tried to access {} owned by {}",
                authenticatedUserId, resourceType, resourceOwnerId);
            throw new UnauthorizedAccessException(customMessage);
        }
    }

    /**
     * Check if authenticated user owns the resource (returns boolean)
     */
    public boolean ownsResource(UUID resourceOwnerId) {
        UUID authenticatedUserId = getAuthenticatedUserId();
        return authenticatedUserId.equals(resourceOwnerId);
    }

    /**
     * Get authenticated user ID from security context
     */
    public UUID getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            authorizationDeniedCounter.increment();
            throw new UnauthorizedAccessException("User not authenticated");
        }
        
        Object principal = authentication.getPrincipal();
        
        if (principal instanceof UUID) {
            return (UUID) principal;
        }
        
        if (principal instanceof String) {
            try {
                return UUID.fromString((String) principal);
            } catch (IllegalArgumentException e) {
                log.error("Invalid UUID in authentication principal: {}", principal);
                throw new UnauthorizedAccessException("Invalid user ID format");
            }
        }
        
        // Try to extract from custom user details
        if (principal != null && principal.getClass().getSimpleName().contains("UserDetails")) {
            try {
                var getUserIdMethod = principal.getClass().getMethod("getUserId");
                Object userId = getUserIdMethod.invoke(principal);
                if (userId instanceof UUID) {
                    return (UUID) userId;
                }
            } catch (Exception e) {
                log.error("Failed to extract userId from UserDetails", e);
            }
        }
        
        authorizationDeniedCounter.increment();
        throw new UnauthorizedAccessException("Unable to determine user identity");
    }

    /**
     * Verify user has specific role
     */
    public void verifyRole(String requiredRole) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            authorizationDeniedCounter.increment();
            throw new UnauthorizedAccessException("User not authenticated");
        }
        
        boolean hasRole = authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_" + requiredRole) 
                           || auth.getAuthority().equals(requiredRole));
        
        if (!hasRole) {
            authorizationDeniedCounter.increment();
            log.warn("SECURITY: User {} attempted to access resource requiring role: {}", 
                getAuthenticatedUserId(), requiredRole);
            throw new UnauthorizedAccessException("Insufficient permissions");
        }
    }

    /**
     * Verify user has any of the specified roles
     */
    public void verifyAnyRole(String... roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            authorizationDeniedCounter.increment();
            throw new UnauthorizedAccessException("User not authenticated");
        }
        
        for (String role : roles) {
            boolean hasRole = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_" + role) 
                               || auth.getAuthority().equals(role));
            if (hasRole) {
                return;
            }
        }
        
        authorizationDeniedCounter.increment();
        log.warn("SECURITY: User {} attempted to access resource requiring one of roles: {}", 
            getAuthenticatedUserId(), String.join(", ", roles));
        throw new UnauthorizedAccessException("Insufficient permissions");
    }

    /**
     * Exception for unauthorized access attempts
     */
    public static class UnauthorizedAccessException extends RuntimeException {
        public UnauthorizedAccessException(String message) {
            super(message);
        }
    }
}
