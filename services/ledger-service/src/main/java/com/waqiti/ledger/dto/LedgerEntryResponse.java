package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryResponse {
    private UUID ledgerId;
    private UUID transactionId;
    private UUID accountId;
    private String entryType;
    private BigDecimal amount;
    private BigDecimal runningBalance;
    private String referenceNumber;
    private String description;
    private String narrative;
    private String currency;
    private LocalDateTime transactionDate;
    private LocalDateTime valueDate;
    private UUID contraAccountId;
    private String status;
    private String metadata;
    private LocalDateTime createdAt;
}