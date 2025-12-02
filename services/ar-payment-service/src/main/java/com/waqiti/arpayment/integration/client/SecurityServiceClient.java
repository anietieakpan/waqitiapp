package com.waqiti.arpayment.integration.client;

import com.waqiti.arpayment.integration.dto.BiometricVerificationRequest;
import com.waqiti.arpayment.integration.dto.BiometricVerificationResult;
import com.waqiti.arpayment.integration.dto.SecurityContext;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Feign client for Security Service integration
 * Provides authentication, authorization, and security verification
 */
@FeignClient(
    name = "security-service",
    url = "${feign.client.config.security-service.url:http://security-service/api/v1}",
    fallback = SecurityServiceFallback.class
)
public interface SecurityServiceClient {

    /**
     * Verify biometric authentication
     * @param request Biometric verification request
     * @return Verification result with confidence score
     */
    @PostMapping("/security/biometric/verify")
    BiometricVerificationResult verifyBiometric(@RequestBody BiometricVerificationRequest request);

    /**
     * Get security context for user
     * @param userId User identifier
     * @return Security context with permissions and risk level
     */
    @GetMapping("/security/context/{userId}")
    SecurityContext getSecurityContext(@PathVariable("userId") UUID userId);

    /**
     * Validate transaction security
     * @param userId User ID
     * @param amount Transaction amount (BigDecimal for financial precision)
     * @param deviceId Device identifier
     * @return true if transaction is secure, false otherwise
     */
    @PostMapping("/security/validate-transaction")
    boolean validateTransaction(
        @RequestParam("userId") UUID userId,
        @RequestParam("amount") BigDecimal amount,
        @RequestParam("deviceId") String deviceId
    );

    /**
     * Check if additional authentication is required
     * @param userId User ID
     * @param transactionType Type of transaction
     * @param amount Transaction amount (BigDecimal for financial precision)
     * @return true if additional auth required
     */
    @GetMapping("/security/requires-mfa")
    boolean requiresMultiFactorAuth(
        @RequestParam("userId") UUID userId,
        @RequestParam("type") String transactionType,
        @RequestParam("amount") BigDecimal amount
    );
}
