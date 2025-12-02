package com.waqiti.common.enums;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Availability SLA tracking for system uptime and availability
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilitySLA {
    private double targetUptime;
    private double actualUptime;
    private long totalDowntimeMs;
    private int outageCount;
    private boolean slaMet;
    private String availabilityLevel;
    private double compliancePercentage;

    public double getCompliancePercentage() {
        return compliancePercentage;
    }
}
