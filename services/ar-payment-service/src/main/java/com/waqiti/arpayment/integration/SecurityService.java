package com.waqiti.arpayment.integration;

import com.waqiti.arpayment.integration.client.SecurityServiceClient;
import com.waqiti.arpayment.integration.dto.BiometricVerificationRequest;
import com.waqiti.arpayment.integration.dto.BiometricVerificationResult;
import com.waqiti.arpayment.integration.dto.SecurityContext;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Security service wrapper with caching, metrics, and comprehensive security features
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityService {

    private final SecurityServiceClient securityServiceClient;
    private final MeterRegistry meterRegistry;

    /**
     * Verify biometric authentication with security logging
     */
    public BiometricVerificationResult verifyBiometric(BiometricVerificationRequest request) {
        log.info("Biometric verification requested for user: {} type: {}",
                request.getUserId(), request.getBiometricType());

        try {
            BiometricVerificationResult result = securityServiceClient.verifyBiometric(request);

            meterRegistry.counter("security.biometric.verification",
                    "type", request.getBiometricType(),
                    "verified", String.valueOf(result.isVerified())).increment();

            if (!result.isVerified()) {
                log.warn("Biometric verification failed for user: {} confidence: {} reasons: {}",
                        request.getUserId(), result.getConfidence(), result.getFailureReasons());
                meterRegistry.counter("security.biometric.failure",
                        "type", request.getBiometricType()).increment();
            }

            return result;

        } catch (Exception e) {
            log.error("Biometric verification error for user: {}", request.getUserId(), e);
            meterRegistry.counter("security.biometric.error").increment();

            return BiometricVerificationResult.builder()
                    .verified(false)
                    .confidence(0.0f)
                    .errorMessage("Biometric verification service error")
                    .requiresManualReview(true)
                    .build();
        }
    }

    /**
     * Get security context with caching (5 minutes TTL)
     */
    @Cacheable(value = "securityContext", key = "#userId", unless = "#result.restrictedMode")
    public SecurityContext getSecurityContext(UUID userId) {
        try {
            return securityServiceClient.getSecurityContext(userId);
        } catch (Exception e) {
            log.error("Failed to fetch security context for user: {}", userId, e);
            return SecurityContext.builder()
                    .userId(userId)
                    .riskLevel("HIGH") // Fail-secure
                    .restrictedMode(true)
                    .build();
        }
    }

    /**
     * Validate transaction with comprehensive security checks
     * @param userId User ID performing the transaction
     * @param amount Transaction amount (must be BigDecimal for financial precision)
     * @param deviceId Device identifier
     * @return true if transaction is valid, false otherwise
     */
    public boolean validateTransaction(UUID userId, BigDecimal amount, String deviceId) {
        if (amount == null) {
            log.error("Transaction validation failed: amount is null for user: {}", userId);
            return false;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Transaction validation failed: invalid amount {} for user: {}", amount, userId);
            return false;
        }

        log.debug("Validating transaction for user: {} amount: {} device: {}", userId, amount, deviceId);

        try {
            boolean valid = securityServiceClient.validateTransaction(userId, amount, deviceId);

            meterRegistry.counter("security.transaction.validation",
                    "valid", String.valueOf(valid)).increment();

            if (!valid) {
                log.warn("Transaction validation failed for user: {} amount: {}", userId, amount);
            }

            return valid;

        } catch (Exception e) {
            log.error("Transaction validation error for user: {}", userId, e);
            meterRegistry.counter("security.transaction.validation.error").increment();
            return false; // Fail-secure
        }
    }

    /**
     * Check if multi-factor authentication is required
     * @param userId User ID performing the transaction
     * @param transactionType Type of transaction (PAYMENT, TRANSFER, etc.)
     * @param amount Transaction amount (must be BigDecimal for financial precision)
     * @return true if MFA is required, false otherwise
     */
    public boolean requiresMultiFactorAuth(UUID userId, String transactionType, BigDecimal amount) {
        if (amount == null) {
            log.warn("MFA check with null amount for user: {} - requiring MFA by default", userId);
            return true; // Fail-secure
        }

        try {
            boolean requiresMfa = securityServiceClient.requiresMultiFactorAuth(userId, transactionType, amount);

            if (requiresMfa) {
                log.info("MFA required for user: {} type: {} amount: {}", userId, transactionType, amount);
                meterRegistry.counter("security.mfa.required").increment();
            }

            return requiresMfa;

        } catch (Exception e) {
            log.error("MFA check error for user: {}", userId, e);
            return true; // Fail-secure: require MFA on error
        }
    }
}
