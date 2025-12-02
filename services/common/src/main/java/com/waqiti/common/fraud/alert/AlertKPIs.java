package com.waqiti.common.fraud.alert;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; /**
 * Alert Key Performance Indicators
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertKPIs {
    private long totalAlerts;
    private double resolutionRate;
    private double escalationRate;
    private double pendingRate;
    private double averageResolutionTime;
    private double falsePositiveRate;
    private double truePositiveRate;
    private double precision;
    private double recall;
    private double f1Score;
    private double criticalAlertRate;
}
