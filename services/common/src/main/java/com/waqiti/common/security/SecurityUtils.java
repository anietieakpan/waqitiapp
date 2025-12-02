package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Comprehensive security utilities for preventing common vulnerabilities
 * Implements OWASP best practices for secure coding
 */
@Slf4j
public class SecurityUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);
    
    // Patterns for SQL injection prevention
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "('.+--)|(--.*)|(\\|\\|)|(\\*/)|(/\\*)|" +
        "(\\*/)|(xp_)|(sp_)|" +
        "(exec(\\s|\\+)+(s|x)p\\w+)|" +
        "(union(.*?)select)|(insert(.*?)into)|(delete(.*?)from)|" +
        "(drop(.*?)table)|(update(.*?)set)",
        Pattern.CASE_INSENSITIVE
    );
    
    // XSS prevention patterns
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(<script[^>]*>.*?</script>)|" +
        "(<iframe[^>]*>.*?</iframe>)|" +
        "(javascript:)|" +
        "(on\\w+\\s*=)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    // Path traversal prevention
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
        "(\\.\\./)|(\\.\\\\)|(%2e%2e)|(%252e%252e)"
    );

    /**
     * Sanitizes user input to prevent SQL injection
     */
    public static String sanitizeSQLInput(String input) {
        if (StringUtils.isBlank(input)) {
            return "";
        }
        
        // Check for SQL injection patterns
        if (SQL_INJECTION_PATTERN.matcher(input).find()) {
            log.warn("Potential SQL injection attempt detected: {}", input);
            throw new SecurityException("Invalid input detected");
        }
        
        // Escape special characters
        return input.replace("'", "''")
                   .replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\0", "")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\032", "\\Z");
    }

    /**
     * Prevents XSS attacks by sanitizing HTML content
     */
    public static String sanitizeXSS(String input) {
        if (StringUtils.isBlank(input)) {
            return "";
        }
        
        // Check for XSS patterns
        if (XSS_PATTERN.matcher(input).find()) {
            log.warn("Potential XSS attempt detected");
            input = XSS_PATTERN.matcher(input).replaceAll("");
        }
        
        // HTML entity encoding
        return input.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;")
                   .replace("/", "&#x2F;");
    }

    /**
     * Validates file paths to prevent directory traversal attacks
     */
    public static String sanitizePath(String path) {
        if (StringUtils.isBlank(path)) {
            return "";
        }
        
        // Check for path traversal patterns
        if (PATH_TRAVERSAL_PATTERN.matcher(path).find()) {
            log.warn("Potential path traversal attempt detected: {}", path);
            throw new SecurityException("Invalid path detected");
        }
        
        // Remove dangerous characters
        return path.replaceAll("[^a-zA-Z0-9._/-]", "");
    }

    /**
     * Generates a secure random token
     */
    public static String generateSecureToken(int length) {
        byte[] randomBytes = new byte[length];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Generates a secure OTP
     */
    public static String generateOTP(int length) {
        StringBuilder otp = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            otp.append(SECURE_RANDOM.nextInt(10));
        }
        return otp.toString();
    }

    /**
     * Hashes a password securely
     */
    public static String hashPassword(String password) {
        validatePasswordStrength(password);
        return PASSWORD_ENCODER.encode(password);
    }

    /**
     * Verifies a password against its hash
     */
    public static boolean verifyPassword(String password, String hash) {
        return PASSWORD_ENCODER.matches(password, hash);
    }

    /**
     * Validates password strength
     */
    public static void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }
        
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (!Character.isLetterOrDigit(c)) hasSpecial = true;
        }
        
        if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
            throw new IllegalArgumentException(
                "Password must contain uppercase, lowercase, digit, and special character"
            );
        }
    }

    /**
     * Encrypts sensitive data using AES-GCM
     */
    public static String encryptAES(String plaintext, String key) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(key);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[12];
        SECURE_RANDOM.nextBytes(iv);
        
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
        
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        
        byte[] encryptedData = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, encryptedData, 0, iv.length);
        System.arraycopy(ciphertext, 0, encryptedData, iv.length, ciphertext.length);
        
        return Base64.getEncoder().encodeToString(encryptedData);
    }

    /**
     * Decrypts AES-GCM encrypted data
     */
    public static String decryptAES(String encryptedData, String key) throws Exception {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
        byte[] keyBytes = Base64.getDecoder().decode(key);
        
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        
        byte[] iv = new byte[12];
        byte[] ciphertext = new byte[encryptedBytes.length - 12];
        System.arraycopy(encryptedBytes, 0, iv, 0, iv.length);
        System.arraycopy(encryptedBytes, iv.length, ciphertext, 0, ciphertext.length);
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
        
        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * Generates a secure AES key
     */
    public static String generateAESKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, SECURE_RANDOM);
        SecretKey secretKey = keyGen.generateKey();
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }

    /**
     * Creates a secure hash of data using SHA-256
     */
    public static String hashSHA256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash data", e);
        }
    }

    /**
     * Validates JWT token format (basic validation)
     */
    public static boolean isValidJWTFormat(String token) {
        if (StringUtils.isBlank(token)) {
            return false;
        }
        
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }
        
        try {
            Base64.getUrlDecoder().decode(parts[0]);
            Base64.getUrlDecoder().decode(parts[1]);
            Base64.getUrlDecoder().decode(parts[2]);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Gets the current authenticated user
     */
    public static Optional<String> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() 
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.of(authentication.getName());
        }
        return Optional.empty();
    }

    /**
     * Masks sensitive data for logging
     */
    public static String maskSensitiveData(String data, int visibleChars) {
        if (StringUtils.isBlank(data) || data.length() <= visibleChars * 2) {
            return "****";
        }
        
        int prefixLength = Math.min(visibleChars, data.length() / 3);
        int suffixLength = Math.min(visibleChars, data.length() / 3);
        
        String prefix = data.substring(0, prefixLength);
        String suffix = data.substring(data.length() - suffixLength);
        String masked = "*".repeat(Math.max(4, data.length() - prefixLength - suffixLength));
        
        return prefix + masked + suffix;
    }

    /**
     * Validates email format
     */
    public static boolean isValidEmail(String email) {
        if (StringUtils.isBlank(email)) {
            return false;
        }
        
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }

    /**
     * Validates phone number format
     */
    public static boolean isValidPhoneNumber(String phone) {
        if (StringUtils.isBlank(phone)) {
            return false;
        }
        
        String phoneRegex = "^\\+?[1-9]\\d{1,14}$";
        return phone.replaceAll("[\\s()-]", "").matches(phoneRegex);
    }

    /**
     * Generates a secure session ID
     */
    public static String generateSessionId() {
        return UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
    }

    /**
     * Validates IP address format
     */
    public static boolean isValidIPAddress(String ip) {
        if (StringUtils.isBlank(ip)) {
            return false;
        }
        
        // IPv4 pattern
        String ipv4Pattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
                            "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        
        // IPv6 pattern (simplified)
        String ipv6Pattern = "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|" +
                            "([0-9a-fA-F]{1,4}:){1,7}:|" +
                            "::([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4})$";
        
        return ip.matches(ipv4Pattern) || ip.matches(ipv6Pattern);
    }

    /**
     * Rate limiting check (should be used with Redis/cache)
     */
    public static class RateLimiter {
        private final Map<String, List<Long>> requestTimes = new HashMap<>();
        private final int maxRequests;
        private final long timeWindowMs;
        
        public RateLimiter(int maxRequests, long timeWindowMs) {
            this.maxRequests = maxRequests;
            this.timeWindowMs = timeWindowMs;
        }
        
        public synchronized boolean allowRequest(String key) {
            long now = System.currentTimeMillis();
            List<Long> times = requestTimes.computeIfAbsent(key, k -> new ArrayList<>());
            
            // Remove old entries
            times.removeIf(time -> now - time > timeWindowMs);
            
            if (times.size() >= maxRequests) {
                return false;
            }
            
            times.add(now);
            return true;
        }
        
        public synchronized void reset(String key) {
            requestTimes.remove(key);
        }
    }
}