package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDetailsResponse {
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private String accountType;
    private UUID parentAccountId;
    private String description;
    private Boolean isActive;
    private Boolean allowsTransactions;
    private String currency;
    private String normalBalance;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
}