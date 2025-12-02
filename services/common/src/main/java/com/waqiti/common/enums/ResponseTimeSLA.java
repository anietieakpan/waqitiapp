package com.waqiti.common.enums;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response time SLA shared across observability DTOs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseTimeSLA {
    private String endpoint;
    private double targetMs;
    private double actualMs;
    private double p50;
    private double p95;
    private double p99;
    private double p999;
    private boolean slaMet;
    private String degradationReason;
    private double compliancePercentage; // Percentage of requests meeting SLA

    public double getCompliancePercentage() {
        return compliancePercentage;
    }
}