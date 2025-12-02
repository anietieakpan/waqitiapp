package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

@Data
@Builder
public class FraudAlertStatistics {
    private int totalPendingAlerts;
    private int criticalAlerts;
    private int highAlerts;
    private int mediumAlerts;
    private int lowAlerts;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastUpdated;
}