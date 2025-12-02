package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for resolution result
 * Contains the final result and outcome of a resolution process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionResult {
    
    private UUID resultId;
    private UUID disputeId;
    private UUID requestId;
    private String resultType; // RESOLVED, PARTIALLY_RESOLVED, UNRESOLVED, REQUIRES_ESCALATION
    private String resolutionMethod; // ADJUSTMENT, REVERSAL, WRITE_OFF, RECLASSIFICATION, NO_ACTION
    private boolean successful;
    private LocalDateTime completedAt;
    private String completedBy;
    private BigDecimal finalAmount;
    private String currency;
    private String resultSummary;
    private List<String> actionsPerformed;
    private List<UUID> adjustmentJournalEntryIds;
    private BigDecimal totalAdjustmentAmount;
    private String impactAssessment;
    private boolean requiresFollowUp;
    private String followUpActions;
    private boolean preventiveMeasuresImplemented;
    private List<String> preventiveMeasures;
    private String qualityAssessment;
    private boolean customerSatisfied;
    private String feedbackReceived;
    private String lessonsLearned;
    private List<String> attachedEvidence;
    private String finalNotes;
    private LocalDateTime archivedAt;
    private boolean archived;
}