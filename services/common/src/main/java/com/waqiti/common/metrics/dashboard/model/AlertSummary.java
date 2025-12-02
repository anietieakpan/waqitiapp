package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Alert summary information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertSummary {
    private Long criticalAlerts;
    private Long warningAlerts;
    private Long infoAlerts;
    private List<Alert> recentAlerts;
    private Map<String, Integer> alertsByCategory;
    private Double alertRate;
}