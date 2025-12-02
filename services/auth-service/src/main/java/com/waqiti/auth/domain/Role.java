package com.waqiti.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Enterprise-grade Role entity for RBAC (Role-Based Access Control).
 *
 * Features:
 * - Hierarchical roles (role inheritance)
 * - Fine-grained permissions
 * - Role templates for common use cases
 * - Audit trail
 * - Soft delete support
 *
 * Standard Roles:
 * - SUPER_ADMIN: Full system access
 * - ADMIN: Administrative access
 * - COMPLIANCE_OFFICER: Compliance and audit access
 * - SUPPORT_AGENT: Customer support access
 * - USER: Standard user access
 * - MERCHANT: Merchant-specific access
 * - API_CLIENT: API-only access
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 */
@Entity
@Table(name = "roles", indexes = {
    @Index(name = "idx_roles_name", columnList = "name", unique = true),
    @Index(name = "idx_roles_type", columnList = "role_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", unique = true, nullable = false, length = 50)
    private String name;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 20)
    private RoleType roleType;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 100; // Lower number = higher priority

    @Column(name = "is_system_role", nullable = false)
    @Builder.Default
    private Boolean isSystemRole = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // Permissions
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

    // Hierarchical roles (parent-child relationship)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_role_id")
    private Role parentRole;

    @OneToMany(mappedBy = "parentRole", cascade = CascadeType.ALL)
    @Builder.Default
    private Set<Role> childRoles = new HashSet<>();

    // Audit fields
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    // Business methods
    public void addPermission(Permission permission) {
        permissions.add(permission);
    }

    public void removePermission(Permission permission) {
        permissions.remove(permission);
    }

    public boolean hasPermission(String permissionName) {
        return permissions.stream()
            .anyMatch(p -> p.getName().equals(permissionName));
    }

    public Set<Permission> getAllPermissions() {
        Set<Permission> allPermissions = new HashSet<>(permissions);

        // Inherit permissions from parent role
        if (parentRole != null) {
            allPermissions.addAll(parentRole.getAllPermissions());
        }

        return allPermissions;
    }

    public void softDelete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.isActive = false;
    }

    // Enums
    public enum RoleType {
        SYSTEM,        // System-level roles (SUPER_ADMIN)
        ADMINISTRATIVE,// Admin roles (ADMIN, COMPLIANCE_OFFICER)
        OPERATIONAL,   // Operational roles (SUPPORT_AGENT, MERCHANT)
        USER,          // Standard user roles
        SERVICE,       // Service-to-service roles
        CUSTOM         // Custom roles
    }
}
