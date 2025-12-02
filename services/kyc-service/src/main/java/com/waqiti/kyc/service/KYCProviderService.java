package com.waqiti.kyc.service;

import com.waqiti.kyc.dto.request.KYCVerificationRequest;
import com.waqiti.kyc.dto.response.KYCVerificationResponse;

import java.util.Map;

public interface KYCProviderService {
    
    // Provider Operations
    String createVerificationSession(String userId, KYCVerificationRequest request);
    
    Map<String, Object> getVerificationStatus(String sessionId);
    
    Map<String, Object> getVerificationResults(String sessionId);
    
    void cancelVerificationSession(String sessionId);
    
    // Document Operations
    String uploadDocumentToProvider(String sessionId, byte[] documentData, String documentType);
    
    Map<String, String> extractDocumentDataFromProvider(String documentId);
    
    // Webhook Processing
    void processWebhook(Map<String, Object> webhookData);
    
    boolean validateWebhookSignature(String signature, String payload);
    
    // Provider Management
    boolean isProviderAvailable();
    
    String getProviderName();
    
    Map<String, Object> getProviderCapabilities();
    
    // Configuration
    void updateConfiguration(Map<String, String> config);
    
    Map<String, String> getConfiguration();
}