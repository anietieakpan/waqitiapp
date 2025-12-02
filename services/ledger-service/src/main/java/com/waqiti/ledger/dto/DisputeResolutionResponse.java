package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for dispute resolution response
 * Contains the response and outcome of a dispute resolution process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeResolutionResponse {
    
    private UUID responseId;
    private UUID requestId;
    private UUID disputeId;
    private String resolutionStatus; // RESOLVED, PARTIALLY_RESOLVED, UNRESOLVED, ESCALATED
    private String resolutionType; // ADJUSTMENT, REVERSAL, NO_ACTION, INVESTIGATION
    private LocalDateTime resolvedAt;
    private String resolvedBy;
    private String resolutionSummary;
    private String rootCauseAnalysis;
    private BigDecimal adjustedAmount;
    private String currency;
    private UUID adjustmentJournalEntryId;
    private String actionTaken;
    private List<String> correctiveActions;
    private List<String> preventiveMeasures;
    private boolean requiresFollowUp;
    private LocalDate followUpDate;
    private String followUpNotes;
    private String businessImpactAssessment;
    private boolean requiresApproval;
    private LocalDateTime approvedAt;
    private String approvedBy;
    private String approvalNotes;
    private List<String> attachedDocuments;
    private String resolutionNotes;
    private String lessonsLearned;
    private boolean satisfiedRequester;
    private LocalDateTime closedAt;
    private String closedBy;
}