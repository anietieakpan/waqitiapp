package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO for requesting consolidation elimination entries
 * Contains parameters and criteria for generating elimination entries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidationEliminationRequest {
    
    private UUID requestId;
    private LocalDate consolidationDate;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private List<UUID> entityIds;
    private List<String> eliminationTypes; // INTERCOMPANY_SALES, INTERCOMPANY_RECEIVABLES, INVESTMENT_ELIMINATION
    private String consolidationScope; // FULL, PARTIAL, SPECIFIC_ENTITIES
    private String currency;
    private boolean includeIntraGroupTransactions;
    private boolean includeMinorityInterests;
    private boolean automaticEliminations;
    private String eliminationMethod; // FULL_ELIMINATION, PROPORTIONATE, EQUITY_METHOD
    private List<String> accountCodesToInclude;
    private List<String> accountCodesToExclude;
    private String materialityThreshold;
    private boolean generateJournalEntries;
    private boolean requireApproval;
    private String requestedBy;
    private String consolidationGroup;
    private String reportingStandard; // IFRS, GAAP, LOCAL
    private String notes;
    private boolean dryRun; // If true, only calculate but don't create entries
}