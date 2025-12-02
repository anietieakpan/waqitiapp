package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SLAResult {
    private String slaName;
    private double actualValue;
    private double targetValue;
    private double compliancePercentage;
    private boolean isCompliant;
    private String status;
    private LocalDateTime lastMeasured;
}