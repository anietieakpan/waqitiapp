package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Risk indicator model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskIndicator {
    private String indicatorType;
    private String description;
    private BigDecimal severity; // 0.0 to 10.0
    private String category;
    private String recommendation;
}