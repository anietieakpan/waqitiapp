package com.waqiti.compliance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Audit Service for Compliance
 * Delegates to ComplianceAuditService for actual audit operations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final com.waqiti.compliance.audit.ComplianceAuditService complianceAuditService;
    
    public void auditEvent(String eventType, String userId, String details, Map<String, Object> metadata) {
        log.debug("Auditing event: type={}, userId={}", eventType, userId);
        complianceAuditService.logAuditEvent(eventType, userId, details, metadata);
    }
    
    public void auditComplianceEvent(String eventType, String userId, String details) {
        auditEvent(eventType, userId, details, Map.of());
    }
    
    public void auditFraudEvent(String userId, String transactionId, String fraudType, String details) {
        auditEvent("FRAUD_DETECTION", userId, details, 
            Map.of("transactionId", transactionId, "fraudType", fraudType));
    }
    
    public void auditSuspiciousActivity(String userId, String activityType, String details) {
        auditEvent("SUSPICIOUS_ACTIVITY", userId, details, 
            Map.of("activityType", activityType));
    }
}