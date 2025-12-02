package com.waqiti.reconciliation.domain;

import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DiscrepancyAnalysis {
    private final Long totalDiscrepancies;
    private final BigDecimal totalDiscrepancyAmount;
    private final List<DiscrepancyCategory> categorizedDiscrepancies;
    private final Map<String, BigDecimal> discrepancyTrends;
    private final List<String> rootCauseAnalysis;
    private final List<String> recommendations;
    private final LocalDateTime analysisDate;
    private final String timeFrame;
    
    @Data
    @Builder
    public static class DiscrepancyCategory {
        private final String categoryName;
        private final Long count;
        private final BigDecimal totalAmount;
        private final BigDecimal averageAmount;
        private final String description;
        private final DiscrepancyImpact impact;
    }
    
    public enum DiscrepancyImpact {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    public BigDecimal getAverageDiscrepancyAmount() {
        if (totalDiscrepancies == null || totalDiscrepancies == 0) {
            return BigDecimal.ZERO;
        }
        return totalDiscrepancyAmount.divide(BigDecimal.valueOf(totalDiscrepancies),
            2, RoundingMode.HALF_UP);
    }
    
    public boolean hasHighImpactDiscrepancies() {
        return categorizedDiscrepancies != null && 
            categorizedDiscrepancies.stream()
                .anyMatch(cat -> cat.getImpact() == DiscrepancyImpact.HIGH || 
                               cat.getImpact() == DiscrepancyImpact.CRITICAL);
    }
}