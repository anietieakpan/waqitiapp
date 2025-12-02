package com.waqiti.compliance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewKYCRequest {
    
    @NotNull
    private Boolean approved;
    
    private String notes;
    private String rejectionReason;
    private Map<String, String> documentVerificationResults;
    private String riskLevel;
    private Boolean requiresEnhancedDueDiligence;
}