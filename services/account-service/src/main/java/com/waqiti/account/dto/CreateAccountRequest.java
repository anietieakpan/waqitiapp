package com.waqiti.account.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotBlank(message = "Account name is required")
    @Size(min = 3, max = 100, message = "Account name must be between 3 and 100 characters")
    private String accountName;
    
    @NotBlank(message = "Account type is required")
    private String accountType;
    
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;
    
    @PositiveOrZero(message = "Daily limit must be positive or zero")
    private BigDecimal dailyLimit;
    
    @PositiveOrZero(message = "Monthly limit must be positive or zero")
    private BigDecimal monthlyLimit;
    
    @PositiveOrZero(message = "Minimum balance must be positive or zero")
    private BigDecimal minimumBalance;
    
    @Positive(message = "Maximum balance must be positive")
    private BigDecimal maximumBalance;
    
    @PositiveOrZero(message = "Credit limit must be positive or zero")
    private BigDecimal creditLimit;
    
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
}