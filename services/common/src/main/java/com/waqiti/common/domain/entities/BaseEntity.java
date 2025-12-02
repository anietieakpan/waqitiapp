package com.waqiti.common.domain.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Base Entity
 * Abstract base class for all domain entities
 * Provides common fields and behavior for all entities
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity<ID extends Serializable> implements Serializable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private ID id;
    
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
    
    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;
    
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    @Column(name = "deleted_by")
    private String deletedBy;
    
    /**
     * Pre-persist callback
     * Called before entity is persisted to database
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }
    
    /**
     * Pre-update callback
     * Called before entity is updated in database
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    /**
     * Soft delete the entity
     */
    public void softDelete(String deletedBy) {
        this.deleted = true;
        this.deletedAt = Instant.now();
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
    
    /**
     * Check if entity is new (not persisted)
     */
    @Transient
    public boolean isNew() {
        return id == null;
    }
    
    /**
     * Check if entity has been modified
     */
    @Transient
    public boolean isModified() {
        return version != null && version > 0;
    }
    
    /**
     * Get age of entity in milliseconds
     */
    @Transient
    public long getAge() {
        if (createdAt == null) {
            return 0;
        }
        return Instant.now().toEpochMilli() - createdAt.toEpochMilli();
    }
    
    /**
     * Get time since last modification in milliseconds
     */
    @Transient
    public long getTimeSinceLastModification() {
        if (updatedAt == null) {
            return 0;
        }
        return Instant.now().toEpochMilli() - updatedAt.toEpochMilli();
    }
    
    /**
     * Validate entity before save
     * Subclasses should override this method to provide validation logic
     */
    public void validate() {
        // Default implementation - subclasses can override
    }
    
    /**
     * Generate a new ID if needed
     * Useful for entities that need custom ID generation
     */
    protected ID generateId() {
        // Default implementation for UUID-based IDs
        @SuppressWarnings("unchecked")
        ID generatedId = (ID) UUID.randomUUID().toString();
        return generatedId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        BaseEntity<?> that = (BaseEntity<?>) o;
        
        // If both entities are new, they are not equal
        if (isNew() && that.isNew()) {
            return false;
        }
        
        // Compare by ID if both have IDs
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        // Use ID for hash code if available, otherwise use identity hash code
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }
    
    @Override
    public String toString() {
        return String.format("%s[id=%s, version=%d, createdAt=%s, updatedAt=%s]",
                getClass().getSimpleName(), id, version, createdAt, updatedAt);
    }
}