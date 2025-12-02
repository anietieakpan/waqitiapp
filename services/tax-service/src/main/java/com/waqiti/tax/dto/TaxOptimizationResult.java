package com.waqiti.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxOptimizationResult {
    private List<TaxSavingOpportunity> suggestions;
    private BigDecimal savings;
    private int optimizationScore; // 0-100
}
