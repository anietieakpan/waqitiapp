package com.waqiti.kyc.provider;

import com.waqiti.kyc.dto.request.KYCVerificationRequest;
import com.waqiti.kyc.dto.response.KYCVerificationResponse;

import java.util.List;
import java.util.Map;

/**
 * Interface for KYC verification providers.
 * Each provider must implement this interface to integrate with the KYC service.
 */
public interface KYCProvider {
    
    /**
     * Create a new verification session with the provider
     * @param userId The user ID to create the session for
     * @param request The verification request details
     * @return The session ID from the provider
     */
    String createVerificationSession(String userId, KYCVerificationRequest request);
    
    /**
     * Get the current status of a verification session
     * @param sessionId The provider's session ID
     * @return Status information as a map
     */
    Map<String, Object> getVerificationStatus(String sessionId);
    
    /**
     * Get the detailed results of a completed verification
     * @param sessionId The provider's session ID
     * @return Verification results including documents and checks
     */
    Map<String, Object> getVerificationResults(String sessionId);
    
    /**
     * Cancel an active verification session
     * @param sessionId The provider's session ID
     */
    void cancelVerificationSession(String sessionId);
    
    /**
     * Upload a document to the provider
     * @param sessionId The provider's session ID
     * @param documentData The document bytes
     * @param documentType The type of document (passport, driver_license, etc)
     * @return The document ID from the provider
     */
    String uploadDocument(String sessionId, byte[] documentData, String documentType);
    
    /**
     * Extract structured data from an uploaded document
     * @param documentId The provider's document ID
     * @return Extracted data (name, DOB, address, etc)
     */
    Map<String, String> extractDocumentData(String documentId);
    
    /**
     * Process incoming webhook data from the provider
     * @param webhookData The webhook payload
     */
    void processWebhook(Map<String, Object> webhookData);
    
    /**
     * Check if the provider is currently available
     * @return true if the provider is operational
     */
    boolean isAvailable();
    
    /**
     * Get the list of document types supported by this provider
     * @return List of supported document types
     */
    List<String> getSupportedDocumentTypes();
    
    /**
     * Get the list of countries supported by this provider
     * @return List of ISO country codes
     */
    List<String> getSupportedCountries();
    
    /**
     * Get the features supported by this provider
     * @return Map of feature names to enabled status
     */
    Map<String, Boolean> getFeatures();
    
    /**
     * Get the current configuration of the provider
     * @return Configuration map
     */
    Map<String, String> getConfiguration();
    
    /**
     * Update a configuration value for the provider
     * @param key The configuration key
     * @param value The new value
     */
    void updateConfiguration(String key, String value);
    
    /**
     * Get the provider name
     * @return The provider identifier
     */
    String getProviderName();
    
    /**
     * Initialize the provider with configuration
     * @param config Provider-specific configuration
     */
    void initialize(Map<String, String> config);
    
    /**
     * Perform any cleanup when shutting down
     */
    void shutdown();
}