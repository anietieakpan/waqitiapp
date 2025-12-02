package com.waqiti.accounting.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Internal DTO for fee calculations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeCalculation {

    private BigDecimal platformFee;
    private BigDecimal processorFee;
    private BigDecimal totalFees;
}
