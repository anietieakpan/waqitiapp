package com.waqiti.security.rbac.service;

import com.waqiti.security.rbac.domain.Role;
import com.waqiti.security.rbac.domain.Permission;
import com.waqiti.security.rbac.domain.UserRole;
import com.waqiti.security.rbac.domain.UserPermission;
import com.waqiti.security.rbac.repository.RoleRepository;
import com.waqiti.security.rbac.repository.PermissionRepository;
import com.waqiti.security.rbac.repository.UserRoleRepository;
import com.waqiti.security.rbac.repository.UserPermissionRepository;
import com.waqiti.security.rbac.exception.RBACException;
import com.waqiti.security.rbac.exception.InsufficientPermissionException;
import com.waqiti.security.rbac.exception.RoleNotFoundException;
import com.waqiti.security.rbac.cache.RBACCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core RBAC service providing comprehensive role-based access control functionality.
 * Supports hierarchical roles, permission inheritance, delegation, and advanced authorization.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RBACService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final RBACCacheService cacheService;

    /**
     * Checks if a user has a specific permission
     */
    @Cacheable(value = "user-permissions", key = "#userId + ':' + #permission")
    public boolean hasPermission(UUID userId, String permission) {
        return hasPermission(userId, permission, null);
    }

    /**
     * Checks if a user has a specific permission for a resource
     */
    @Cacheable(value = "user-permissions", key = "#userId + ':' + #permission + ':' + (#resource ?: 'null')")
    public boolean hasPermission(UUID userId, String permission, String resource) {
        log.debug("Checking permission: userId={}, permission={}, resource={}", userId, permission, resource);
        
        try {
            // Get all effective permissions for the user
            Set<Permission> userPermissions = getEffectivePermissions(userId);
            
            // Check if user has the required permission
            return userPermissions.stream()
                    .anyMatch(p -> p.matches(permission) && p.appliesTo(resource, null));
                    
        } catch (Exception e) {
            log.error("Error checking permission: userId={}, permission={}, resource={}", 
                    userId, permission, resource, e);
            return false; // Fail securely
        }
    }

    /**
     * Checks if a user has any of the specified permissions
     */
    public boolean hasAnyPermission(UUID userId, String... permissions) {
        return Arrays.stream(permissions)
                .anyMatch(permission -> hasPermission(userId, permission));
    }

    /**
     * Checks if a user has all specified permissions
     */
    public boolean hasAllPermissions(UUID userId, String... permissions) {
        return Arrays.stream(permissions)
                .allMatch(permission -> hasPermission(userId, permission));
    }

    /**
     * Gets all effective permissions for a user (from roles and direct assignments)
     */
    @Cacheable(value = "user-effective-permissions", key = "#userId")
    public Set<Permission> getEffectivePermissions(UUID userId) {
        log.debug("Getting effective permissions for user: {}", userId);
        
        Set<Permission> allPermissions = new HashSet<>();
        
        // Get permissions from active roles
        List<UserRole> activeRoles = userRoleRepository.findActiveRolesByUserId(userId);
        for (UserRole userRole : activeRoles) {
            if (userRole.isCurrentlyActive()) {
                allPermissions.addAll(userRole.getRole().getAllPermissions());
            }
        }
        
        // Get direct permissions
        List<UserPermission> directPermissions = userPermissionRepository.findActivePermissionsByUserId(userId);
        for (UserPermission userPermission : directPermissions) {
            if (userPermission.isCurrentlyActive()) {
                allPermissions.add(userPermission.getPermission());
            }
        }
        
        log.debug("User {} has {} effective permissions", userId, allPermissions.size());
        return allPermissions;
    }

    /**
     * Gets all active roles for a user
     */
    @Cacheable(value = "user-roles", key = "#userId")
    public Set<Role> getUserRoles(UUID userId) {
        return userRoleRepository.findActiveRolesByUserId(userId)
                .stream()
                .filter(UserRole::isCurrentlyActive)
                .map(UserRole::getRole)
                .collect(Collectors.toSet());
    }

    /**
     * Assigns a role to a user
     */
    @Transactional
    @CacheEvict(value = {"user-permissions", "user-roles", "user-effective-permissions"}, key = "#userId")
    public UserRole assignRole(UUID userId, UUID roleId, UUID assignedBy, String reason) {
        return assignRole(userId, roleId, assignedBy, reason, null, null);
    }

    /**
     * Assigns a role to a user with time constraints
     */
    @Transactional
    @CacheEvict(value = {"user-permissions", "user-roles", "user-effective-permissions"}, key = "#userId")
    public UserRole assignRole(UUID userId, UUID roleId, UUID assignedBy, String reason, 
                              LocalDateTime effectiveFrom, LocalDateTime expiresAt) {
        log.info("Assigning role: userId={}, roleId={}, assignedBy={}", userId, roleId, assignedBy);
        
        // Validate inputs
        if (userId == null || roleId == null || assignedBy == null) {
            throw new IllegalArgumentException("UserId, roleId, and assignedBy cannot be null");
        }
        
        // Check if role exists
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleId));
        
        // Check if user already has this role
        Optional<UserRole> existingAssignment = userRoleRepository.findByUserIdAndRoleId(userId, roleId);
        if (existingAssignment.isPresent() && existingAssignment.get().isCurrentlyActive()) {
            throw new RBACException("User already has this role: " + role.getName());
        }
        
        // Check if assigner has permission to assign this role
        if (!canAssignRole(assignedBy, role)) {
            throw new InsufficientPermissionException("Insufficient permission to assign role: " + role.getName());
        }
        
        // Check role constraints
        if (!role.canAssignMoreUsers()) {
            throw new RBACException("Role has reached maximum user limit: " + role.getName());
        }
        
        // Create the assignment
        UserRole userRole = UserRole.builder()
                .userId(userId)
                .role(role)
                .status(UserRole.UserRoleStatus.ACTIVE)
                .effectiveFrom(effectiveFrom != null ? effectiveFrom : LocalDateTime.now())
                .expiresAt(expiresAt)
                .assignedBy(assignedBy)
                .assignmentReason(reason)
                .build();
        
        userRole = userRoleRepository.save(userRole);
        
        // Clear cache
        cacheService.evictUserCache(userId);
        
        log.info("Role assigned successfully: userId={}, role={}", userId, role.getName());
        return userRole;
    }

    /**
     * Revokes a role from a user
     */
    @Transactional
    @CacheEvict(value = {"user-permissions", "user-roles", "user-effective-permissions"}, key = "#userId")
    public void revokeRole(UUID userId, UUID roleId, UUID revokedBy, String reason) {
        log.info("Revoking role: userId={}, roleId={}, revokedBy={}", userId, roleId, revokedBy);
        
        UserRole userRole = userRoleRepository.findByUserIdAndRoleId(userId, roleId)
                .orElseThrow(() -> new RBACException("User role assignment not found"));
        
        // Check if revoker has permission
        if (!canRevokeRole(revokedBy, userRole.getRole())) {
            throw new InsufficientPermissionException("Insufficient permission to revoke role");
        }
        
        userRole.revoke(reason);
        userRoleRepository.save(userRole);
        
        // Clear cache
        cacheService.evictUserCache(userId);
        
        log.info("Role revoked successfully: userId={}, role={}", userId, userRole.getRole().getName());
    }

    /**
     * Delegates a role from one user to another
     */
    @Transactional
    public UserRole delegateRole(UUID fromUserId, UUID toUserId, UUID roleId, 
                                LocalDateTime delegationExpiry, String reason) {
        log.info("Delegating role: from={}, to={}, roleId={}", fromUserId, toUserId, roleId);
        
        // Check if source user has the role
        UserRole sourceRole = userRoleRepository.findByUserIdAndRoleId(fromUserId, roleId)
                .orElseThrow(() -> new RBACException("Source user does not have the role to delegate"));
        
        if (!sourceRole.canBeDelegated()) {
            throw new RBACException("Role cannot be delegated");
        }
        
        // Create delegation
        UserRole delegation = sourceRole.createDelegation(toUserId, fromUserId, delegationExpiry, reason);
        delegation = userRoleRepository.save(delegation);
        
        // Clear cache for target user
        cacheService.evictUserCache(toUserId);
        
        log.info("Role delegated successfully: from={}, to={}, role={}", 
                fromUserId, toUserId, sourceRole.getRole().getName());
        return delegation;
    }

    /**
     * Grants a direct permission to a user
     */
    @Transactional
    @CacheEvict(value = {"user-permissions", "user-effective-permissions"}, key = "#userId")
    public UserPermission grantPermission(UUID userId, UUID permissionId, UUID grantedBy, 
                                        String reason, LocalDateTime expiresAt) {
        log.info("Granting direct permission: userId={}, permissionId={}, grantedBy={}", 
                userId, permissionId, grantedBy);
        
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new RBACException("Permission not found: " + permissionId));
        
        // Check if granter has permission to grant this permission
        if (!canGrantPermission(grantedBy, permission)) {
            throw new InsufficientPermissionException("Insufficient permission to grant: " + permission.getName());
        }
        
        // Create the permission assignment
        UserPermission userPermission = UserPermission.builder()
                .userId(userId)
                .permission(permission)
                .status(UserPermission.UserPermissionStatus.ACTIVE)
                .effectiveFrom(LocalDateTime.now())
                .expiresAt(expiresAt)
                .grantedBy(grantedBy)
                .grantReason(reason)
                .build();
        
        userPermission = userPermissionRepository.save(userPermission);
        
        // Clear cache
        cacheService.evictUserCache(userId);
        
        log.info("Permission granted successfully: userId={}, permission={}", userId, permission.getName());
        return userPermission;
    }

    /**
     * Creates a new role
     */
    @Transactional
    public Role createRole(String name, String description, UUID parentRoleId, 
                          Set<UUID> permissionIds, UUID createdBy) {
        log.info("Creating role: name={}, createdBy={}", name, createdBy);
        
        // Check if role name already exists
        if (roleRepository.existsByName(name)) {
            throw new RBACException("Role with name already exists: " + name);
        }
        
        // Get parent role if specified
        Role parentRole = null;
        if (parentRoleId != null) {
            parentRole = roleRepository.findById(parentRoleId)
                    .orElseThrow(() -> new RoleNotFoundException("Parent role not found: " + parentRoleId));
        }
        
        // Get permissions
        Set<Permission> permissions = new HashSet<>();
        if (permissionIds != null && !permissionIds.isEmpty()) {
            permissions = permissionRepository.findByIdIn(permissionIds);
        }
        
        // Create role
        Role role = Role.builder()
                .name(name)
                .description(description)
                .parentRole(parentRole)
                .permissions(permissions)
                .hierarchyLevel(parentRole != null ? parentRole.getHierarchyLevel() + 1 : 0)
                .build();
        
        role = roleRepository.save(role);
        
        log.info("Role created successfully: name={}, id={}", name, role.getId());
        return role;
    }

    /**
     * Creates a new permission
     */
    @Transactional
    public Permission createPermission(String name, String resource, String action, 
                                     String description, Permission.PermissionCategory category,
                                     Permission.SecurityLevel securityLevel, UUID createdBy) {
        log.info("Creating permission: name={}, resource={}, action={}, createdBy={}", 
                name, resource, action, createdBy);
        
        // Check if permission already exists
        if (permissionRepository.existsByNameAndResourceAndAction(name, resource, action)) {
            throw new RBACException("Permission already exists: " + name + ":" + resource + ":" + action);
        }
        
        Permission permission = Permission.builder()
                .name(name)
                .resource(resource)
                .action(action)
                .description(description)
                .category(category)
                .securityLevel(securityLevel)
                .requiresApproval(securityLevel == Permission.SecurityLevel.RESTRICTED)
                .build();
        
        permission = permissionRepository.save(permission);
        
        log.info("Permission created successfully: name={}, id={}", name, permission.getId());
        return permission;
    }

    /**
     * Checks if a user can assign a specific role
     */
    private boolean canAssignRole(UUID userId, Role role) {
        // System administrators can assign any role
        if (hasPermission(userId, "ROLE_MANAGEMENT:ASSIGN")) {
            return true;
        }
        
        // Users can only assign roles at their level or below
        Set<Role> userRoles = getUserRoles(userId);
        int userMaxHierarchy = userRoles.stream()
                .mapToInt(Role::getHierarchyLevel)
                .max()
                .orElse(-1);
        
        return role.getHierarchyLevel() >= userMaxHierarchy && role.getAssignable();
    }

    /**
     * Checks if a user can revoke a specific role
     */
    private boolean canRevokeRole(UUID userId, Role role) {
        // System administrators can revoke any role
        if (hasPermission(userId, "ROLE_MANAGEMENT:REVOKE")) {
            return true;
        }
        
        // Users can only revoke roles at their level or below
        return canAssignRole(userId, role);
    }

    /**
     * Checks if a user can grant a specific permission
     */
    private boolean canGrantPermission(UUID userId, Permission permission) {
        // System administrators can grant any permission
        if (hasPermission(userId, "PERMISSION_MANAGEMENT:GRANT")) {
            return true;
        }
        
        // Users can only grant permissions they already have
        return hasPermission(userId, permission.getFullName());
    }

    /**
     * Gets role hierarchy for a user
     */
    public Map<String, Object> getUserRoleHierarchy(UUID userId) {
        Set<Role> userRoles = getUserRoles(userId);
        Map<String, Object> hierarchy = new HashMap<>();
        
        for (Role role : userRoles) {
            hierarchy.put(role.getName(), buildRoleHierarchy(role));
        }
        
        return hierarchy;
    }

    /**
     * Builds role hierarchy map
     */
    private Map<String, Object> buildRoleHierarchy(Role role) {
        Map<String, Object> roleData = new HashMap<>();
        roleData.put("id", role.getId());
        roleData.put("name", role.getName());
        roleData.put("level", role.getHierarchyLevel());
        roleData.put("permissions", role.getAllPermissions().stream()
                .map(Permission::getFullName)
                .collect(Collectors.toList()));
        
        if (role.getParentRole() != null) {
            roleData.put("parent", buildRoleHierarchy(role.getParentRole()));
        }
        
        return roleData;
    }

    /**
     * Performs cleanup of expired assignments
     */
    @Transactional
    public void cleanupExpiredAssignments() {
        log.info("Cleaning up expired role and permission assignments");
        
        // Update expired user roles
        List<UserRole> expiredRoles = userRoleRepository.findExpiredActiveRoles();
        for (UserRole userRole : expiredRoles) {
            userRole.expire();
            userRoleRepository.save(userRole);
            cacheService.evictUserCache(userRole.getUserId());
        }
        
        // Update expired user permissions
        List<UserPermission> expiredPermissions = userPermissionRepository.findExpiredActivePermissions();
        for (UserPermission userPermission : expiredPermissions) {
            userPermission.expire();
            userPermissionRepository.save(userPermission);
            cacheService.evictUserCache(userPermission.getUserId());
        }
        
        log.info("Cleanup completed: {} expired roles, {} expired permissions", 
                expiredRoles.size(), expiredPermissions.size());
    }
}