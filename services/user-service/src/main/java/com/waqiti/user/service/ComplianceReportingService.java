package com.waqiti.user.service;

import com.waqiti.common.events.AccountFreezeRequestEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Compliance Reporting Service Interface
 * 
 * Handles regulatory reporting and notifications for compliance events.
 * This service is critical for meeting regulatory requirements.
 */
public interface ComplianceReportingService {
    
    /**
     * Notify law enforcement about account freeze
     */
    void notifyLawEnforcement(AccountFreezeRequestEvent event, String freezeId);
    
    /**
     * Notify regulators about account freeze
     */
    void notifyRegulators(AccountFreezeRequestEvent event, String freezeId);
    
    /**
     * Notify compliance team about account freeze
     */
    void notifyComplianceTeam(AccountFreezeRequestEvent event, String freezeId);
    
    /**
     * Notify specific regulatory body
     */
    void notifyRegulatoryBody(String regulatoryBody, UUID userId, 
                             AccountFreezeRequestEvent.FreezeReason reason, String caseId);
    
    /**
     * File Suspicious Activity Report (SAR)
     */
    void fileSuspiciousActivityReport(UUID userId, String reason, String activityPattern,
                                     BigDecimal accountBalance, String caseId);

    /**
     * Report account freeze to compliance
     */
    void reportAccountFreeze(String accountId, String userId, String freezeReason, String freezeType);

    /**
     * Report account status change to compliance
     */
    void reportAccountStatusChange(String accountId, String userId, String previousStatus,
                                   String newStatus, String changeReason, String changedBy);

    /**
     * Update risk assessment
     */
    void updateRiskAssessment(String userId, String accountId, String newStatus, java.util.Map<String, Object> riskFactors);

    /**
     * File regulatory report
     */
    void fileRegulatoryReport(String accountId, String newStatus, String changeReason, String regulatoryReference);
}