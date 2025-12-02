package com.waqiti.common.fraud.alert;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.waqiti.common.fraud.model.AlertLevel;

import java.time.LocalDateTime;
import java.util.Map; /**
 * Alert trend data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertTrend {
    private LocalDateTime timestamp;
    private long alertCount;
    private AlertLevel predominantLevel;
    private double averageRiskScore;
    private Map<String, Long> typeDistribution;
}
