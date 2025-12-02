package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryFilter {
    private LocalDate startDate;
    private LocalDate endDate;
    private String accountCode;
    private String reference;
    private String description;
    private String entryType;
    private String status;
    private UUID accountingPeriodId;
    private String sourceSystem;
    private String createdBy;
    private Boolean requiresApproval;
}