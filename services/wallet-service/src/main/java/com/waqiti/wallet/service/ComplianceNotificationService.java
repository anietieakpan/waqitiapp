package com.waqiti.wallet.service;

import com.waqiti.common.events.TransactionBlockEvent;

/**
 * Compliance Notification Service Interface
 * 
 * Handles sending notifications for compliance-related events and violations.
 * This service is critical for regulatory compliance and internal communication.
 */
public interface ComplianceNotificationService {
    
    /**
     * Send executive alert for critical compliance violations
     * 
     * @param event Transaction block event
     */
    void sendExecutiveAlert(TransactionBlockEvent event);
    
    /**
     * Send alert to compliance team
     * 
     * @param event Transaction block event
     */
    void sendComplianceTeamAlert(TransactionBlockEvent event);
    
    /**
     * Send notification for manual compliance review
     * 
     * @param event Transaction block event
     */
    void sendComplianceReviewNotification(TransactionBlockEvent event);
    
    /**
     * Send regulatory notification to authorities
     * 
     * @param event Transaction block event
     * @param blockId Block ID for reference
     */
    void sendRegulatoryNotification(TransactionBlockEvent event, String blockId);
}