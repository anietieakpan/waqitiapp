package com.waqiti.ledger.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Comprehensive Keycloak and OAuth2 configuration properties
 * Provides complete integration with Keycloak for authentication and authorization
 */
@Slf4j
@Data
@Validated
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {
    
    @NotNull
    private Boolean enabled = true;
    
    @NotBlank
    @Size(min = 1, max = 255)
    private String realm = "waqiti";
    
    @NotBlank
    @Pattern(regexp = "^(http|https)://.*")
    private String authServerUrl = "http://localhost:8080";
    
    @NotNull
    private SslRequired sslRequired = SslRequired.EXTERNAL;
    
    @NotBlank
    @Size(min = 1, max = 255)
    private String resource = "ledger-service";
    
    @NotNull
    private Boolean bearerOnly = true;
    
    @Valid
    @NotNull
    private Credentials credentials = new Credentials();
    
    @NotNull
    private Boolean useResourceRoleMappings = true;
    
    @NotNull
    private Boolean cors = true;
    
    @Min(0)
    @Max(86400)
    private Integer corsMaxAge = 3600;
    
    @NotNull
    private Set<String> corsAllowedMethods = new HashSet<>(Arrays.asList(
        "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"
    ));
    
    @NotNull
    private Set<String> corsAllowedHeaders = new HashSet<>(Arrays.asList(
        "Authorization", "Content-Type", "X-Requested-With", "X-Correlation-Id"
    ));
    
    @NotNull
    private Boolean corsExposedHeaders = true;
    
    @NotNull
    private Boolean publicClient = false;
    
    @NotNull
    private Boolean enableBasicAuth = false;
    
    @Valid
    private AdvancedConfig advanced = new AdvancedConfig();
    
    @Valid
    private PolicyEnforcer policyEnforcer = new PolicyEnforcer();
    
    @Valid
    private TokenConfig token = new TokenConfig();
    
    @Valid
    private ConnectionConfig connection = new ConnectionConfig();
    
    /**
     * SSL requirement levels
     */
    public enum SslRequired {
        ALL,      // All requests must use HTTPS
        EXTERNAL, // Only external requests require HTTPS
        NONE      // HTTPS not required (not recommended for production)
    }
    
    /**
     * Credentials configuration for client authentication
     */
    @Data
    public static class Credentials {
        @NotBlank
        @Size(min = 8, max = 512)
        private String secret;
        
        private String provider;
        
        private Map<String, Object> providerConfig = new HashMap<>();
        
        @NotNull
        private Boolean rotateSecret = false;
        
        @Min(1)
        @Max(365)
        private Integer secretRotationDays = 90;
        
        private String jwt;
        
        @NotNull
        private CredentialType type = CredentialType.SECRET;
        
        public enum CredentialType {
            SECRET, JWT, X509
        }
        
        /**
         * Check if secret rotation is due
         */
        public boolean isRotationDue(Date lastRotation) {
            if (!rotateSecret) return false;
            
            long daysSinceRotation = (System.currentTimeMillis() - lastRotation.getTime()) / (1000 * 60 * 60 * 24);
            return daysSinceRotation >= secretRotationDays;
        }
    }
    
    /**
     * Advanced Keycloak configuration options
     */
    @Data
    public static class AdvancedConfig {
        @NotNull
        private Boolean autodetectBearerOnly = true;
        
        @NotNull
        private Boolean alwaysRefreshToken = false;
        
        @NotNull
        private Boolean registerNodeAtStartup = true;
        
        @Min(-1)
        @Max(86400)
        private Integer registerNodePeriod = 60;
        
        @NotNull
        private Boolean tokenStore = true;
        
        @NotNull
        private TokenStore tokenStoreType = TokenStore.SESSION;
        
        @Min(1)
        @Max(10)
        private Integer principalAttribute = 1;
        
        @NotNull
        private Boolean turnOffChangeSessionIdOnLogin = false;
        
        @Min(0)
        @Max(3600)
        private Integer tokenMinimumTimeToLive = 10;
        
        @Min(1)
        @Max(86400)
        private Integer minTimeBetweenJwksRequests = 10;
        
        @Min(1)
        @Max(1000)
        private Integer publicKeyCacheTtl = 86400;
        
        @NotNull
        private Boolean ignoreOauthQueryParameter = false;
        
        @NotNull
        private Boolean verifyTokenAudience = true;
        
        @NotNull
        private Boolean enablePkce = true;
        
        public enum TokenStore {
            SESSION, COOKIE, HYBRID
        }
    }
    
    /**
     * Policy enforcement configuration for fine-grained authorization
     */
    @Data
    public static class PolicyEnforcer {
        @NotNull
        private Boolean enabled = false;
        
        @NotNull
        private EnforcementMode enforcementMode = EnforcementMode.ENFORCING;
        
        @Valid
        private PathConfig paths = new PathConfig();
        
        @NotNull
        private Boolean lazyLoadPaths = false;
        
        @Min(1)
        @Max(1000)
        private Integer pathCacheMaxEntries = 1000;
        
        @Min(1)
        @Max(86400)
        private Integer pathCacheLifespan = 3600;
        
        @NotNull
        private Boolean httpMethodAsScope = false;
        
        @Valid
        private ClaimConfig claimInformationPoint = new ClaimConfig();
        
        public enum EnforcementMode {
            ENFORCING,  // Deny all requests by default
            PERMISSIVE, // Allow requests when no policy found
            DISABLED    // Disable policy enforcement
        }
        
        @Data
        public static class PathConfig {
            private List<PathEntry> entries = new ArrayList<>();
            
            @Data
            public static class PathEntry {
                @NotBlank
                private String path;
                
                private String name;
                
                @NotNull
                private EnforcementMode enforcementMode = EnforcementMode.ENFORCING;
                
                private List<String> methods = new ArrayList<>();
                
                private List<String> scopes = new ArrayList<>();
                
                private Map<String, String> claimInformationPoint = new HashMap<>();
            }
        }
        
        @Data
        public static class ClaimConfig {
            private Map<String, Map<String, Object>> claims = new HashMap<>();
            
            @NotNull
            private Boolean includeAccessToken = true;
            
            @NotNull
            private Boolean includeIdToken = false;
            
            @NotNull
            private Boolean includeUserInfo = false;
        }
    }
    
    /**
     * Token validation and handling configuration
     */
    @Data
    public static class TokenConfig {
        @Min(1)
        @Max(86400)
        private Integer timeSkewSeconds = 30;
        
        @NotNull
        private Boolean validateExpiry = true;
        
        @NotNull
        private Boolean validateSignature = true;
        
        @NotNull
        private Boolean validateIssuer = true;
        
        @NotNull
        private Boolean validateAudience = true;
        
        @NotNull
        private Boolean validateNotBefore = true;
        
        @NotNull
        private Boolean requireExpirationTime = true;
        
        @NotNull
        private Boolean requireIssuedAt = true;
        
        @NotNull
        private Boolean cacheTokens = true;
        
        @Min(100)
        @Max(100000)
        private Integer cacheSize = 1000;
        
        @Min(60)
        @Max(86400)
        private Integer cacheTtlSeconds = 3600;
        
        @NotNull
        private Boolean introspectTokens = false;
        
        private String introspectionEndpoint;
        
        private String introspectionClientId;
        
        private String introspectionClientSecret;
        
        /**
         * Validate token configuration consistency
         */
        public boolean isValid() {
            if (introspectTokens) {
                return introspectionEndpoint != null && 
                       introspectionClientId != null && 
                       introspectionClientSecret != null;
            }
            return true;
        }
    }
    
    /**
     * Connection configuration for Keycloak server communication
     */
    @Data
    public static class ConnectionConfig {
        @Min(1000)
        @Max(120000)
        private Integer connectionTimeout = 10000;
        
        @Min(1000)
        @Max(120000)
        private Integer socketTimeout = 30000;
        
        @Min(1)
        @Max(1000)
        private Integer connectionPoolSize = 100;
        
        @NotNull
        private Boolean disableTrustManager = false;
        
        @NotNull
        private Boolean allowAnyHostname = false;
        
        private String truststore;
        
        private String truststorePassword;
        
        private String clientKeystore;
        
        private String clientKeystorePassword;
        
        private String clientKeyPassword;
        
        @NotNull
        private ProxyConfig proxy = new ProxyConfig();
        
        @Data
        public static class ProxyConfig {
            @NotNull
            private Boolean enabled = false;
            
            private String host;
            
            @Min(1)
            @Max(65535)
            private Integer port;
            
            private String username;
            
            private String password;
            
            @NotNull
            private ProxyType type = ProxyType.HTTP;
            
            public enum ProxyType {
                HTTP, SOCKS4, SOCKS5
            }
            
            public boolean isConfigured() {
                return enabled && host != null && port != null;
            }
        }
        
        /**
         * Get connection pool configuration
         */
        public Map<String, Object> getPoolConfig() {
            Map<String, Object> config = new HashMap<>();
            config.put("maxTotal", connectionPoolSize);
            config.put("maxPerRoute", Math.max(connectionPoolSize / 4, 25));
            config.put("validateAfterInactivity", 2000);
            config.put("connectionTimeout", connectionTimeout);
            config.put("socketTimeout", socketTimeout);
            return config;
        }
    }
    
    /**
     * Build complete Keycloak URLs
     */
    public String getRealmUrl() {
        return String.format("%s/realms/%s", authServerUrl, realm);
    }
    
    public String getAuthUrl() {
        return String.format("%s/protocol/openid-connect/auth", getRealmUrl());
    }
    
    public String getTokenUrl() {
        return String.format("%s/protocol/openid-connect/token", getRealmUrl());
    }
    
    public String getLogoutUrl() {
        return String.format("%s/protocol/openid-connect/logout", getRealmUrl());
    }
    
    public String getUserInfoUrl() {
        return String.format("%s/protocol/openid-connect/userinfo", getRealmUrl());
    }
    
    public String getCertsUrl() {
        return String.format("%s/protocol/openid-connect/certs", getRealmUrl());
    }
    
    public String getIntrospectionUrl() {
        return String.format("%s/protocol/openid-connect/token/introspect", getRealmUrl());
    }
    
    public String getAccountUrl() {
        return String.format("%s/account", authServerUrl);
    }
    
    public String getAdminUrl() {
        return String.format("%s/admin/realms/%s", authServerUrl, realm);
    }
    
    /**
     * Build issuer URI for JWT validation
     */
    public String getIssuerUri() {
        return getRealmUrl();
    }
    
    /**
     * Build JWK Set URI for JWT signature validation
     */
    public String getJwkSetUri() {
        return getCertsUrl();
    }
    
    /**
     * Validate configuration on startup
     */
    @PostConstruct
    public void validateConfiguration() {
        log.info("Validating Keycloak configuration...");
        
        // Validate auth server URL
        try {
            URI uri = new URI(authServerUrl);
            if (uri.getHost() == null) {
                throw new IllegalArgumentException("Invalid Keycloak auth server URL: " + authServerUrl);
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid Keycloak auth server URL format: " + authServerUrl, e);
        }
        
        // Validate SSL configuration
        if (sslRequired == SslRequired.ALL && !authServerUrl.startsWith("https://")) {
            log.warn("SSL required is set to ALL but auth server URL is not HTTPS");
        }
        
        // Validate bearer-only vs public client
        if (bearerOnly && publicClient) {
            throw new IllegalArgumentException("Cannot be both bearer-only and public client");
        }
        
        // Validate credentials
        if (!publicClient && credentials.getSecret() == null) {
            throw new IllegalArgumentException("Client secret is required for confidential clients");
        }
        
        // Validate token configuration
        if (!token.isValid()) {
            throw new IllegalArgumentException("Invalid token introspection configuration");
        }
        
        // Validate proxy configuration
        if (connection.getProxy().isEnabled() && !connection.getProxy().isConfigured()) {
            throw new IllegalArgumentException("Proxy is enabled but not properly configured");
        }
        
        // Validate policy enforcer
        if (policyEnforcer.isEnabled() && !bearerOnly) {
            log.warn("Policy enforcement is typically used with bearer-only clients");
        }
        
        log.info("Keycloak configuration validation completed successfully");
        logConfigurationSummary();
    }
    
    /**
     * Log configuration summary
     */
    private void logConfigurationSummary() {
        log.info("=== Keycloak Configuration Summary ===");
        log.info("Enabled: {}", enabled);
        log.info("Realm: {}", realm);
        log.info("Auth Server: {}", authServerUrl);
        log.info("Resource: {}", resource);
        log.info("Bearer Only: {}", bearerOnly);
        log.info("SSL Required: {}", sslRequired);
        log.info("CORS Enabled: {}", cors);
        log.info("Token Validation: signature={}, expiry={}, audience={}", 
            token.getValidateSignature(), token.getValidateExpiry(), token.getValidateAudience());
        log.info("Policy Enforcer: enabled={}, mode={}", 
            policyEnforcer.isEnabled(), policyEnforcer.getEnforcementMode());
        log.info("Connection: timeout={}ms, poolSize={}", 
            connection.getConnectionTimeout(), connection.getConnectionPoolSize());
        log.info("URLs:");
        log.info("  - Realm: {}", getRealmUrl());
        log.info("  - Token: {}", getTokenUrl());
        log.info("  - Certs: {}", getCertsUrl());
        log.info("  - UserInfo: {}", getUserInfoUrl());
        log.info("=====================================");
    }
    
    /**
     * Runtime configuration cache for performance
     */
    private final Map<String, Object> configCache = new ConcurrentHashMap<>();
    
    /**
     * Get cached configuration value
     */
    @SuppressWarnings("unchecked")
    public <T> T getCachedConfig(String key, java.util.function.Supplier<T> supplier) {
        return (T) configCache.computeIfAbsent(key, k -> supplier.get());
    }
    
    /**
     * Clear configuration cache
     */
    public void clearConfigCache() {
        configCache.clear();
        log.info("Keycloak configuration cache cleared");
    }
    
    /**
     * Check if a specific feature is enabled
     */
    public boolean isFeatureEnabled(String feature) {
        switch (feature.toLowerCase()) {
            case "cors":
                return cors;
            case "bearer-only":
                return bearerOnly;
            case "policy-enforcement":
                return policyEnforcer.isEnabled();
            case "token-cache":
                return token.getCacheTokens();
            case "pkce":
                return advanced.getEnablePkce();
            default:
                return false;
        }
    }
}