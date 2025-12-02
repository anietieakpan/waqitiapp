package com.waqiti.security.rbac.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Permission Entity
 *
 * Represents fine-grained permissions granted to specific users
 */
@Entity
@Table(name = "user_permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "granted_at")
    private LocalDateTime grantedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "active")
    private Boolean active;

    @PrePersist
    protected void onCreate() {
        if (grantedAt == null) {
            grantedAt = LocalDateTime.now();
        }
        if (active == null) {
            active = true;
        }
    }
}
