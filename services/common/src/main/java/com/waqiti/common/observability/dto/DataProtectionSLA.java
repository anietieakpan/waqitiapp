package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataProtectionSLA {
    private int maxDataBreaches;
    private int actualDataBreaches;
    private double maxDataProcessingTime;
    private double actualDataProcessingTime;
    private double compliancePercentage;
    private boolean isCompliant;
}