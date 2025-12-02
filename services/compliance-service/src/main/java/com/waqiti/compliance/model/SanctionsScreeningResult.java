package com.waqiti.compliance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Sanctions Screening Result Model
 * 
 * Represents the result of screening an entity against sanctions lists
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SanctionsScreeningResult {
    
    private String screeningId;
    private UUID entityId;
    private String entityName;
    private String entityType;
    
    // Screening results
    private ScreeningStatus status;
    private boolean hasMatch;
    private Double matchScore; // 0.0 to 1.0, where 1.0 is exact match
    private List<SanctionsMatch> matches;
    
    // Risk assessment
    private RiskLevel riskLevel;
    private String riskReason;
    private List<String> riskIndicators;
    
    // Screening metadata
    private LocalDateTime screenedAt;
    private String screeningMethod;
    private List<String> listsChecked;
    private String screeningVersion;
    
    // Action required
    private boolean requiresManualReview;
    private boolean requiresImmediateAction;
    private String recommendedAction;
    private String complianceNotes;
    
    /**
     * Screening status
     */
    public enum ScreeningStatus {
        CLEAR,              // No matches found
        POTENTIAL_MATCH,    // Potential match requiring review
        CONFIRMED_MATCH,    // Confirmed sanctions match
        FALSE_POSITIVE,     // Cleared as false positive
        PENDING_REVIEW,     // Awaiting manual review
        ERROR              // Screening error
    }
    
    /**
     * Risk level
     */
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    /**
     * Individual sanctions match
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SanctionsMatch {
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
    
    /**
     * Check if immediate action is required
     */
    public boolean requiresImmediateAction() {
        return status == ScreeningStatus.CONFIRMED_MATCH ||
               (hasMatch && matchScore != null && matchScore > 0.95) ||
               riskLevel == RiskLevel.CRITICAL;
    }
    
    /**
     * Check if manual review is needed
     */
    public boolean requiresManualReview() {
        return status == ScreeningStatus.POTENTIAL_MATCH ||
               status == ScreeningStatus.PENDING_REVIEW ||
               (hasMatch && matchScore != null && matchScore > 0.7 && matchScore <= 0.95);
    }
    
    /**
     * Get the highest match score
     */
    public Double getHighestMatchScore() {
        if (matches == null || matches.isEmpty()) {
            return 0.0;
        }
        return matches.stream()
            .mapToDouble(SanctionsMatch::getMatchScore)
            .max()
            .orElse(0.0);
    }
}