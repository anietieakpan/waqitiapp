package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Variance Analysis DTO
 * 
 * Provides variance analysis between two financial periods
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VarianceAnalysis {
    
    private BigDecimal currentAmount;
    private BigDecimal priorAmount;
    private BigDecimal variance;
    private BigDecimal percentageChange;
    private Boolean favorable;
    private Boolean significant;
    private String commentary;
}