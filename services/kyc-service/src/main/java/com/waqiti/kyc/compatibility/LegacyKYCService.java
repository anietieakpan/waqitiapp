package com.waqiti.kyc.compatibility;

import com.waqiti.kyc.dto.request.KYCVerificationRequest;
import com.waqiti.kyc.dto.response.KYCStatusResponse;
import com.waqiti.kyc.dto.response.KYCVerificationResponse;

/**
 * Interface for legacy KYC service operations
 */
public interface LegacyKYCService {
    
    KYCVerificationResponse initiateVerification(String userId, KYCVerificationRequest request);
    
    KYCStatusResponse getUserKYCStatus(String userId);
    
    boolean isUserVerified(String userId, String level);
    
    void syncFromNewService(String userId, KYCVerificationResponse newResponse);
    
    boolean isHealthy();
}