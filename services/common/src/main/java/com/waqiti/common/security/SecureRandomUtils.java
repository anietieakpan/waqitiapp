package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CRITICAL SECURITY FIX - SecureRandomUtils
 * Secure replacement for new SecureRandom() with strong entropy sources
 * Prevents blocking and ensures cryptographically secure random number generation
 */
@Component
@Slf4j
public class SecureRandomUtils {
    
    // Cache for SecureRandom instances to avoid expensive initialization
    private static final ConcurrentHashMap<String, SecureRandom> SECURE_RANDOM_CACHE = new ConcurrentHashMap<>();
    
    // Default algorithm preference order (strongest first)
    private static final String[] PREFERRED_ALGORITHMS = {
        "NativePRNGNonBlocking",  // Linux /dev/urandom
        "Windows-PRNG",           // Windows CryptGenRandom
        "SHA1PRNG"                // Fallback
    };
    
    /**
     * Get a cryptographically strong SecureRandom instance
     * Uses non-blocking entropy sources to prevent performance issues
     * 
     * @return SecureRandom instance with strong entropy
     */
    public static SecureRandom getSecureRandom() {
        return SECURE_RANDOM_CACHE.computeIfAbsent("default", k -> createSecureRandom());
    }
    
    /**
     * Get a cryptographically strong SecureRandom instance for specific use case
     * 
     * @param purpose The purpose/context for this random generator
     * @return SecureRandom instance with strong entropy
     */
    public static SecureRandom getSecureRandom(String purpose) {
        if (purpose == null || purpose.trim().isEmpty()) {
            return getSecureRandom();
        }
        
        return SECURE_RANDOM_CACHE.computeIfAbsent(purpose, k -> createSecureRandom());
    }
    
    /**
     * Create a new SecureRandom instance with the strongest available algorithm
     */
    private static SecureRandom createSecureRandom() {
        SecureRandom secureRandom = null;
        
        // Try each algorithm in order of preference
        for (String algorithm : PREFERRED_ALGORITHMS) {
            try {
                secureRandom = SecureRandom.getInstance(algorithm);
                log.debug("Successfully created SecureRandom with algorithm: {}", algorithm);
                break;
            } catch (NoSuchAlgorithmException e) {
                log.debug("Algorithm {} not available: {}", algorithm, e.getMessage());
            }
        }
        
        // Fallback to default if all algorithms fail
        if (secureRandom == null) {
            log.warn("All preferred algorithms failed, using default SecureRandom");
            secureRandom = new SecureRandom();
        }
        
        // Force seeding to ensure proper initialization
        secureRandom.nextBytes(new byte[32]);
        
        log.info("Initialized SecureRandom with algorithm: {}", secureRandom.getAlgorithm());
        return secureRandom;
    }
    
    /**
     * Generate cryptographically secure random bytes
     * 
     * @param length Number of bytes to generate
     * @return Secure random bytes
     */
    public static byte[] generateSecureBytes(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive");
        }
        
        byte[] bytes = new byte[length];
        getSecureRandom().nextBytes(bytes);
        return bytes;
    }
    
    /**
     * Generate cryptographically secure random integer
     * 
     * @param bound Upper bound (exclusive)
     * @return Secure random integer
     */
    public static int generateSecureInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        
        return getSecureRandom().nextInt(bound);
    }
    
    /**
     * Generate cryptographically secure random long
     * 
     * @return Secure random long
     */
    public static long generateSecureLong() {
        return getSecureRandom().nextLong();
    }
    
    /**
     * Generate cryptographically secure random string
     * 
     * @param length Length of string
     * @param charset Character set to use
     * @return Secure random string
     */
    public static String generateSecureString(int length, String charset) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive");
        }
        if (charset == null || charset.isEmpty()) {
            throw new IllegalArgumentException("Charset cannot be null or empty");
        }
        
        SecureRandom random = getSecureRandom();
        StringBuilder sb = new StringBuilder(length);
        
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(charset.length());
            sb.append(charset.charAt(index));
        }
        
        return sb.toString();
    }
    
    /**
     * Generate cryptographically secure alphanumeric string
     * 
     * @param length Length of string
     * @return Secure random alphanumeric string
     */
    public static String generateSecureAlphanumeric(int length) {
        String charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        return generateSecureString(length, charset);
    }
    
    /**
     * Generate cryptographically secure token
     * 
     * @param length Length of token
     * @return Secure random token (URL-safe base64)
     */
    public static String generateSecureToken(int length) {
        byte[] bytes = generateSecureBytes(length);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * Generate cryptographically secure UUID-like string
     * 
     * @return Secure random UUID-like string
     */
    public static String generateSecureUUID() {
        SecureRandom random = getSecureRandom();
        return String.format("%08x-%04x-%04x-%04x-%012x",
            random.nextInt(),
            random.nextInt(0x10000),
            random.nextInt(0x10000),
            random.nextInt(0x10000),
            random.nextLong() & 0xFFFFFFFFFFFFL);
    }
    
    /**
     * Clear cached SecureRandom instances (for testing or security purposes)
     */
    public static void clearCache() {
        SECURE_RANDOM_CACHE.clear();
        log.info("SecureRandom cache cleared");
    }
    
    /**
     * Get information about the current SecureRandom algorithm
     * 
     * @return Algorithm name and provider info
     */
    public static String getSecureRandomInfo() {
        SecureRandom sr = getSecureRandom();
        return String.format("Algorithm: %s, Provider: %s", 
            sr.getAlgorithm(), 
            sr.getProvider().getName());
    }
}