package com.waqiti.common.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Base Entity with common auditing fields
 *
 * Provides standard fields for all domain entities:
 * - id: Primary key (UUID/String)
 * - version: Optimistic locking version
 * - createdAt: Automatic creation timestamp
 * - updatedAt: Automatic update timestamp
 * - createdBy: User who created the entity
 * - updatedBy: User who last updated the entity
 *
 * USAGE:
 * @Entity
 * @Table(name = "my_table")
 * public class MyEntity extends BaseEntity {
 *     // Additional fields
 * }
 *
 * FEATURES:
 * - Optimistic locking via @Version
 * - Automatic timestamp management via Hibernate annotations
 * - Audit trail (createdBy, updatedBy)
 * - Serializable for caching support
 * - Lombok @Data for getters/setters
 */
@MappedSuperclass
@Data
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key - String/UUID type for flexibility
     * Override in subclasses if different ID type is needed
     */
    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 255)
    private String id;

    /**
     * Optimistic locking version
     * Automatically incremented by JPA on each update
     * Prevents lost updates in concurrent scenarios
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * Creation timestamp
     * Automatically set when entity is first persisted
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    /**
     * Last update timestamp
     * Automatically updated on each entity modification
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * User who created this entity
     * Should be set by application layer (security context)
     */
    @Column(name = "created_by", updatable = false, length = 255)
    private String createdBy;

    /**
     * User who last updated this entity
     * Should be updated by application layer (security context)
     */
    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    /**
     * Soft delete flag (optional)
     * Set to true instead of deleting records
     */
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    /**
     * Deletion timestamp (optional)
     * Set when soft delete is performed
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * User who deleted this entity (optional)
     * Set when soft delete is performed
     */
    @Column(name = "deleted_by", length = 255)
    private String deletedBy;

    /**
     * Pre-persist callback
     * Initialize default values before first save
     */
    @PrePersist
    protected void onCreate() {
        if (this.deleted == null) {
            this.deleted = false;
        }
        // createdAt and updatedAt are handled by @CreationTimestamp and @UpdateTimestamp
        // createdBy and updatedBy should be set by application layer (e.g., audit interceptor)
    }

    /**
     * Pre-update callback
     * Can be used for additional pre-update logic
     */
    @PreUpdate
    protected void onUpdate() {
        // updatedAt is automatically handled by @UpdateTimestamp
        // updatedBy should be set by application layer (e.g., audit interceptor)
    }

    /**
     * Check if entity is new (not yet persisted)
     *
     * @return true if entity has no ID, false otherwise
     */
    @Transient
    public boolean isNew() {
        return this.id == null;
    }

    /**
     * Check if entity is soft deleted
     *
     * @return true if deleted flag is set
     */
    @Transient
    public boolean isDeleted() {
        return this.deleted != null && this.deleted;
    }

    /**
     * Perform soft delete
     * Sets deleted flag and deletion timestamp
     *
     * @param deletedBy User performing the deletion
     */
    public void softDelete(String deletedBy) {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }

    /**
     * Restore soft deleted entity
     */
    public void restore() {
        this.deleted = false;
        this.deletedAt = null;
        this.deletedBy = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity)) return false;
        BaseEntity that = (BaseEntity) o;
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s{id='%s', version=%d, createdAt=%s, updatedAt=%s, createdBy='%s', updatedBy='%s', deleted=%s}",
                getClass().getSimpleName(), id, version, createdAt, updatedAt, createdBy, updatedBy, deleted);
    }
}
