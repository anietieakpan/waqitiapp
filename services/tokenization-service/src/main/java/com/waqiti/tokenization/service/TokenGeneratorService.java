package com.waqiti.tokenization.service;

import com.waqiti.tokenization.domain.TokenType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Token Generator Service
 *
 * Generates cryptographically secure tokens for sensitive data.
 *
 * Security:
 * - Uses SecureRandom (CSPRNG - Cryptographically Secure Pseudo-Random Number Generator)
 * - 256-bit random tokens (32 bytes)
 * - Base64URL encoding for URL safety
 * - Format: {TYPE}_{32_random_chars}
 *
 * Examples:
 * - CARD_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6
 * - BANK_x7y8z9a0b1c2d3e4f5g6h7i8j9k0l1m2
 * - SSN_m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8
 *
 * @author Waqiti Platform Engineering
 */
@Service
@Slf4j
public class TokenGeneratorService {

    private final SecureRandom secureRandom;

    public TokenGeneratorService() {
        // Use strong SecureRandom instance
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generate a cryptographically secure token
     *
     * @param type The token type (CARD, BANK_ACCOUNT, etc.)
     * @return Unique token string
     */
    public String generateToken(TokenType type) {
        String prefix = type.getPrefix();
        String randomPart = generateRandomString(32);

        String token = prefix + "_" + randomPart;

        log.debug("Generated token with prefix: {}", prefix);

        return token;
    }

    /**
     * Generate cryptographically secure random string
     *
     * Uses SecureRandom to generate 32 bytes of random data,
     * then encodes to Base64URL (URL-safe, no padding)
     *
     * @param length Desired length of random string
     * @return Random string
     */
    private String generateRandomString(int length) {
        // Calculate bytes needed (Base64 encodes 3 bytes to 4 characters)
        int bytesNeeded = (length * 3) / 4 + 1;

        byte[] randomBytes = new byte[bytesNeeded];
        secureRandom.nextBytes(randomBytes);

        // Use Base64 URL-safe encoder (no padding)
        String encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(randomBytes);

        // Trim to exact length
        return encoded.substring(0, Math.min(length, encoded.length()));
    }

    /**
     * Validate token format
     *
     * Checks if token matches expected format: TYPE_32chars
     *
     * @param token Token to validate
     * @return true if valid format
     */
    public boolean isValidTokenFormat(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        // Check format: PREFIX_32chars
        String[] parts = token.split("_", 2);
        if (parts.length != 2) {
            return false;
        }

        String prefix = parts[0];
        String randomPart = parts[1];

        // Validate prefix is known token type
        boolean validPrefix = false;
        for (TokenType type : TokenType.values()) {
            if (type.getPrefix().equals(prefix)) {
                validPrefix = true;
                break;
            }
        }

        if (!validPrefix) {
            return false;
        }

        // Validate random part length (should be 32 characters)
        if (randomPart.length() != 32) {
            return false;
        }

        // Validate random part contains only Base64URL characters
        return randomPart.matches("^[A-Za-z0-9_-]+$");
    }

    /**
     * Extract token type from token string
     *
     * @param token Token string
     * @return TokenType or null if invalid
     */
    public TokenType extractTokenType(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        String[] parts = token.split("_", 2);
        if (parts.length != 2) {
            return null;
        }

        String prefix = parts[0];

        for (TokenType type : TokenType.values()) {
            if (type.getPrefix().equals(prefix)) {
                return type;
            }
        }

        return null;
    }
}
