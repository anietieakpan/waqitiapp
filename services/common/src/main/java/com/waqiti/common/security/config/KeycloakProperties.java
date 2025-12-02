package com.waqiti.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Configuration properties for Keycloak integration
 */
@Data
@Validated
@ConfigurationProperties(prefix = "waqiti.security.keycloak")
public class KeycloakProperties {
    
    /**
     * Enable Keycloak integration
     */
    private boolean enabled = false;
    
    /**
     * Keycloak server URL
     */
    @NotBlank
    private String serverUrl = "http://localhost:8080";
    
    /**
     * Keycloak realm name
     */
    @NotBlank
    private String realm = "waqiti";
    
    /**
     * Client ID for this application
     */
    @NotBlank
    private String clientId = "waqiti-app";
    
    /**
     * Client secret (if using confidential client)
     */
    private String clientSecret;
    
    /**
     * JWK Set URI for token validation
     */
    private String jwkSetUri;
    
    /**
     * Issuer URI for token validation
     */
    private String issuerUri;
    
    /**
     * Authorization endpoint
     */
    private String authServerUrl;
    
    /**
     * Token endpoint
     */
    private String tokenUri;
    
    /**
     * User info endpoint
     */
    private String userInfoUri;
    
    /**
     * Logout endpoint
     */
    private String logoutUri;
    
    /**
     * Use resource owner password credentials flow
     */
    private boolean useResourceOwnerPasswordFlow = false;
    
    /**
     * Connection and read timeouts
     */
    private int connectionTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    
    /**
     * SSL verification
     */
    private boolean verifyTokenAudience = true;
    private boolean sslRequired = true;
    
    /**
     * Role mappings
     */
    private Map<String, String> roleMappings = Map.of(
        "user", "ROLE_USER",
        "admin", "ROLE_ADMIN",
        "merchant", "ROLE_MERCHANT"
    );
    
    /**
     * Public client configuration
     */
    private boolean publicClient = false;
    
    /**
     * Bearer token only mode
     */
    private boolean bearerOnly = true;
    
    /**
     * Enable CORS for Keycloak endpoints
     */
    private boolean enableCors = true;
    
    public String getJwkSetUri() {
        if (jwkSetUri != null) {
            return jwkSetUri;
        }
        return serverUrl + "/realms/" + realm + "/protocol/openid_connect/certs";
    }
    
    public String getIssuerUri() {
        if (issuerUri != null) {
            return issuerUri;
        }
        return serverUrl + "/realms/" + realm;
    }
    
    public String getAuthServerUrl() {
        if (authServerUrl != null) {
            return authServerUrl;
        }
        return serverUrl + "/realms/" + realm + "/protocol/openid_connect/auth";
    }
    
    public String getTokenUri() {
        if (tokenUri != null) {
            return tokenUri;
        }
        return serverUrl + "/realms/" + realm + "/protocol/openid_connect/token";
    }
    
    public String getUserInfoUri() {
        if (userInfoUri != null) {
            return userInfoUri;
        }
        return serverUrl + "/realms/" + realm + "/protocol/openid_connect/userinfo";
    }
    
    public String getLogoutUri() {
        if (logoutUri != null) {
            return logoutUri;
        }
        return serverUrl + "/realms/" + realm + "/protocol/openid_connect/logout";
    }
}