package com.waqiti.compliance.pci.validation;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CRITICAL COMPLIANCE: PCI DSS Compliance Report
 * PRODUCTION-READY: Comprehensive compliance validation report
 */
@Data
public class PCIComplianceReport {
    
    private String reportId = java.util.UUID.randomUUID().toString();
    private LocalDateTime scanStartTime;
    private LocalDateTime scanEndTime;
    private int pciLevel;
    private boolean compliant;
    private List<RequirementValidation> requirements = new ArrayList<>();
    private List<String> criticalFindings = new ArrayList<>();
    private List<String> recommendations = new ArrayList<>();
    
    public void addRequirement(RequirementValidation requirement) {
        requirements.add(requirement);
        
        // Track critical findings
        if (!requirement.isCompliant()) {
            requirement.getFailedChecks().forEach(check -> {
                criticalFindings.add(String.format("Requirement %s - %s: FAILED - %s",
                        requirement.getRequirementId(), check.getCheckId(), check.getDescription()));
            });
        }
    }
    
    public double getComplianceScore() {
        if (requirements.isEmpty()) return 0.0;
        
        long compliantCount = requirements.stream()
                .filter(RequirementValidation::isCompliant)
                .count();
        
        return (double) compliantCount / requirements.size() * 100;
    }
    
    public String getScanDuration() {
        if (scanStartTime == null || scanEndTime == null) {
            return "N/A";
        }
        
        long seconds = java.time.Duration.between(scanStartTime, scanEndTime).getSeconds();
        return String.format("%d minutes %d seconds", seconds / 60, seconds % 60);
    }
    
    public boolean hasHighRiskFindings() {
        return criticalFindings.stream()
                .anyMatch(finding -> finding.contains("REQ3") || // Data protection
                                     finding.contains("REQ4") || // Transmission security
                                     finding.contains("REQ8"));  // Authentication
    }
}