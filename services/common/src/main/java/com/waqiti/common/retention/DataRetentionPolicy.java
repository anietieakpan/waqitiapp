package com.waqiti.common.retention;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Retention Policy Configuration
 *
 * PRODUCTION FIX: Created to support DataRetentionService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataRetentionPolicy {
    private String policyId;
    private String dataType;
    private String tableName;
    private String identifierColumn;
    private String timestampColumn;
    private Integer retentionDays;
    private boolean enabled;
    private String deletionStrategy; // SOFT_DELETE, HARD_DELETE, ARCHIVE

    /**
     * Alias for policyId to match service expectations
     */
    public String getId() {
        return policyId;
    }

    /**
     * Check if soft delete strategy is enabled
     */
    public boolean isUseSoftDelete() {
        return "SOFT_DELETE".equals(deletionStrategy);
    }
}
