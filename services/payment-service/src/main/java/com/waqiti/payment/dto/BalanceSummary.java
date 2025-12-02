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
 * DTO for balance summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceSummary {
    
    private String customerId;
    private BigDecimal totalBalance;
    private BigDecimal availableBalance;
    private BigDecimal reservedBalance;
    private BigDecimal frozenBalance;
    private Map<String, BigDecimal> currencyBalances;
    private String status;
    private Instant lastUpdated;
}