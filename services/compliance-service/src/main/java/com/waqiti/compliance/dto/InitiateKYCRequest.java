package com.waqiti.compliance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateKYCRequest {
    
    @NotNull
    private UUID userId;
    
    @NotNull
    private String kycLevel; // LEVEL_1, LEVEL_2, LEVEL_3
    
    private String verificationType; // BASIC, ENHANCED, FULL
    private String purpose; // ACCOUNT_OPENING, LIMIT_INCREASE, REGULATORY
    private String country;
    private Boolean skipDocumentUpload;
}