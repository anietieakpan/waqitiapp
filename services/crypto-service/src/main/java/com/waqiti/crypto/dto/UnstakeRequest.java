package com.waqiti.crypto.dto;

import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigInteger;

@Data
@Builder
public class UnstakeRequest {
    @NotBlank(message = "User address is required")
    private String userAddress;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0", inclusive = false, message = "Amount must be greater than zero")
    private BigInteger amount;
    
    private boolean acceptEarlyUnstakePenalty;
    private String reason;
}