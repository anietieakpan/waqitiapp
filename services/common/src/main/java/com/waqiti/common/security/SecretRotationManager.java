package com.waqiti.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Secret Rotation Manager for automated secret validation and rotation
 * Integrates with HashiCorp Vault for secure secret management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecretRotationManager {
    
    private final VaultTemplate vaultTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ContextRefresher contextRefresher;
    private final ObjectMapper objectMapper;
    
    @Value("${secret.rotation.enabled:true}")
    private boolean rotationEnabled;
    
    @Value("${secret.rotation.interval-days:30}")
    private int rotationIntervalDays;
    
    @Value("${secret.rotation.warning-days:7}")
    private int warningDays;
    
    @Value("${secret.validation.enabled:true}")
    private boolean validationEnabled;
    
    @Value("${secret.vault.path:secret/data/waqiti}")
    private String vaultPath;
    
    @Value("${secret.encryption.enabled:true}")
    private boolean encryptionEnabled;
    
    private static final String ROTATION_STATE_KEY = "secret:rotation:state";
    private static final String ROTATION_HISTORY_KEY = "secret:rotation:history";
    private static final String SECRET_METADATA_KEY = "secret:metadata";
    
    private final ConcurrentHashMap<String, SecretMetadata> secretCache = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    
    // Secret validation patterns
    private static final Pattern STRONG_PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{16,}$"
    );
    
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
        "^[A-Za-z0-9]{32,}$"
    );
    
    private static final Pattern JWT_SECRET_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+/]{43,}=$"
    );
    
    /**
     * Schedule secret rotation check
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void checkAndRotateSecrets() {
        if (!rotationEnabled) {
            log.debug("Secret rotation is disabled");
            return;
        }
        
        log.info("Starting scheduled secret rotation check");
        
        try {
            List<SecretInfo> secrets = listAllSecrets();
            RotationReport report = new RotationReport();
            report.setStartTime(Instant.now());
            report.setTotalSecrets(secrets.size());
            
            for (SecretInfo secret : secrets) {
                try {
                    RotationResult result = evaluateAndRotateSecret(secret);
                    report.addResult(result);
                    
                    if (result.isRotated()) {
                        log.info("Successfully rotated secret: {}", secret.getName());
                    }
                } catch (Exception e) {
                    log.error("Error rotating secret: {}", secret.getName(), e);
                    report.addFailure(secret.getName(), e.getMessage());
                }
            }
            
            report.setEndTime(Instant.now());
            storeRotationReport(report);
            
            log.info("Secret rotation check completed: {}", report);
            
        } catch (Exception e) {
            log.error("Error during secret rotation check", e);
        }
    }
    
    /**
     * Evaluate and rotate a single secret if needed
     */
    public RotationResult evaluateAndRotateSecret(SecretInfo secret) {
        RotationResult result = new RotationResult();
        result.setSecretName(secret.getName());
        result.setCheckTime(Instant.now());
        
        try {
            // Get secret metadata
            SecretMetadata metadata = getSecretMetadata(secret.getName());
            
            // Check if rotation is needed
            if (shouldRotate(metadata)) {
                log.info("Secret {} needs rotation", secret.getName());
                
                // Validate current secret
                ValidationResult validation = validateSecret(secret);
                if (!validation.isValid()) {
                    log.warn("Current secret {} is invalid: {}", secret.getName(), validation.getIssues());
                }
                
                // Generate new secret
                String newSecret = generateNewSecret(secret.getType());
                
                // Rotate the secret
                rotateSecret(secret, newSecret);
                
                // Update metadata
                metadata.setLastRotated(Instant.now());
                metadata.setRotationCount(metadata.getRotationCount() + 1);
                updateSecretMetadata(metadata);
                
                result.setRotated(true);
                result.setNewSecretGenerated(true);
                
                // Trigger application refresh
                refreshApplicationSecrets();
                
            } else if (shouldWarn(metadata)) {
                log.warn("Secret {} will expire soon", secret.getName());
                result.setWarning(true);
                result.setDaysUntilExpiration(getDaysUntilExpiration(metadata));
            } else {
                result.setRotated(false);
            }
            
        } catch (Exception e) {
            log.error("Error evaluating secret: {}", secret.getName(), e);
            result.setError(true);
            result.setErrorMessage(e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Validate secret strength and compliance
     */
    public ValidationResult validateSecret(SecretInfo secret) {
        ValidationResult result = new ValidationResult();
        result.setSecretName(secret.getName());
        result.setValidatedAt(Instant.now());
        
        String secretValue = getSecretValue(secret.getName());
        
        if (secretValue == null || secretValue.isEmpty()) {
            result.setValid(false);
            result.addIssue("Secret is empty or null");
            return result;
        }
        
        switch (secret.getType()) {
            case PASSWORD:
                result.setValid(validatePassword(secretValue, result));
                break;
                
            case API_KEY:
                result.setValid(validateApiKey(secretValue, result));
                break;
                
            case JWT_SECRET:
                result.setValid(validateJwtSecret(secretValue, result));
                break;
                
            case CLIENT_SECRET:
                result.setValid(validateClientSecret(secretValue, result));
                break;
                
            case ENCRYPTION_KEY:
                result.setValid(validateEncryptionKey(secretValue, result));
                break;
                
            default:
                result.setValid(validateGenericSecret(secretValue, result));
        }
        
        // Check for common weak patterns
        checkWeakPatterns(secretValue, result);
        
        // Check entropy
        double entropy = calculateEntropy(secretValue);
        if (entropy < 4.0) {
            result.addIssue("Low entropy: " + entropy);
            result.setValid(false);
        }
        
        return result;
    }
    
    /**
     * Generate new secret based on type
     */
    public String generateNewSecret(SecretType type) {
        switch (type) {
            case PASSWORD:
                return generateStrongPassword();
                
            case API_KEY:
                return generateApiKey();
                
            case JWT_SECRET:
                return generateJwtSecret();
                
            case CLIENT_SECRET:
                return generateClientSecret();
                
            case ENCRYPTION_KEY:
                return generateEncryptionKey();
                
            default:
                return generateGenericSecret();
        }
    }
    
    /**
     * Rotate a secret in Vault
     */
    private void rotateSecret(SecretInfo secret, String newValue) {
        log.info("Rotating secret: {}", secret.getName());
        
        // Store the old secret for rollback
        String oldValue = getSecretValue(secret.getName());
        storeOldSecret(secret.getName(), oldValue);
        
        // Update secret in Vault
        Map<String, Object> data = new HashMap<>();
        data.put(secret.getName(), encryptionEnabled ? encrypt(newValue) : newValue);
        data.put("rotated_at", Instant.now().toString());
        data.put("rotation_version", UUID.randomUUID().toString());
        
        vaultTemplate.write(vaultPath + "/" + secret.getName(), data);
        
        // Update cache
        secretCache.remove(secret.getName());
        
        // Store rotation event
        storeRotationEvent(secret.getName(), "ROTATED");
        
        log.info("Secret rotated successfully: {}", secret.getName());
    }
    
    /**
     * Generate strong password
     */
    private String generateStrongPassword() {
        String uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowercase = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "@$!%*?&";
        String all = uppercase + lowercase + digits + special;
        
        StringBuilder password = new StringBuilder();
        
        // Ensure at least one of each required character type
        password.append(uppercase.charAt(secureRandom.nextInt(uppercase.length())));
        password.append(lowercase.charAt(secureRandom.nextInt(lowercase.length())));
        password.append(digits.charAt(secureRandom.nextInt(digits.length())));
        password.append(special.charAt(secureRandom.nextInt(special.length())));
        
        // Fill the rest randomly
        for (int i = 4; i < 24; i++) {
            password.append(all.charAt(secureRandom.nextInt(all.length())));
        }
        
        // Shuffle the password
        return shuffleString(password.toString());
    }
    
    /**
     * Generate API key
     */
    private String generateApiKey() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * Generate JWT secret
     */
    private String generateJwtSecret() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA256");
            keyGen.init(256, secureRandom);
            SecretKey secretKey = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            log.error("Error generating JWT secret", e);
            return generateGenericSecret();
        }
    }
    
    /**
     * Generate client secret
     */
    private String generateClientSecret() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * Generate encryption key
     */
    private String generateEncryptionKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, secureRandom);
            SecretKey secretKey = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            log.error("Error generating encryption key", e);
            return generateGenericSecret();
        }
    }
    
    /**
     * Generate generic secret
     */
    private String generateGenericSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * Validate password strength
     */
    private boolean validatePassword(String password, ValidationResult result) {
        if (password.length() < 16) {
            result.addIssue("Password too short (minimum 16 characters)");
            return false;
        }
        
        if (!STRONG_PASSWORD_PATTERN.matcher(password).matches()) {
            result.addIssue("Password does not meet complexity requirements");
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate API key
     */
    private boolean validateApiKey(String apiKey, ValidationResult result) {
        if (apiKey.length() < 32) {
            result.addIssue("API key too short (minimum 32 characters)");
            return false;
        }
        
        if (!API_KEY_PATTERN.matcher(apiKey).matches()) {
            result.addIssue("API key contains invalid characters");
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate JWT secret
     */
    private boolean validateJwtSecret(String secret, ValidationResult result) {
        if (secret.length() < 32) {
            result.addIssue("JWT secret too short (minimum 256 bits)");
            return false;
        }
        
        try {
            Base64.getDecoder().decode(secret);
        } catch (Exception e) {
            result.addIssue("JWT secret is not valid Base64");
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate client secret
     */
    private boolean validateClientSecret(String secret, ValidationResult result) {
        if (secret.length() < 32) {
            result.addIssue("Client secret too short");
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate encryption key
     */
    private boolean validateEncryptionKey(String key, ValidationResult result) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(key);
            if (keyBytes.length != 32 && keyBytes.length != 16) {
                result.addIssue("Invalid encryption key length");
                return false;
            }
        } catch (Exception e) {
            result.addIssue("Encryption key is not valid Base64");
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate generic secret
     */
    private boolean validateGenericSecret(String secret, ValidationResult result) {
        if (secret.length() < 16) {
            result.addIssue("Secret too short");
            return false;
        }
        
        return true;
    }
    
    /**
     * Check for weak patterns
     */
    private void checkWeakPatterns(String secret, ValidationResult result) {
        // Check for common weak patterns
        if (secret.contains("password") || secret.contains("123456") || 
            secret.contains("admin") || secret.contains("secret")) {
            result.addIssue("Contains weak pattern");
            result.setValid(false);
        }
        
        // Check for sequential characters
        if (hasSequentialCharacters(secret, 4)) {
            result.addIssue("Contains sequential characters");
        }
        
        // Check for repeated characters
        if (hasRepeatedCharacters(secret, 4)) {
            result.addIssue("Contains repeated characters");
        }
    }
    
    /**
     * Calculate entropy of a string
     */
    private double calculateEntropy(String str) {
        Map<Character, Integer> freq = new HashMap<>();
        for (char c : str.toCharArray()) {
            freq.put(c, freq.getOrDefault(c, 0) + 1);
        }
        
        double entropy = 0.0;
        int len = str.length();
        
        for (int count : freq.values()) {
            double probability = (double) count / len;
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }
        
        return entropy;
    }
    
    /**
     * Check if secret should be rotated
     */
    private boolean shouldRotate(SecretMetadata metadata) {
        if (metadata.getLastRotated() == null) {
            return true;
        }
        
        Duration age = Duration.between(metadata.getLastRotated(), Instant.now());
        return age.toDays() >= rotationIntervalDays;
    }
    
    /**
     * Check if warning should be issued
     */
    private boolean shouldWarn(SecretMetadata metadata) {
        if (metadata.getLastRotated() == null) {
            return true;
        }
        
        Duration age = Duration.between(metadata.getLastRotated(), Instant.now());
        long daysUntilRotation = rotationIntervalDays - age.toDays();
        
        return daysUntilRotation <= warningDays && daysUntilRotation > 0;
    }
    
    /**
     * Get days until expiration
     */
    private long getDaysUntilExpiration(SecretMetadata metadata) {
        if (metadata.getLastRotated() == null) {
            return 0;
        }
        
        Duration age = Duration.between(metadata.getLastRotated(), Instant.now());
        return Math.max(0, rotationIntervalDays - age.toDays());
    }
    
    /**
     * List all secrets
     */
    private List<SecretInfo> listAllSecrets() {
        List<SecretInfo> secrets = new ArrayList<>();
        
        // Add known secrets
        secrets.add(new SecretInfo("keycloak-admin-password", SecretType.PASSWORD));
        secrets.add(new SecretInfo("database-password", SecretType.PASSWORD));
        secrets.add(new SecretInfo("jwt-secret", SecretType.JWT_SECRET));
        secrets.add(new SecretInfo("encryption-key", SecretType.ENCRYPTION_KEY));
        
        // Add service client secrets
        for (String service : getServiceNames()) {
            secrets.add(new SecretInfo(service + "-client-secret", SecretType.CLIENT_SECRET));
        }
        
        // Add API keys
        secrets.add(new SecretInfo("twilio-api-key", SecretType.API_KEY));
        secrets.add(new SecretInfo("sendgrid-api-key", SecretType.API_KEY));
        secrets.add(new SecretInfo("stripe-api-key", SecretType.API_KEY));
        
        return secrets;
    }
    
    /**
     * Get service names
     */
    private List<String> getServiceNames() {
        return Arrays.asList(
            "user-service", "payment-service", "wallet-service", "transaction-service",
            "notification-service", "analytics-service", "fraud-detection-service",
            "gamification-service", "nfc-testing-service",
            "ar-payment-service", "voice-payment-service", "predictive-scaling-service"
        );
    }
    
    /**
     * Get secret value from Vault
     */
    private String getSecretValue(String secretName) {
        try {
            VaultResponse response = vaultTemplate.read(vaultPath + "/" + secretName);
            if (response != null && response.getData() != null) {
                String value = (String) response.getData().get(secretName);
                return encryptionEnabled ? decrypt(value) : value;
            }
        } catch (Exception e) {
            log.error("Error getting secret value: {}", secretName, e);
        }
        return null;
    }
    
    /**
     * Get secret metadata
     */
    private SecretMetadata getSecretMetadata(String secretName) {
        SecretMetadata metadata = secretCache.get(secretName);
        if (metadata == null) {
            metadata = loadSecretMetadata(secretName);
            secretCache.put(secretName, metadata);
        }
        return metadata;
    }
    
    /**
     * Load secret metadata from storage
     */
    private SecretMetadata loadSecretMetadata(String secretName) {
        String key = SECRET_METADATA_KEY + ":" + secretName;
        Object data = redisTemplate.opsForValue().get(key);
        
        if (data instanceof SecretMetadata) {
            return (SecretMetadata) data;
        }
        
        // Create new metadata if not exists
        SecretMetadata metadata = new SecretMetadata();
        metadata.setSecretName(secretName);
        metadata.setCreatedAt(Instant.now());
        metadata.setRotationCount(0);
        
        return metadata;
    }
    
    /**
     * Update secret metadata
     */
    private void updateSecretMetadata(SecretMetadata metadata) {
        String key = SECRET_METADATA_KEY + ":" + metadata.getSecretName();
        redisTemplate.opsForValue().set(key, metadata);
        secretCache.put(metadata.getSecretName(), metadata);
    }
    
    /**
     * Store old secret for rollback
     */
    private void storeOldSecret(String secretName, String oldValue) {
        String key = "secret:backup:" + secretName + ":" + System.currentTimeMillis();
        Map<String, Object> backup = new HashMap<>();
        backup.put("value", oldValue);
        backup.put("backed_up_at", Instant.now().toString());
        
        redisTemplate.opsForValue().set(key, backup, Duration.ofDays(7));
    }
    
    /**
     * Store rotation event
     */
    private void storeRotationEvent(String secretName, String action) {
        String key = ROTATION_HISTORY_KEY + ":" + System.currentTimeMillis();
        Map<String, Object> event = new HashMap<>();
        event.put("secret_name", secretName);
        event.put("action", action);
        event.put("timestamp", Instant.now().toString());
        
        redisTemplate.opsForValue().set(key, event, Duration.ofDays(90));
    }
    
    /**
     * Store rotation report
     */
    private void storeRotationReport(RotationReport report) {
        String key = "secret:rotation:report:" + System.currentTimeMillis();
        redisTemplate.opsForValue().set(key, report, Duration.ofDays(30));
    }
    
    /**
     * Refresh application secrets
     */
    private void refreshApplicationSecrets() {
        try {
            contextRefresher.refresh();
            log.info("Application secrets refreshed");
        } catch (Exception e) {
            log.error("Error refreshing application secrets", e);
        }
    }
    
    /**
     * Encrypt sensitive data
     */
    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            
            SecretKeySpec keySpec = new SecretKeySpec(getEncryptionKey(), "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);
            byte[] cipherText = cipher.doFinal(plaintext.getBytes());
            
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Encryption error", e);
            return plaintext;
        }
    }
    
    /**
     * Decrypt sensitive data
     */
    private String decrypt(String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            
            byte[] iv = new byte[12];
            byte[] cipherText = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, cipherText, 0, cipherText.length);
            
            SecretKeySpec keySpec = new SecretKeySpec(getEncryptionKey(), "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
            byte[] plainText = cipher.doFinal(cipherText);
            
            return new String(plainText);
        } catch (Exception e) {
            log.error("Decryption error", e);
            return ciphertext;
        }
    }
    
    /**
     * Get encryption key
     */
    private byte[] getEncryptionKey() {
        String key = System.getenv("MASTER_ENCRYPTION_KEY");
        if (key == null) {
            // CRITICAL: Never use a default key in production
            // Fail securely if encryption key is not configured
            log.error("MASTER_ENCRYPTION_KEY environment variable not set. Encryption operations will fail.");
            throw new SecurityException("Encryption key not configured. Set MASTER_ENCRYPTION_KEY environment variable.");
        }
        
        // Validate key length (AES-256 requires 32 bytes)
        if (key.getBytes().length < 32) {
            throw new SecurityException("Encryption key must be at least 32 bytes for AES-256");
        }
        
        return key.getBytes();
    }
    
    /**
     * Shuffle string characters
     */
    private String shuffleString(String str) {
        List<Character> chars = new ArrayList<>();
        for (char c : str.toCharArray()) {
            chars.add(c);
        }
        Collections.shuffle(chars, secureRandom);
        
        StringBuilder result = new StringBuilder();
        for (char c : chars) {
            result.append(c);
        }
        return result.toString();
    }
    
    /**
     * Check for sequential characters
     */
    private boolean hasSequentialCharacters(String str, int threshold) {
        int count = 1;
        for (int i = 1; i < str.length(); i++) {
            if (str.charAt(i) == str.charAt(i - 1) + 1) {
                count++;
                if (count >= threshold) {
                    return true;
                }
            } else {
                count = 1;
            }
        }
        return false;
    }
    
    /**
     * Check for repeated characters
     */
    private boolean hasRepeatedCharacters(String str, int threshold) {
        int count = 1;
        for (int i = 1; i < str.length(); i++) {
            if (str.charAt(i) == str.charAt(i - 1)) {
                count++;
                if (count >= threshold) {
                    return true;
                }
            } else {
                count = 1;
            }
        }
        return false;
    }
    
    // Data classes
    
    @Data
    public static class SecretInfo {
        private String name;
        private SecretType type;
        private String path;
        
        public SecretInfo(String name, SecretType type) {
            this.name = name;
            this.type = type;
        }
    }
    
    public enum SecretType {
        PASSWORD,
        API_KEY,
        JWT_SECRET,
        CLIENT_SECRET,
        ENCRYPTION_KEY,
        CERTIFICATE,
        GENERIC
    }
    
    @Data
    public static class SecretMetadata {
        private String secretName;
        private Instant createdAt;
        private Instant lastRotated;
        private Instant lastValidated;
        private int rotationCount;
        private Map<String, Object> attributes;
    }
    
    @Data
    public static class RotationResult {
        private String secretName;
        private boolean rotated;
        private boolean warning;
        private boolean error;
        private String errorMessage;
        private Instant checkTime;
        private boolean newSecretGenerated;
        private long daysUntilExpiration;
    }
    
    @Data
    public static class ValidationResult {
        private String secretName;
        private boolean valid;
        private List<String> issues = new ArrayList<>();
        private Instant validatedAt;
        
        public void addIssue(String issue) {
            issues.add(issue);
        }
    }
    
    @Data
    public static class RotationReport {
        private Instant startTime;
        private Instant endTime;
        private int totalSecrets;
        private int rotatedCount;
        private int warningCount;
        private int failureCount;
        private List<RotationResult> results = new ArrayList<>();
        private Map<String, String> failures = new HashMap<>();
        
        public void addResult(RotationResult result) {
            results.add(result);
            if (result.isRotated()) {
                rotatedCount++;
            }
            if (result.isWarning()) {
                warningCount++;
            }
            if (result.isError()) {
                failureCount++;
            }
        }
        
        public void addFailure(String secretName, String error) {
            failures.put(secretName, error);
            failureCount++;
        }
    }
}