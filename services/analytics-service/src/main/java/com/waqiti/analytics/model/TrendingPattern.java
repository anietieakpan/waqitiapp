package com.waqiti.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Trending pattern model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendingPattern {
    
    private String pattern;
    private String period;
    private BigDecimal value;
    private String trend;
    private BigDecimal changePercentage;
    private String category;
    private Instant detectedAt;
    private String description;
}