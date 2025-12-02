package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class FraudRuleViolation {
    private String ruleId;
    @NotBlank(message = "Rule name is required")
    private String ruleName;
    private String ruleDescription;
    private RuleViolationSeverity severity;
    private String message;

    @Builder.Default
    private Map<String, Object> ruleParameters = new HashMap<>();
    private double confidence;
}