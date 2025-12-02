package com.waqiti.audit.dto;

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
public class OperationalRisk {
    private String approach; // Basic, Standardized, Advanced
    private BigDecimal grossIncome;
    private BigDecimal capitalRequirement;
    private List<String> keyRiskIndicators;
    private List<LossEvent> lossEvents;
}
