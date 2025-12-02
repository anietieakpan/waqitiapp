package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * CRITICAL SECURITY FIX - SecureHashingUtils
 * Secure replacement for MD5 and other weak hashing algorithms
 * Uses SHA-256, SHA-3, and other cryptographically secure hash functions
 */
@Component
@Slf4j
public class SecureHashingUtils {
    
    // Preferred hash algorithms in order of strength
    private static final String[] PREFERRED_HASH_ALGORITHMS = {
        "SHA3-256",    // SHA-3 (most secure)
        "SHA-256",     // SHA-2 (widely supported)
        "SHA-512"      // SHA-2 longer variant
    };
    
    /**
     * Generate secure hash using SHA-256 (replacement for MD5)
     * 
     * @param data Data to hash
     * @return Hexadecimal hash string
     */
    public static String generateSecureHash(byte[] data) {
        return generateSecureHash(data, "SHA-256");
    }
    
    /**
     * Generate secure hash using SHA-256 (replacement for MD5)
     * 
     * @param data String data to hash
     * @return Hexadecimal hash string
     */
    public static String generateSecureHash(String data) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        return generateSecureHash(data.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Generate secure hash using specified algorithm
     * 
     * @param data Data to hash
     * @param algorithm Hash algorithm (SHA-256, SHA3-256, etc.)
     * @return Hexadecimal hash string
     */
    public static String generateSecureHash(byte[] data, String algorithm) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hashBytes = digest.digest(data);
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("Hash algorithm {} not available, falling back to SHA-256", algorithm);
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = digest.digest(data);
                return HexFormat.of().formatHex(hashBytes);
            } catch (NoSuchAlgorithmException ex) {
                throw new RuntimeException("No secure hash algorithm available", ex);
            }
        }
    }
    
    /**
     * Generate secure hash using the strongest available algorithm
     * 
     * @param data Data to hash
     * @return Hexadecimal hash string with algorithm prefix
     */
    public static String generateStrongestHash(byte[] data) {
        for (String algorithm : PREFERRED_HASH_ALGORITHMS) {
            try {
                MessageDigest digest = MessageDigest.getInstance(algorithm);
                byte[] hashBytes = digest.digest(data);
                String hash = HexFormat.of().formatHex(hashBytes);
                return algorithm + ":" + hash;
            } catch (NoSuchAlgorithmException e) {
                log.debug("Algorithm {} not available, trying next", algorithm);
            }
        }
        
        throw new RuntimeException("No secure hash algorithm available");
    }
    
    /**
     * Generate file checksum using SHA-256 (secure replacement for MD5)
     * 
     * @param data File data
     * @return SHA-256 checksum
     */
    public static String generateFileChecksum(byte[] data) {
        return generateSecureHash(data, "SHA-256");
    }
    
    /**
     * Generate content integrity hash
     * 
     * @param content Content to hash
     * @return SHA-256 hash for integrity checking
     */
    public static String generateIntegrityHash(String content) {
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }
        return generateSecureHash(content.getBytes(StandardCharsets.UTF_8), "SHA-256");
    }
    
    /**
     * Generate HMAC using SHA-256
     * 
     * @param data Data to authenticate
     * @param key Secret key
     * @return HMAC-SHA256
     */
    public static String generateHMAC(byte[] data, byte[] key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data);
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC generation failed", e);
        }
    }
    
    /**
     * Verify hash against known value (constant-time comparison)
     * 
     * @param data Original data
     * @param expectedHash Expected hash value
     * @param algorithm Hash algorithm used
     * @return true if hash matches
     */
    public static boolean verifyHash(byte[] data, String expectedHash, String algorithm) {
        if (data == null || expectedHash == null || algorithm == null) {
            return false;
        }
        
        try {
            String actualHash = generateSecureHash(data, algorithm);
            return constantTimeEquals(actualHash, expectedHash);
        } catch (Exception e) {
            log.error("Hash verification failed", e);
            return false;
        }
    }
    
    /**
     * Constant-time string comparison to prevent timing attacks
     * 
     * @param a First string
     * @param b Second string
     * @return true if strings are equal
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        
        if (a.length() != b.length()) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        
        return result == 0;
    }
    
    /**
     * Get information about available hash algorithms
     * 
     * @return List of available secure hash algorithms
     */
    public static String[] getAvailableSecureAlgorithms() {
        return java.security.Security.getAlgorithms("MessageDigest")
            .stream()
            .filter(alg -> alg.startsWith("SHA") && !alg.equals("SHA-1"))
            .toArray(String[]::new);
    }
    
    /**
     * Check if an algorithm is considered secure
     * 
     * @param algorithm Algorithm name
     * @return true if algorithm is secure
     */
    public static boolean isSecureAlgorithm(String algorithm) {
        if (algorithm == null) return false;
        
        // Block known weak algorithms
        String upperAlg = algorithm.toUpperCase();
        if (upperAlg.equals("MD5") || upperAlg.equals("SHA1") || upperAlg.equals("SHA-1")) {
            return false;
        }
        
        // Allow SHA-2 and SHA-3 variants
        return upperAlg.startsWith("SHA-2") || 
               upperAlg.startsWith("SHA-3") || 
               upperAlg.startsWith("SHA-256") || 
               upperAlg.startsWith("SHA-384") || 
               upperAlg.startsWith("SHA-512") ||
               upperAlg.startsWith("SHA3-");
    }
}