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
public class LargeExposure {
    private String counterparty;
    private BigDecimal exposureAmount;
    private BigDecimal percentageOfCapital;
}
