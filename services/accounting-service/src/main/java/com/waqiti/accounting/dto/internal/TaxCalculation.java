package com.waqiti.accounting.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Internal DTO for tax calculations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxCalculation {

    private BigDecimal salesTax;
    private BigDecimal vatTax;
    private BigDecimal totalTax;
}
