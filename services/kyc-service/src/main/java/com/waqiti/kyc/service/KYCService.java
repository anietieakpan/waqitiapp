package com.waqiti.kyc.service;

import com.waqiti.kyc.dto.request.KYCVerificationRequest;
import com.waqiti.kyc.dto.request.ReviewRequest;
import com.waqiti.kyc.dto.response.KYCStatusResponse;
import com.waqiti.kyc.dto.response.KYCVerificationResponse;
import com.waqiti.kyc.dto.response.VerificationHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface KYCService {
    
    // Verification Operations
    KYCVerificationResponse initiateVerification(String userId, KYCVerificationRequest request);
    
    KYCVerificationResponse getVerification(String verificationId);
    
    KYCVerificationResponse getActiveVerificationForUser(String userId);
    
    List<KYCVerificationResponse> getUserVerifications(String userId);
    
    KYCVerificationResponse updateVerificationStatus(String verificationId, String status);
    
    KYCVerificationResponse cancelVerification(String verificationId, String reason);
    
    void deleteVerification(String verificationId);
    
    // Review Operations
    KYCVerificationResponse reviewVerification(String verificationId, ReviewRequest request);
    
    Page<KYCVerificationResponse> getPendingReviews(Pageable pageable);
    
    // Status and Compliance
    KYCStatusResponse getUserKYCStatus(String userId);
    
    boolean isUserVerified(String userId, String requiredLevel);
    
    boolean canUserPerformAction(String userId, String action);
    
    // History and Analytics
    VerificationHistoryResponse getUserVerificationHistory(String userId);
    
    Page<KYCVerificationResponse> searchVerifications(String query, Pageable pageable);
    
    // Webhook Processing
    void processProviderWebhook(String provider, String webhookData);
    
    // Scheduled Tasks
    void checkExpiredVerifications();
    
    void syncPendingVerifications();
    
    void generateComplianceReports();
    
    // Compatibility Operations
    void syncFromLegacyService(String userId, KYCVerificationResponse legacyResponse);
}