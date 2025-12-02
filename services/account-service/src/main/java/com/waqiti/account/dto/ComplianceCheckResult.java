package com.waqiti.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Compliance Check Result DTO
 * Contains the result of compliance verification checks
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceCheckResult {
    
    private String checkId;
    private boolean compliant;
    private String complianceLevel;
    private String riskScore;
    private List<String> violations;
    private List<String> warnings;
    private List<String> requiredActions;
    private Map<String, Object> metadata;
    private String reason;
    private LocalDateTime checkedAt;
    private String checkedBy;
    private boolean requiresManualReview;
    private LocalDateTime expiresAt;
    private String referenceNumber;
    
    // Legacy compatibility
    private boolean approved;
    
    public boolean isApproved() {
        return compliant && approved && (violations == null || violations.isEmpty());
    }
    
    public boolean hasViolations() {
        return violations != null && !violations.isEmpty();
    }
    
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
}