package com.waqiti.account.dto;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAccountLimitsRequest {
    @PositiveOrZero(message = "Daily limit must be positive or zero")
    private BigDecimal dailyLimit;
    
    @PositiveOrZero(message = "Monthly limit must be positive or zero")
    private BigDecimal monthlyLimit;
    
    @PositiveOrZero(message = "Minimum balance must be positive or zero")
    private BigDecimal minimumBalance;
    
    private BigDecimal maximumBalance;
    
    @PositiveOrZero(message = "Credit limit must be positive or zero")
    private BigDecimal creditLimit;
}