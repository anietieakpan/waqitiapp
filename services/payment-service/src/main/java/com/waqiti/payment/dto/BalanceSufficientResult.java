package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * DTO for balance sufficiency check results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceSufficientResult {
    
    private boolean sufficient;
    private String customerId;
    private BigDecimal requestedAmount;
    private BigDecimal availableBalance;
    private BigDecimal totalBalance;
    private BigDecimal shortfall;
    private String currency;
    private String reason;
    private boolean accountFrozen;
    private boolean overdraftAvailable;
    private BigDecimal overdraftLimit;
}