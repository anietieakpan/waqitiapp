package com.waqiti.common.kyc.client;

import com.waqiti.common.kyc.dto.KYCStatusResponse;
import com.waqiti.common.kyc.dto.KYCVerificationRequest;
import com.waqiti.common.kyc.dto.KYCVerificationResponse;
import com.waqiti.common.kyc.exception.KYCServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class KYCClientFallbackFactory implements FallbackFactory<KYCFeignClient> {

    @Override
    public KYCFeignClient create(Throwable cause) {
        return new KYCFeignClientFallback(cause);
    }

    @Slf4j
    static class KYCFeignClientFallback implements KYCFeignClient {

        private final Throwable cause;

        public KYCFeignClientFallback(Throwable cause) {
            this.cause = cause;
        }

        @Override
        public KYCVerificationResponse initiateVerification(String userId, KYCVerificationRequest request) {
            log.error("Failed to initiate KYC verification for user: {}", userId, cause);
            throw new KYCServiceUnavailableException("KYC service unavailable for verification initiation", cause);
        }

        @Override
        public KYCVerificationResponse getVerification(String verificationId) {
            log.error("Failed to get KYC verification: {}", verificationId, cause);
            throw new KYCServiceUnavailableException("KYC service unavailable for verification retrieval", cause);
        }

        @Override
        public KYCVerificationResponse getActiveVerification(String userId) {
            log.error("Failed to get active KYC verification for user: {}", userId, cause);
            throw new KYCServiceUnavailableException("KYC service unavailable for active verification retrieval", cause);
        }

        @Override
        public List<KYCVerificationResponse> getUserVerifications(String userId) {
            log.error("Failed to get user verifications for user: {}", userId, cause);
            return Collections.emptyList();
        }

        @Override
        public KYCStatusResponse getUserKYCStatus(String userId) {
            log.error("Failed to get KYC status for user: {}", userId, cause);
            
            // Return a safe fallback status
            return KYCStatusResponse.builder()
                    .userId(userId)
                    .currentStatus(KYCVerificationResponse.KYCStatus.NOT_STARTED)
                    .isActive(false)
                    .canUpgrade(false) // Conservative fallback
                    .build();
        }

        @Override
        public Boolean isUserVerified(String userId, String level) {
            log.error("Failed to check if user is verified: {} at level: {}", userId, level, cause);
            return false; // Fail safe - deny access when service is unavailable
        }

        @Override
        public Boolean canUserPerformAction(String userId, String action) {
            log.error("Failed to check if user can perform action: {} for user: {}", action, userId, cause);
            return false; // Fail safe - deny action when service is unavailable
        }

        @Override
        public KYCVerificationResponse cancelVerification(String verificationId, String reason) {
            log.error("Failed to cancel KYC verification: {}", verificationId, cause);
            throw new KYCServiceUnavailableException("KYC service unavailable for verification cancellation", cause);
        }
    }
}