package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class LedgerEntryRequest {
    private String accountId;
    private String entryType;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String referenceNumber;
    private LocalDateTime transactionDate;
    private LocalDateTime valueDate;
    private String contraAccountId;
}