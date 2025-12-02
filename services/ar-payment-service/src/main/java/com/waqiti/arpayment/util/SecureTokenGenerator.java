package com.waqiti.arpayment.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Secure token generator for AR payment sessions
 * Uses cryptographically secure random number generation and HMAC signatures
 * to prevent token prediction and tampering attacks.
 *
 * Security Features:
 * - SecureRandom for unpredictable token generation
 * - HMAC-SHA256 signature for tamper detection
 * - Base64 URL-safe encoding
 * - 32 bytes of entropy (256 bits)
 */
@Slf4j
@Component
public class SecureTokenGenerator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int TOKEN_BYTE_LENGTH = 32; // 256 bits of entropy
    private static final String TOKEN_PREFIX = "AR_";

    private final SecureRandom secureRandom;
    private final String hmacSecretKey;

    /**
     * Constructor with HMAC secret from configuration
     * @param hmacSecretKey Secret key for HMAC signature (should be from Vault)
     */
    public SecureTokenGenerator(
            @Value("${ar-payment.security.token-hmac-secret:${VAULT_AR_TOKEN_SECRET:}}") String hmacSecretKey) {
        this.secureRandom = new SecureRandom();
        this.hmacSecretKey = hmacSecretKey;
    }

    @PostConstruct
    public void init() {
        if (hmacSecretKey == null || hmacSecretKey.trim().isEmpty()) {
            log.error("CRITICAL SECURITY WARNING: HMAC secret key not configured! " +
                     "Set ar-payment.security.token-hmac-secret or VAULT_AR_TOKEN_SECRET");
            throw new IllegalStateException("HMAC secret key must be configured for secure token generation");
        }

        if (hmacSecretKey.length() < 32) {
            log.warn("SECURITY WARNING: HMAC secret key is shorter than recommended 32 characters");
        }

        // Seed the SecureRandom (optional, it self-seeds, but this ensures it's ready)
        secureRandom.nextBytes(new byte[1]);
        log.info("SecureTokenGenerator initialized with HMAC-SHA256 signature");
    }

    /**
     * Generate a cryptographically secure AR session token
     * Format: AR_<base64-random-token>.<hmac-signature>
     *
     * @return Secure session token with HMAC signature
     */
    public String generateSessionToken() {
        // Generate 32 bytes (256 bits) of cryptographically secure random data
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);

        // Encode to URL-safe Base64 (no padding for cleaner tokens)
        String randomToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        // Generate HMAC signature for the token
        String signature = generateHmacSignature(randomToken);

        // Combine: AR_<token>.<signature>
        return TOKEN_PREFIX + randomToken + "." + signature;
    }

    /**
     * Validate a session token by verifying its HMAC signature
     *
     * @param token Token to validate
     * @return true if token signature is valid, false otherwise
     */
    public boolean validateSessionToken(String token) {
        if (token == null || !token.startsWith(TOKEN_PREFIX)) {
            log.warn("Invalid token format: missing AR_ prefix");
            return false;
        }

        // Remove prefix
        String tokenWithoutPrefix = token.substring(TOKEN_PREFIX.length());

        // Split into token and signature
        String[] parts = tokenWithoutPrefix.split("\\.");
        if (parts.length != 2) {
            log.warn("Invalid token format: missing signature component");
            return false;
        }

        String randomToken = parts[0];
        String providedSignature = parts[1];

        // Compute expected signature
        String expectedSignature = generateHmacSignature(randomToken);

        // Constant-time comparison to prevent timing attacks
        boolean valid = constantTimeEquals(providedSignature, expectedSignature);

        if (!valid) {
            log.warn("Token validation failed: HMAC signature mismatch (possible tampering attempt)");
        }

        return valid;
    }

    /**
     * Generate HMAC-SHA256 signature for a token
     *
     * @param data Data to sign
     * @return Base64-encoded HMAC signature
     */
    private String generateHmacSignature(String data) {
        try {
            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(
                    hmacSecretKey.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            hmac.init(secretKey);

            byte[] signatureBytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // Return first 16 bytes (128 bits) of signature, Base64-encoded
            // This balances security with token length
            byte[] truncatedSignature = new byte[16];
            System.arraycopy(signatureBytes, 0, truncatedSignature, 0, 16);

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(truncatedSignature);

        } catch (NoSuchAlgorithmException e) {
            log.error("HMAC algorithm not available: {}", HMAC_ALGORITHM, e);
            throw new RuntimeException("Failed to generate HMAC signature", e);
        } catch (InvalidKeyException e) {
            log.error("Invalid HMAC secret key", e);
            throw new RuntimeException("Failed to generate HMAC signature", e);
        }
    }

    /**
     * Constant-time string comparison to prevent timing attacks
     *
     * @param a First string
     * @param b Second string
     * @return true if strings are equal, false otherwise
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        if (aBytes.length != bBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }

        return result == 0;
    }

    /**
     * Generate a secure experience ID
     * Format: AREXP_<random-token>
     *
     * @return Secure experience identifier
     */
    public String generateExperienceId() {
        byte[] randomBytes = new byte[16]; // 128 bits for experience IDs
        secureRandom.nextBytes(randomBytes);

        String randomToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        return "AREXP_" + randomToken;
    }
}
