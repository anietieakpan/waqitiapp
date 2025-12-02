package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * Request DTO for balance freeze operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceFreezeRequest {
    
    @NotBlank
    private String reason;
    
    private BigDecimal amount;
    private String currency;
    private String reference;
    private String authorizedBy;
    private Integer durationDays;
}