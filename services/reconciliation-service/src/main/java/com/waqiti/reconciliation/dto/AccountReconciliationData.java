package com.waqiti.reconciliation.dto;

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
public class AccountReconciliationData {
    private UUID accountId;
    private String accountNumber;
    private String accountType;
    private BigDecimal currentBalance;
    private BigDecimal availableBalance;
    private BigDecimal reservedBalance;
    private LocalDateTime asOfDate;
    private String status;
    private Integer transactionCount;
    private LocalDateTime lastTransactionDate;
}
