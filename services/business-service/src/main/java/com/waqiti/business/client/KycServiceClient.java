package com.waqiti.business.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.Builder;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * KYC Service Client with Circuit Breaker
 *
 * Integrates with KYC service for business account verification.
 *
 * Resilience Features:
 * - Circuit Breaker: Opens after 50% failure rate (5 of 10 calls)
 * - Retry: 3 attempts with exponential backoff
 * - Timeout: 5 seconds
 * - Fallback: Returns cached or default verification status
 *
 * Circuit Breaker States:
 * - CLOSED: Normal operation, all calls go through
 * - OPEN: Service down, calls immediately fail with fallback
 * - HALF_OPEN: Testing if service recovered (limited calls)
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@FeignClient(
        name = "kyc-service",
        url = "${kyc.service.url:http://kyc-service:8080}",
        fallback = KycServiceClientFallback.class
)
public interface KycServiceClient {

    /**
     * Verify business account KYC status
     *
     * Circuit breaker "kycService" protects against KYC service failures
     */
    @GetMapping("/api/v1/kyc/business/{accountId}/status")
    @CircuitBreaker(name = "kycService", fallbackMethod = "getVerificationStatusFallback")
    @Retry(name = "kycService")
    @TimeLimiter(name = "kycService")
    KycVerificationResponse getVerificationStatus(@PathVariable UUID accountId);

    /**
     * Submit business for KYC verification
     */
    @PostMapping("/api/v1/kyc/business/verify")
    @CircuitBreaker(name = "kycService", fallbackMethod = "submitVerificationFallback")
    @Retry(name = "kycService")
    @TimeLimiter(name = "kycService")
    KycVerificationResponse submitVerification(@RequestBody KycVerificationRequest request);

    /**
     * Check if KYC verification is required for amount
     */
    @GetMapping("/api/v1/kyc/business/{accountId}/required/{amount}")
    @CircuitBreaker(name = "kycService", fallbackMethod = "isVerificationRequiredFallback")
    @Retry(name = "kycService")
    @TimeLimiter(name = "kycService")
    boolean isVerificationRequired(@PathVariable UUID accountId, @PathVariable String amount);

    // DTOs

    @Data
    @Builder
    class KycVerificationRequest {
        private UUID accountId;
        private String businessName;
        private String registrationNumber;
        private String taxId;
        private String ownerName;
        private String ownerNationalId;
        private String businessAddress;
        private String businessType;
    }

    @Data
    @Builder
    class KycVerificationResponse {
        private UUID verificationId;
        private UUID accountId;
        private String status; // PENDING, IN_PROGRESS, APPROVED, REJECTED, EXPIRED
        private String riskLevel; // LOW, MEDIUM, HIGH
        private boolean verified;
        private String reason;
        private String verifiedAt;
    }
}
