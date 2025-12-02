package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for consolidation elimination response
 * Contains the generated elimination entries and processing results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidationEliminationResponse {
    
    private UUID responseId;
    private UUID requestId;
    private String processingStatus; // COMPLETED, PARTIALLY_COMPLETED, FAILED, REQUIRES_APPROVAL
    private LocalDateTime processedAt;
    private String processedBy;
    private List<EliminationEntry> eliminationEntries;
    private int totalEliminationCount;
    private BigDecimal totalEliminationAmount;
    private String currency;
    private Map<String, Integer> eliminationCountByType;
    private Map<String, BigDecimal> eliminationAmountByType;
    private Map<String, List<EliminationEntry>> eliminationsByEntityPair;
    private List<String> warnings;
    private List<String> errors;
    private List<String> processingNotes;
    private boolean requiresManualReview;
    private List<String> itemsRequiringReview;
    private boolean allEntriesPosted;
    private List<UUID> postedJournalEntryIds;
    private List<UUID> pendingApprovalIds;
    private String consolidationSummary;
    private BigDecimal netEliminationImpact;
    private boolean balanceValidationPassed;
    private String validationResults;
    private LocalDateTime nextProcessingDate;
    private boolean isRecurringProcess;
}