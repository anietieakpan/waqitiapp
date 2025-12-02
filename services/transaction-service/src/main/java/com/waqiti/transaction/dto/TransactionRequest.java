package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TransactionRequest {
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String currency;
    private String transactionType;
    private String description;
    private String initiatedBy;
}