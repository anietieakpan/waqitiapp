package com.waqiti.common.client;

import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Debit Request DTO
 */
@Data
@Builder
public class DebitRequest {
    @NotNull
    @Positive
    private BigDecimal amount;
    
    @NotBlank
    private String currency;
    
    @NotBlank
    private String reference;
    
    private String description;
    private String destination;
}