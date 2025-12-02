package com.waqiti.common.events;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sanctions Screening Result Event
 * 
 * Published for all sanctions screening results to provide audit trail
 * and enable downstream processing of screening outcomes
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Data
@Builder
public class SanctionsScreeningResultEvent {
    
    // Event identification
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    
    @Builder.Default
    private String eventType = "SANCTIONS_SCREENING_RESULT";
    
    @Builder.Default
    private LocalDateTime eventTimestamp = LocalDateTime.now();
    
    // Screening details
    private String screeningId;
    private String screeningType; // USER, TRANSACTION, BATCH, PERIODIC
    private LocalDateTime screeningTimestamp;
    private String screeningSource; // ONBOARDING, TRANSACTION, SCHEDULED, MANUAL
    
    // Subject information
    private UUID userId;
    private UUID transactionId;
    private UUID merchantId;
    private String entityName;
    private String entityType;
    
    // Screening result
    private ScreeningResult screeningResult;
    private String resultStatus; // CLEAR, POTENTIAL_MATCH, CONFIRMED_MATCH, ERROR
    private List<SanctionsMatch> matches;
    private String riskLevel;
    
    // Processing details
    private String processingTime; // Duration in milliseconds
    private List<String> sanctionsListsScreened;
    private String screeningVersion;
    private Map<String, Object> screeningParameters;
    
    // Follow-up actions
    private boolean requiresManualReview;
    private boolean requiresEnhancedDueDiligence;
    private String assignedTo;
    private LocalDateTime reviewDeadline;
    
    // Compliance tracking
    private String complianceStatus;
    private String complianceOfficer;
    private Map<String, String> complianceNotes;
    
    // Additional context
    private String correlationId;
    private String originalRequestId;
    private Map<String, Object> additionalData;
    
    /**
     * Overall screening result
     */
    public enum ScreeningResult {
        CLEAR,                    // No matches found
        POTENTIAL_MATCH,         // Possible match requires review
        CONFIRMED_MATCH,         // Confirmed sanctions violation
        FALSE_POSITIVE,          // Previously cleared false positive
        UNDER_REVIEW,           // Currently under compliance review
        ERROR                   // Screening error occurred
    }
    
    /**
     * Individual sanctions match
     */
    @Data
    @Builder
    public static class SanctionsMatch {
        private String matchId;
        private String sanctionedEntityId;
        private String sanctionedEntityName;
        private String sanctionsList;
        private String matchType;
        private Double confidence;
        private List<String> matchedFields;
        private String sanctionsProgram;
        private LocalDateTime sanctionsDate;
        private String sanctionsReason;
        private Map<String, String> entityDetails;
    }
    
    /**
     * Check if screening was successful
     */
    public boolean isScreeningSuccessful() {
        return !ScreeningResult.ERROR.equals(screeningResult);
    }
    
    /**
     * Check if any action is required
     */
    public boolean requiresAction() {
        return requiresManualReview || 
               requiresEnhancedDueDiligence ||
               ScreeningResult.CONFIRMED_MATCH.equals(screeningResult) ||
               ScreeningResult.POTENTIAL_MATCH.equals(screeningResult);
    }
    
    /**
     * Get highest confidence match
     */
    public SanctionsMatch getHighestConfidenceMatch() {
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        
        return matches.stream()
            .max((m1, m2) -> Double.compare(
                m1.getConfidence() != null ? m1.getConfidence() : 0.0,
                m2.getConfidence() != null ? m2.getConfidence() : 0.0
            ))
            .orElse(null);
    }
}