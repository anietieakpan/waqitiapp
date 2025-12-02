package com.waqiti.security.rbac.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a role in the RBAC system.
 * Roles are hierarchical and can inherit permissions from parent roles.
 */
@Entity
@Table(name = "roles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(unique = true, nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RoleType type = RoleType.CUSTOM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RoleStatus status = RoleStatus.ACTIVE;

    /**
     * Parent role for hierarchical inheritance
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_role_id")
    private Role parentRole;

    /**
     * Child roles that inherit from this role
     */
    @OneToMany(mappedBy = "parentRole", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Role> childRoles = new HashSet<>();

    /**
     * Permissions directly assigned to this role
     */
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

    /**
     * User assignments to this role
     */
    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<UserRole> userRoles = new HashSet<>();

    /**
     * Role hierarchy level (0 = root, higher = deeper)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer hierarchyLevel = 0;

    /**
     * Priority for role precedence when user has multiple roles
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 100;

    /**
     * Whether this role can be assigned by users with lower hierarchy
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean assignable = true;

    /**
     * Maximum number of users that can have this role (null = unlimited)
     */
    @Column
    private Integer maxUsers;

    /**
     * Role metadata for custom attributes
     */
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum RoleType {
        SYSTEM,     // Built-in system roles
        CUSTOM,     // User-defined roles
        TEMPORARY   // Time-limited roles
    }

    public enum RoleStatus {
        ACTIVE,
        INACTIVE,
        DEPRECATED
    }

    /**
     * Gets all permissions including inherited ones from parent roles
     */
    public Set<Permission> getAllPermissions() {
        Set<Permission> allPermissions = new HashSet<>(this.permissions);
        
        // Add inherited permissions from parent roles
        Role current = this.parentRole;
        while (current != null) {
            allPermissions.addAll(current.getPermissions());
            current = current.getParentRole();
        }
        
        return allPermissions;
    }

    /**
     * Checks if this role has a specific permission (including inherited)
     */
    public boolean hasPermission(String permissionName) {
        return getAllPermissions().stream()
                .anyMatch(permission -> permission.getName().equals(permissionName));
    }

    /**
     * Checks if this role has a specific permission for a resource
     */
    public boolean hasPermission(String permissionName, String resource) {
        return getAllPermissions().stream()
                .anyMatch(permission -> 
                    permission.getName().equals(permissionName) && 
                    (permission.getResource() == null || permission.getResource().equals(resource))
                );
    }

    /**
     * Adds a permission to this role
     */
    public void addPermission(Permission permission) {
        this.permissions.add(permission);
    }

    /**
     * Removes a permission from this role
     */
    public void removePermission(Permission permission) {
        this.permissions.remove(permission);
    }

    /**
     * Adds a child role
     */
    public void addChildRole(Role childRole) {
        childRole.setParentRole(this);
        childRole.setHierarchyLevel(this.hierarchyLevel + 1);
        this.childRoles.add(childRole);
    }

    /**
     * Removes a child role
     */
    public void removeChildRole(Role childRole) {
        childRole.setParentRole(null);
        this.childRoles.remove(childRole);
    }

    /**
     * Checks if this role is a descendant of another role
     */
    public boolean isDescendantOf(Role role) {
        Role current = this.parentRole;
        while (current != null) {
            if (current.equals(role)) {
                return true;
            }
            current = current.getParentRole();
        }
        return false;
    }

    /**
     * Checks if this role is an ancestor of another role
     */
    public boolean isAncestorOf(Role role) {
        return role.isDescendantOf(this);
    }

    /**
     * Gets the root role in the hierarchy
     */
    public Role getRootRole() {
        Role current = this;
        while (current.getParentRole() != null) {
            current = current.getParentRole();
        }
        return current;
    }

    /**
     * Gets all descendant roles
     */
    public Set<Role> getAllDescendants() {
        Set<Role> descendants = new HashSet<>();
        for (Role child : childRoles) {
            descendants.add(child);
            descendants.addAll(child.getAllDescendants());
        }
        return descendants;
    }

    /**
     * Validates role hierarchy to prevent cycles
     */
    public boolean isValidHierarchy() {
        Set<Role> visited = new HashSet<>();
        Role current = this;
        
        while (current != null) {
            if (visited.contains(current)) {
                return false; // Cycle detected
            }
            visited.add(current);
            current = current.getParentRole();
        }
        
        return true;
    }

    /**
     * Gets the current number of users with this role
     */
    public long getUserCount() {
        return userRoles.stream()
                .filter(ur -> ur.getStatus() == UserRole.UserRoleStatus.ACTIVE)
                .count();
    }

    /**
     * Checks if more users can be assigned to this role
     */
    public boolean canAssignMoreUsers() {
        return maxUsers == null || getUserCount() < maxUsers;
    }
}