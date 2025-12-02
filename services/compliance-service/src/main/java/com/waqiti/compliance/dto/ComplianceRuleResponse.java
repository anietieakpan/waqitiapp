package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceRuleResponse {
    private UUID ruleId;
    private String ruleName;
    private String ruleType; // AML, KYC, SANCTIONS, THRESHOLD
    private String status; // ACTIVE, INACTIVE, DRAFT
    private String description;
    private Map<String, Object> conditions;
    private String action; // ALERT, BLOCK, ESCALATE, APPROVE
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String lastModifiedBy;
}