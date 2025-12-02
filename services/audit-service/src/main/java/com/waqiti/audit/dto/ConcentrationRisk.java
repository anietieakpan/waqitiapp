package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcentrationRisk {
    private BigDecimal herfindahlIndex;
    private List<LargeExposure> largeExposures;
    private Map<String, BigDecimal> sectorConcentration;
    private Map<String, BigDecimal> geographicConcentration;
}
