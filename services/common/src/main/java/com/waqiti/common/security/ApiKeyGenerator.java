package com.waqiti.common.security;

import org.springframework.stereotype.Component;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Secure API Key Generator using cryptographically strong random generation
 */
@Component
public class ApiKeyGenerator {
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String API_KEY_PREFIX = "waq_";
    private static final String SECRET_KEY_PREFIX = "waq_sk_";
    
    /**
     * Generate a secure API key with proper entropy
     * Format: waq_[base64-encoded-random-bytes]
     * Length: ~43 characters (prefix + 32 bytes base64 encoded)
     */
    public String generateApiKey() {
        byte[] randomBytes = new byte[32]; // 256 bits of entropy
        SECURE_RANDOM.nextBytes(randomBytes);
        return API_KEY_PREFIX + BASE64_ENCODER.encodeToString(randomBytes);
    }
    
    /**
     * Generate a secure API secret with high entropy
     * Format: waq_sk_[base64-encoded-random-bytes]
     * Length: ~70 characters (prefix + 48 bytes base64 encoded)
     */
    public String generateApiSecret() {
        byte[] randomBytes = new byte[48]; // 384 bits of entropy
        SECURE_RANDOM.nextBytes(randomBytes);
        return SECRET_KEY_PREFIX + BASE64_ENCODER.encodeToString(randomBytes);
    }
    
    /**
     * Generate a secure webhook signing secret
     * Used for HMAC signature verification of webhooks
     */
    public String generateWebhookSecret() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA256");
            keyGen.init(256, SECURE_RANDOM);
            SecretKey secretKey = keyGen.generateKey();
            return BASE64_ENCODER.encodeToString(secretKey.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            // Fallback to raw random bytes if HMAC not available
            byte[] randomBytes = new byte[32];
            SECURE_RANDOM.nextBytes(randomBytes);
            return BASE64_ENCODER.encodeToString(randomBytes);
        }
    }
    
    /**
     * Generate a secure session token
     * Used for temporary authentication tokens
     */
    public String generateSessionToken() {
        byte[] randomBytes = new byte[64]; // 512 bits for session tokens
        SECURE_RANDOM.nextBytes(randomBytes);
        return BASE64_ENCODER.encodeToString(randomBytes);
    }
    
    /**
     * Generate a short secure code (for 2FA, verification codes, etc.)
     * @param length The desired length of the code (typically 6-8 digits)
     */
    public String generateSecureCode(int length) {
        if (length < 4 || length > 10) {
            throw new IllegalArgumentException("Code length must be between 4 and 10");
        }
        
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(SECURE_RANDOM.nextInt(10)); // 0-9
        }
        return code.toString();
    }
    
    /**
     * Generate a secure alphanumeric code
     * Used for referral codes, promo codes, etc.
     */
    public String generateAlphanumericCode(int length) {
        if (length < 4 || length > 32) {
            throw new IllegalArgumentException("Code length must be between 4 and 32");
        }
        
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder(length);
        
        for (int i = 0; i < length; i++) {
            code.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        
        return code.toString();
    }
    
    /**
     * Validate API key format
     */
    public boolean isValidApiKeyFormat(String apiKey) {
        if (apiKey == null || !apiKey.startsWith(API_KEY_PREFIX)) {
            return false;
        }
        
        String keyPart = apiKey.substring(API_KEY_PREFIX.length());
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(keyPart);
            return decoded.length == 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Validate API secret format
     */
    public boolean isValidApiSecretFormat(String apiSecret) {
        if (apiSecret == null || !apiSecret.startsWith(SECRET_KEY_PREFIX)) {
            return false;
        }
        
        String secretPart = apiSecret.substring(SECRET_KEY_PREFIX.length());
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(secretPart);
            return decoded.length == 48;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}