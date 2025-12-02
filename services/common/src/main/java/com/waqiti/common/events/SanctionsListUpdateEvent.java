package com.waqiti.common.events;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sanctions List Update Event
 * 
 * Published when sanctions lists are updated from official sources
 * Triggers reprocessing of existing customers and transactions
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Data
@Builder
public class SanctionsListUpdateEvent {
    
    // Event identification
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    
    @Builder.Default
    private String eventType = "SANCTIONS_LIST_UPDATE";
    
    @Builder.Default
    private LocalDateTime eventTimestamp = LocalDateTime.now();
    
    // Update details
    private String updateId;
    private String updateType; // FULL_REFRESH, INCREMENTAL, EMERGENCY_UPDATE
    private LocalDateTime updateTimestamp;
    private String updateSource; // OFAC, EU, UN, UK_HMT, INTERNAL
    
    // Lists updated
    private List<String> sanctionsListsUpdated;
    private String primaryList; // SDN, CONSOLIDATED, etc.
    
    // Update statistics
    private UpdateStatistics updateStats;
    
    // Processing requirements
    private boolean requiresCustomerReprocessing;
    private boolean requiresTransactionReprocessing;
    private boolean requiresEmergencyScreening;
    private String reprocessingPriority; // LOW, MEDIUM, HIGH, CRITICAL
    
    // Affected entities
    private List<String> newlyAddedEntities;
    private List<String> removedEntities;
    private List<String> modifiedEntities;
    
    // Operational details
    private String updateInitiatedBy;
    private String updateReason;
    private Map<String, Object> updateMetadata;
    private String dataSourceVersion;
    private String checksumBefore;
    private String checksumAfter;
    
    // Follow-up actions
    private boolean requiresStakeholderNotification;
    private boolean requiresComplianceReview;
    private List<String> immediateActions;
    
    /**
     * Update statistics
     */
    @Data
    @Builder
    public static class UpdateStatistics {
        private int totalRecordsBefore;
        private int totalRecordsAfter;
        private int recordsAdded;
        private int recordsRemoved;
        private int recordsModified;
        private int recordsUnchanged;
        private String processingTime;
        private boolean updateSuccessful;
        private List<String> errors;
        private Map<String, Integer> recordsByList;
        private Map<String, Integer> recordsByType;
    }
    
    /**
     * Check if this is an emergency update
     */
    public boolean isEmergencyUpdate() {
        return "EMERGENCY_UPDATE".equals(updateType) || 
               requiresEmergencyScreening ||
               "CRITICAL".equals(reprocessingPriority);
    }
    
    /**
     * Check if reprocessing is required
     */
    public boolean requiresReprocessing() {
        return requiresCustomerReprocessing || 
               requiresTransactionReprocessing ||
               (newlyAddedEntities != null && !newlyAddedEntities.isEmpty());
    }
    
    /**
     * Get total entities affected
     */
    public int getTotalEntitiesAffected() {
        int total = 0;
        if (newlyAddedEntities != null) total += newlyAddedEntities.size();
        if (removedEntities != null) total += removedEntities.size();
        if (modifiedEntities != null) total += modifiedEntities.size();
        return total;
    }
    
    /**
     * Check if update was successful
     */
    public boolean wasUpdateSuccessful() {
        return updateStats != null && updateStats.isUpdateSuccessful();
    }
}