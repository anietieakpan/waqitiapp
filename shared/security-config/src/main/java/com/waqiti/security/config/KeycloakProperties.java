package com.waqiti.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Keycloak Configuration Properties
 * 
 * Maps all Keycloak-related configuration from application.yml
 * Provides centralized configuration for OAuth2/OIDC integration
 */
@Data
@Component
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {
    
    // Basic Keycloak Configuration
    private boolean enabled = true;
    private String serverUrl = "http://localhost:8080";
    private String realm = "waqiti";
    private String clientId;
    private String clientSecret;
    
    // OAuth2/OIDC Endpoints
    private String issuerUri;
    private String jwkSetUri;
    private String tokenUri;
    private String authorizationUri;
    private String userInfoUri;
    private String endSessionUri;
    
    // JWT Configuration
    private Duration jwkCacheDuration = Duration.ofMinutes(5);
    private Duration tokenValidityDuration = Duration.ofMinutes(30);
    private boolean validateIssuer = true;
    private boolean validateAudience = true;
    private boolean validateNotBefore = true;
    
    // Role and Scope Configuration
    private String roleClaimPath = "realm_access.roles";
    private String scopeClaimPath = "scope";
    private String resourceAccessClaimPath = "resource_access";
    private String preferredUsernameClaimPath = "preferred_username";
    private String emailClaimPath = "email";
    private String nameClaimPath = "name";
    
    // CORS Configuration
    private boolean developmentMode = false;
    private List<String> allowedOrigins;
    private List<String> allowedMethods;
    private List<String> allowedHeaders;
    private List<String> exposedHeaders;
    private boolean allowCredentials = true;
    private long maxAge = 3600;
    
    // Service-specific Configuration
    private Map<String, ServiceConfig> services;
    
    // Security Configuration
    private Security security = new Security();
    
    // Admin Configuration
    private Admin admin = new Admin();
    
    @Data
    public static class ServiceConfig {
        private String clientId;
        private String clientSecret;
        private List<String> requiredRoles;
        private List<String> requiredScopes;
        private Map<String, Object> additionalClaims;
    }
    
    @Data
    public static class Security {
        private boolean strictMode = false;
        private boolean logSecurityEvents = true;
        private boolean enableAuditLog = true;
        private int maxLoginAttempts = 5;
        private Duration lockoutDuration = Duration.ofMinutes(15);
        private boolean requireHttps = false;
        private List<String> trustedProxies;
    }
    
    @Data
    public static class Admin {
        private String username;
        private String password;
        private String clientId = "admin-cli";
        private String grantType = "password";
        private List<String> defaultRoles;
    }
    
    // Convenience methods
    
    public String getRealmUrl() {
        return serverUrl + "/realms/" + realm;
    }
    
    public String getProtocolUrl() {
        return getRealmUrl() + "/protocol/openid-connect";
    }
    
    public String getDefaultIssuerUri() {
        return getRealmUrl();
    }
    
    public String getDefaultJwkSetUri() {
        return getProtocolUrl() + "/certs";
    }
    
    public String getDefaultTokenUri() {
        return getProtocolUrl() + "/token";
    }
    
    public String getDefaultAuthorizationUri() {
        return getProtocolUrl() + "/auth";
    }
    
    public String getDefaultUserInfoUri() {
        return getProtocolUrl() + "/userinfo";
    }
    
    public String getDefaultEndSessionUri() {
        return getProtocolUrl() + "/logout";
    }
    
    public ServiceConfig getServiceConfig(String serviceName) {
        return services != null ? services.get(serviceName) : null;
    }
    
    public boolean isServiceConfigured(String serviceName) {
        ServiceConfig config = getServiceConfig(serviceName);
        return config != null && config.getClientId() != null;
    }
    
    public List<String> getRequiredRolesForService(String serviceName) {
        ServiceConfig config = getServiceConfig(serviceName);
        return config != null ? config.getRequiredRoles() : List.of();
    }
    
    public List<String> getRequiredScopesForService(String serviceName) {
        ServiceConfig config = getServiceConfig(serviceName);
        return config != null ? config.getRequiredScopes() : List.of();
    }
    
    // Initialize default values if not provided
    public void initializeDefaults() {
        if (issuerUri == null) {
            issuerUri = getDefaultIssuerUri();
        }
        if (jwkSetUri == null) {
            jwkSetUri = getDefaultJwkSetUri();
        }
        if (tokenUri == null) {
            tokenUri = getDefaultTokenUri();
        }
        if (authorizationUri == null) {
            authorizationUri = getDefaultAuthorizationUri();
        }
        if (userInfoUri == null) {
            userInfoUri = getDefaultUserInfoUri();
        }
        if (endSessionUri == null) {
            endSessionUri = getDefaultEndSessionUri();
        }
    }
}