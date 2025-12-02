package com.waqiti.crypto.dto;

import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
@Builder
public class ClaimRewardsRequest {
    @NotBlank(message = "User address is required")
    private String userAddress;
    
    private boolean autoRestake;
    private String preferredPaymentMethod;
}