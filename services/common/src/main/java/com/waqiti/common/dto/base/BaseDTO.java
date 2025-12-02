package com.waqiti.common.dto.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base Data Transfer Object providing common fields and behavior
 * 
 * This abstract class serves as the foundation for all DTOs in the system,
 * ensuring consistent structure, serialization, and metadata handling.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class BaseDTO implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique identifier for the entity
     */
    @EqualsAndHashCode.Include
    protected UUID id;
    
    /**
     * Version for optimistic locking
     */
    protected Long version;
    
    /**
     * Creation timestamp
     */
    protected LocalDateTime createdAt;
    
    /**
     * Last modification timestamp
     */
    protected LocalDateTime updatedAt;
    
    /**
     * User who created the entity
     */
    protected UUID createdBy;
    
    /**
     * User who last updated the entity
     */
    protected UUID updatedBy;
    
    /**
     * Indicates if the entity is active
     */
    protected Boolean active;
    
    /**
     * Metadata for extensibility
     */
    protected java.util.Map<String, Object> metadata;
    
    /**
     * Validates the DTO state
     * 
     * @return true if valid, false otherwise
     */
    public abstract boolean isValid();
    
    /**
     * Gets validation errors if any
     * 
     * @return list of validation errors
     */
    public abstract java.util.List<String> getValidationErrors();
    
    /**
     * Sanitizes the DTO data for security
     */
    public abstract void sanitize();
    
    /**
     * Creates a deep copy of this DTO
     * 
     * @return deep copy of the DTO
     */
    public abstract BaseDTO deepCopy();
    
    /**
     * Checks if this is a new entity (not persisted)
     * 
     * @return true if new, false otherwise
     */
    public boolean isNew() {
        return id == null;
    }
    
    /**
     * Checks if this entity has been modified
     * 
     * @return true if modified, false otherwise
     */
    public boolean isModified() {
        return updatedAt != null && !updatedAt.equals(createdAt);
    }
    
    /**
     * Gets the age of this entity in milliseconds
     * 
     * @return age in milliseconds
     */
    public long getAge() {
        if (createdAt == null) {
            return 0;
        }
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toMillis();
    }
}