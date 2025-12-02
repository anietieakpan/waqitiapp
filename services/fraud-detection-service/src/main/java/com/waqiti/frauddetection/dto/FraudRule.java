package com.waqiti.frauddetection.dto;

import com.waqiti.frauddetection.entity.FraudRuleDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fraud Rule DTO
 * 
 * Represents a triggered fraud detection rule with its evaluation results
 * 
 * @author Waqiti Security Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudRule {

    private String ruleCode;
    
    private String ruleName;
    
    private String description;
    
    private FraudRuleDefinition.RuleSeverity severity;
    
    private Double riskScore;
    
    private Double confidence;
    
    private Double weight;
    
    private String category;
    
    private LocalDateTime triggeredAt;
    
    private Map<String, Object> metadata;
    
    private String recommendation;
}