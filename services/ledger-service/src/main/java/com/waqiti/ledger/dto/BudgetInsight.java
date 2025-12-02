package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO representing budget insights and analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetInsight {
    
    private String type; // INFO, WARNING, CRITICAL, SUCCESS
    private String category; // OVER_BUDGET, UNDER_UTILIZED, TREND_ANALYSIS, etc.
    private String title;
    private String message;
    private String impact; // LOW, MEDIUM, HIGH
    private BigDecimal value;
    private String unit;
    
    private String recommendation;
    private String actionRequired;
    
    private LocalDateTime generatedAt;
    
    // Additional context
    private String accountName;
    private String costCenter;
    private BigDecimal threshold;
    private BigDecimal currentValue;
    
    public static BudgetInsight warning(String category, String message, String impact) {
        return BudgetInsight.builder()
            .type("WARNING")
            .category(category)
            .message(message)
            .impact(impact)
            .generatedAt(LocalDateTime.now())
            .build();
    }
    
    public static BudgetInsight info(String category, String message, String impact) {
        return BudgetInsight.builder()
            .type("INFO")
            .category(category)
            .message(message)
            .impact(impact)
            .generatedAt(LocalDateTime.now())
            .build();
    }
    
    public static BudgetInsight critical(String category, String message, String impact) {
        return BudgetInsight.builder()
            .type("CRITICAL")
            .category(category)
            .message(message)
            .impact(impact)
            .generatedAt(LocalDateTime.now())
            .build();
    }
}