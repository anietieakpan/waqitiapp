package com.waqiti.common.kyc.service;

import com.waqiti.common.kyc.client.KYCFeignClient;
import com.waqiti.common.kyc.dto.KYCStatusResponse;
import com.waqiti.common.kyc.dto.KYCVerificationRequest;
import com.waqiti.common.kyc.dto.KYCVerificationResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class KYCClientService {

    private final KYCFeignClient kycFeignClient;
    
    private static final String KYC_SERVICE = "kyc-service";

    @CircuitBreaker(name = KYC_SERVICE, fallbackMethod = "initiateVerificationFallback")
    @Retry(name = KYC_SERVICE)
    public KYCVerificationResponse initiateVerification(String userId, KYCVerificationRequest request) {
        log.debug("Initiating KYC verification for user: {}", userId);
        return kycFeignClient.initiateVerification(userId, request);
    }

    @CircuitBreaker(name = KYC_SERVICE, fallbackMethod = "getVerificationFallback")
    @Retry(name = KYC_SERVICE)
    @Cacheable(value = "kyc-verifications", key = "#verificationId", unless = "#result == null")
    public KYCVerificationResponse getVerification(String verificationId) {
        log.debug("Getting KYC verification: {}", verificationId);
        return kycFeignClient.getVerification(verificationId);
    }

    @CircuitBreaker(name = KYC_SERVICE, fallbackMethod = "getActiveVerificationFallback")
    @Retry(name = KYC_SERVICE)
    @Cacheable(value = "kyc-active-verifications", key = "#userId", unless = "#result == null")
    public KYCVerificationResponse getActiveVerification(String userId) {
        log.debug("Getting active KYC verification for user: {}", userId);
        return kycFeignClient.getActiveVerification(userId);
    }

    @CircuitBreaker(name = KYC_SERVICE, fallbackMethod = "getUserVerificationsFallback")
    @Retry(name = KYC_SERVICE)
    @Cacheable(value = "kyc-user-verifications", key = "#userId")
    public List<KYCVerificationResponse> getUserVerifications(String userId) {
        log.debug("Getting user verifications for user: {}", userId);
        return kycFeignClient.getUserVerifications(userId);
    }

    @CircuitBreaker(name = KYC_SERVICE, fallbackMethod = "getUserKYCStatusFallback")
    @Retry(name = KYC_SERVICE)
    @Cacheable(value = "kyc-user-status", key = "#userId", unless = "#result == null")
    public KYCStatusResponse getUserKYCStatus(String userId) {
        log.debug("Getting KYC status for user: {}", userId);
        return kycFeignClient.getUserKYCStatus(userId);
    }

    @CircuitBreaker(name = KYC_SERVICE, fallbackMethod = "isUserVerifiedFallback")
    @Retry(name = KYC_SERVICE)
    @Cacheable(value = "kyc-user-verified", key = "#userId + '-' + #level")
    public Boolean isUserVerified(String userId, String level) {
        log.debug("Checking if user {} is verified at level: {}", userId, level);
        return kycFeignClient.isUserVerified(userId, level);
    }

    @CircuitBreaker(name = KYC_SERVICE, fallbackMethod = "canUserPerformActionFallback")
    @Retry(name = KYC_SERVICE)
    @Cacheable(value = "kyc-user-actions", key = "#userId + '-' + #action")
    public Boolean canUserPerformAction(String userId, String action) {
        log.debug("Checking if user {} can perform action: {}", userId, action);
        return kycFeignClient.canUserPerformAction(userId, action);
    }

    @CircuitBreaker(name = KYC_SERVICE, fallbackMethod = "cancelVerificationFallback")
    @Retry(name = KYC_SERVICE)
    public KYCVerificationResponse cancelVerification(String verificationId, String reason) {
        log.debug("Cancelling KYC verification: {} with reason: {}", verificationId, reason);
        return kycFeignClient.cancelVerification(verificationId, reason);
    }

    // Fallback methods
    public KYCVerificationResponse initiateVerificationFallback(String userId, 
                                                               KYCVerificationRequest request, 
                                                               Exception ex) {
        log.error("Fallback: Failed to initiate verification for user: {}", userId, ex);
        throw new RuntimeException("KYC service unavailable for verification initiation", ex);
    }

    public KYCVerificationResponse getVerificationFallback(String verificationId, Exception ex) {
        log.error("Fallback: Failed to get verification: {}", verificationId, ex);
        return null;
    }

    public KYCVerificationResponse getActiveVerificationFallback(String userId, Exception ex) {
        log.error("Fallback: Failed to get active verification for user: {}", userId, ex);
        return null;
    }

    public List<KYCVerificationResponse> getUserVerificationsFallback(String userId, Exception ex) {
        log.error("Fallback: Failed to get user verifications for user: {}", userId, ex);
        return List.of();
    }

    public KYCStatusResponse getUserKYCStatusFallback(String userId, Exception ex) {
        log.error("Fallback: Failed to get KYC status for user: {}", userId, ex);
        return KYCStatusResponse.builder()
                .userId(userId)
                .currentStatus(KYCVerificationResponse.KYCStatus.NOT_STARTED)
                .isActive(false)
                .canUpgrade(false)
                .build();
    }

    public Boolean isUserVerifiedFallback(String userId, String level, Exception ex) {
        log.error("Fallback: Failed to check verification status for user: {} at level: {}", userId, level, ex);
        return false; // Fail safe
    }

    public Boolean canUserPerformActionFallback(String userId, String action, Exception ex) {
        log.error("Fallback: Failed to check action permission for user: {} action: {}", userId, action, ex);
        return false; // Fail safe
    }

    public KYCVerificationResponse cancelVerificationFallback(String verificationId, String reason, Exception ex) {
        log.error("Fallback: Failed to cancel verification: {}", verificationId, ex);
        throw new RuntimeException("KYC service unavailable for verification cancellation", ex);
    }

    // Helper methods for common use cases
    public boolean isUserBasicVerified(String userId) {
        return Boolean.TRUE.equals(isUserVerified(userId, "BASIC"));
    }

    public boolean isUserIntermediateVerified(String userId) {
        return Boolean.TRUE.equals(isUserVerified(userId, "INTERMEDIATE"));
    }

    public boolean isUserAdvancedVerified(String userId) {
        return Boolean.TRUE.equals(isUserVerified(userId, "ADVANCED"));
    }

    public boolean canUserSendMoney(String userId) {
        return Boolean.TRUE.equals(canUserPerformAction(userId, "SEND_MONEY"));
    }

    public boolean canUserReceiveMoney(String userId) {
        return Boolean.TRUE.equals(canUserPerformAction(userId, "RECEIVE_MONEY"));
    }

    public boolean canUserMakeInternationalTransfer(String userId) {
        return Boolean.TRUE.equals(canUserPerformAction(userId, "INTERNATIONAL_TRANSFER"));
    }

    public boolean canUserMakeHighValueTransfer(String userId) {
        return Boolean.TRUE.equals(canUserPerformAction(userId, "HIGH_VALUE_TRANSFER"));
    }

    public boolean canUserPurchaseCrypto(String userId) {
        return Boolean.TRUE.equals(canUserPerformAction(userId, "CRYPTO_PURCHASE"));
    }
}