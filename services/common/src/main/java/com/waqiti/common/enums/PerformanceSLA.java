package com.waqiti.common.enums;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Performance SLA tracking for system performance metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceSLA {
    private String metric;
    private double target;
    private double actual;
    private boolean slaMet;
    private String performanceLevel;
    private double compliancePercentage;

    public double getCompliancePercentage() {
        return compliancePercentage;
    }
}
