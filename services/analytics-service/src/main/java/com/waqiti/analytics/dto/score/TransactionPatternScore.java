package com.waqiti.analytics.dto.score;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionPatternScore {
    private BigDecimal score; // 0-100
    private String level; // EXCELLENT, GOOD, FAIR, POOR
    private BigDecimal regularityScore;
    private BigDecimal fraudRiskScore;
    private BigDecimal diversityScore;
    private String recommendation;
}