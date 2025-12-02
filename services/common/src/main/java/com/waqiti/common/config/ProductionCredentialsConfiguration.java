package com.waqiti.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PRODUCTION CREDENTIALS CONFIGURATION
 * 
 * This configuration validates that all required production credentials
 * are properly set before the application starts. This prevents applications
 * from starting with missing or placeholder credentials.
 * 
 * Features:
 * - Validates all critical API keys and secrets
 * - Provides clear error messages for missing credentials
 * - Fails fast to prevent production deployment with invalid config
 * - Logs security warnings for placeholder values
 */
@Configuration
@Slf4j
public class ProductionCredentialsConfiguration {

    private final Environment environment;
    
    public ProductionCredentialsConfiguration(Environment environment) {
        this.environment = environment;
    }

    /**
     * Critical credentials that must be set in production
     */
    private static final Map<String, String> REQUIRED_CREDENTIALS = Map.of(
        "KEYCLOAK_CLIENT_SECRET", "Keycloak client authentication",
        "VAULT_DATABASE_PASSWORD", "Database connection",
        "JWT_SECRET", "JWT token signing",
        "EXCHANGE_API_KEY", "Currency exchange service",
        "OPENAI_API_KEY", "AI/ML services"
    );

    /**
     * External service API keys that should be configured
     */
    private static final Map<String, String> EXTERNAL_API_KEYS = Map.of(
        "AWS_ACCESS_KEY_ID", "AWS services integration",
        "AWS_SECRET_ACCESS_KEY", "AWS services authentication",
        "GOOGLE_CLOUD_PROJECT_ID", "Google Cloud services",
        "STRIPE_SECRET_KEY", "Stripe payment processing",
        "PAYPAL_CLIENT_SECRET", "PayPal payment processing",
        "TWILIO_AUTH_TOKEN", "SMS notifications",
        "SENDGRID_API_KEY", "Email notifications"
    );

    /**
     * Validate credentials after application context is ready
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateProductionCredentials() {
        log.info("SECURITY: Validating production credentials configuration");
        
        List<String> missingCredentials = new ArrayList<>();
        List<String> placeholderCredentials = new ArrayList<>();
        
        // Check required credentials
        for (Map.Entry<String, String> entry : REQUIRED_CREDENTIALS.entrySet()) {
            String key = entry.getKey();
            String description = entry.getValue();
            String value = environment.getProperty(key);
            
            if (isCredentialMissing(value)) {
                missingCredentials.add(String.format("%s (%s)", key, description));
            } else if (isPlaceholderValue(value)) {
                placeholderCredentials.add(String.format("%s (%s): %s", key, description, value));
            }
        }
        
        // Check external API keys
        for (Map.Entry<String, String> entry : EXTERNAL_API_KEYS.entrySet()) {
            String key = entry.getKey();
            String description = entry.getValue();
            String value = environment.getProperty(key);
            
            if (isCredentialMissing(value)) {
                log.warn("SECURITY: External API key not configured: {} ({})", key, description);
            } else if (isPlaceholderValue(value)) {
                log.warn("SECURITY: External API key has placeholder value: {} ({}): {}", 
                    key, description, value);
            }
        }
        
        // Report findings
        if (!missingCredentials.isEmpty()) {
            log.error("SECURITY: CRITICAL - Missing required production credentials:");
            missingCredentials.forEach(cred -> log.error("  - {}", cred));
            
            // In strict production mode, fail the application
            if (isStrictProductionMode()) {
                throw new IllegalStateException(
                    "Production deployment blocked - Missing required credentials: " + 
                    String.join(", ", missingCredentials));
            }
        }
        
        if (!placeholderCredentials.isEmpty()) {
            log.error("SECURITY: CRITICAL - Placeholder credentials detected in production:");
            placeholderCredentials.forEach(cred -> log.error("  - {}", cred));
            
            if (isStrictProductionMode()) {
                throw new IllegalStateException(
                    "Production deployment blocked - Placeholder credentials detected: " + 
                    String.join(", ", placeholderCredentials));
            }
        }
        
        if (missingCredentials.isEmpty() && placeholderCredentials.isEmpty()) {
            log.info("SECURITY: All required production credentials are properly configured");
        }
        
        // Additional security checks
        validateSecurityConfiguration();
    }
    
    /**
     * Check if credential is missing
     */
    private boolean isCredentialMissing(String value) {
        return value == null || value.trim().isEmpty();
    }
    
    /**
     * Check if credential has a placeholder value
     */
    private boolean isPlaceholderValue(String value) {
        if (value == null) return false;
        
        String lowerValue = value.toLowerCase();
        return lowerValue.contains("required") ||
               lowerValue.contains("placeholder") ||
               lowerValue.contains("changeme") ||
               lowerValue.contains("todo") ||
               lowerValue.contains("fixme") ||
               lowerValue.equals("your_key_here") ||
               lowerValue.equals("replace_me") ||
               lowerValue.length() < 8; // Suspiciously short
    }
    
    /**
     * Check if running in strict production mode
     */
    private boolean isStrictProductionMode() {
        String profile = environment.getProperty("spring.profiles.active", "dev");
        String strictMode = environment.getProperty("waqiti.security.strict-credentials", "false");
        
        return profile.contains("prod") || "true".equals(strictMode);
    }
    
    /**
     * Validate additional security configuration
     */
    private void validateSecurityConfiguration() {
        try {
            // Check SSL configuration
            String sslRequired = environment.getProperty("server.ssl.enabled", "false");
            String profile = environment.getProperty("spring.profiles.active", "dev");
            
            if (profile.contains("prod") && !"true".equals(sslRequired)) {
                log.warn("SECURITY: SSL is not enabled in production profile");
            }
            
            // Check Keycloak SSL requirement
            String keycloakSsl = environment.getProperty("keycloak.ssl-required", "none");
            if (profile.contains("prod") && "none".equals(keycloakSsl)) {
                log.warn("SECURITY: Keycloak SSL requirement is 'none' in production");
            }
            
            // Check database SSL
            String dbUrl = environment.getProperty("spring.datasource.url", "");
            if (profile.contains("prod") && !dbUrl.contains("sslmode=require")) {
                log.warn("SECURITY: Database connection may not require SSL in production");
            }
            
            // Check Redis password
            String redisPassword = environment.getProperty("spring.data.redis.password");
            if (profile.contains("prod") && isCredentialMissing(redisPassword)) {
                log.warn("SECURITY: Redis password not configured in production");
            }
            
        } catch (Exception e) {
            log.warn("SECURITY: Error during security configuration validation", e);
        }
    }
    
    /**
     * Generate credential template for deployment
     */
    public void generateCredentialTemplate() {
        log.info("=== PRODUCTION CREDENTIALS TEMPLATE ===");
        log.info("Set the following environment variables before deployment:");
        log.info("");
        
        log.info("# Core Authentication");
        for (Map.Entry<String, String> entry : REQUIRED_CREDENTIALS.entrySet()) {
            log.info("export {}=your_{}  # {}", 
                entry.getKey(), entry.getKey().toLowerCase(), entry.getValue());
        }
        
        log.info("");
        log.info("# External Services (optional but recommended)");
        for (Map.Entry<String, String> entry : EXTERNAL_API_KEYS.entrySet()) {
            log.info("export {}=your_{}  # {}", 
                entry.getKey(), entry.getKey().toLowerCase(), entry.getValue());
        }
        
        log.info("");
        log.info("# Security Configuration");
        log.info("export WAQITI_SECURITY_STRICT_CREDENTIALS=true");
        log.info("export SPRING_PROFILES_ACTIVE=prod");
        log.info("=======================================");
    }
    
    /**
     * Development mode credential warnings
     */
    @PostConstruct
    public void developmentModeWarnings() {
        String profile = environment.getProperty("spring.profiles.active", "dev");
        
        if (profile.contains("dev") || profile.contains("test")) {
            log.info("DEVELOPMENT: Running in {} mode - credential validation relaxed", profile);
            
            // Check if any production-like credentials are set in dev
            String keycloakSecret = environment.getProperty("KEYCLOAK_CLIENT_SECRET");
            if (keycloakSecret != null && keycloakSecret.length() > 20 && 
                !isPlaceholderValue(keycloakSecret)) {
                log.warn("DEVELOPMENT: Production-like Keycloak secret detected in dev mode");
            }
        }
    }
}