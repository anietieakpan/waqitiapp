package com.waqiti.common.security.authorization;

import java.util.UUID;

/**
 * Role-Based Access Control (RBAC) Service Interface
 * Provides authorization and permission checking capabilities for the Waqiti platform.
 */
public interface RBACService {
    
    /**
     * Checks if a user has a specific permission
     * @param userId The user's unique identifier
     * @param permission The permission to check
     * @return true if the user has the permission, false otherwise
     */
    boolean hasPermission(UUID userId, String permission);
    
    /**
     * Checks if a user has a specific permission on a resource
     * @param userId The user's unique identifier
     * @param permission The permission to check
     * @param resourceId The resource identifier
     * @return true if the user has the permission on the resource, false otherwise
     */
    boolean hasPermission(UUID userId, String permission, String resourceId);
}