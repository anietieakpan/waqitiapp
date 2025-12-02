package com.waqiti.security.service;

import com.waqiti.common.events.FraudDetectionEvent;
import com.waqiti.security.kafka.FraudDetectionEventConsumer.FraudDetectionDecision;

/**
 * Alert Service Interface
 * 
 * Handles sending various security and fraud-related alerts
 * to different stakeholders (users, security team, executives)
 */
public interface AlertService {
    
    /**
     * Send executive-level ML fraud alert for critical cases
     */
    void sendExecutiveMLFraudAlert(FraudDetectionEvent event, Object decision);
    
    /**
     * Send security team ML fraud alert
     */
    void sendSecurityTeamMLAlert(FraudDetectionEvent event, Object decision);
    
    /**
     * Send user notification about blocked transaction
     */
    void sendUserTransactionBlockedAlert(FraudDetectionEvent event, Object decision);
    
    /**
     * Send user notification about required additional authentication
     */
    void sendUserAdditionalAuthRequiredAlert(FraudDetectionEvent event);
    
    /**
     * Send executive security alert
     */
    void sendExecutiveSecurityAlert(Object event);
    
    /**
     * Send security team alert
     */
    void sendSecurityTeamAlert(Object event);
    
    /**
     * Send user security notification
     */
    void sendUserSecurityNotification(Object event);
}