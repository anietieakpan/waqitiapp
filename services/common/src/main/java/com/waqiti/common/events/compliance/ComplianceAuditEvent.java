package com.waqiti.common.events.compliance;

import com.waqiti.common.compliance.model.OFACScreeningResult;
import com.waqiti.common.compliance.model.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Compliance audit event for tracking compliance-related activities
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceAuditEvent {
    
    private String eventId;
    private String eventType;
    private String entityName;
    private String userId;
    private String transactionId;
    private String requestId;
    
    // Screening results
    private OFACScreeningResult screeningResult;
    private boolean isMatch;
    private double confidenceScore;
    private RiskLevel riskLevel;
    private boolean requiresInvestigation;
    private boolean requiresImmediateAction;
    
    // Provider information
    private List<String> providersUsed;
    private String primaryProvider;
    
    // Override information
    private String overrideReason;
    private String authorizedBy;
    private String authorizationCode;
    
    // Error information
    private String errorMessage;
    private String errorCode;
    
    // Metadata
    private String sourceService;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime occurredAt;
    private LocalDateTime processedAt;
    
    // Audit trail
    private String previousValue;
    private String newValue;
    private String changeReason;
    private String workflowStatus;
}