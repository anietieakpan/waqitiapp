package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO representing a pair of entities for inter-company reconciliation
 * Contains information about the two entities being reconciled
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityPair {
    
    private UUID pairId;
    private UUID sourceEntityId;
    private String sourceEntityName;
    private String sourceEntityCode;
    private UUID targetEntityId;
    private String targetEntityName;
    private String targetEntityCode;
    private String relationshipType; // PARENT_SUBSIDIARY, SISTER_COMPANIES, JOINT_VENTURE, BRANCH_HEAD_OFFICE
    private String reconciliationType; // FULL, PARTIAL, SPECIFIC_ACCOUNTS
    private String frequency; // DAILY, WEEKLY, MONTHLY, QUARTERLY, ANNUALLY, ON_DEMAND
    private LocalDate lastReconciliationDate;
    private LocalDate nextScheduledReconciliation;
    private boolean isActive;
    private String status; // ACTIVE, INACTIVE, SUSPENDED, UNDER_REVIEW
    private String currency; // Primary currency for transactions between entities
    private boolean automaticReconciliation;
    private String reconciliationThreshold;
    private String matchingCriteria;
    private String notes;
    private String createdBy;
    private String updatedBy;
}