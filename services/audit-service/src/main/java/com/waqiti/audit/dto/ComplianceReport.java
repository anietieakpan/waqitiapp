package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for compliance reports
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceReport {
    private String reportType;
    private String reportName;
    private LocalDateTime reportPeriodStart;
    private LocalDateTime reportPeriodEnd;
    private LocalDateTime generatedAt;
    private String complianceStatus;
    
    // General metrics
    private long totalEvents;
    private long criticalEvents;
    private long userAccessEvents;
    private long dataModificationEvents;
    private long systemChangeEvents;
    
    // PCI DSS specific
    private long paymentCardEvents;
    private long securityEvents;
    private long accessControlEvents;
    private long encryptionEvents;
    private List<SecurityVulnerability> vulnerabilities;
    private List<String> remediations;
    
    // GDPR specific
    private long dataProcessingEvents;
    private long consentEvents;
    private long dataAccessRequests;
    private long dataDeletionRequests;
    private List<DataBreach> dataBreaches;
    private List<PrivacyImpactAssessment> privacyImpactAssessments;
    
    // Basel III specific
    private long riskManagementEvents;
    private long capitalAdequacyEvents;
    private long liquidityEvents;
    private long leverageRatioEvents;
    private List<RiskAssessment> riskAssessments;
    
    // Common fields
    private List<ComplianceFinding> findings;
    private List<String> recommendations;
    private Map<String, Object> customMetrics;
    private Map<String, String> metadata;
}