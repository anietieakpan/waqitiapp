package com.waqiti.security.service;

import com.waqiti.common.events.FraudDetectionEvent;

/**
 * Fraud Case Management Service Interface
 * 
 * Handles creation and management of fraud investigation cases
 * for manual review and resolution tracking
 */
public interface FraudCaseManagementService {
    
    /**
     * Create manual review case from ML fraud detection
     * 
     * @param event ML fraud detection event
     * @param decision Processing decision (generic object to avoid circular dependency)
     * @return Case ID for tracking
     */
    String createMLReviewCase(FraudDetectionEvent event, Object decision);
}