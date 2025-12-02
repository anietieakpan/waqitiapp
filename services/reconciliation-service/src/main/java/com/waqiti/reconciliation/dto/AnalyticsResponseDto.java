package com.waqiti.reconciliation.dto;

import com.waqiti.reconciliation.domain.*;
import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AnalyticsResponseDto {
    private final PerformanceMetrics performanceMetrics;
    private final List<TrendData> trends;
    private final SystemHealth systemHealth;
    private final List<Alert> activeAlerts;
    private final DiscrepancyAnalysis discrepancyAnalysis;
    private final List<ReconciliationSummary> recentReconciliations;
    private final LocalDateTime generatedAt;
    private final String requestId;
    
    public boolean hasIssues() {
        return (activeAlerts != null && !activeAlerts.isEmpty()) ||
               (systemHealth != null && systemHealth.requiresAttention()) ||
               (discrepancyAnalysis != null && discrepancyAnalysis.hasHighImpactDiscrepancies());
    }
    
    public int getCriticalAlertCount() {
        if (activeAlerts == null) return 0;
        return (int) activeAlerts.stream()
                .filter(Alert::requiresImmedateAttention)
                .count();
    }
}