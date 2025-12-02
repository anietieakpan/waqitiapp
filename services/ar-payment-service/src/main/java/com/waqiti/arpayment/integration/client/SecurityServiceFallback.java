package com.waqiti.arpayment.integration.client;

import com.waqiti.arpayment.integration.dto.BiometricVerificationRequest;
import com.waqiti.arpayment.integration.dto.BiometricVerificationResult;
import com.waqiti.arpayment.integration.dto.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

/**
 * Fallback implementation for Security Service client
 * Implements fail-secure pattern - deny access when service unavailable
 */
@Slf4j
@Component
public class SecurityServiceFallback implements SecurityServiceClient {

    @Override
    public BiometricVerificationResult verifyBiometric(BiometricVerificationRequest request) {
        log.error("Security service unavailable - failing biometric verification");
        return BiometricVerificationResult.builder()
                .verified(false)
                .confidence(0.0f)
                .errorMessage("Security service unavailable")
                .requiresManualReview(true)
                .build();
    }

    @Override
    public SecurityContext getSecurityContext(UUID userId) {
        log.error("Security service unavailable - returning restricted security context");
        return SecurityContext.builder()
                .userId(userId)
                .riskLevel("HIGH") // Fail-secure: assume high risk
                .permissions(Collections.emptySet())
                .restrictedMode(true)
                .build();
    }

    @Override
    public boolean validateTransaction(UUID userId, BigDecimal amount, String deviceId) {
        log.error("Security service unavailable - failing transaction validation for user: {} amount: {}",
                  userId, amount);
        return false; // Fail-secure: deny transaction
    }

    @Override
    public boolean requiresMultiFactorAuth(UUID userId, String transactionType, BigDecimal amount) {
        log.error("Security service unavailable - requiring MFA as fail-safe for user: {} type: {} amount: {}",
                  userId, transactionType, amount);
        return true; // Fail-secure: require MFA
    }
}
