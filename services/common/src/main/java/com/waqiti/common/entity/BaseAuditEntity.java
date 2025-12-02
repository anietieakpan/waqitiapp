package com.waqiti.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * BASE AUDIT ENTITY
 *
 * Base class for all JPA entities requiring audit trail and optimistic locking.
 *
 * COMPLIANCE REQUIREMENTS:
 * - SOX: Audit trail for all financial data changes
 * - GDPR: Track who created/modified personal data
 * - PCI DSS: Audit logging for all payment card data
 * - ISO 27001: Information security audit requirements
 *
 * FEATURES:
 * 1. OPTIMISTIC LOCKING: @Version prevents lost updates in concurrent transactions
 * 2. AUDIT TRAIL: createdAt, updatedAt, createdBy, updatedBy for compliance
 * 3. SOFT DELETE: Logical deletion preserves data for audit/compliance
 * 4. AUTOMATIC POPULATION: JPA listeners auto-fill audit fields
 *
 * USAGE:
 * <pre>
 * {@code
 * @Entity
 * @Table(name = "transactions")
 * public class Transaction extends BaseAuditEntity {
 *     // Your entity fields
 * }
 * }
 * </pre>
 *
 * DATA INTEGRITY:
 * - @Version ensures atomic updates (prevents race conditions)
 * - Audit fields are immutable (createdAt, createdBy)
 * - Soft delete preserves historical data
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 * @since 2025-01-01
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseAuditEntity {

    /**
     * OPTIMISTIC LOCKING VERSION
     *
     * Prevents lost updates in concurrent transactions.
     *
     * HOW IT WORKS:
     * 1. JPA checks version on UPDATE
     * 2. If version changed → throws OptimisticLockException
     * 3. Application retries or fails gracefully
     *
     * CRITICAL FOR:
     * - Wallet balance updates (prevent double-spend)
     * - Transaction status changes
     * - Account balance modifications
     * - Any concurrent financial operations
     *
     * EXAMPLE SCENARIO:
     * Thread 1 reads balance (version=1)
     * Thread 2 reads balance (version=1)
     * Thread 1 updates balance (version=2) ✅ SUCCESS
     * Thread 2 updates balance (version=1) ❌ FAILS - prevents lost update
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /**
     * CREATED TIMESTAMP
     *
     * Automatically populated on entity creation.
     * Immutable after creation (updatable=false).
     *
     * COMPLIANCE:
     * - Required for SOX audit trail
     * - Required for GDPR data lifecycle
     * - Required for PCI DSS logging
     *
     * TIMEZONE: Always UTC to prevent ambiguity
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * LAST MODIFIED TIMESTAMP
     *
     * Automatically updated on every entity modification.
     *
     * COMPLIANCE:
     * - Tracks data modification history
     * - Required for change auditing
     * - Supports forensic investigations
     */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * CREATED BY USER
     *
     * User ID who created this entity.
     * Automatically populated from Spring Security context.
     * Immutable after creation.
     *
     * COMPLIANCE:
     * - Required for SOX (who created financial record)
     * - Required for GDPR (data controller tracking)
     * - Required for security audits
     *
     * FORMAT: UUID as String (user ID from authentication)
     */
    @CreatedBy
    @Column(name = "created_by", length = 36, updatable = false)
    private String createdBy;

    /**
     * LAST MODIFIED BY USER
     *
     * User ID who last modified this entity.
     * Automatically updated from Spring Security context.
     *
     * COMPLIANCE:
     * - Tracks who made changes
     * - Required for audit trail
     * - Supports access reviews
     */
    @LastModifiedBy
    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    /**
     * SOFT DELETE FLAG
     *
     * Logical deletion flag - preserves data for audit/compliance.
     *
     * NEVER PHYSICALLY DELETE:
     * - Financial transactions (regulatory requirement)
     * - Audit logs (compliance requirement)
     * - User activity (security requirement)
     * - Payment records (PCI DSS requirement)
     *
     * GDPR RIGHT TO BE FORGOTTEN:
     * - Mark deleted=true
     * - Anonymize PII fields
     * - Preserve transaction history
     *
     * DEFAULT: false (not deleted)
     */
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    /**
     * DELETION TIMESTAMP
     *
     * When entity was soft-deleted (if deleted=true).
     * Null if entity is active.
     *
     * COMPLIANCE:
     * - Tracks when data was marked for deletion
     * - Supports data retention policies
     * - Required for GDPR deletion audits
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * DELETED BY USER
     *
     * User ID who soft-deleted this entity.
     * Null if entity is active.
     *
     * COMPLIANCE:
     * - Tracks who deleted data
     * - Required for forensic investigations
     * - Supports access reviews
     */
    @Column(name = "deleted_by", length = 36)
    private String deletedBy;

    /**
     * PRE-PERSIST HOOK
     *
     * Ensure defaults before saving new entity.
     * Called automatically before INSERT.
     */
    @PrePersist
    protected void onCreate() {
        if (deleted == null) {
            deleted = false;
        }
        if (version == null) {
            version = 0L;
        }
    }

    /**
     * PRE-UPDATE HOOK
     *
     * Additional validation before UPDATE.
     * Called automatically before UPDATE.
     */
    @PreUpdate
    protected void onUpdate() {
        // Prevent modification of soft-deleted entities
        if (deleted != null && deleted) {
            throw new IllegalStateException(
                "Cannot update soft-deleted entity: " + this.getClass().getSimpleName()
            );
        }
    }

    /**
     * Soft delete this entity
     *
     * USAGE:
     * <pre>
     * {@code
     * transaction.softDelete(userId);
     * transactionRepository.save(transaction);
     * }
     * </pre>
     *
     * @param deletedByUserId User performing the deletion
     */
    public void softDelete(String deletedByUserId) {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedByUserId;
    }

    /**
     * Restore soft-deleted entity
     *
     * @param restoredByUserId User performing the restoration
     */
    public void restore(String restoredByUserId) {
        this.deleted = false;
        this.deletedAt = null;
        this.deletedBy = null;
        // updatedBy will be set automatically by @LastModifiedBy
    }

    /**
     * Check if entity is soft-deleted
     */
    public boolean isDeleted() {
        return deleted != null && deleted;
    }

    /**
     * Check if entity is active (not deleted)
     */
    public boolean isActive() {
        return !isDeleted();
    }
}
