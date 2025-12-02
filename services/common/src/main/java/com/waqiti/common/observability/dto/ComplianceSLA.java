package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ComplianceSLA {
    private String regulationType;
    private List<String> requirements;
    private List<String> violations;
    private double compliancePercentage;
    private boolean isCompliant;
    private LocalDateTime lastAudit;
}