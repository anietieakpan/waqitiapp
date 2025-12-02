package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiquidityRisk {
    private BigDecimal cashRatio;
    private BigDecimal quickRatio;
    private BigDecimal currentRatio;
    private BigDecimal liquidityGap;
    private Map<String, BigDecimal> maturityBuckets;
}
