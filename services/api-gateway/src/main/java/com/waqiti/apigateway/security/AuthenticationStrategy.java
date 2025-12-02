package com.waqiti.apigateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * CRITICAL SECURITY: Consolidated Authentication Strategy Configuration
 * FIXED: Single authentication mechanism - Keycloak OIDC/OAuth2
 * REMOVED: Legacy JWT conflicting implementations
 */
@Configuration
@ConfigurationProperties(prefix = "waqiti.security.auth")
@Slf4j
public class AuthenticationStrategy {
    
    /**
     * PRODUCTION DECISION: Use Keycloak as single authentication provider
     * - Enterprise-grade identity management
     * - OAuth2/OIDC compliance
     * - Multi-factor authentication support
     * - Role-based access control
     * - Audit trail and compliance reporting
     */
    public enum AuthMode {
        KEYCLOAK_OIDC,    // PRIMARY: Production authentication
        DEVELOPMENT_ONLY  // For development/testing only - NOT for production
    }
    
    private AuthMode mode = AuthMode.KEYCLOAK_OIDC;
    private boolean enableLegacyJwt = false; // REMOVED: Legacy JWT support
    
    // Keycloak configuration
    private String keycloakServerUrl;
    private String keycloakRealm;
    private String keycloakClientId;
    private String keycloakClientSecret;
    
    // Security settings
    private boolean enableMfa = true;
    private int sessionTimeoutMinutes = 30;
    private boolean enablePasswordPolicy = true;
    private boolean enableAccountLocking = true;
    private int maxFailedAttempts = 5;
    
    public AuthMode getMode() {
        return mode;
    }
    
    public void setMode(AuthMode mode) {
        this.mode = mode;
        log.info("SECURITY: Authentication mode set to: {}", mode);
        
        if (mode != AuthMode.KEYCLOAK_OIDC) {
            log.warn("SECURITY WARNING: Non-production authentication mode enabled: {}", mode);
        }
    }
    
    public boolean isKeycloakEnabled() {
        return mode == AuthMode.KEYCLOAK_OIDC;
    }
    
    public boolean isLegacyJwtEnabled() {
        return false; // CRITICAL: Legacy JWT permanently disabled
    }
    
    public void setEnableLegacyJwt(boolean enableLegacyJwt) {
        if (enableLegacyJwt) {
            log.error("SECURITY VIOLATION: Attempt to enable legacy JWT authentication - BLOCKED");
            throw new SecurityException("Legacy JWT authentication is permanently disabled for security reasons");
        }
        this.enableLegacyJwt = false;
    }
    
    // Keycloak getters and setters
    public String getKeycloakServerUrl() {
        return keycloakServerUrl;
    }
    
    public void setKeycloakServerUrl(String keycloakServerUrl) {
        this.keycloakServerUrl = keycloakServerUrl;
    }
    
    public String getKeycloakRealm() {
        return keycloakRealm;
    }
    
    public void setKeycloakRealm(String keycloakRealm) {
        this.keycloakRealm = keycloakRealm;
    }
    
    public String getKeycloakClientId() {
        return keycloakClientId;
    }
    
    public void setKeycloakClientId(String keycloakClientId) {
        this.keycloakClientId = keycloakClientId;
    }
    
    public String getKeycloakClientSecret() {
        return keycloakClientSecret;
    }
    
    public void setKeycloakClientSecret(String keycloakClientSecret) {
        this.keycloakClientSecret = keycloakClientSecret;
    }
    
    // Security settings getters and setters
    public boolean isEnableMfa() {
        return enableMfa;
    }
    
    public void setEnableMfa(boolean enableMfa) {
        this.enableMfa = enableMfa;
    }
    
    public int getSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }
    
    public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) {
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
    }
    
    public boolean isEnablePasswordPolicy() {
        return enablePasswordPolicy;
    }
    
    public void setEnablePasswordPolicy(boolean enablePasswordPolicy) {
        this.enablePasswordPolicy = enablePasswordPolicy;
    }
    
    public boolean isEnableAccountLocking() {
        return enableAccountLocking;
    }
    
    public void setEnableAccountLocking(boolean enableAccountLocking) {
        this.enableAccountLocking = enableAccountLocking;
    }
    
    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }
    
    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }
    
    /**
     * Validate authentication configuration on startup
     */
    public void validateConfiguration() {
        if (mode == AuthMode.KEYCLOAK_OIDC) {
            if (keycloakServerUrl == null || keycloakServerUrl.trim().isEmpty()) {
                throw new IllegalStateException("Keycloak server URL is required when Keycloak authentication is enabled");
            }
            if (keycloakRealm == null || keycloakRealm.trim().isEmpty()) {
                throw new IllegalStateException("Keycloak realm is required when Keycloak authentication is enabled");
            }
            if (keycloakClientId == null || keycloakClientId.trim().isEmpty()) {
                throw new IllegalStateException("Keycloak client ID is required when Keycloak authentication is enabled");
            }
            if (keycloakClientSecret == null || keycloakClientSecret.trim().isEmpty()) {
                throw new IllegalStateException("Keycloak client secret is required when Keycloak authentication is enabled");
            }
            
            log.info("SECURITY: Keycloak authentication configuration validated successfully");
            log.info("SECURITY: Keycloak server: {}", keycloakServerUrl);
            log.info("SECURITY: Keycloak realm: {}", keycloakRealm);
            log.info("SECURITY: MFA enabled: {}", enableMfa);
            log.info("SECURITY: Session timeout: {} minutes", sessionTimeoutMinutes);
        }
    }
}