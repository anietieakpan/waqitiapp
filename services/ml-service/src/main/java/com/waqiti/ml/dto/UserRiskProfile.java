package com.waqiti.ml.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Data
public class UserRiskProfile {
    private String userId;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private BigDecimal averageTransactionAmount = BigDecimal.ZERO;
    private long totalTransactionCount = 0;
    private long recentTransactionCount = 0;
    private double riskScore = 0.0;
    
    public void incrementTransactionCount() {
        this.totalTransactionCount++;
        this.recentTransactionCount++;
    }
    
    public void updateAverageAmount(BigDecimal newAmount) {
        if (newAmount != null && totalTransactionCount > 0) {
            BigDecimal totalAmount = averageTransactionAmount.multiply(BigDecimal.valueOf(totalTransactionCount - 1));
            totalAmount = totalAmount.add(newAmount);
            this.averageTransactionAmount = totalAmount.divide(BigDecimal.valueOf(totalTransactionCount), 2, RoundingMode.HALF_UP);
        } else if (newAmount != null) {
            this.averageTransactionAmount = newAmount;
        }
    }
}