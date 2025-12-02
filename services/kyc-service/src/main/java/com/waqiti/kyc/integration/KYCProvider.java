package com.waqiti.kyc.integration;

import com.waqiti.kyc.dto.AMLScreeningRequest;
import com.waqiti.kyc.dto.AddressVerificationRequest;
import com.waqiti.kyc.dto.DocumentVerificationRequest;
import com.waqiti.kyc.dto.VerificationResult;

import java.util.Map;

/**
 * Common interface for all KYC providers
 */
public interface KYCProvider {
    
    /**
     * Verify user identity
     */
    VerificationResult verifyIdentity(String userId, Map<String, Object> identityData);
    
    /**
     * Verify documents
     */
    VerificationResult verifyDocument(DocumentVerificationRequest request);
    
    /**
     * Verify selfie with liveness check
     */
    VerificationResult verifySelfie(Map<String, Object> selfieData);
    
    /**
     * Verify address
     */
    VerificationResult verifyAddress(AddressVerificationRequest request);
    
    /**
     * Perform AML/sanctions screening
     */
    VerificationResult performAMLCheck(AMLScreeningRequest request);
    
    /**
     * Get provider name
     */
    String getProviderName();
    
    /**
     * Check if provider is available
     */
    boolean isAvailable();
}