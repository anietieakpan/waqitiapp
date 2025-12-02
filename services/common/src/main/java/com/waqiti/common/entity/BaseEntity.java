package com.waqiti.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Base entity class providing common fields and audit support
 * 
 * This abstract class serves as the foundation for all JPA entities in the system,
 * providing:
 * - UUID-based primary keys for distributed systems
 * - Optimistic locking with version control
 * - Comprehensive audit trail
 * - Soft delete capability
 * - Hibernate Envers integration for history tracking
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Audited
public abstract class BaseEntity implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique identifier using UUID for distributed systems compatibility
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @ToString.Include
    private UUID id;
    
    /**
     * Version for optimistic locking
     */
    @Version
    @Column(name = "version", nullable = false)
    @lombok.Builder.Default
    private Long version = 0L;
    
    /**
     * Creation timestamp - automatically set by Hibernate
     */
    @CreatedDate
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * Last modification timestamp - automatically updated by Hibernate
     */
    @LastModifiedDate
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * User who created this entity
     */
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
    
    /**
     * User who last modified this entity
     */
    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;
    
    /**
     * Soft delete flag - entities are not physically deleted
     */
    @Column(name = "deleted", nullable = false)
    @lombok.Builder.Default
    private Boolean deleted = false;
    
    /**
     * Deletion timestamp for soft deletes
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    /**
     * User who deleted this entity
     */
    @Column(name = "deleted_by")
    private String deletedBy;
    
    /**
     * Entity active status
     */
    @Column(name = "active", nullable = false)
    @lombok.Builder.Default
    private Boolean active = true;
    
    /**
     * Business key for natural identification (optional)
     * Subclasses should override this with their natural business key
     */
    @Column(name = "business_key", unique = true)
    private String businessKey;
    
    /**
     * Tenant ID for multi-tenancy support
     */
    @Column(name = "tenant_id")
    private String tenantId;
    
    /**
     * Additional metadata stored as JSON
     */
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Convert(converter = JsonAttributeConverter.class)
    private java.util.Map<String, Object> metadata;
    
    /**
     * Entity lifecycle callbacks
     */
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (active == null) {
            active = true;
        }
        if (deleted == null) {
            deleted = false;
        }
        onPrePersist();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        onPreUpdate();
    }
    
    @PreRemove
    protected void onRemove() {
        onPreRemove();
    }
    
    /**
     * Hook methods for subclasses to override
     */
    protected void onPrePersist() {
        // Override in subclasses if needed
    }
    
    protected void onPreUpdate() {
        // Override in subclasses if needed
    }
    
    protected void onPreRemove() {
        // Override in subclasses if needed
    }
    
    /**
     * Soft delete this entity
     */
    public void softDelete(String deletedBy) {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
        this.active = false;
    }
    
    /**
     * Restore a soft-deleted entity
     */
    public void restore() {
        this.deleted = false;
        this.deletedAt = null;
        this.deletedBy = null;
        this.active = true;
    }
    
    /**
     * Check if entity is new (not persisted)
     */
    @Transient
    public boolean isNew() {
        return id == null || version == null || version == 0L;
    }
    
    /**
     * Check if entity has been modified
     */
    @Transient
    public boolean isModified() {
        return version != null && version > 0L;
    }
    
    /**
     * Get age of entity in milliseconds
     */
    @Transient
    public long getAge() {
        if (createdAt == null) {
            return 0;
        }
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toMillis();
    }
    
    /**
     * Equals based on ID for entity identity
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity)) return false;
        BaseEntity that = (BaseEntity) o;
        return id != null && Objects.equals(id, that.id);
    }
    
    /**
     * HashCode based on ID
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
    
    /**
     * JSON Attribute Converter for metadata field
     */
    @Converter
    public static class JsonAttributeConverter implements AttributeConverter<java.util.Map<String, Object>, String> {
        
        private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper = 
            new com.fasterxml.jackson.databind.ObjectMapper();
        
        @Override
        public String convertToDatabaseColumn(java.util.Map<String, Object> attribute) {
            if (attribute == null || attribute.isEmpty()) {
                return null;
            }
            try {
                return objectMapper.writeValueAsString(attribute);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error converting map to JSON", e);
            }
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public java.util.Map<String, Object> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.trim().isEmpty()) {
                return new java.util.HashMap<>();
            }
            try {
                return objectMapper.readValue(dbData, java.util.Map.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error converting JSON to map", e);
            }
        }
    }
}