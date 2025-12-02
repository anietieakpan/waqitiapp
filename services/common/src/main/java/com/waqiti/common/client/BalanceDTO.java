package com.waqiti.common.client;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Balance Data Transfer Object
 */
@Data
@Builder
public class BalanceDTO {
    private BigDecimal availableBalance;
    private BigDecimal totalBalance;
    private BigDecimal reservedBalance;
    private String currency;
    private LocalDateTime lastUpdated;
}