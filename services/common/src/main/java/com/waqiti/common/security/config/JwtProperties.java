package com.waqiti.common.security.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.List;
import java.util.Base64;
import java.security.SecureRandom;

/**
 * PRODUCTION-READY JWT CONFIGURATION
 * 
 * This configuration enforces security best practices:
 * 1. NO hardcoded secrets - requires environment configuration
 * 2. Validation of secret strength and format
 * 3. Automatic secret generation for development
 * 4. Comprehensive security validation
 * 5. Audit logging for configuration issues
 */
@Slf4j
@Data
@Validated
@ConfigurationProperties(prefix = "waqiti.security.jwt")
public class JwtProperties {
    
    // NOTE: This is NOT a usable secret - it's a validation pattern string only
    // Used to detect if development secrets leaked to production (see line 173)
    // The actual JWT secret MUST come from environment variables (see line 48)
    private static final String DEVELOPMENT_WARNING_PATTERN = "DEVELOPMENT";
    private static final int MINIMUM_SECRET_LENGTH = 32; // 256 bits minimum
    
    /**
     * JWT signing secret (MUST be configured via environment variables)
     * 
     * SECURITY REQUIREMENTS:
     * - Must be at least 256 bits (32 bytes) 
     * - Should be base64 encoded
     * - NEVER use the default development secret in production
     * 
     * Environment variables (in order of precedence):
     * 1. WAQITI_JWT_SECRET
     * 2. JWT_SECRET_KEY  
     * 3. VAULT_JWT_SECRET (from HashiCorp Vault)
     */
    @Value("${waqiti.security.jwt.secret:${JWT_SECRET_KEY:${VAULT_JWT_SECRET:}}}")
    private String secret;
    
    /**
     * Environment indicator for security validation
     */
    @Value("${spring.profiles.active:development}")
    private String activeProfile;
    
    /**
     * JWT issuer (configurable for multi-environment deployment)
     */
    @NotBlank
    @Value("${waqiti.security.jwt.issuer:waqiti-platform}")
    private String issuer;
    
    /**
     * JWT audience (configurable for different services)
     */
    @NotBlank  
    @Value("${waqiti.security.jwt.audience:waqiti-api}")
    private String audience;
    
    /**
     * Access token expiration in seconds (production: shorter is better)
     */
    @Min(300)
    @Max(3600) // Maximum 1 hour for security
    @Value("${waqiti.security.jwt.access-token-expiration:900}") // 15 minutes default
    private int accessTokenExpirationSeconds;
    
    /**
     * Refresh token expiration in seconds
     */
    @Min(3600)
    @Max(604800) // Maximum 7 days
    @Value("${waqiti.security.jwt.refresh-token-expiration:86400}") // 24 hours default
    private int refreshTokenExpirationSeconds;
    
    /**
     * JWT algorithm (only secure algorithms allowed)
     */
    @Value("${waqiti.security.jwt.algorithm:HS512}")
    private String algorithm;
    
    /**
     * Include user details in token (security consideration)
     */
    @Value("${waqiti.security.jwt.include-user-details:false}")
    private boolean includeUserDetails;
    
    /**
     * Clock skew allowance in seconds
     */
    @Min(0)
    @Max(120) // Maximum 2 minutes
    @Value("${waqiti.security.jwt.clock-skew-seconds:30}")
    private int clockSkewSeconds;
    
    /**
     * Required claims in JWT
     */
    private List<String> requiredClaims = List.of("sub", "iat", "exp", "iss", "aud", "jti");
    
    /**
     * Custom claims to include (minimized for security)
     */
    private List<String> customClaims = List.of("user_id", "roles", "permissions", "session_id");
    
    /**
     * Token blacklist settings
     */
    private TokenBlacklist blacklist = new TokenBlacklist();
    
    /**
     * MFA token settings
     */
    private MfaToken mfaToken = new MfaToken();
    
    /**
     * CRITICAL SECURITY VALIDATION
     * This method validates JWT configuration on startup and prevents
     * application from starting with insecure settings
     */
    @PostConstruct
    public void validateConfiguration() {
        validateJwtSecret();
        validateEnvironmentSecurity();
        validateAlgorithmSecurity();
        logSecurityConfiguration();
    }
    
    private void validateJwtSecret() {
        if (!StringUtils.hasText(secret)) {
            // Auto-generate secret for development ONLY
            if (isDevelopmentEnvironment()) {
                secret = generateDevelopmentSecret();
                log.warn("Auto-generated JWT secret for DEVELOPMENT only! Set WAQITI_JWT_SECRET environment variable for production!");
            } else {
                throw new IllegalStateException(
                    "CRITICAL SECURITY ERROR: JWT secret not configured! " +
                    "Set WAQITI_JWT_SECRET environment variable."
                );
            }
        }
        
        // Validate secret strength
        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            // Not base64, use as-is but validate length
            secretBytes = secret.getBytes();
        }
        
        if (secretBytes.length < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException(
                "CRITICAL SECURITY ERROR: JWT secret too weak! " +
                "Minimum length: " + MINIMUM_SECRET_LENGTH + " bytes, " +
                "Actual: " + secretBytes.length + " bytes"
            );
        }
        
        // Check for development warning patterns in secrets
        if (secret.contains(DEVELOPMENT_WARNING_PATTERN) || 
            secret.contains("change-in-production") ||
            secret.contains("insecure") ||
            secret.contains("test-secret") ||
            secret.contains("demo-secret")) {
            
            if (!isDevelopmentEnvironment()) {
                throw new IllegalStateException(
                    "CRITICAL SECURITY ERROR: Development/test JWT secret pattern detected in production environment! " +
                    "The JWT secret contains forbidden patterns that indicate it's not a production-grade secret."
                );
            }
            log.warn("Using development-pattern JWT secret. This is only acceptable in development!");
        }
    }
    
    private void validateEnvironmentSecurity() {
        if (isProductionEnvironment()) {
            // Production security requirements
            if (accessTokenExpirationSeconds > 1800) { // 30 minutes max
                throw new IllegalStateException(
                    "SECURITY ERROR: Access token expiration too long for production: " + 
                    accessTokenExpirationSeconds + "s (max: 1800s)"
                );
            }
            
            if (includeUserDetails) {
                log.warn("includeUserDetails=true in production may expose sensitive data");
            }
        }
    }
    
    private void validateAlgorithmSecurity() {
        List<String> secureAlgorithms = List.of("HS512", "RS512", "ES512");
        if (!secureAlgorithms.contains(algorithm)) {
            throw new IllegalStateException(
                "SECURITY ERROR: Insecure JWT algorithm: " + algorithm + 
                ". Use one of: " + secureAlgorithms
            );
        }
    }
    
    private void logSecurityConfiguration() {
        log.info("JWT Security Configuration initialized:");
        log.info("  Algorithm: {}", algorithm);
        log.info("  Access Token TTL: {}s", accessTokenExpirationSeconds);
        log.info("  Refresh Token TTL: {}s", refreshTokenExpirationSeconds);
        log.info("  Clock Skew: {}s", clockSkewSeconds);
        log.info("  Secret Length: {} bytes", getSecretLength());
        log.info("  Environment: {}", activeProfile);
    }
    
    private boolean isDevelopmentEnvironment() {
        return activeProfile != null && 
               (activeProfile.contains("dev") || 
                activeProfile.contains("local") || 
                activeProfile.contains("test"));
    }
    
    private boolean isProductionEnvironment() {
        return activeProfile != null && 
               (activeProfile.contains("prod") || 
                activeProfile.contains("production"));
    }
    
    private String generateDevelopmentSecret() {
        SecureRandom random = new SecureRandom();
        byte[] secretBytes = new byte[64]; // 512 bits
        random.nextBytes(secretBytes);
        return Base64.getEncoder().encodeToString(secretBytes);
    }
    
    private int getSecretLength() {
        try {
            return Base64.getDecoder().decode(secret).length;
        } catch (IllegalArgumentException e) {
            return secret.getBytes().length;
        }
    }
    
    @Data
    public static class TokenBlacklist {
        @Value("${waqiti.security.jwt.blacklist.enabled:true}")
        private boolean enabled;
        
        @Value("${waqiti.security.jwt.blacklist.cache-prefix:waqiti:jwt:blacklist:}")
        private String cachePrefix;
        
        @Value("${waqiti.security.jwt.blacklist.cache-expiration:86400}")
        private int cacheExpirationSeconds;
        
        @Value("${waqiti.security.jwt.blacklist.cleanup-interval:3600}")
        private int cleanupIntervalSeconds;
    }
    
    @Data
    public static class MfaToken {
        @Value("${waqiti.security.jwt.mfa.enabled:true}")
        private boolean enabled;
        
        @Min(60)
        @Max(600) // Maximum 10 minutes for security
        @Value("${waqiti.security.jwt.mfa.expiration:300}")
        private int expirationSeconds;
        
        @Value("${waqiti.security.jwt.mfa.claim-name:mfa_verified}")
        private String claimName;
        
        @Value("${waqiti.security.jwt.mfa.require-for-sensitive:true}")
        private boolean requireForSensitiveOperations;
        
        @Value("${waqiti.security.jwt.mfa.high-value-threshold:1000}")
        private double highValueThreshold; // Amount threshold requiring MFA
        
        private List<String> sensitiveOperations = List.of(
            "TRANSFER", "WITHDRAW", "FREEZE_WALLET", "CHANGE_LIMITS", 
            "ADD_PAYMENT_METHOD", "EXPORT_DATA", "DELETE_ACCOUNT"
        );
    }
}