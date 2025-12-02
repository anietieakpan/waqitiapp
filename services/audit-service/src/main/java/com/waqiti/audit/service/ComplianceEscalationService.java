package com.waqiti.audit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Compliance Escalation Service for handling compliance escalations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ComplianceEscalationService {
    
    private final ComplianceReportingService complianceReportingService;
    private final AuditNotificationService notificationService;
    
    public void escalateComplianceIssue(String issueType, String severity, Object issueData) {
        log.warn("Escalating compliance issue: type={}, severity={}", issueType, severity);
        
        // Record the escalation
        complianceReportingService.recordComplianceEvent("COMPLIANCE_ESCALATION", issueType, issueData);
        
        // Send notification
        notificationService.sendCriticalAlert("COMPLIANCE_ESCALATION", 
            String.format("Compliance issue escalated: %s (Severity: %s)", issueType, severity));
    }
    
    public void processEscalation(String escalationId) {
        log.info("Processing compliance escalation: {}", escalationId);
        
        complianceReportingService.recordComplianceEvent("ESCALATION_PROCESSED", escalationId, 
            java.util.Map.of("processedAt", java.time.LocalDateTime.now()));
    }
}