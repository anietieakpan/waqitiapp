package com.waqiti.common.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Base Domain Entity
 * Enhanced base class for domain entities with additional domain-specific features
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseDomainEntity<ID extends Serializable> extends BaseEntity<ID> {
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "tenant_id")
    private String tenantId;
    
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    @Column(name = "tags")
    private String tags;
    
    @Transient
    private Map<String, Object> transientData = new HashMap<>();
    
    /**
     * Get domain entity type
     * Subclasses should return their specific entity type
     */
    public abstract String getEntityType();
    
    /**
     * Get business key
     * Returns the natural/business key for the entity
     */
    public abstract String getBusinessKey();
    
    /**
     * Check if entity can be modified
     * Subclasses can override to implement custom logic
     */
    public boolean canBeModified() {
        return !isDeleted() && isActive();
    }
    
    /**
     * Check if entity can be deleted
     * Subclasses can override to implement custom logic
     */
    public boolean canBeDeleted() {
        return !isDeleted();
    }
    
    /**
     * Check if entity is active
     */
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
    
    /**
     * Check if entity is inactive
     */
    public boolean isInactive() {
        return "INACTIVE".equalsIgnoreCase(status);
    }
    
    /**
     * Check if entity is pending
     */
    public boolean isPending() {
        return "PENDING".equalsIgnoreCase(status);
    }
    
    /**
     * Check if entity is suspended
     */
    public boolean isSuspended() {
        return "SUSPENDED".equalsIgnoreCase(status);
    }
    
    /**
     * Activate the entity
     */
    public void activate() {
        if (canBeModified()) {
            this.status = "ACTIVE";
        } else {
            throw new IllegalStateException("Entity cannot be activated in current state");
        }
    }
    
    /**
     * Deactivate the entity
     */
    public void deactivate() {
        if (canBeModified()) {
            this.status = "INACTIVE";
        } else {
            throw new IllegalStateException("Entity cannot be deactivated in current state");
        }
    }
    
    /**
     * Suspend the entity
     */
    public void suspend(String reason) {
        if (canBeModified()) {
            this.status = "SUSPENDED";
            addMetadata("suspensionReason", reason);
            addMetadata("suspendedAt", java.time.Instant.now().toString());
        } else {
            throw new IllegalStateException("Entity cannot be suspended in current state");
        }
    }
    
    /**
     * Add metadata entry
     */
    public void addMetadata(String key, String value) {
        Map<String, String> metadataMap = parseMetadata();
        metadataMap.put(key, value);
        this.metadata = serializeMetadata(metadataMap);
    }
    
    /**
     * Get metadata value
     */
    public String getMetadataValue(String key) {
        Map<String, String> metadataMap = parseMetadata();
        return metadataMap.get(key);
    }
    
    /**
     * Remove metadata entry
     */
    public void removeMetadata(String key) {
        Map<String, String> metadataMap = parseMetadata();
        metadataMap.remove(key);
        this.metadata = serializeMetadata(metadataMap);
    }
    
    /**
     * Add tag
     */
    public void addTag(String tag) {
        if (tags == null || tags.isEmpty()) {
            tags = tag;
        } else if (!tags.contains(tag)) {
            tags = tags + "," + tag;
        }
    }
    
    /**
     * Remove tag
     */
    public void removeTag(String tag) {
        if (tags != null && tags.contains(tag)) {
            tags = tags.replace(tag, "").replace(",,", ",");
            if (tags.startsWith(",")) tags = tags.substring(1);
            if (tags.endsWith(",")) tags = tags.substring(0, tags.length() - 1);
        }
    }
    
    /**
     * Check if entity has tag
     */
    public boolean hasTag(String tag) {
        return tags != null && tags.contains(tag);
    }
    
    /**
     * Get all tags as array
     */
    public String[] getTags() {
        if (tags == null || tags.isEmpty()) {
            return new String[0];
        }
        return tags.split(",");
    }
    
    /**
     * Store transient data (not persisted)
     */
    public void setTransientValue(String key, Object value) {
        transientData.put(key, value);
    }
    
    /**
     * Get transient data (not persisted)
     */
    @SuppressWarnings("unchecked")
    public <T> T getTransientValue(String key, Class<T> type) {
        Object value = transientData.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Clear all transient data
     */
    public void clearTransientData() {
        transientData.clear();
    }
    
    /**
     * Parse metadata from string
     */
    private Map<String, String> parseMetadata() {
        Map<String, String> map = new HashMap<>();
        if (metadata != null && !metadata.isEmpty()) {
            // Simple key=value;key=value format
            String[] pairs = metadata.split(";");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    map.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return map;
    }
    
    /**
     * Serialize metadata to string
     */
    private String serializeMetadata(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        map.forEach((key, value) -> {
            if (sb.length() > 0) sb.append(";");
            sb.append(key).append("=").append(value);
        });
        return sb.toString();
    }
    
    /**
     * Validate entity state
     */
    @Override
    public void validate() {
        super.validate();
        
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalStateException("Entity status cannot be null or empty");
        }
        
        if (getEntityType() == null || getEntityType().trim().isEmpty()) {
            throw new IllegalStateException("Entity type cannot be null or empty");
        }
    }
    
    /**
     * Check equality based on business key
     */
    public boolean equalsBusinessKey(BaseDomainEntity<?> other) {
        if (other == null) return false;
        if (!Objects.equals(getEntityType(), other.getEntityType())) return false;
        return Objects.equals(getBusinessKey(), other.getBusinessKey());
    }
    
    @Override
    public String toString() {
        return String.format("%s[id=%s, businessKey=%s, status=%s, tenant=%s]",
                getEntityType(), getId(), getBusinessKey(), status, tenantId);
    }
}