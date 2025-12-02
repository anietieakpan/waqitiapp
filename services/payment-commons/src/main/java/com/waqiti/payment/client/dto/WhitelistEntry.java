package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Whitelist Entry
 * 
 * Represents an entry in the fraud detection whitelist.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhitelistEntry {
    
    /**
     * Unique entry ID
     */
    private String entryId;
    
    /**
     * Type of entity being whitelisted
     */
    private EntityType entityType;
    
    /**
     * Value being whitelisted
     */
    private String entityValue;
    
    /**
     * Reason for whitelisting
     */
    private String reason;
    
    /**
     * Who added this entry
     */
    private String addedBy;
    
    /**
     * When the entry was created
     */
    private LocalDateTime createdAt;
    
    /**
     * When the entry was last updated
     */
    private LocalDateTime updatedAt;
    
    /**
     * Expiration date (null for permanent)
     */
    private LocalDateTime expiresAt;
    
    /**
     * Whether the entry is currently active
     */
    @Builder.Default
    private Boolean active = true;
    
    /**
     * Source system that added this entry
     */
    private String source;
    
    /**
     * Risk level override for this entity
     */
    private RiskLevel riskLevelOverride;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Notes about this whitelist entry
     */
    private String notes;
    
    /**
     * Whether this is a temporary entry
     */
    @Builder.Default
    private Boolean temporary = false;
    
    /**
     * Priority level for whitelist processing
     */
    @Builder.Default
    private Priority priority = Priority.NORMAL;
    
    /**
     * Tags for categorizing entries
     */
    private String tags;
    
    public enum EntityType {
        EMAIL,
        PHONE,
        IP_ADDRESS,
        DEVICE_ID,
        USER_ID,
        MERCHANT_ID,
        ACCOUNT_NUMBER,
        CARD_NUMBER,
        DOMAIN,
        USER_AGENT,
        GEOLOCATION,
        CUSTOM
    }
    
    public enum RiskLevel {
        VERY_LOW,
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH
    }
    
    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }
    
    /**
     * Check if the whitelist entry is currently valid
     */
    public boolean isValid() {
        return active && !isExpired();
    }
    
    /**
     * Check if the entry has expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * Check if the entry should bypass fraud checks
     */
    public boolean shouldBypassFraudChecks() {
        return isValid() && (riskLevelOverride == RiskLevel.VERY_LOW || riskLevelOverride == RiskLevel.LOW);
    }
    
    /**
     * Get display name for entity type
     */
    public String getEntityTypeDisplayName() {
        return entityType.name().replace("_", " ").toLowerCase();
    }
}