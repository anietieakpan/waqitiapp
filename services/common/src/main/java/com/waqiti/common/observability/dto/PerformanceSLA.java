package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PerformanceSLA {
    private double responseTimeTarget;
    private double actualResponseTime;
    private double errorRateTarget;
    private double actualErrorRate;
    private double throughputTarget;
    private double actualThroughput;
    private double compliancePercentage;
    private boolean isCompliant;
}