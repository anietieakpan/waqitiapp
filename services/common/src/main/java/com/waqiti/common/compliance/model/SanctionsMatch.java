package com.waqiti.common.compliance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Individual sanctions match details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SanctionsMatch {
    private String matchId;
    private String sanctionsListName; // OFAC SDN, EU, UN, etc.
    private String sanctionedEntityId;
    private String sanctionedEntityName;
    private String sanctionedEntityType;
    private Double matchScore;
    private String matchType; // EXACT, FUZZY, PARTIAL
    private List<String> matchedFields;
    private List<String> aliases;
    private String program; // Sanctions program name
    private String reason; // Reason for sanctions
    private LocalDateTime listedDate;
    private String remarks;
    private boolean isPrimaryMatch;
}