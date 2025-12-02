package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountLedgerResponse {
    private UUID accountId;
    private List<LedgerEntryResponse> entries;
    private long totalEntries;
    private int page;
    private int size;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
}