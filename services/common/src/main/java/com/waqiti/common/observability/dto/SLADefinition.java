package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;

import com.waqiti.common.enums.SLAPriority;

@Data
@Builder
public class SLADefinition {
    private String name;
    private String description;
    private SLAType type;
    private String metricName;
    private double targetValue;
    private String unit;
    private ComparisonOperator operator;
    private SLAPriority priority;
    private String businessImpact;
}

enum SLAType {
    AVAILABILITY, PERFORMANCE, SECURITY, BUSINESS, REGULATORY
}

enum ComparisonOperator {
    GREATER_THAN, LESS_THAN, EQUALS, GREATER_THAN_OR_EQUAL, LESS_THAN_OR_EQUAL
}

