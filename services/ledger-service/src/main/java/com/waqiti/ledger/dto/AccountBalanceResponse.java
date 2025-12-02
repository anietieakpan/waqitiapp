package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalanceResponse {
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private String accountType;
    private BigDecimal balance;
    private BigDecimal currentBalance; // Current balance (alias for balance)
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance; // Pending balance (sum of pending debits and credits)
    private BigDecimal reservedBalance; // Reserved balance
    private BigDecimal pendingDebits;
    private BigDecimal pendingCredits;
    private LocalDate asOfDate;
    private String currency;
    private LocalDateTime lastUpdated;
    private boolean isActive;
    private LocalDateTime lastTransactionDate;
    private BalanceBreakdown breakdown;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BalanceBreakdown {
    private BigDecimal openingBalance;
    private BigDecimal periodDebits;
    private BigDecimal periodCredits;
    private BigDecimal adjustments;
    private BigDecimal closingBalance;
}