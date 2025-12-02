package com.waqiti.account.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDetailsResponse {
    private UUID accountId;
    private String accountNumber;
    private UUID userId;
    private String accountName;
    private String accountType;
    private String accountCategory;
    private String status;
    private String currency;
    private BigDecimal currentBalance;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal reservedBalance;
    private BigDecimal dailyTransactionLimit;
    private BigDecimal monthlyTransactionLimit;
    private BigDecimal minimumBalance;
    private BigDecimal maximumBalance;
    private String complianceLevel;
    private LocalDateTime openedDate;
    private LocalDateTime lastTransactionDate;
    private Map<String, String> metadata;
}