package com.waqiti.compliance.service;

import com.waqiti.common.events.SarFilingRequestEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SAR Filing Service Interface
 * 
 * CRITICAL REGULATORY COMPLIANCE: Automated SAR filing and management
 * LEGAL IMPACT: Ensures timely compliance with BSA requirements
 * 
 * Handles the complete SAR lifecycle:
 * - Report generation
 * - Regulatory filing
 * - Follow-up scheduling
 * - Compliance case management
 */
public interface SarFilingService {
    
    /**
     * Generate SAR report from event data
     * 
     * @param event SAR filing request event
     * @param priority Filing priority
     * @return Generated SAR ID
     */
    String generateSarReport(SarFilingRequestEvent event, SarFilingRequestEvent.SarPriority priority);
    
    /**
     * File SAR with regulatory body immediately
     * 
     * @param sarId SAR ID
     * @param regulatoryBody Regulatory body identifier
     * @param event Original event data
     * @param expedited Whether to use expedited filing
     * @return Filing confirmation ID
     */
    String fileWithRegulatoryBody(String sarId, String regulatoryBody, SarFilingRequestEvent event, 
                                 boolean expedited);
    
    /**
     * Schedule regulatory filing for later processing
     * 
     * @param sarId SAR ID
     * @param event Event data
     * @param scheduledTime When to file
     * @return Schedule ID
     */
    String scheduleRegulatoryFiling(String sarId, SarFilingRequestEvent event, LocalDateTime scheduledTime);
    
    /**
     * Send executive notification for critical SARs
     * 
     * @param event SAR event
     * @param sarId SAR ID
     */
    void sendExecutiveSarNotification(SarFilingRequestEvent event, String sarId);
    
    /**
     * Notify compliance team
     * 
     * @param event SAR event  
     * @param sarId SAR ID
     */
    void notifyComplianceTeam(SarFilingRequestEvent event, String sarId);
    
    /**
     * Create high priority compliance case
     * 
     * @param userId User ID
     * @param sarId SAR ID
     * @param violationType Type of violation
     * @param caseId Case ID
     */
    void createHighPriorityComplianceCase(UUID userId, String sarId, String violationType, String caseId);
    
    /**
     * Create urgent compliance case
     * 
     * @param userId User ID
     * @param sarId SAR ID
     * @param violationType Type of violation
     * @param caseId Case ID
     */
    void createUrgentComplianceCase(UUID userId, String sarId, String violationType, String caseId);
    
    /**
     * Create standard compliance case
     * 
     * @param userId User ID
     * @param sarId SAR ID
     * @param violationType Type of violation
     * @param caseId Case ID
     * @param priority Priority level
     */
    void createComplianceCase(UUID userId, String sarId, String violationType, String caseId, String priority);
    
    /**
     * Schedule account review
     * 
     * @param userId User ID
     * @param caseId Case ID
     * @param reviewDate When to review
     */
    void scheduleAccountReview(UUID userId, String caseId, LocalDateTime reviewDate);
    
    /**
     * Enable enhanced monitoring for user
     * 
     * @param userId User ID
     * @param reason Monitoring reason
     * @param monitoringUntil When to stop monitoring
     */
    void enableEnhancedMonitoring(UUID userId, String reason, LocalDateTime monitoringUntil);
    
    /**
     * Schedule relationship review for related parties
     * 
     * @param partyId Related party ID
     * @param caseId Case ID
     * @param reviewType Type of review
     */
    void scheduleRelationshipReview(String partyId, String caseId, String reviewType);
    
    /**
     * Get SAR filing status
     * 
     * @param sarId SAR ID
     * @return Filing status information
     */
    SarFilingStatus getSarFilingStatus(String sarId);
    
    /**
     * Update SAR filing status
     * 
     * @param sarId SAR ID
     * @param status New status
     * @param notes Update notes
     */
    void updateSarFilingStatus(String sarId, String status, String notes);
}