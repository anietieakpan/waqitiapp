package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryDetailResponse {
    private JournalEntryResponse journalEntry;
    private List<LedgerEntryResponse> ledgerEntries;
    private List<AuditTrailEntry> auditTrail;
    private List<RelatedJournalEntry> relatedEntries;
    private List<ApprovalHistory> approvalHistory;
    private AccountingPeriodInfo accountingPeriod;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AuditTrailEntry {
    private UUID auditId;
    private String action;
    private String performedBy;
    private LocalDateTime performedAt;
    private String details;
    private String ipAddress;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RelatedJournalEntry {
    private UUID journalEntryId;
    private String entryNumber;
    private String relationship;
    private String description;
    private String status;
    private LocalDateTime entryDate;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ApprovalHistory {
    private UUID approvalId;
    private String action;
    private String performedBy;
    private LocalDateTime performedAt;
    private String notes;
    private String previousStatus;
    private String newStatus;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AccountingPeriodInfo {
    private UUID periodId;
    private String periodCode;
    private String periodName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
}