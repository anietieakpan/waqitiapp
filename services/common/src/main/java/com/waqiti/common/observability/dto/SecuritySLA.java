package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SecuritySLA {
    private int maxSecurityIncidents;
    private int actualSecurityIncidents;
    private double maxAuthFailureRate;
    private double actualAuthFailureRate;
    private int maxVulnerabilities;
    private int actualVulnerabilities;
    private double compliancePercentage;
    private boolean isCompliant;
}