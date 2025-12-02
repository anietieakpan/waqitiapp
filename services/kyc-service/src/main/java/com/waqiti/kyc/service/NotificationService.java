package com.waqiti.kyc.service;

import java.util.Map;

/**
 * Service interface for sending KYC-related notifications
 */
public interface NotificationService {
    
    /**
     * Send verification reminder to user
     * @param userId The user ID
     * @param verificationId The verification ID
     */
    void sendVerificationReminder(String userId, String verificationId);
    
    /**
     * Send verification completed notification
     * @param userId The user ID
     * @param verificationId The verification ID
     * @param status The verification status
     */
    void sendVerificationCompleted(String userId, String verificationId, String status);
    
    /**
     * Send document upload confirmation
     * @param userId The user ID
     * @param documentType The document type
     */
    void sendDocumentUploadConfirmation(String userId, String documentType);
    
    /**
     * Send document rejection notification
     * @param userId The user ID
     * @param documentType The document type
     * @param reason The rejection reason
     */
    void sendDocumentRejected(String userId, String documentType, String reason);
    
    /**
     * Send manual review required notification
     * @param userId The user ID
     * @param verificationId The verification ID
     * @param reason The review reason
     */
    void sendManualReviewRequired(String userId, String verificationId, String reason);
    
    /**
     * Send provider health alert to operations team
     * @param providerName The provider name
     * @param isHealthy The health status
     */
    void sendProviderHealthAlert(String providerName, boolean isHealthy);
    
    /**
     * Send daily statistics report
     * @param stats The statistics data
     */
    void sendDailyStatsReport(Map<String, Object> stats);
    
    /**
     * Send verification expired notification
     * @param userId The user ID
     * @param verificationId The verification ID
     */
    void sendVerificationExpired(String userId, String verificationId);
    
    /**
     * Send additional documents required notification
     * @param userId The user ID
     * @param verificationId The verification ID
     * @param requiredDocuments List of required documents
     */
    void sendAdditionalDocumentsRequired(String userId, String verificationId, 
                                        java.util.List<String> requiredDocuments);
    
    /**
     * Send verification started notification
     * @param userId The user ID
     * @param verificationId The verification ID
     */
    void sendVerificationStarted(String userId, String verificationId);
}