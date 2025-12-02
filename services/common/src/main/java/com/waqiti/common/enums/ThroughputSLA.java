package com.waqiti.common.enums;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Throughput SLA tracking for system transaction/request throughput
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThroughputSLA {
    private double targetThroughput;
    private double actualThroughput;
    private String throughputUnit;
    private boolean slaMet;
    private double compliancePercentage;

    public double getCompliancePercentage() {
        return compliancePercentage;
    }
}
