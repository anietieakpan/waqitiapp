package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Production-grade Secure Random Number Generator Service
 *
 * This service provides cryptographically secure random number generation
 * for security-sensitive operations throughout the application.
 *
 * CRITICAL SECURITY FIX:
 * Replaces all instances of Math.random() which is NOT cryptographically secure
 * and can be predicted/manipulated by attackers.
 *
 * Use Cases:
 * - Fraud detection risk scoring
 * - Security token generation
 * - Random factors in ML models
 * - Session ID generation
 * - Challenge-response mechanisms
 * - Cryptographic nonces
 *
 * Thread-Safety: SecureRandom is thread-safe and can be shared across threads
 *
 * Performance: Initialization uses NativePRNGNonBlocking for better performance
 * while maintaining cryptographic strength
 *
 * @author Waqiti Security Team
 * @since 1.0 (CRITICAL SECURITY FIX)
 */
@Slf4j
@Service
public class SecureRandomService {

    private SecureRandom secureRandom;

    @PostConstruct
    public void init() {
        try {
            // Try to get NativePRNGNonBlocking for better performance on Unix systems
            try {
                secureRandom = SecureRandom.getInstance("NativePRNGNonBlocking");
                log.info("SecureRandomService initialized with NativePRNGNonBlocking");
            } catch (NoSuchAlgorithmException e) {
                // Fallback to default strong algorithm
                secureRandom = SecureRandom.getInstanceStrong();
                log.info("SecureRandomService initialized with SecureRandom.getInstanceStrong()");
            }

            // Seed the generator (though SecureRandom self-seeds on first use)
            secureRandom.nextBytes(new byte[20]);

            log.info("SecureRandomService successfully initialized. Algorithm: {}, Provider: {}",
                secureRandom.getAlgorithm(), secureRandom.getProvider().getName());

        } catch (NoSuchAlgorithmException e) {
            log.error("CRITICAL: Failed to initialize strong SecureRandom. Falling back to default.", e);
            secureRandom = new SecureRandom();
        }
    }

    /**
     * Generates a random double between 0.0 (inclusive) and 1.0 (exclusive)
     *
     * REPLACES: Math.random()
     *
     * @return cryptographically secure random double
     */
    public double nextDouble() {
        return secureRandom.nextDouble();
    }

    /**
     * Generates a random double within specified range
     *
     * @param min minimum value (inclusive)
     * @param max maximum value (exclusive)
     * @return cryptographically secure random double in range [min, max)
     */
    public double nextDouble(double min, double max) {
        if (min >= max) {
            throw new IllegalArgumentException("min must be less than max");
        }
        return min + (secureRandom.nextDouble() * (max - min));
    }

    /**
     * Generates a random integer between 0 (inclusive) and bound (exclusive)
     *
     * @param bound upper bound (exclusive)
     * @return cryptographically secure random integer
     */
    public int nextInt(int bound) {
        return secureRandom.nextInt(bound);
    }

    /**
     * Generates a random integer within specified range
     *
     * @param min minimum value (inclusive)
     * @param max maximum value (exclusive)
     * @return cryptographically secure random integer in range [min, max)
     */
    public int nextInt(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("min must be less than max");
        }
        return secureRandom.nextInt(max - min) + min;
    }

    /**
     * Generates a random long value
     *
     * @return cryptographically secure random long
     */
    public long nextLong() {
        return secureRandom.nextLong();
    }

    /**
     * Generates a random long within specified range
     *
     * @param min minimum value (inclusive)
     * @param max maximum value (exclusive)
     * @return cryptographically secure random long in range [min, max)
     */
    public long nextLong(long min, long max) {
        if (min >= max) {
            throw new IllegalArgumentException("min must be less than max");
        }
        return min + (long) (secureRandom.nextDouble() * (max - min));
    }

    /**
     * Generates random bytes
     *
     * @param bytes array to fill with random bytes
     */
    public void nextBytes(byte[] bytes) {
        secureRandom.nextBytes(bytes);
    }

    /**
     * Generates cryptographically secure random bytes
     *
     * @param length number of bytes to generate
     * @return byte array filled with random bytes
     */
    public byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    /**
     * Generates a cryptographically secure random token
     *
     * @param length number of bytes (token will be base64 encoded, so output length will be larger)
     * @return URL-safe base64 encoded random token
     */
    public String generateSecureToken(int length) {
        byte[] randomBytes = generateRandomBytes(length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Generates a random factor for fraud detection/ML models
     * Typically used to add randomness to scoring algorithms
     *
     * REPLACES: 0.8 + (Math.random() * 0.4)
     *
     * @param min minimum factor value
     * @param max maximum factor value
     * @return random factor in range [min, max)
     */
    public double generateRandomFactor(double min, double max) {
        return nextDouble(min, max);
    }

    /**
     * Generates a random risk adjustment factor
     * Common pattern in fraud detection: 0.8 to 1.2 range
     *
     * @return random risk adjustment factor between 0.8 and 1.2
     */
    public double generateRiskAdjustmentFactor() {
        return nextDouble(0.8, 1.2);
    }

    /**
     * Generates a random scoring variance
     * Used in ML models to add controlled randomness
     *
     * @param baseScore the base score to vary
     * @param variancePercent percentage of variance (e.g., 0.1 for ±10%)
     * @return score with random variance applied
     */
    public double generateScoringVariance(double baseScore, double variancePercent) {
        double variance = baseScore * variancePercent;
        double randomVariance = nextDouble(-variance, variance);
        return baseScore + randomVariance;
    }

    /**
     * Generates a cryptographically secure UUID-like token
     *
     * @return 32-character hexadecimal token
     */
    public String generateUUIDToken() {
        byte[] randomBytes = generateRandomBytes(16);
        StringBuilder sb = new StringBuilder();
        for (byte b : randomBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Generates a secure numeric code (e.g., for 2FA, verification)
     *
     * @param digits number of digits in the code
     * @return numeric code as string (zero-padded if necessary)
     */
    public String generateNumericCode(int digits) {
        if (digits < 1 || digits > 10) {
            throw new IllegalArgumentException("Digits must be between 1 and 10");
        }

        int bound = (int) Math.pow(10, digits);
        int code = secureRandom.nextInt(bound);
        return String.format("%0" + digits + "d", code);
    }

    /**
     * Re-seeds the SecureRandom instance
     * Generally not necessary as SecureRandom self-seeds, but provided for completeness
     *
     * @param seed additional seed bytes
     */
    public void reseed(byte[] seed) {
        secureRandom.setSeed(seed);
        log.debug("SecureRandom re-seeded with {} bytes", seed.length);
    }

    /**
     * Gets the algorithm name being used
     *
     * @return algorithm name (e.g., "NativePRNGNonBlocking", "SHA1PRNG")
     */
    public String getAlgorithm() {
        return secureRandom.getAlgorithm();
    }

    /**
     * Gets the provider name
     *
     * @return provider name (e.g., "SUN")
     */
    public String getProviderName() {
        return secureRandom.getProvider().getName();
    }
}
