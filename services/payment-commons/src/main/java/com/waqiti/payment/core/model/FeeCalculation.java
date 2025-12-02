package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Fee calculation model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeCalculation {
    
    private BigDecimal processingFee;
    private BigDecimal networkFee;
    private BigDecimal totalFees;
    private String feeStructure;
    private String currency;
    
    public static FeeCalculation noFees() {
        return FeeCalculation.builder()
            .processingFee(BigDecimal.ZERO)
            .networkFee(BigDecimal.ZERO)
            .totalFees(BigDecimal.ZERO)
            .feeStructure("No fees")
            .build();
    }
}