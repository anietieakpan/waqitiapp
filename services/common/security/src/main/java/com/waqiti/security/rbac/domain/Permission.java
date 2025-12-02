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
 * Represents a permission in the RBAC system.
 * Permissions define what actions can be performed on specific resources.
 */
@Entity
@Table(name = "permissions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "resource", "action"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /**
     * Permission name (e.g., "USER_MANAGEMENT", "PAYMENT_PROCESSING")
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Resource this permission applies to (e.g., "users", "payments", "wallets")
     */
    @Column(length = 100)
    private String resource;

    /**
     * Specific action allowed (e.g., "READ", "WRITE", "DELETE", "EXECUTE")
     */
    @Column(nullable = false, length = 50)
    private String action;

    /**
     * Human-readable description
     */
    @Column(length = 500)
    private String description;

    /**
     * Permission category for organization
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PermissionCategory category = PermissionCategory.GENERAL;

    /**
     * Permission scope (GLOBAL, SERVICE, RESOURCE)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PermissionScope scope = PermissionScope.RESOURCE;

    /**
     * Security level (PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SecurityLevel securityLevel = SecurityLevel.INTERNAL;

    /**
     * Whether this permission requires additional approval
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean requiresApproval = false;

    /**
     * Whether this permission is audited
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean audited = true;

    /**
     * Resource constraints (JSON format for complex rules)
     */
    @Column(columnDefinition = "jsonb")
    private String constraints;

    /**
     * Roles that have this permission
     */
    @ManyToMany(mappedBy = "permissions", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    /**
     * Direct user permissions (for exceptional cases)
     */
    @OneToMany(mappedBy = "permission", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<UserPermission> userPermissions = new HashSet<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum PermissionCategory {
        USER_MANAGEMENT,
        PAYMENT_PROCESSING,
        WALLET_OPERATIONS,
        TRANSACTION_MANAGEMENT,
        COMPLIANCE_MONITORING,
        SYSTEM_ADMINISTRATION,
        ANALYTICS_REPORTING,
        SECURITY_OPERATIONS,
        GENERAL
    }

    public enum PermissionScope {
        GLOBAL,     // System-wide permission
        SERVICE,    // Service-specific permission
        RESOURCE    // Resource-specific permission
    }

    public enum SecurityLevel {
        PUBLIC,         // No restrictions
        INTERNAL,       // Internal users only
        CONFIDENTIAL,   // Privileged users only
        RESTRICTED      // Highly restricted access
    }

    /**
     * Creates a full permission identifier
     */
    public String getFullName() {
        if (resource != null && !resource.trim().isEmpty()) {
            return String.format("%s:%s:%s", resource, name, action);
        }
        return String.format("%s:%s", name, action);
    }

    /**
     * Checks if this permission matches a given permission string
     */
    public boolean matches(String permissionString) {
        return getFullName().equals(permissionString) || 
               name.equals(permissionString) ||
               (name + ":" + action).equals(permissionString);
    }

    /**
     * Checks if this permission applies to a specific resource instance
     */
    public boolean appliesTo(String resourceType, String resourceId) {
        // If permission has no resource constraint, it applies globally
        if (resource == null || resource.equals("*")) {
            return true;
        }
        
        // Check resource type match
        if (!resource.equals(resourceType)) {
            return false;
        }
        
        // Check resource-specific constraints
        return evaluateConstraints(resourceId);
    }

    /**
     * Evaluates permission constraints for a specific resource
     */
    private boolean evaluateConstraints(String resourceId) {
        if (constraints == null || constraints.trim().isEmpty()) {
            return true; // No constraints mean permission applies to all resources of this type
        }
        
        // Parse constraints and evaluate
        // This would implement a constraint evaluation engine
        // For now, return true (implementation would depend on constraint format)
        return true;
    }

    /**
     * Checks if this permission is more restrictive than another
     */
    public boolean isMoreRestrictiveThan(Permission other) {
        // Compare security levels
        int thisLevel = securityLevel.ordinal();
        int otherLevel = other.securityLevel.ordinal();
        
        if (thisLevel > otherLevel) {
            return true;
        }
        
        // Compare scopes
        if (scope == PermissionScope.RESOURCE && other.scope == PermissionScope.GLOBAL) {
            return true;
        }
        
        // Check if this permission has more specific resource constraints
        return constraints != null && other.constraints == null;
    }

    /**
     * Creates a permission hierarchy key for sorting
     */
    public String getHierarchyKey() {
        return String.format("%s.%s.%s.%s", 
                category.name(), 
                securityLevel.name(), 
                scope.name(), 
                getFullName());
    }

    /**
     * Validates that the permission configuration is valid
     */
    public boolean isValid() {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        if (action == null || action.trim().isEmpty()) {
            return false;
        }
        
        // Validate that high-security permissions require approval
        if (securityLevel == SecurityLevel.RESTRICTED && !requiresApproval) {
            return false;
        }
        
        return true;
    }

    /**
     * Gets a human-readable permission display name
     */
    public String getDisplayName() {
        StringBuilder display = new StringBuilder();
        
        // Add action
        display.append(action.substring(0, 1).toUpperCase())
               .append(action.substring(1).toLowerCase());
        
        // Add resource if present
        if (resource != null && !resource.trim().isEmpty()) {
            display.append(" ").append(resource);
        }
        
        // Add name if different from action
        if (!name.equalsIgnoreCase(action)) {
            display.append(" (").append(name).append(")");
        }
        
        return display.toString();
    }

    /**
     * Creates a permission from a permission string
     */
    public static Permission fromString(String permissionString) {
        String[] parts = permissionString.split(":");
        
        PermissionBuilder builder = Permission.builder();
        
        switch (parts.length) {
            case 1:
                builder.name(parts[0]).action("EXECUTE");
                break;
            case 2:
                builder.name(parts[0]).action(parts[1]);
                break;
            case 3:
                builder.resource(parts[0]).name(parts[1]).action(parts[2]);
                break;
            default:
                throw new IllegalArgumentException("Invalid permission string format: " + permissionString);
        }
        
        return builder.build();
    }
}