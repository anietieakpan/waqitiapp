package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * DTO for balance sufficiency check requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceSufficientRequest {
    
    @NotBlank
    private String customerId;
    
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;
    
    @NotBlank
    private String currency;
    
    private boolean includeReserved;
    private boolean includeFrozen;
    private String transactionType;
}