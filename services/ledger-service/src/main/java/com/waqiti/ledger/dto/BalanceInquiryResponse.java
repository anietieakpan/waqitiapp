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
public class BalanceInquiryResponse {
    private UUID accountId;
    private BigDecimal currentBalance;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal reservedBalance;
    private String currency;
    private LocalDateTime lastUpdated;
    private boolean reconciled;
}