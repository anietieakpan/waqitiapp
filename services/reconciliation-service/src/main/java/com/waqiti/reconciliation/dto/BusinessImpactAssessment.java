package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessImpactAssessment {

    @Builder.Default
    private LocalDateTime assessmentDate = LocalDateTime.now();
    
    private UUID breakId;
    
    private BigDecimal financialImpact;
    
    private String operationalImpact;
    
    private String regulatoryImpact;
    
    private String customerImpact;
    
    private String reputationalImpact;
    
    private BreakInvestigationService.BusinessImpactSeverity overallSeverity;
    
    private String assessmentNotes;

    public boolean hasFinancialImpact() {
        return financialImpact != null && financialImpact.compareTo(BigDecimal.ZERO) != 0;
    }

    public boolean hasSignificantFinancialImpact() {
        return hasFinancialImpact() && 
               financialImpact.abs().compareTo(new BigDecimal("10000")) > 0;
    }

    public boolean isCriticalSeverity() {
        return BreakInvestigationService.BusinessImpactSeverity.CRITICAL.equals(overallSeverity);
    }

    public boolean isHighSeverity() {
        return BreakInvestigationService.BusinessImpactSeverity.HIGH.equals(overallSeverity);
    }
}