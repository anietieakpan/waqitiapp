package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditRisk {
    private BigDecimal probabilityOfDefault;
    private BigDecimal lossGivenDefault;
    private BigDecimal exposureAtDefault;
    private BigDecimal expectedLoss;
    private BigDecimal riskWeightedAssets;
    private String rating;
}
