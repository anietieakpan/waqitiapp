package com.waqiti.analytics.dto.analytics;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryBehavior {
    private String category;
    private String frequency; // HIGH, MEDIUM, LOW
    private String seasonality; // SEASONAL, NON_SEASONAL, NONE
    private String predictability; // HIGH, MEDIUM, LOW
    private BigDecimal averageAmount;
    private BigDecimal volatility;
    private List<String> patterns;
    private LocalDateTime lastAnalyzed;
}