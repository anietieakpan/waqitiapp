package com.waqiti.common.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base entity with optimistic locking support for all financial entities.
 * 
 * CRITICAL: This prevents race conditions and ensures data consistency in 
 * concurrent operations by using version-based optimistic locking.
 * 
 * All financial entities (Wallet, Transaction, Payment, etc.) MUST extend this class.
 * 
 * Features:
 * - Optimistic locking via @Version field
 * - Automatic audit fields (createdAt, updatedAt, createdBy, updatedBy)
 * - UUID-based primary keys
 * - Soft delete support
 * - Timestamp tracking for all modifications
 * 
 * Usage:
 * <pre>
 * {@code
 * @Entity
 * @Table(name = "wallets")
 * public class Wallet extends OptimisticLockingEntity {
 *     private BigDecimal balance;
 *     private String currency;
 *     // ... other fields
 * }
 * }
 * </pre>
 * 
 * When updating:
 * <pre>
 * {@code
 * try {
 *     wallet.setBalance(newBalance);
 *     walletRepository.save(wallet); // Automatically checks version
 * } catch (OptimisticLockException e) {
 *     // Handle concurrent modification
 *     log.warn("Concurrent modification detected for wallet: {}", wallet.getId());
 *     // Retry or handle appropriately
 * }
 * }
 * </pre>
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@MappedSuperclass
@Data
@SuperBuilder
@EntityListeners(AuditingEntityListener.class)
public abstract class OptimisticLockingEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key - UUID for distributed system compatibility
     */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * CRITICAL: Version field for optimistic locking
     * 
     * This field is automatically incremented by JPA on each update.
     * If two transactions try to update the same entity concurrently,
     * the second one will fail with OptimisticLockException.
     * 
     * How it works:
     * 1. Entity is loaded with version=1
     * 2. Transaction A loads entity (version=1)
     * 3. Transaction B loads entity (version=1)
     * 4. Transaction A updates and saves -> version becomes 2 (SUCCESS)
     * 5. Transaction B tries to save with version=1 -> OptimisticLockException (FAILURE)
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    /**
     * Creation timestamp - automatically populated
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last modification timestamp - automatically updated
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * User who created this entity - automatically populated from security context
     */
    @CreatedBy
    @Column(name = "created_by", length = 255, updatable = false)
    private String createdBy;

    /**
     * User who last modified this entity - automatically updated
     */
    @LastModifiedBy
    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    /**
     * Soft delete flag - entity is marked as deleted but not physically removed
     * This preserves audit trail and referential integrity
     */
    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    /**
     * Soft delete timestamp
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * User who deleted this entity
     */
    @Column(name = "deleted_by", length = 255)
    private String deletedBy;

    /**
     * Row-level security tenant ID for multi-tenancy support
     */
    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    /**
     * Default constructor required by JPA
     */
    protected OptimisticLockingEntity() {
        // JPA requires this
    }

    /**
     * Pre-persist hook to set timestamps
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (version == null) {
            version = 0L;
        }
    }

    /**
     * Pre-update hook to update timestamp
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Soft delete this entity
     */
    public void softDelete(String deletedBy) {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Restore a soft-deleted entity
     */
    public void restore() {
        this.deleted = false;
        this.deletedAt = null;
        this.deletedBy = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if entity is soft-deleted
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * Check if entity is newly created (not yet persisted)
     */
    public boolean isNew() {
        return id == null || version == null || version == 0L;
    }

    /**
     * Get age of entity in seconds
     */
    public long getAgeInSeconds() {
        if (createdAt == null) {
            return 0;
        }
        return java.time.Duration.between(createdAt, LocalDateTime.now()).getSeconds();
    }

    /**
     * Get time since last update in seconds
     */
    public long getSecondsSinceUpdate() {
        if (updatedAt == null) {
            return 0;
        }
        return java.time.Duration.between(updatedAt, LocalDateTime.now()).getSeconds();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OptimisticLockingEntity)) return false;
        OptimisticLockingEntity that = (OptimisticLockingEntity) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id=" + id +
                ", version=" + version +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", deleted=" + deleted +
                '}';
    }
}