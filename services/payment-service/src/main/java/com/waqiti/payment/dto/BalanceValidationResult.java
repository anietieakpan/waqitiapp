package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Result DTO for balance validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceValidationResult {
    
    private boolean valid;
    private String customerId;
    private String accountStatus;
    private BigDecimal availableBalance;
    private String currency;
    private boolean isFrozen;
    private boolean isActive;
    private String errorMessage;
}