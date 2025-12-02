package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ExternalTransactionRecord {
    private String externalId;
    private String sourceAccountId;
    private String targetAccountId;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime transactionDate;
    private String status;
    private String description;
}