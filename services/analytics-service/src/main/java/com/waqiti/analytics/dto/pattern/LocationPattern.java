package com.waqiti.analytics.dto.pattern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationPattern {
    private String primaryLocation;
    private Map<String, BigDecimal> locationSpending; // Location -> amount
    private List<String> frequentLocations;
    private BigDecimal mobilityScore; // 0-1, how much user moves around
    private String homeBaseRadius; // SMALL, MEDIUM, LARGE
}