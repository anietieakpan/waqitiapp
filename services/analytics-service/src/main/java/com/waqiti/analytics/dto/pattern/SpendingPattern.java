package com.waqiti.analytics.dto.pattern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpendingPattern {
    private String patternType; // CONSISTENT, IRREGULAR, CYCLICAL
    private BigDecimal averageAmount;
    private String frequency; // DAILY, WEEKLY, MONTHLY
    private List<String> categories;
    private BigDecimal consistency;
}