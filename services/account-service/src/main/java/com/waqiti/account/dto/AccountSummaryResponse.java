package com.waqiti.account.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSummaryResponse {
    private UUID accountId;
    private String accountNumber;
    private String accountName;
    private String accountType;
    private String status;
    private String currency;
    private BigDecimal currentBalance;
    private BigDecimal availableBalance;
    private LocalDateTime lastTransactionDate;
}