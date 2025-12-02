package com.waqiti.reconciliation.domain;

import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Data
@Builder
public class PerformanceMetrics {
    private final Long totalReconciliations;
    private final Long successfulReconciliations;
    private final Long failedReconciliations;
    private final BigDecimal successRate;
    private final Long averageProcessingTimeMs;
    private final BigDecimal totalVolumeProcessed;
    private final LocalDateTime calculatedAt;
    private final String timeFrame;
    
    public BigDecimal getFailureRate() {
        if (totalReconciliations == null || totalReconciliations == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(failedReconciliations)
                .divide(BigDecimal.valueOf(totalReconciliations), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
    
    public boolean isHealthy() {
        return successRate != null && successRate.compareTo(BigDecimal.valueOf(95)) >= 0;
    }
}