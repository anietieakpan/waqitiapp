package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceCheckRequest {

    private List<String> complianceRules;
    
    private String checkType;
    
    private LocalDateTime effectiveDate;
    
    private boolean includeWarnings;
    
    private String requestedBy;
    
    private Map<String, Object> parameters;

    public enum CheckType {
        FULL_COMPLIANCE,
        KYC_COMPLIANCE,
        AML_COMPLIANCE,
        REGULATORY_COMPLIANCE,
        RISK_COMPLIANCE
    }

    public boolean isFullCheck() {
        return "FULL_COMPLIANCE".equalsIgnoreCase(checkType);
    }

    public boolean includesWarnings() {
        return includeWarnings;
    }

    public boolean hasParameters() {
        return parameters != null && !parameters.isEmpty();
    }
}