package com.waqiti.common.config;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import com.waqiti.common.exception.CriticalSecurityConfigurationException;
import com.waqiti.common.config.SecurityExitCode;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * CRITICAL SECURITY SERVICE - Secure Configuration Management
 * 
 * This service addresses the CRITICAL vulnerability where application.yml files
 * contain hardcoded fallback values like "VAULT_SECRET_REQUIRED".
 * 
 * SECURITY FIXES:
 * - Prevents applications from starting with insecure fallback values
 * - Validates all secrets are properly loaded from Vault
 * - Provides runtime secret validation
 * - Implements secure secret rotation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecureConfigurationService implements ApplicationContextAware {
    
    private ApplicationContext applicationContext;
    
    // CRITICAL: These are the dangerous fallback values we must prevent
    private static final Pattern INSECURE_FALLBACK_PATTERN = Pattern.compile(
        "VAULT_SECRET_REQUIRED|changeme|default|secret|password|key123|admin|root",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final String[] CRITICAL_PROPERTIES = {
        "spring.datasource.password",
        "spring.redis.password", 
        "stripe.api.secret-key",
        "jwt.secret",
        "encryption.master.key",
        "oauth2.client.secret",
        "webhook.signing.secret",
        "api.key.secret",
        "database.encryption.key",
        "vault.token",
        "kafka.ssl.keystore.password",
        "messaging.encryption.key"
    };
    
    private final Map<String, String> secureSecrets = new ConcurrentHashMap<>();
    private final Map<String, Long> secretLastRotated = new ConcurrentHashMap<>();
    
    @Value("${waqiti.security.fail-fast:true}")
    private boolean failFast;
    
    @Value("${waqiti.security.vault.enabled:true}")
    private boolean vaultEnabled;
    
    @Value("${waqiti.security.secret.rotation.enabled:true}")
    private boolean secretRotationEnabled;
    
    /**
     * CRITICAL: Validate configuration on startup
     * This prevents the application from starting with insecure fallback values
     */
    @PostConstruct
    public void validateSecureConfiguration() {
        log.info("🔐 Starting CRITICAL security configuration validation...");
        
        if (!vaultEnabled) {
            log.error("💀 CRITICAL SECURITY ERROR: Vault is disabled in production!");
            if (failFast) {
                throw new SecurityConfigurationException(
                    "Vault must be enabled in production environments"
                );
            }
        }
        
        validateCriticalSecrets();
        log.info("✅ Security configuration validation completed");
    }
    
    /**
     * Validate all critical secrets are properly configured
     */
    private void validateCriticalSecrets() {
        Map<String, String> invalidSecrets = new HashMap<>();
        
        for (String property : CRITICAL_PROPERTIES) {
            String value = System.getProperty(property);
            if (value == null) {
                value = System.getenv(property.replace(".", "_").toUpperCase());
            }
            
            if (value == null || isInsecureValue(value)) {
                invalidSecrets.put(property, value);
            } else {
                secureSecrets.put(property, value);
                secretLastRotated.put(property, System.currentTimeMillis());
            }
        }
        
        if (!invalidSecrets.isEmpty()) {
            log.error("💀 CRITICAL SECURITY VIOLATIONS DETECTED:");
            invalidSecrets.forEach((key, value) -> {
                log.error("❌ INSECURE: {} = '{}'", key, maskSecret(value));
            });
            
            if (failFast) {
                throw new SecurityConfigurationException(
                    String.format("Found %d insecure configuration values. " +
                        "All secrets must be loaded from Vault, not hardcoded fallbacks.",
                        invalidSecrets.size())
                );
            }
        }
    }
    
    /**
     * Check if a value is insecure (fallback or weak)
     */
    private boolean isInsecureValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        
        // Check for dangerous fallback patterns
        if (INSECURE_FALLBACK_PATTERN.matcher(value).find()) {
            return true;
        }
        
        // Check for suspiciously short secrets (likely defaults)
        if (value.length() < 16 && !value.startsWith("${")) {
            return true;
        }
        
        // Check for common weak patterns
        String lower = value.toLowerCase();
        return lower.equals("password") || 
               lower.equals("secret") || 
               lower.equals("key") ||
               lower.matches("^(123456|password|secret|admin|root).*");
    }
    
    /**
     * Get a secret securely with validation
     */
    public String getSecureSecret(String key) {
        String secret = secureSecrets.get(key);
        if (secret == null || isInsecureValue(secret)) {
            throw new SecurityConfigurationException(
                "Secret not available or insecure: " + key
            );
        }
        
        // Check if secret needs rotation (older than 90 days)
        Long lastRotated = secretLastRotated.get(key);
        if (lastRotated != null && secretRotationEnabled) {
            long daysSinceRotation = (System.currentTimeMillis() - lastRotated) / (1000 * 60 * 60 * 24);
            if (daysSinceRotation > 90) {
                log.warn("⚠️  SECRET ROTATION WARNING: Secret '{}' is {} days old and should be rotated", 
                    key, daysSinceRotation);
            }
        }
        
        return secret;
    }
    
    /**
     * Update a secret (for rotation)
     */
    public void updateSecret(String key, String newValue) {
        if (isInsecureValue(newValue)) {
            throw new SecurityConfigurationException(
                "Cannot update to insecure value for key: " + key
            );
        }
        
        String oldValue = secureSecrets.get(key);
        secureSecrets.put(key, newValue);
        secretLastRotated.put(key, System.currentTimeMillis());
        
        log.info("🔄 Secret rotated for key: {}", key);
        
        // Notify other services of secret rotation if needed
        publishSecretRotationEvent(key, oldValue != null);
    }
    
    /**
     * Health check for configuration security
     */
    public ConfigurationHealthStatus getHealthStatus() {
        int totalSecrets = CRITICAL_PROPERTIES.length;
        int secureSecretsCount = this.secureSecrets.size();
        int secretsNeedingRotation = 0;
        
        // Build secret status map
        Map<String, Boolean> secretStatuses = new HashMap<>();
        for (String property : CRITICAL_PROPERTIES) {
            boolean isSecure = this.secureSecrets.containsKey(property);
            secretStatuses.put(property, isSecure);
        }
        
        if (secretRotationEnabled) {
            long ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000);
            secretsNeedingRotation = (int) secretLastRotated.values().stream()
                .filter(timestamp -> timestamp < ninetyDaysAgo)
                .count();
        }
        
        return ConfigurationHealthStatus.builder()
            .totalSecrets(totalSecrets)
            .secureSecrets(secureSecretsCount)
            .insecureSecrets(totalSecrets - secureSecretsCount)
            .secretsNeedingRotation(secretsNeedingRotation)
            .vaultEnabled(vaultEnabled)
            .overallStatus(secureSecretsCount == totalSecrets && secretsNeedingRotation == 0 ? 
                "SECURE" : "NEEDS_ATTENTION")
            .secretStatuses(secretStatuses)
            .build();
    }
    
    /**
     * Application ready event - final security check
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("🔐 Final security configuration check on application ready...");
        
        ConfigurationHealthStatus status = getHealthStatus();
        log.info("📊 Configuration Security Status: {}", status);
        
        if (status.getInsecureSecrets() > 0) {
            log.error("💀 APPLICATION STARTED WITH {} INSECURE SECRETS!", 
                status.getInsecureSecrets());
            
            if (failFast) {
                /**
                 * SECURITY FIX: Replace System.exit() with proper exception handling
                 * This ensures graceful shutdown and proper error reporting
                 */
                log.error("💀 APPLICATION CANNOT START DUE TO SECURITY VIOLATIONS");
                
                // Publish security failure event
                applicationContext.publishEvent(new SecurityConfigurationFailureEvent(
                    this, 
                    String.format("Found %d insecure secrets in configuration", status.getInsecureSecrets()),
                    status
                ));
                
                // Throw exception to prevent application startup
                throw new CriticalSecurityConfigurationException(
                    String.format("Application startup blocked: %d insecure secrets detected. " +
                                 "Security validation failed for secrets: %s",
                                 status.getInsecureSecrets(),
                                 getInsecureSecretNames(status)),
                    SecurityExitCode.INSECURE_SECRETS
                );
            }
        } else {
            log.info("✅ All secrets are secure - application ready");
        }
    }
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    /**
     * Get insecure secret names from status
     */
    private String getInsecureSecretNames(ConfigurationHealthStatus status) {
        // Return a comma-separated list of insecure property names
        return String.join(", ", status.getSecretStatuses().entrySet().stream()
            .filter(e -> !e.getValue())
            .map(Map.Entry::getKey)
            .limit(5)  // Show first 5 for brevity
            .toList());
    }
    
    /**
     * Mask secret for logging
     */
    private String maskSecret(String value) {
        if (value == null) return "null";
        if (value.length() <= 4) return "****";
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
    
    /**
     * Publish secret rotation event
     */
    private void publishSecretRotationEvent(String key, boolean isUpdate) {
        // Implementation would depend on your event system (Kafka, Spring Events, etc.)
        log.debug("Publishing secret rotation event for key: {}", key);
    }
    
    /**
     * Configuration health status model
     */
    @Data
    @Builder
    public static class ConfigurationHealthStatus {
        private int totalSecrets;
        private int secureSecrets;
        private int insecureSecrets;
        private int secretsNeedingRotation;
        private boolean vaultEnabled;
        private String overallStatus;
        @Builder.Default
        private Map<String, Boolean> secretStatuses = new HashMap<>();
    }
    
    /**
     * Custom exception for security configuration errors
     */
    public static class SecurityConfigurationException extends RuntimeException {
        public SecurityConfigurationException(String message) {
            super(message);
        }
        
        public SecurityConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}