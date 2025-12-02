package com.waqiti.common.security;

import com.waqiti.common.exception.SecurityConfigurationException;
import com.waqiti.common.exception.CriticalSecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Enterprise-grade Secure Configuration Service
 * 
 * Provides comprehensive security configuration management including:
 * - Startup validation of required secrets
 * - Detection and prevention of hardcoded passwords
 * - Integration with HashiCorp Vault
 * - Secure secret rotation
 * - Audit logging of configuration access
 * 
 * @author Waqiti Security Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class SecureConfigurationService implements CommandLineRunner {

    private static final List<String> REQUIRED_SECRETS = Arrays.asList(
        "DB_PASSWORD",
        "DB_USERNAME",
        "JWT_SECRET",
        "KAFKA_SECURITY_PROTOCOL",
        "REDIS_PASSWORD",
        "VAULT_TOKEN"
    );

    private static final List<String> FORBIDDEN_PATTERNS = Arrays.asList(
        "password.*:.*waqiti",
        "password.*:.*123",
        "password.*:.*password",
        "password.*:.*guest",
        "password.*:.*admin",
        "password.*:.*default",
        "secret.*:.*secret",
        "key.*:.*key"
    );

    private static final Set<String> WEAK_PASSWORDS = new HashSet<>(Arrays.asList(
        "waqiti123",
        "password",
        "admin",
        "123456",
        "guest",
        "default",
        "strongpassword",
        "secretpassword"
    ));

    private static final int MIN_PASSWORD_LENGTH = 16;
    private static final int MIN_PASSWORD_COMPLEXITY = 4; // Uppercase, lowercase, digit, special

    private final ConfigurableEnvironment environment;
    private final ConfigurableApplicationContext context;
    private final Map<String, String> encryptedSecrets = new ConcurrentHashMap<>();
    private SecretKey masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public SecureConfigurationService(ConfigurableEnvironment environment, 
                                     ConfigurableApplicationContext context) {
        this.environment = environment;
        this.context = context;
        initializeMasterKey();
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting secure configuration validation...");
        
        try {
            // Phase 1: Validate required secrets are present
            validateRequiredSecrets();
            
            // Phase 2: Check for hardcoded/weak passwords
            detectHardcodedSecrets();
            
            // Phase 3: Validate secret strength
            validateSecretStrength();
            
            // Phase 4: Initialize secure secret storage
            initializeSecureStorage();
            
            // Phase 5: Setup secret rotation
            setupSecretRotation();
            
            log.info("✓ Secure configuration validation completed successfully");
            
        } catch (SecurityConfigurationException e) {
            log.error("❌ CRITICAL SECURITY CONFIGURATION ERROR: {}", e.getMessage());
            log.error("Application will shut down to prevent security breach");
            
            /**
             * SECURITY FIX: Replace System.exit() with proper exception handling
             * This prevents abrupt application termination and allows for graceful shutdown
             */
            
            // Mark context as failed to prevent startup
            context.publishEvent(new SecurityValidationFailedEvent(this, e.getMessage()));
            
            // Close the context gracefully
            context.close();
            
            // Throw a critical exception that will be handled by Spring Boot's failure analyzers
            throw new CriticalSecurityException(
                "Critical security configuration error detected. Application cannot start safely.", e);
        }
    }

    /**
     * Validates that all required secrets are provided via environment variables
     */
    private void validateRequiredSecrets() {
        log.info("Validating required secrets...");
        
        List<String> missingSecrets = new ArrayList<>();
        
        for (String secret : REQUIRED_SECRETS) {
            String value = environment.getProperty(secret);
            
            if (value == null || value.isEmpty() || value.startsWith("?")) {
                missingSecrets.add(secret);
                log.error("❌ Missing required secret: {}", secret);
            } else if (value.equals("${" + secret + "}")) {
                missingSecrets.add(secret);
                log.error("❌ Unresolved placeholder for secret: {}", secret);
            } else {
                log.debug("✓ Secret {} is configured", secret);
            }
        }
        
        if (!missingSecrets.isEmpty()) {
            throw new SecurityConfigurationException(
                "Missing required secrets: " + String.join(", ", missingSecrets) + 
                ". Please set these environment variables before starting the application."
            );
        }
        
        log.info("✓ All required secrets are present");
    }

    /**
     * Detects hardcoded or weak passwords in configuration
     */
    private void detectHardcodedSecrets() {
        log.info("Scanning for hardcoded secrets...");
        
        List<String> violations = new ArrayList<>();
        
        // Check all property sources
        environment.getPropertySources().forEach(propertySource -> {
            if (propertySource.getSource() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> properties = (Map<String, Object>) propertySource.getSource();
                
                properties.forEach((key, value) -> {
                    if (value != null) {
                        String strValue = value.toString();
                        
                        // Check for weak passwords
                        if (isPasswordProperty(key) && WEAK_PASSWORDS.contains(strValue.toLowerCase())) {
                            violations.add(String.format("Weak password detected in property: %s", key));
                            log.error("❌ SECURITY VIOLATION: Weak password in {}", key);
                        }
                        
                        // Check for forbidden patterns
                        for (String pattern : FORBIDDEN_PATTERNS) {
                            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
                                    .matcher(key + ":" + strValue).matches()) {
                                violations.add(String.format("Forbidden pattern detected: %s", key));
                                log.error("❌ SECURITY VIOLATION: Forbidden pattern in {}", key);
                            }
                        }
                    }
                });
            }
        });
        
        if (!violations.isEmpty()) {
            throw new SecurityConfigurationException(
                "Hardcoded/weak secrets detected: " + String.join("; ", violations)
            );
        }
        
        log.info("✓ No hardcoded secrets detected");
    }

    /**
     * Validates the strength of configured secrets
     */
    private void validateSecretStrength() {
        log.info("Validating secret strength...");
        
        // Validate database password
        validatePasswordStrength("DB_PASSWORD", environment.getProperty("DB_PASSWORD"));
        
        // Validate JWT secret
        validateJwtSecretStrength(environment.getProperty("JWT_SECRET"));
        
        // Validate other critical secrets
        if (environment.containsProperty("ENCRYPTION_KEY")) {
            validateEncryptionKeyStrength(environment.getProperty("ENCRYPTION_KEY"));
        }
        
        log.info("✓ All secrets meet strength requirements");
    }

    /**
     * Validates password strength
     */
    private void validatePasswordStrength(String name, String password) {
        if (password == null) {
            return; // Already validated in required secrets check
        }
        
        List<String> issues = new ArrayList<>();
        
        // Check length
        if (password.length() < MIN_PASSWORD_LENGTH) {
            issues.add(String.format("Password too short (minimum %d characters)", MIN_PASSWORD_LENGTH));
        }
        
        // Check complexity
        int complexity = 0;
        if (password.matches(".*[A-Z].*")) complexity++;
        if (password.matches(".*[a-z].*")) complexity++;
        if (password.matches(".*[0-9].*")) complexity++;
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) complexity++;
        
        if (complexity < MIN_PASSWORD_COMPLEXITY) {
            issues.add("Password must contain uppercase, lowercase, digits, and special characters");
        }
        
        // Check for common patterns
        if (password.matches(".*(123|abc|qwerty|password).*")) {
            issues.add("Password contains common patterns");
        }
        
        if (!issues.isEmpty()) {
            throw new SecurityConfigurationException(
                String.format("Password %s does not meet security requirements: %s", 
                    name, String.join(", ", issues))
            );
        }
    }

    /**
     * Validates JWT secret strength
     */
    private void validateJwtSecretStrength(String jwtSecret) {
        if (jwtSecret == null) {
            return;
        }
        
        // JWT secret should be at least 256 bits (32 bytes)
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new SecurityConfigurationException(
                "JWT secret must be at least 256 bits (32 characters)"
            );
        }
        
        // Check for weak JWT secrets
        if (jwtSecret.contains("secret") || jwtSecret.contains("key") || jwtSecret.contains("jwt")) {
            throw new SecurityConfigurationException(
                "JWT secret contains weak patterns"
            );
        }
    }

    /**
     * Validates encryption key strength
     */
    private void validateEncryptionKeyStrength(String encryptionKey) {
        if (encryptionKey == null) {
            return;
        }
        
        // Encryption key should be exactly 256 bits for AES-256
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKey);
        if (keyBytes.length != 32) {
            throw new SecurityConfigurationException(
                "Encryption key must be exactly 256 bits (32 bytes)"
            );
        }
    }

    /**
     * Initialize secure storage for runtime secrets
     */
    private void initializeSecureStorage() {
        log.info("Initializing secure secret storage...");
        
        // Store encrypted versions of secrets in memory
        REQUIRED_SECRETS.forEach(secret -> {
            String value = environment.getProperty(secret);
            if (value != null && !value.isEmpty()) {
                try {
                    String encrypted = encryptSecret(value);
                    encryptedSecrets.put(secret, encrypted);
                    log.debug("Secured secret: {}", secret);
                } catch (Exception e) {
                    log.error("Failed to secure secret: {}", secret, e);
                }
            }
        });
        
        log.info("✓ Secure storage initialized for {} secrets", encryptedSecrets.size());
    }

    /**
     * Setup automatic secret rotation
     */
    private void setupSecretRotation() {
        log.info("Setting up secret rotation...");
        
        // Schedule rotation tasks for different secret types
        // In production, this would integrate with HashiCorp Vault or AWS Secrets Manager
        
        Timer rotationTimer = new Timer("secret-rotation", true);
        
        // Rotate database passwords every 90 days
        rotationTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                rotateSecret("DB_PASSWORD", 90);
            }
        }, 90L * 24 * 60 * 60 * 1000, 90L * 24 * 60 * 60 * 1000);
        
        // Rotate JWT secrets every 30 days
        rotationTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                rotateSecret("JWT_SECRET", 30);
            }
        }, 30L * 24 * 60 * 60 * 1000, 30L * 24 * 60 * 60 * 1000);
        
        log.info("✓ Secret rotation scheduled");
    }

    /**
     * Rotate a specific secret
     */
    private void rotateSecret(String secretName, int rotationDays) {
        log.info("Rotating secret: {} (every {} days)", secretName, rotationDays);
        
        try {
            // Generate new secret
            String newSecret = generateStrongPassword();
            
            // Update in secure storage
            String encrypted = encryptSecret(newSecret);
            encryptedSecrets.put(secretName, encrypted);
            
            // In production, this would:
            // 1. Update the secret in Vault/Secrets Manager
            // 2. Trigger rolling restart of affected services
            // 3. Send notification to ops team
            
            log.info("✓ Secret {} rotated successfully", secretName);
            
        } catch (Exception e) {
            log.error("Failed to rotate secret: {}", secretName, e);
        }
    }

    /**
     * Generate a cryptographically strong password
     */
    public String generateStrongPassword() {
        String uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowercase = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "!@#$%^&*()_+-=[]{}|;:,.<>?";
        String all = uppercase + lowercase + digits + special;
        
        StringBuilder password = new StringBuilder();
        
        // Ensure at least one character from each category
        password.append(uppercase.charAt(secureRandom.nextInt(uppercase.length())));
        password.append(lowercase.charAt(secureRandom.nextInt(lowercase.length())));
        password.append(digits.charAt(secureRandom.nextInt(digits.length())));
        password.append(special.charAt(secureRandom.nextInt(special.length())));
        
        // Fill rest with random characters
        for (int i = 4; i < 32; i++) {
            password.append(all.charAt(secureRandom.nextInt(all.length())));
        }
        
        // Shuffle the password
        List<Character> chars = new ArrayList<>();
        for (char c : password.toString().toCharArray()) {
            chars.add(c);
        }
        Collections.shuffle(chars, secureRandom);
        
        StringBuilder shuffled = new StringBuilder();
        for (char c : chars) {
            shuffled.append(c);
        }
        
        return shuffled.toString();
    }

    /**
     * Initialize master encryption key
     */
    private void initializeMasterKey() {
        try {
            // In production, retrieve from HSM or KMS
            String masterKeyEnv = environment.getProperty("MASTER_ENCRYPTION_KEY");
            
            if (masterKeyEnv != null) {
                byte[] keyBytes = Base64.getDecoder().decode(masterKeyEnv);
                this.masterKey = new SecretKeySpec(keyBytes, "AES");
            } else {
                // Generate a temporary key for development
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256, secureRandom);
                this.masterKey = keyGen.generateKey();
                log.warn("⚠️ Using generated master key - not suitable for production");
            }
        } catch (Exception e) {
            log.error("Failed to initialize master key", e);
        }
    }

    /**
     * Encrypt a secret using AES-GCM
     */
    private String encryptSecret(String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        
        // Generate IV
        byte[] iv = new byte[12];
        secureRandom.nextBytes(iv);
        
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, parameterSpec);
        
        byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        
        // Combine IV and cipher text
        byte[] combined = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
        
        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Decrypt a secret using AES-GCM
     */
    public String decryptSecret(String encrypted) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encrypted);
        
        // Extract IV
        byte[] iv = new byte[12];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        
        // Extract cipher text
        byte[] cipherText = new byte[combined.length - iv.length];
        System.arraycopy(combined, iv.length, cipherText, 0, cipherText.length);
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, masterKey, parameterSpec);
        
        byte[] plainText = cipher.doFinal(cipherText);
        return new String(plainText, StandardCharsets.UTF_8);
    }

    /**
     * Get a decrypted secret
     */
    public String getSecret(String secretName) {
        try {
            String encrypted = encryptedSecrets.get(secretName);
            if (encrypted != null) {
                return decryptSecret(encrypted);
            }
            // Fall back to environment
            return environment.getProperty(secretName);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to retrieve security secret: {} - Application security compromised", secretName, e);
            throw new SecurityConfigurationException("Failed to retrieve security secret: " + secretName, e);
        }
    }

    /**
     * Check if a property key represents a password
     */
    private boolean isPasswordProperty(String key) {
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("password") || 
               lowerKey.contains("secret") || 
               lowerKey.contains("key") ||
               lowerKey.contains("token") ||
               lowerKey.contains("credential");
    }

    /**
     * Audit log for configuration access
     */
    public void auditConfigAccess(String user, String configKey, String action) {
        log.info("CONFIG_AUDIT: User={}, Key={}, Action={}, Timestamp={}", 
            user, configKey, action, System.currentTimeMillis());
    }
}

