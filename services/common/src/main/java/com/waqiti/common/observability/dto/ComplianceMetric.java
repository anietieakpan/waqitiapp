package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ComplianceMetric {
    private LocalDateTime timestamp;
    private String metricName;
    private double value;
    private double target;
    private boolean isCompliant;
}