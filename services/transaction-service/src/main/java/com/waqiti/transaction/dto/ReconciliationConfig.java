package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ReconciliationConfig {
    private boolean autoResolveDiscrepancies;
    private BigDecimal toleranceAmount;
    private double tolerancePercentage;
    private String externalSystemUrl;
    private String apiKey;
    
    public boolean canAutoResolve(String discrepancyType) {
        if (!autoResolveDiscrepancies) {
            return false;
        }
        // Define which discrepancy types can be auto-resolved
        return "AMOUNT_MISMATCH".equals(discrepancyType) || 
               "DATE_MISMATCH".equals(discrepancyType);
    }
}