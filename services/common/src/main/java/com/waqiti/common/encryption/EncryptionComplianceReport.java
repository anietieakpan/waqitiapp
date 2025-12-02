package com.waqiti.common.encryption;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Compliance report for encryption audit trail
 */
@Data
@Builder
public class EncryptionComplianceReport {
    private String reportId;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
    private String complianceStandard;
    private LocalDateTime generatedAt;
    
    @Builder.Default
    private Map<String, Object> sections = new HashMap<>();
    
    private String executiveSummary;
    private String complianceStatus; // COMPLIANT, NON_COMPLIANT, PARTIAL
    private int totalEvents;
    private int violationCount;
    
    public void addSection(String sectionName, Map<String, Object> sectionData) {
        sections.put(sectionName, sectionData);
    }
    
    public void generateExecutiveSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Encryption Compliance Report Summary\n");
        summary.append("Report Period: ").append(fromDate).append(" to ").append(toDate).append("\n");
        summary.append("Compliance Standard: ").append(complianceStandard).append("\n");
        summary.append("Total Events Analyzed: ").append(totalEvents).append("\n");
        summary.append("Violations Detected: ").append(violationCount).append("\n");
        
        if (violationCount == 0) {
            complianceStatus = "COMPLIANT";
            summary.append("Status: COMPLIANT - No violations detected\n");
        } else if (violationCount <= 5) {
            complianceStatus = "PARTIAL";
            summary.append("Status: PARTIAL COMPLIANCE - Minor violations detected\n");
        } else {
            complianceStatus = "NON_COMPLIANT";
            summary.append("Status: NON-COMPLIANT - Multiple violations require immediate attention\n");
        }
        
        executiveSummary = summary.toString();
    }
}