package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FinancialImpact {
    private double totalImpact;
    private double penaltyAmount;
    private double creditAmount;
    private double revenueImpact;
    private String currency;
    private String calculationMethod;
    
    public double getTotalImpact() {
        return totalImpact;
    }
}