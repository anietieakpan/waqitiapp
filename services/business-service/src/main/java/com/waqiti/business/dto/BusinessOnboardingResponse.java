package com.waqiti.business.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class BusinessOnboardingResponse {
    
    private UUID accountId;
    private String businessName;
    private String status;
    private boolean verificationRequired;
    private String estimatedVerificationTime;
    private String nextSteps;
    private String accountNumber;
}