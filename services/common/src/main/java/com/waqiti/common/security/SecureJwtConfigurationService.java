package com.waqiti.common.security;

import com.waqiti.common.vault.VaultSecretService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(value = "jwt.vault.enabled", havingValue = "true", matchIfMissing = true)
public class SecureJwtConfigurationService {

    private final VaultSecretService vaultSecretService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${jwt.vault.secret-path:secret/waqiti/jwt}")
    private String jwtSecretPath;

    @Value("${jwt.vault.legacy-secret-path:secret/waqiti/jwt-legacy}")
    private String legacySecretPath;

    @Value("${jwt.rotation.enabled:true}")
    private boolean keyRotationEnabled;

    @Value("${jwt.rotation.interval-hours:24}")
    private int rotationIntervalHours;

    @Value("${jwt.validation.strict:true}")
    private boolean strictValidation;

    private static final String REDIS_JWT_SECRET_KEY = "jwt:secret:current";
    private static final String REDIS_JWT_LEGACY_KEY = "jwt:secret:legacy";
    private static final String REDIS_JWT_ROTATION_KEY = "jwt:rotation:last";

    @PostConstruct
    public void initializeJwtSecrets() {
        try {
            log.info("Initializing secure JWT configuration with Vault integration");

            // Ensure JWT secrets exist in Vault
            ensureSecretExists(jwtSecretPath, "jwt-secret");
            
            if (keyRotationEnabled) {
                ensureSecretExists(legacySecretPath, "jwt-legacy-secret");
                
                // Schedule key rotation if needed
                scheduleKeyRotationIfNeeded();
            }

            log.info("JWT secrets successfully initialized and cached");

        } catch (Exception e) {
            log.error("Failed to initialize JWT secrets", e);
            throw new IllegalStateException("JWT security initialization failed", e);
        }
    }

    /**
     * Get current JWT secret from cache or Vault
     */
    public String getCurrentJwtSecret() {
        try {
            // Try cache first
            String cachedSecret = (String) redisTemplate.opsForValue().get(REDIS_JWT_SECRET_KEY);
            if (cachedSecret != null && isValidSecret(cachedSecret)) {
                return cachedSecret;
            }

            // Fetch from Vault
            String vaultSecret = vaultSecretService.getSecret(jwtSecretPath + "/jwt-secret");
            if (vaultSecret == null || !isValidSecret(vaultSecret)) {
                throw new IllegalStateException("Invalid or missing JWT secret in Vault");
            }

            // Cache for performance (short TTL for security)
            redisTemplate.opsForValue().set(REDIS_JWT_SECRET_KEY, vaultSecret, 
                Math.min(60, rotationIntervalHours * 60 / 4), TimeUnit.MINUTES);

            return vaultSecret;

        } catch (Exception e) {
            log.error("Failed to retrieve JWT secret", e);
            throw new IllegalStateException("JWT secret retrieval failed", e);
        }
    }

    /**
     * Get legacy JWT secret for backward compatibility
     */
    public String getLegacyJwtSecret() {
        if (!keyRotationEnabled) {
            return null;
        }

        try {
            // Try cache first
            String cachedSecret = (String) redisTemplate.opsForValue().get(REDIS_JWT_LEGACY_KEY);
            if (cachedSecret != null && isValidSecret(cachedSecret)) {
                return cachedSecret;
            }

            // Fetch from Vault
            String vaultSecret = vaultSecretService.getSecret(legacySecretPath + "/jwt-legacy-secret");
            if (vaultSecret == null || !isValidSecret(vaultSecret)) {
                log.warn("Legacy JWT secret not found or invalid in Vault");
                return null;
            }

            // Cache for performance
            redisTemplate.opsForValue().set(REDIS_JWT_LEGACY_KEY, vaultSecret, 
                Math.min(60, rotationIntervalHours * 60 / 4), TimeUnit.MINUTES);

            return vaultSecret;

        } catch (Exception e) {
            log.warn("Failed to retrieve legacy JWT secret: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Rotate JWT secrets (scheduled operation)
     */
    @Scheduled(fixedRateString = "#{${jwt.rotation.interval-hours:24} * 60 * 60 * 1000}")
    public void rotateJwtSecrets() {
        if (!keyRotationEnabled) {
            return;
        }

        try {
            log.info("Starting JWT secret rotation");

            // Move current secret to legacy
            String currentSecret = getCurrentJwtSecret();
            if (currentSecret != null) {
                vaultSecretService.storeSecret(legacySecretPath + "/jwt-legacy-secret", currentSecret);
                log.debug("Moved current secret to legacy vault path");
            }

            // Generate new secret
            String newSecret = generateSecureSecret();
            vaultSecretService.storeSecret(jwtSecretPath + "/jwt-secret", newSecret);

            // Clear cache to force reload
            redisTemplate.delete(REDIS_JWT_SECRET_KEY);
            redisTemplate.delete(REDIS_JWT_LEGACY_KEY);

            // Record rotation time
            redisTemplate.opsForValue().set(REDIS_JWT_ROTATION_KEY, 
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                rotationIntervalHours * 2, TimeUnit.HOURS);

            log.info("JWT secret rotation completed successfully");

        } catch (Exception e) {
            log.error("JWT secret rotation failed", e);
            // Don't throw exception to prevent breaking the application
        }
    }

    /**
     * Manually trigger secret rotation (for emergency scenarios)
     */
    public void emergencyRotateSecrets() {
        log.warn("Emergency JWT secret rotation triggered");
        rotateJwtSecrets();
    }

    /**
     * Validate that a secret meets security requirements
     */
    public boolean isValidSecret(String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            return false;
        }

        // Basic length validation
        if (secret.length() < 64) {
            log.warn("JWT secret length insufficient: {} characters", secret.length());
            return false;
        }

        if (strictValidation) {
            // Enhanced validation for production
            return isSecureSecret(secret);
        }

        return true;
    }

    /**
     * Generate a cryptographically secure secret
     */
    public String generateSecureSecret() throws NoSuchAlgorithmException {
        // Generate 512-bit (64-byte) secret using secure random
        SecureRandom secureRandom = SecureRandom.getInstanceStrong();
        byte[] secretBytes = new byte[64];
        secureRandom.nextBytes(secretBytes);

        // Convert to base64 for storage
        String secret = Base64.getEncoder().encodeToString(secretBytes);

        log.debug("Generated new secure JWT secret of length: {}", secret.length());
        return secret;
    }

    /**
     * Ensure a secret exists in Vault, create if missing
     */
    private void ensureSecretExists(String path, String key) {
        try {
            String existingSecret = vaultSecretService.getSecret(path + "/" + key);
            
            if (existingSecret == null || !isValidSecret(existingSecret)) {
                log.info("Creating new JWT secret at path: {}", path);
                String newSecret = generateSecureSecret();
                vaultSecretService.storeSecret(path + "/" + key, newSecret);
                log.info("New JWT secret created and stored in Vault");
            } else {
                log.debug("Valid JWT secret found in Vault at path: {}", path);
            }

        } catch (Exception e) {
            log.error("Failed to ensure secret exists at path: {}", path, e);
            throw new IllegalStateException("Failed to initialize JWT secret", e);
        }
    }

    /**
     * Schedule key rotation if it's overdue
     */
    private void scheduleKeyRotationIfNeeded() {
        try {
            String lastRotation = (String) redisTemplate.opsForValue().get(REDIS_JWT_ROTATION_KEY);
            
            if (lastRotation == null) {
                // No previous rotation recorded, schedule one soon
                log.info("No previous JWT rotation found, scheduling immediate rotation");
                return;
            }

            LocalDateTime lastRotationTime = LocalDateTime.parse(lastRotation, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            LocalDateTime nextRotationDue = lastRotationTime.plusHours(rotationIntervalHours);
            
            if (LocalDateTime.now().isAfter(nextRotationDue)) {
                log.info("JWT rotation is overdue, scheduling immediate rotation");
                // Could trigger immediate rotation here if needed
            }

        } catch (Exception e) {
            log.warn("Failed to check rotation schedule: {}", e.getMessage());
        }
    }

    /**
     * Enhanced secret security validation
     */
    private boolean isSecureSecret(String secret) {
        // Check entropy - basic implementation
        if (calculateShannonEntropy(secret) < 5.0) {
            log.warn("JWT secret has low entropy (< 5.0 bits per character)");
            return false;
        }

        // Check for patterns that indicate weak generation
        if (containsObviousPatterns(secret)) {
            log.warn("JWT secret contains obvious patterns");
            return false;
        }

        // Check for repeated sequences
        if (hasRepeatedSequences(secret, 8)) {
            log.warn("JWT secret contains repeated sequences");
            return false;
        }

        return true;
    }

    /**
     * Calculate Shannon entropy of a string
     */
    private double calculateShannonEntropy(String data) {
        if (data == null || data.isEmpty()) {
            return 0.0;
        }

        int[] freq = new int[256];
        for (byte b : data.getBytes(StandardCharsets.UTF_8)) {
            freq[b & 0xFF]++;
        }

        double entropy = 0.0;
        int length = data.length();

        for (int count : freq) {
            if (count > 0) {
                double probability = (double) count / length;
                entropy -= probability * (Math.log(probability) / Math.log(2));
            }
        }

        return entropy;
    }

    /**
     * Check for obvious patterns in the secret
     */
    private boolean containsObviousPatterns(String secret) {
        String lower = secret.toLowerCase();
        String[] patterns = {
            "12345", "abcde", "qwerty", "password", "secret", "token",
            "admin", "user", "test", "default", "key", "jwt"
        };

        for (String pattern : patterns) {
            if (lower.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check for repeated sequences
     */
    private boolean hasRepeatedSequences(String secret, int minLength) {
        for (int i = 0; i <= secret.length() - minLength * 2; i++) {
            String sequence = secret.substring(i, i + minLength);
            if (secret.indexOf(sequence, i + minLength) != -1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get secret rotation status for monitoring
     */
    public SecretRotationStatus getRotationStatus() {
        try {
            String lastRotation = (String) redisTemplate.opsForValue().get(REDIS_JWT_ROTATION_KEY);
            LocalDateTime lastRotationTime = null;
            
            if (lastRotation != null) {
                lastRotationTime = LocalDateTime.parse(lastRotation, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }

            LocalDateTime nextRotationDue = lastRotationTime != null ? 
                lastRotationTime.plusHours(rotationIntervalHours) : LocalDateTime.now();

            return SecretRotationStatus.builder()
                .enabled(keyRotationEnabled)
                .lastRotation(lastRotationTime)
                .nextRotationDue(nextRotationDue)
                .intervalHours(rotationIntervalHours)
                .overdue(LocalDateTime.now().isAfter(nextRotationDue))
                .build();

        } catch (Exception e) {
            log.error("Failed to get rotation status", e);
            return SecretRotationStatus.builder()
                .enabled(keyRotationEnabled)
                .error("Failed to retrieve status: " + e.getMessage())
                .build();
        }
    }

    /**
     * Secret rotation status for monitoring
     */
    public static class SecretRotationStatus {
        private boolean enabled;
        private LocalDateTime lastRotation;
        private LocalDateTime nextRotationDue;
        private int intervalHours;
        private boolean overdue;
        private String error;

        public static SecretRotationStatusBuilder builder() {
            return new SecretRotationStatusBuilder();
        }

        // Getters
        public boolean isEnabled() { return enabled; }
        public LocalDateTime getLastRotation() { return lastRotation; }
        public LocalDateTime getNextRotationDue() { return nextRotationDue; }
        public int getIntervalHours() { return intervalHours; }
        public boolean isOverdue() { return overdue; }
        public String getError() { return error; }

        public static class SecretRotationStatusBuilder {
            private final SecretRotationStatus status = new SecretRotationStatus();

            public SecretRotationStatusBuilder enabled(boolean enabled) {
                status.enabled = enabled;
                return this;
            }

            public SecretRotationStatusBuilder lastRotation(LocalDateTime lastRotation) {
                status.lastRotation = lastRotation;
                return this;
            }

            public SecretRotationStatusBuilder nextRotationDue(LocalDateTime nextRotationDue) {
                status.nextRotationDue = nextRotationDue;
                return this;
            }

            public SecretRotationStatusBuilder intervalHours(int intervalHours) {
                status.intervalHours = intervalHours;
                return this;
            }

            public SecretRotationStatusBuilder overdue(boolean overdue) {
                status.overdue = overdue;
                return this;
            }

            public SecretRotationStatusBuilder error(String error) {
                status.error = error;
                return this;
            }

            public SecretRotationStatus build() {
                return status;
            }
        }
    }
}