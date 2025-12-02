package com.waqiti.compliance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateComplianceRuleRequest {
    
    private String ruleName;
    private String description;
    private Map<String, Object> conditions;
    private String action;
    private Integer priority;
    private Boolean isActive;
    private String reason; // Reason for the update
}