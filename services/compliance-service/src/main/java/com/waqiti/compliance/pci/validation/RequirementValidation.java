package com.waqiti.compliance.pci.validation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * CRITICAL COMPLIANCE: PCI DSS Requirement Validation Result
 * PRODUCTION-READY: Individual requirement validation tracking
 */
@Data
public class RequirementValidation {
    
    private final String requirementId;
    private final String description;
    private final List<ComplianceCheck> checks = new ArrayList<>();
    private boolean compliant = true;
    
    public RequirementValidation(String requirementId, String description) {
        this.requirementId = requirementId;
        this.description = description;
    }
    
    public void addCheck(String checkId, String description, boolean passed) {
        ComplianceCheck check = new ComplianceCheck(checkId, description, passed);
        checks.add(check);
        
        if (!passed) {
            compliant = false;
        }
    }
    
    public List<ComplianceCheck> getFailedChecks() {
        return checks.stream()
                .filter(check -> !check.isPassed())
                .collect(java.util.stream.Collectors.toList());
    }
    
    public double getCompliancePercentage() {
        if (checks.isEmpty()) return 0.0;
        
        long passedCount = checks.stream()
                .filter(ComplianceCheck::isPassed)
                .count();
        
        return (double) passedCount / checks.size() * 100;
    }
    
    /**
     * Individual compliance check result
     */
    @Data
    public static class ComplianceCheck {
        private final String checkId;
        private final String description;
        private final boolean passed;
        
        public ComplianceCheck(String checkId, String description, boolean passed) {
            this.checkId = checkId;
            this.description = description;
            this.passed = passed;
        }
    }
}