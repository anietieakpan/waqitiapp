package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for balance validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceValidationRequest {
    
    @NotBlank
    private String customerId;
    
    @NotBlank
    private String accountType;
    
    private String currency;
    private boolean checkFrozen;
    private boolean checkActive;
}