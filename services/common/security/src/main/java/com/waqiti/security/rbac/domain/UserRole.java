package com.waqiti.security.rbac.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents the assignment of a role to a user.
 * Supports time-limited assignments and delegation tracking.
 */
@Entity
@Table(name = "user_roles", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "role_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /**
     * User who has the role
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Role assigned to the user
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /**
     * Assignment status
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserRoleStatus status = UserRoleStatus.ACTIVE;

    /**
     * When the role assignment becomes effective
     */
    @Column
    private LocalDateTime effectiveFrom;

    /**
     * When the role assignment expires (null = permanent)
     */
    @Column
    private LocalDateTime expiresAt;

    /**
     * User who assigned this role
     */
    @Column(name = "assigned_by")
    private UUID assignedBy;

    /**
     * Reason for role assignment
     */
    @Column(length = 500)
    private String assignmentReason;

    /**
     * Whether this assignment was delegated
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean delegated = false;

    /**
     * Original role if this is a delegated assignment
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delegated_from_role_id")
    private Role delegatedFromRole;

    /**
     * User who delegated this role (if applicable)
     */
    @Column(name = "delegated_by")
    private UUID delegatedBy;

    /**
     * Context or scope for this role assignment
     */
    @Column(length = 200)
    private String context;

    /**
     * Additional metadata for the assignment
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

    public enum UserRoleStatus {
        ACTIVE,         // Currently active
        INACTIVE,       // Temporarily deactivated
        EXPIRED,        // Assignment has expired
        REVOKED,        // Assignment was revoked
        PENDING         // Awaiting activation
    }

    /**
     * Checks if the role assignment is currently active
     */
    public boolean isCurrentlyActive() {
        if (status != UserRoleStatus.ACTIVE) {
            return false;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // Check if effective date has passed
        if (effectiveFrom != null && now.isBefore(effectiveFrom)) {
            return false;
        }
        
        // Check if not expired
        if (expiresAt != null && now.isAfter(expiresAt)) {
            return false;
        }
        
        return true;
    }

    /**
     * Checks if the role assignment has expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Checks if the role assignment is pending activation
     */
    public boolean isPendingActivation() {
        return effectiveFrom != null && LocalDateTime.now().isBefore(effectiveFrom);
    }

    /**
     * Gets the remaining time for this assignment (if it expires)
     */
    public Long getRemainingTimeInMinutes() {
        if (expiresAt == null) {
            return null; // Permanent assignment
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(expiresAt)) {
            return 0L; // Already expired
        }
        
        return java.time.Duration.between(now, expiresAt).toMinutes();
    }

    /**
     * Activates the role assignment
     */
    public void activate() {
        this.status = UserRoleStatus.ACTIVE;
        if (this.effectiveFrom == null) {
            this.effectiveFrom = LocalDateTime.now();
        }
    }

    /**
     * Deactivates the role assignment
     */
    public void deactivate() {
        this.status = UserRoleStatus.INACTIVE;
    }

    /**
     * Revokes the role assignment
     */
    public void revoke(String reason) {
        this.status = UserRoleStatus.REVOKED;
        this.assignmentReason = reason;
    }

    /**
     * Expires the role assignment
     */
    public void expire() {
        this.status = UserRoleStatus.EXPIRED;
    }

    /**
     * Extends the expiration time
     */
    public void extend(LocalDateTime newExpirationTime) {
        if (newExpirationTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("New expiration time cannot be in the past");
        }
        this.expiresAt = newExpirationTime;
    }

    /**
     * Creates a delegation of this role assignment
     */
    public UserRole createDelegation(UUID targetUserId, UUID delegatingUserId, 
                                   LocalDateTime delegationExpiry, String delegationReason) {
        return UserRole.builder()
                .userId(targetUserId)
                .role(this.role)
                .status(UserRoleStatus.ACTIVE)
                .effectiveFrom(LocalDateTime.now())
                .expiresAt(delegationExpiry)
                .assignedBy(delegatingUserId)
                .assignmentReason(delegationReason)
                .delegated(true)
                .delegatedFromRole(this.role)
                .delegatedBy(delegatingUserId)
                .context(this.context)
                .build();
    }

    /**
     * Checks if this assignment can be delegated
     */
    public boolean canBeDelegated() {
        // Cannot delegate if:
        // 1. Already delegated
        // 2. Not currently active
        // 3. Role doesn't allow delegation (would need to check role properties)
        return !delegated && isCurrentlyActive();
    }

    /**
     * Gets a display string for this assignment
     */
    public String getDisplayString() {
        StringBuilder display = new StringBuilder();
        display.append(role.getName());
        
        if (context != null && !context.trim().isEmpty()) {
            display.append(" (").append(context).append(")");
        }
        
        if (delegated) {
            display.append(" [Delegated]");
        }
        
        if (expiresAt != null) {
            display.append(" [Expires: ").append(expiresAt.toLocalDate()).append("]");
        }
        
        return display.toString();
    }

    /**
     * Validates the assignment
     */
    public boolean isValid() {
        // Basic validation
        if (userId == null || role == null) {
            return false;
        }
        
        // Check time constraints
        if (effectiveFrom != null && expiresAt != null && effectiveFrom.isAfter(expiresAt)) {
            return false;
        }
        
        // Check delegation constraints
        if (delegated && (delegatedBy == null || delegatedFromRole == null)) {
            return false;
        }
        
        return true;
    }

    /**
     * Updates the status based on current time
     */
    public void updateStatusBasedOnTime() {
        if (status == UserRoleStatus.ACTIVE) {
            if (isExpired()) {
                expire();
            } else if (isPendingActivation()) {
                this.status = UserRoleStatus.PENDING;
            }
        }
    }
}