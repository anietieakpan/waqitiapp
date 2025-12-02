package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * DTO for balance information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceInfo {
    
    private String customerId;
    private String accountId;
    private String currency;
    
    private BigDecimal totalBalance;
    private BigDecimal availableBalance;
    private BigDecimal reservedBalance;
    private BigDecimal frozenBalance;
    private BigDecimal pendingBalance;
    
    private String status; // ACTIVE, FROZEN, SUSPENDED, CLOSED
    private String accountType; // CHECKING, SAVINGS, CREDIT, PREPAID
    
    private Instant lastUpdated;
    private Instant lastTransactionDate;
    
    // Balance limits
    private BigDecimal minimumBalance;
    private BigDecimal maximumBalance;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    
    // Interest information
    private BigDecimal interestRate;
    private BigDecimal accruedInterest;
    
    private Map<String, Object> metadata;
}