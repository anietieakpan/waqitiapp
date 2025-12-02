package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BusinessSLA {
    private String businessMetric;
    private double targetValue;
    private double actualValue;
    private String unit;
    private double compliancePercentage;
    private boolean isCompliant;
    private String businessImpact;
}