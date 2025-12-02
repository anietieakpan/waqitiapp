package com.waqiti.user.security;

import com.waqiti.user.service.KeycloakAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Security component for user authorization checks
 * Used in @PreAuthorize expressions
 */
@Component("userSecurity")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
public class UserSecurity {

    private final KeycloakAuthService keycloakAuthService;

    /**
     * Check if current user is the owner of the resource
     */
    public boolean isCurrentUser(UUID userId) {
        try {
            return keycloakAuthService.isCurrentUser(userId);
        } catch (Exception e) {
            log.warn("Error checking if current user owns resource: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if current user is the owner of the resource (string version)
     */
    public boolean isCurrentUser(String userId) {
        try {
            return isCurrentUser(UUID.fromString(userId));
        } catch (Exception e) {
            log.warn("Error parsing user ID: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if current user has admin role
     */
    public boolean isAdmin() {
        try {
            return keycloakAuthService.isAdmin();
        } catch (Exception e) {
            log.warn("Error checking admin role: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if current user has a specific role
     */
    public boolean hasRole(String role) {
        try {
            return keycloakAuthService.hasRole(role);
        } catch (Exception e) {
            log.warn("Error checking role {}: {}", role, e.getMessage());
            return false;
        }
    }

    /**
     * Check if current user can access user resource (either owner or admin)
     */
    public boolean canAccessUser(UUID userId) {
        return isCurrentUser(userId) || isAdmin();
    }

    /**
     * Check if current user can access user resource (string version)
     */
    public boolean canAccessUser(String userId) {
        try {
            return canAccessUser(UUID.fromString(userId));
        } catch (Exception e) {
            log.warn("Error parsing user ID: {}", e.getMessage());
            return false;
        }
    }
}