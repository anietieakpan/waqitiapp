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
public class CreateComplianceRuleRequest {
    
    @NotNull
    private String ruleName;
    
    @NotNull
    private String ruleType;
    
    @NotNull
    private String description;
    
    @NotNull
    private Map<String, Object> conditions;
    
    @NotNull
    private String action;
    
    private Integer priority;
    private Boolean isActive;
}