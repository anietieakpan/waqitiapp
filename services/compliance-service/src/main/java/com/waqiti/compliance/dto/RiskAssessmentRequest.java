package com.waqiti.compliance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessmentRequest {
    
    @NotNull
    private String entityId;
    
    @NotNull
    private String entityType; // CUSTOMER, TRANSACTION, RELATIONSHIP
    
    private String customerType; // INDIVIDUAL, BUSINESS, PEP, HIGH_NET_WORTH
    private String occupation;
    private String industry;
    private String country;
    private List<String> riskFactors;
    private BigDecimal transactionVolume;
    private Integer transactionFrequency;
    private Map<String, Object> additionalData;
    private String assessmentContext; // ONBOARDING, PERIODIC, EVENT_DRIVEN
}