package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Whitelist Request
 * 
 * Request to add or modify entries in the fraud detection whitelist.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhitelistRequest {
    
    /**
     * Operation type
     */
    private Operation operation;
    
    /**
     * Entity type to whitelist
     */
    private WhitelistEntry.EntityType entityType;
    
    /**
     * Entity value to whitelist
     */
    private String entityValue;
    
    /**
     * Reason for whitelisting
     */
    private String reason;
    
    /**
     * Expiration date (null for permanent)
     */
    private LocalDateTime expiresAt;
    
    /**
     * Whether this is a temporary entry
     */
    @Builder.Default
    private Boolean temporary = false;
    
    /**
     * Priority level
     */
    @Builder.Default
    private WhitelistEntry.Priority priority = WhitelistEntry.Priority.NORMAL;
    
    /**
     * Risk level override
     */
    private WhitelistEntry.RiskLevel riskLevelOverride;
    
    /**
     * Source system making the request
     */
    private String source;
    
    /**
     * User making the request
     */
    private String requestedBy;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Notes about this request
     */
    private String notes;
    
    /**
     * Tags for categorizing
     */
    private String tags;
    
    /**
     * Whether to notify on expiration
     */
    @Builder.Default
    private Boolean notifyOnExpiration = false;
    
    /**
     * Whether to auto-extend before expiration
     */
    @Builder.Default
    private Boolean autoExtend = false;
    
    /**
     * Auto-extension period in days
     */
    private Integer autoExtendDays;
    
    /**
     * Approval required for this request
     */
    @Builder.Default
    private Boolean requiresApproval = false;
    
    /**
     * Approver user ID
     */
    private String approvedBy;
    
    /**
     * Approval timestamp
     */
    private LocalDateTime approvedAt;
    
    /**
     * Approval notes
     */
    private String approvalNotes;
    
    /**
     * Request timestamp
     */
    private LocalDateTime requestedAt;
    
    public enum Operation {
        ADD,
        UPDATE,
        REMOVE,
        EXTEND,
        ACTIVATE,
        DEACTIVATE
    }
    
    /**
     * Check if the request is for a temporary entry
     */
    public boolean isTemporary() {
        return temporary || (expiresAt != null && expiresAt.isBefore(LocalDateTime.now().plusDays(30)));
    }
    
    /**
     * Check if approval is required
     */
    public boolean needsApproval() {
        return requiresApproval || 
               priority == WhitelistEntry.Priority.CRITICAL ||
               (expiresAt == null); // Permanent entries need approval
    }
    
    /**
     * Check if the request is approved
     */
    public boolean isApproved() {
        return !needsApproval() || (approvedBy != null && approvedAt != null);
    }
    
    /**
     * Get duration in days (if has expiration)
     */
    public Long getDurationDays() {
        if (expiresAt == null) {
            return null;
        }
        LocalDateTime now = requestedAt != null ? requestedAt : LocalDateTime.now();
        return java.time.Duration.between(now, expiresAt).toDays();
    }
}