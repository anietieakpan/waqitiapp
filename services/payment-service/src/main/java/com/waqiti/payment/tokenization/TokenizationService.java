package com.waqiti.payment.tokenization;

import com.waqiti.common.exception.TokenizationException;
import com.waqiti.payment.tokenization.dto.TokenizationRequest;
import com.waqiti.payment.tokenization.dto.TokenizationResponse;
import com.waqiti.payment.tokenization.dto.DetokenizationRequest;
import com.waqiti.payment.tokenization.dto.DetokenizationResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * PCI DSS Compliant Tokenization Service
 *
 * Provides secure tokenization/detokenization of sensitive payment data
 * including credit card numbers, bank account numbers, and CVV codes.
 *
 * Security Features:
 * - AES-256-GCM encryption for data at rest in Vault
 * - Format-preserving encryption (FPE) for regulatory compliance
 * - Hardware Security Module (HSM) integration via Vault
 * - Token rotation and expiration
 * - Comprehensive audit logging
 * - PCI DSS SAQ-A compliance
 *
 * @author Waqiti Security Team
 * @version 3.0.0
 * @since 2025-10-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenizationService {

    private final VaultTokenizationClient vaultClient;
    private final TokenizationAuditService auditService;
    private final TokenizationMetricsService metricsService;
    private final RestTemplate tokenizationRestTemplate;

    @Value("${tokenization.provider:vault}")
    private String provider;

    @Value("${tokenization.vault.transit-key:payment-tokens}")
    private String transitKey;

    @Value("${tokenization.format-preserving:true}")
    private boolean formatPreserving;

    @Value("${tokenization.token-ttl:31536000}") // 1 year default
    private long tokenTtl;

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    /**
     * Tokenize sensitive payment data (credit card, account number, CVV)
     *
     * @param request Tokenization request containing sensitive data
     * @return TokenizationResponse with secure token
     * @throws TokenizationException if tokenization fails
     */
    @CircuitBreaker(name = "tokenization", fallbackMethod = "tokenizeFallback")
    @Retry(name = "tokenization")
    public TokenizationResponse tokenize(TokenizationRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("Tokenizing data: type={}, userId={}",
                request.getDataType(), request.getUserId());

            // Validate input
            validateTokenizationRequest(request);

            // Generate unique token
            String token = generateToken(request);

            // Encrypt and store sensitive data in Vault
            String encryptedData = encryptSensitiveData(request.getSensitiveData());
            vaultClient.storeToken(token, encryptedData, request.getMetadata(), tokenTtl);

            // Create response
            TokenizationResponse response = TokenizationResponse.builder()
                .token(token)
                .tokenType(request.getDataType())
                .expiresAt(System.currentTimeMillis() + (tokenTtl * 1000))
                .formatPreserved(formatPreserving)
                .metadata(request.getMetadata())
                .build();

            // Audit logging (PCI DSS requirement)
            auditService.logTokenization(
                request.getUserId(),
                request.getDataType(),
                token,
                request.getIpAddress(),
                true,
                "Tokenization successful"
            );

            // Metrics
            metricsService.recordTokenization(
                request.getDataType(),
                System.currentTimeMillis() - startTime,
                true
            );

            log.info("Tokenization successful: token={}, type={}",
                maskToken(token), request.getDataType());

            return response;

        } catch (Exception e) {
            log.error("Tokenization failed: type={}, error={}",
                request.getDataType(), e.getMessage(), e);

            auditService.logTokenization(
                request.getUserId(),
                request.getDataType(),
                null,
                request.getIpAddress(),
                false,
                "Tokenization failed: " + e.getMessage()
            );

            metricsService.recordTokenization(
                request.getDataType(),
                System.currentTimeMillis() - startTime,
                false
            );

            throw new TokenizationException("Failed to tokenize data", e);
        }
    }

    /**
     * Detokenize to retrieve original sensitive data
     *
     * @param request Detokenization request with token
     * @return DetokenizationResponse with original data
     * @throws TokenizationException if detokenization fails
     */
    @CircuitBreaker(name = "tokenization", fallbackMethod = "detokenizeFallback")
    @Retry(name = "tokenization")
    @Cacheable(value = "detokenization", key = "#request.token", unless = "#result == null")
    public DetokenizationResponse detokenize(DetokenizationRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("Detokenizing: token={}, userId={}",
                maskToken(request.getToken()), request.getUserId());

            // Validate token format
            validateToken(request.getToken());

            // Retrieve encrypted data from Vault
            String encryptedData = vaultClient.retrieveToken(request.getToken());

            if (encryptedData == null) {
                throw new TokenizationException("Token not found or expired");
            }

            // Decrypt sensitive data
            String sensitiveData = decryptSensitiveData(encryptedData);

            // Create response
            DetokenizationResponse response = DetokenizationResponse.builder()
                .sensitiveData(sensitiveData)
                .metadata(vaultClient.getTokenMetadata(request.getToken()))
                .build();

            // Audit logging
            auditService.logDetokenization(
                request.getUserId(),
                request.getToken(),
                request.getIpAddress(),
                request.getReason(),
                true,
                "Detokenization successful"
            );

            // Metrics
            metricsService.recordDetokenization(
                System.currentTimeMillis() - startTime,
                true
            );

            log.info("Detokenization successful: token={}", maskToken(request.getToken()));

            return response;

        } catch (Exception e) {
            log.error("Detokenization failed: token={}, error={}",
                maskToken(request.getToken()), e.getMessage(), e);

            auditService.logDetokenization(
                request.getUserId(),
                request.getToken(),
                request.getIpAddress(),
                request.getReason(),
                false,
                "Detokenization failed: " + e.getMessage()
            );

            metricsService.recordDetokenization(
                System.currentTimeMillis() - startTime,
                false
            );

            throw new TokenizationException("Failed to detokenize data", e);
        }
    }

    /**
     * Rotate token with new encryption
     *
     * @param oldToken Existing token to rotate
     * @param userId User ID for audit
     * @return New token
     */
    public String rotateToken(String oldToken, String userId) {
        try {
            log.info("Rotating token: oldToken={}, userId={}", maskToken(oldToken), userId);

            // Retrieve original data
            DetokenizationRequest detokenRequest = DetokenizationRequest.builder()
                .token(oldToken)
                .userId(userId)
                .reason("TOKEN_ROTATION")
                .build();

            DetokenizationResponse detokenResponse = detokenize(detokenRequest);

            // Create new token
            TokenizationRequest tokenRequest = TokenizationRequest.builder()
                .sensitiveData(detokenResponse.getSensitiveData())
                .dataType(detokenResponse.getMetadata().get("dataType"))
                .userId(userId)
                .metadata(detokenResponse.getMetadata())
                .build();

            TokenizationResponse tokenResponse = tokenize(tokenRequest);

            // Delete old token
            vaultClient.deleteToken(oldToken);

            auditService.logTokenRotation(userId, oldToken, tokenResponse.getToken());

            return tokenResponse.getToken();

        } catch (Exception e) {
            log.error("Token rotation failed: oldToken={}, error={}",
                maskToken(oldToken), e.getMessage(), e);
            throw new TokenizationException("Failed to rotate token", e);
        }
    }

    /**
     * Delete token and associated data
     *
     * @param token Token to delete
     * @param userId User ID for audit
     */
    public void deleteToken(String token, String userId) {
        try {
            log.info("Deleting token: token={}, userId={}", maskToken(token), userId);

            vaultClient.deleteToken(token);

            auditService.logTokenDeletion(userId, token, true, "Token deleted successfully");

            log.info("Token deleted successfully: token={}", maskToken(token));

        } catch (Exception e) {
            log.error("Token deletion failed: token={}, error={}",
                maskToken(token), e.getMessage(), e);

            auditService.logTokenDeletion(userId, token, false,
                "Token deletion failed: " + e.getMessage());

            throw new TokenizationException("Failed to delete token", e);
        }
    }

    /**
     * Generate secure token with format preservation
     *
     * @param request Tokenization request
     * @return Generated token
     */
    private String generateToken(TokenizationRequest request) {
        if (formatPreserving && "CREDIT_CARD".equals(request.getDataType())) {
            // Format-preserving token (maintains card number format for validation)
            return generateFormatPreservingToken(request.getSensitiveData());
        } else {
            // UUID-based token
            return "tok_" + UUID.randomUUID().toString().replace("-", "");
        }
    }

    /**
     * Generate format-preserving token for credit cards
     * Maintains BIN (first 6 digits) and last 4 digits for display
     *
     * @param cardNumber Original card number
     * @return Format-preserving token
     */
    private String generateFormatPreservingToken(String cardNumber) {
        if (cardNumber.length() < 13) {
            return "tok_" + UUID.randomUUID().toString().replace("-", "");
        }

        // Keep first 6 (BIN) and last 4 digits
        String bin = cardNumber.substring(0, 6);
        String last4 = cardNumber.substring(cardNumber.length() - 4);

        // Generate random middle digits
        SecureRandom random = new SecureRandom();
        int middleLength = cardNumber.length() - 10;
        StringBuilder middle = new StringBuilder();
        for (int i = 0; i < middleLength; i++) {
            middle.append(random.nextInt(10));
        }

        return bin + middle + last4;
    }

    /**
     * Encrypt sensitive data using AES-256-GCM
     *
     * @param sensitiveData Data to encrypt
     * @return Base64-encoded encrypted data
     */
    private String encryptSensitiveData(String sensitiveData) throws Exception {
        // Generate random IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);

        // Get encryption key from Vault
        SecretKey key = vaultClient.getEncryptionKey(transitKey);

        // Initialize cipher
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

        // Encrypt
        byte[] encryptedData = cipher.doFinal(sensitiveData.getBytes(StandardCharsets.UTF_8));

        // Combine IV and encrypted data
        byte[] combined = new byte[iv.length + encryptedData.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Decrypt sensitive data using AES-256-GCM
     *
     * @param encryptedData Base64-encoded encrypted data
     * @return Decrypted sensitive data
     */
    private String decryptSensitiveData(String encryptedData) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedData);

        // Extract IV and encrypted data
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

        // Get decryption key from Vault
        SecretKey key = vaultClient.getEncryptionKey(transitKey);

        // Initialize cipher
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

        // Decrypt
        byte[] decryptedData = cipher.doFinal(ciphertext);

        return new String(decryptedData, StandardCharsets.UTF_8);
    }

    /**
     * Validate tokenization request
     */
    private void validateTokenizationRequest(TokenizationRequest request) {
        if (request.getSensitiveData() == null || request.getSensitiveData().isEmpty()) {
            throw new IllegalArgumentException("Sensitive data cannot be null or empty");
        }

        if (request.getDataType() == null) {
            throw new IllegalArgumentException("Data type must be specified");
        }

        if (request.getUserId() == null) {
            throw new IllegalArgumentException("User ID must be specified");
        }

        // Validate credit card format
        if ("CREDIT_CARD".equals(request.getDataType())) {
            String cardNumber = request.getSensitiveData().replaceAll("\\s+", "");
            if (!cardNumber.matches("\\d{13,19}")) {
                throw new IllegalArgumentException("Invalid credit card number format");
            }
        }

        // Validate account number format
        if ("ACCOUNT_NUMBER".equals(request.getDataType())) {
            String accountNumber = request.getSensitiveData().replaceAll("\\s+", "");
            if (!accountNumber.matches("\\d{8,17}")) {
                throw new IllegalArgumentException("Invalid account number format");
            }
        }
    }

    /**
     * Validate token format
     */
    private void validateToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }

        if (!token.startsWith("tok_") && !token.matches("\\d{13,19}")) {
            throw new IllegalArgumentException("Invalid token format");
        }
    }

    /**
     * Mask token for logging (PCI DSS requirement)
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    /**
     * Fallback method for tokenization circuit breaker
     */
    private TokenizationResponse tokenizeFallback(TokenizationRequest request, Exception e) {
        log.error("Tokenization circuit breaker activated: fallback triggered", e);

        metricsService.recordCircuitBreakerActivation("tokenization");

        throw new TokenizationException(
            "Tokenization service temporarily unavailable. Please try again later.", e);
    }

    /**
     * Verify token ownership for authorization checks
     *
     * @param userId User ID to verify
     * @param token Token to check ownership
     * @return true if user owns token and it's valid
     */
    public boolean verifyTokenOwnership(UUID userId, String token) {
        try {
            log.debug("Verifying token ownership: userId={}, token={}",
                userId, maskToken(token));

            // Retrieve token metadata
            Map<String, String> metadata = vaultClient.getTokenMetadata(token);

            if (metadata.isEmpty()) {
                log.warn("Token not found or expired: token={}", maskToken(token));
                return false;
            }

            // Verify ownership
            String tokenUserId = metadata.get("userId");
            boolean isOwner = userId.toString().equals(tokenUserId);

            // Check expiration
            Long expiresAt = Long.parseLong(metadata.getOrDefault("expires_at", "0"));
            boolean isExpired = System.currentTimeMillis() > expiresAt;

            if (isExpired) {
                log.warn("Token expired: token={}, expiresAt={}",
                    maskToken(token), expiresAt);
                return false;
            }

            if (!isOwner) {
                log.warn("Token ownership mismatch: userId={}, tokenUserId={}",
                    userId, tokenUserId);
            }

            return isOwner && !isExpired;

        } catch (Exception e) {
            log.error("Token ownership verification failed: userId={}, token={}, error={}",
                userId, maskToken(token), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Fallback method for detokenization circuit breaker
     */
    private DetokenizationResponse detokenizeFallback(DetokenizationRequest request, Exception e) {
        log.error("Detokenization circuit breaker activated: fallback triggered", e);

        metricsService.recordCircuitBreakerActivation("detokenization");

        throw new TokenizationException(
            "Detokenization service temporarily unavailable. Please try again later.", e);
    }
}
