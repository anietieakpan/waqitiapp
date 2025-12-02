package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Whitelist Check Response
 * 
 * Response from checking if an entity is on the whitelist.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhitelistCheckResponse {
    
    /**
     * Whether the entity is whitelisted
     */
    private Boolean whitelisted;
    
    /**
     * Type of entity checked
     */
    private String entityType;
    
    /**
     * Value that was checked
     */
    private String entityValue;
    
    /**
     * Whitelist entry details if found
     */
    private WhitelistEntry whitelistEntry;
    
    /**
     * Check timestamp
     */
    private LocalDateTime checkedAt;
    
    /**
     * Check duration in milliseconds
     */
    private Long checkDurationMs;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Confidence level of the check (0.0 to 1.0)
     */
    private Double confidence;
    
    /**
     * Source of the whitelist entry
     */
    private String source;
    
    /**
     * Reason for whitelisting (if applicable)
     */
    private String reason;
    
    /**
     * Expiration date of whitelist entry
     */
    private LocalDateTime expiresAt;
    
    /**
     * Whether the whitelist entry is active
     */
    private Boolean active;
    
    /**
     * Check if entity should be trusted
     */
    public boolean shouldTrust() {
        return whitelisted && active != null && active;
    }
    
    /**
     * Check if whitelist entry is expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}