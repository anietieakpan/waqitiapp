package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fee Calculation Result
 *
 * @author Waqiti Platform Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeCalculationResult {

    private List<FeeComponent> feeComponents;
    private BigDecimal totalFees;
}
