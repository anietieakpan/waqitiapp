package com.waqiti.ledger.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Comprehensive security configuration properties for the application
 * Provides complete security settings for JWT, OAuth2, encryption, and access control
 */
@Slf4j
@Data
@Validated
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "ledger.security")
public class SecurityProperties {
    
    @Valid
    @NotNull
    private JwtConfig jwt = new JwtConfig();
    
    @Valid
    @NotNull
    private OAuth2Config oauth2 = new OAuth2Config();
    
    @Valid
    @NotNull
    private EncryptionConfig encryption = new EncryptionConfig();
    
    @Valid
    @NotNull
    private AccessControlConfig accessControl = new AccessControlConfig();
    
    @Valid
    @NotNull
    private AuditConfig audit = new AuditConfig();
    
    @Valid
    @NotNull
    private RateLimitingConfig rateLimiting = new RateLimitingConfig();
    
    @Valid
    @NotNull
    private CsrfConfig csrf = new CsrfConfig();
    
    @Valid
    @NotNull
    private SessionConfig session = new SessionConfig();
    
    @Valid
    @NotNull
    private PasswordPolicyConfig passwordPolicy = new PasswordPolicyConfig();
    
    @Valid
    @NotNull
    private ThreatProtectionConfig threatProtection = new ThreatProtectionConfig();
    
    /**
     * JWT configuration for token-based authentication
     */
    @Data
    public static class JwtConfig {
        @NotBlank
        @Size(min = 32, max = 512)
        private String secret;
        
        @NotBlank
        private String issuer = "waqiti-platform";
        
        @NotBlank
        private String audience = "waqiti-services";
        
        @Min(60000) // 1 minute
        @Max(86400000) // 24 hours
        private Long expiration = 3600000L; // 1 hour
        
        @Min(60000) // 1 minute
        @Max(604800000) // 7 days
        private Long refreshExpiration = 86400000L; // 24 hours
        
        @NotNull
        private Boolean validateExpiration = true;
        
        @NotNull
        private Boolean validateIssuer = true;
        
        @NotNull
        private Boolean validateAudience = true;
        
        @NotNull
        private Boolean validateSignature = true;
        
        @Min(0)
        @Max(300)
        private Integer clockSkewSeconds = 60;
        
        @NotBlank
        private String algorithm = "HS512";
        
        @NotNull
        private Boolean includeAuthorities = true;
        
        @NotNull
        private Boolean includeClaims = true;
        
        private Map<String, Object> customClaims = new HashMap<>();
        
        @NotNull
        private TokenBlacklistConfig blacklist = new TokenBlacklistConfig();
        
        @Data
        public static class TokenBlacklistConfig {
            @NotNull
            private Boolean enabled = true;
            
            @Min(100)
            @Max(100000)
            private Integer maxSize = 10000;
            
            @Min(60)
            @Max(86400)
            private Integer ttlSeconds = 3600;
            
            @NotNull
            private Boolean persistBlacklist = true;
            
            private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();
            
            public boolean isBlacklisted(String token) {
                return enabled && blacklistedTokens.contains(token);
            }
            
            public void addToBlacklist(String token) {
                if (enabled) {
                    if (blacklistedTokens.size() >= maxSize) {
                        // Remove oldest entries (simplified - in production use proper LRU cache)
                        blacklistedTokens.clear();
                    }
                    blacklistedTokens.add(token);
                }
            }
            
            public void removeFromBlacklist(String token) {
                blacklistedTokens.remove(token);
            }
        }
        
        /**
         * Generate a secure secret key if not provided
         */
        public String generateSecretIfNeeded() {
            if (secret == null || secret.length() < 32) {
                try {
                    KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA512");
                    keyGen.init(512);
                    SecretKey secretKey = keyGen.generateKey();
                    secret = Base64.getEncoder().encodeToString(secretKey.getEncoded());
                    log.warn("Generated new JWT secret key - store this securely!");
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException("Failed to generate JWT secret", e);
                }
            }
            return secret;
        }
        
        public Duration getExpirationDuration() {
            return Duration.ofMillis(expiration);
        }
        
        public Duration getRefreshExpirationDuration() {
            return Duration.ofMillis(refreshExpiration);
        }
    }
    
    /**
     * OAuth2 configuration for external authentication providers
     */
    @Data
    public static class OAuth2Config {
        @NotNull
        private Boolean enabled = true;
        
        @Valid
        private ClientConfig client = new ClientConfig();
        
        @Valid
        private ResourceServerConfig resourceServer = new ResourceServerConfig();
        
        @Valid
        private Map<String, ProviderConfig> providers = new HashMap<>();
        
        @Data
        public static class ClientConfig {
            @NotBlank
            private String clientId;
            
            @NotBlank
            private String clientSecret;
            
            @NotNull
            private Set<String> scopes = new HashSet<>(Arrays.asList(
                "openid", "profile", "email"
            ));
            
            @NotNull
            private Set<String> grantTypes = new HashSet<>(Arrays.asList(
                "authorization_code", "refresh_token", "client_credentials"
            ));
            
            @NotBlank
            private String redirectUri = "http://localhost:8080/login/oauth2/code/";
            
            @NotNull
            private Boolean pkceEnabled = true;
            
            @NotNull
            private AuthenticationMethod authenticationMethod = AuthenticationMethod.CLIENT_SECRET_POST;
            
            public enum AuthenticationMethod {
                CLIENT_SECRET_BASIC,
                CLIENT_SECRET_POST,
                CLIENT_SECRET_JWT,
                PRIVATE_KEY_JWT
            }
        }
        
        @Data
        public static class ResourceServerConfig {
            @NotBlank
            private String issuerUri;
            
            @NotBlank
            private String jwkSetUri;
            
            private String userinfoUri;
            
            @NotNull
            private Boolean opaqueToken = false;
            
            private String introspectionUri;
            
            private String introspectionClientId;
            
            private String introspectionClientSecret;
            
            @NotNull
            private Boolean preferJwt = true;
            
            public boolean isConfigured() {
                if (opaqueToken) {
                    return introspectionUri != null && 
                           introspectionClientId != null && 
                           introspectionClientSecret != null;
                } else {
                    return jwkSetUri != null || issuerUri != null;
                }
            }
        }
        
        @Data
        public static class ProviderConfig {
            @NotBlank
            private String clientId;
            
            @NotBlank
            private String clientSecret;
            
            @NotBlank
            private String authorizationUri;
            
            @NotBlank
            private String tokenUri;
            
            @NotBlank
            private String userInfoUri;
            
            private String jwkSetUri;
            
            @NotNull
            private Set<String> scopes = new HashSet<>();
            
            @NotBlank
            private String userNameAttribute = "sub";
            
            @NotNull
            private Map<String, String> additionalParameters = new HashMap<>();
        }
    }
    
    /**
     * Encryption configuration for data protection
     */
    @Data
    public static class EncryptionConfig {
        @NotNull
        private Boolean enabled = true;
        
        @NotBlank
        private String algorithm = "AES/GCM/NoPadding";
        
        @Min(128)
        @Max(256)
        private Integer keySize = 256;
        
        @NotBlank
        @Size(min = 32, max = 512)
        private String masterKey;
        
        @NotNull
        private Boolean rotateKeys = true;
        
        @Min(1)
        @Max(365)
        private Integer keyRotationDays = 90;
        
        @NotNull
        private KeyManagement keyManagement = KeyManagement.LOCAL;
        
        private String keyManagementUrl;
        
        private String keyManagementToken;
        
        @NotNull
        private Map<String, String> dataClassificationKeys = new HashMap<>();
        
        @NotNull
        private Boolean useHardwareSecurityModule = false;
        
        private String hsmProvider;
        
        private Map<String, String> hsmConfig = new HashMap<>();
        
        public enum KeyManagement {
            LOCAL, VAULT, AWS_KMS, AZURE_KEY_VAULT, GCP_KMS, HSM
        }
        
        /**
         * Generate encryption key for data classification
         */
        public String getKeyForClassification(String classification) {
            return dataClassificationKeys.computeIfAbsent(classification, k -> {
                try {
                    KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                    keyGen.init(keySize);
                    SecretKey secretKey = keyGen.generateKey();
                    String key = Base64.getEncoder().encodeToString(secretKey.getEncoded());
                    log.info("Generated new encryption key for classification: {}", classification);
                    return key;
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException("Failed to generate encryption key", e);
                }
            });
        }
        
        /**
         * Check if key rotation is due
         */
        public boolean isKeyRotationDue(Date lastRotation) {
            if (!rotateKeys) return false;
            
            long daysSinceRotation = (System.currentTimeMillis() - lastRotation.getTime()) / (1000 * 60 * 60 * 24);
            return daysSinceRotation >= keyRotationDays;
        }
    }
    
    /**
     * Access control configuration for authorization
     */
    @Data
    public static class AccessControlConfig {
        @NotNull
        private Boolean enabled = true;
        
        @NotNull
        private AccessControlModel model = AccessControlModel.RBAC;
        
        @NotNull
        private Boolean cachePolicies = true;
        
        @Min(60)
        @Max(3600)
        private Integer policyCacheTtlSeconds = 300;
        
        @NotNull
        private Map<String, List<String>> rolePermissions = new HashMap<>();
        
        @NotNull
        private Map<String, List<String>> publicEndpoints = Map.of(
            "health", Arrays.asList("/actuator/health", "/health"),
            "info", Arrays.asList("/actuator/info", "/info"),
            "docs", Arrays.asList("/swagger-ui/**", "/v3/api-docs/**")
        );
        
        @NotNull
        private Map<String, List<String>> securedEndpoints = new HashMap<>();
        
        @NotNull
        private Boolean enforceMethodSecurity = true;
        
        @NotNull
        private Boolean enableFieldLevelSecurity = false;
        
        @Valid
        private IpWhitelistConfig ipWhitelist = new IpWhitelistConfig();
        
        public enum AccessControlModel {
            RBAC,  // Role-Based Access Control
            ABAC,  // Attribute-Based Access Control
            PBAC,  // Policy-Based Access Control
            HYBRID // Combination of models
        }
        
        @Data
        public static class IpWhitelistConfig {
            @NotNull
            private Boolean enabled = false;
            
            @NotNull
            private Set<String> allowedIps = new HashSet<>(Arrays.asList(
                "127.0.0.1", "::1"
            ));
            
            @NotNull
            private Set<String> allowedCidrs = new HashSet<>();
            
            @NotNull
            private Boolean allowPrivateNetworks = true;
            
            public boolean isIpAllowed(String ip) {
                if (!enabled) return true;
                
                if (allowedIps.contains(ip)) return true;
                
                if (allowPrivateNetworks && isPrivateIp(ip)) return true;
                
                // Check CIDR ranges
                return allowedCidrs.stream().anyMatch(cidr -> isIpInCidr(ip, cidr));
            }
            
            private boolean isPrivateIp(String ip) {
                return ip.startsWith("10.") || 
                       ip.startsWith("172.") || 
                       ip.startsWith("192.168.") ||
                       ip.equals("127.0.0.1") ||
                       ip.equals("::1");
            }
            
            private boolean isIpInCidr(String ip, String cidr) {
                // Simplified CIDR check - in production use proper IP address parsing
                String[] cidrParts = cidr.split("/");
                if (cidrParts.length != 2) return false;
                
                String cidrBase = cidrParts[0];
                return ip.startsWith(cidrBase.substring(0, cidrBase.lastIndexOf(".")));
            }
        }
        
        /**
         * Check if an endpoint is public
         */
        public boolean isPublicEndpoint(String path) {
            return publicEndpoints.values().stream()
                .flatMap(List::stream)
                .anyMatch(pattern -> pathMatches(path, pattern));
        }
        
        /**
         * Check if a path matches a pattern (supports wildcards)
         */
        private boolean pathMatches(String path, String pattern) {
            String regex = pattern.replace("**", ".*").replace("*", "[^/]*");
            return Pattern.compile(regex).matcher(path).matches();
        }
    }
    
    /**
     * Audit configuration for security events
     */
    @Data
    public static class AuditConfig {
        @NotNull
        private Boolean enabled = true;
        
        @NotNull
        private Set<AuditEventType> loggedEvents = new HashSet<>(Arrays.asList(
            AuditEventType.AUTHENTICATION_SUCCESS,
            AuditEventType.AUTHENTICATION_FAILURE,
            AuditEventType.AUTHORIZATION_FAILURE,
            AuditEventType.DATA_ACCESS,
            AuditEventType.DATA_MODIFICATION,
            AuditEventType.CONFIGURATION_CHANGE
        ));
        
        @NotNull
        private Boolean includeRequestBody = false;
        
        @NotNull
        private Boolean includeResponseBody = false;
        
        @NotNull
        private Boolean includeHeaders = true;
        
        @NotNull
        private Set<String> maskedHeaders = new HashSet<>(Arrays.asList(
            "Authorization", "Cookie", "X-Auth-Token"
        ));
        
        @NotNull
        private Boolean persistToDatabase = true;
        
        @NotNull
        private Boolean sendToSiem = false;
        
        private String siemEndpoint;
        
        private String siemApiKey;
        
        @Min(1)
        @Max(365)
        private Integer retentionDays = 90;
        
        public enum AuditEventType {
            AUTHENTICATION_SUCCESS,
            AUTHENTICATION_FAILURE,
            AUTHORIZATION_SUCCESS,
            AUTHORIZATION_FAILURE,
            DATA_ACCESS,
            DATA_MODIFICATION,
            DATA_DELETION,
            CONFIGURATION_CHANGE,
            SECURITY_VIOLATION,
            RATE_LIMIT_EXCEEDED,
            SUSPICIOUS_ACTIVITY
        }
        
        /**
         * Check if an event type should be logged
         */
        public boolean shouldLogEvent(AuditEventType eventType) {
            return enabled && loggedEvents.contains(eventType);
        }
        
        /**
         * Mask sensitive header value
         */
        public String maskHeaderValue(String headerName, String value) {
            if (value == null) return null;
            
            if (maskedHeaders.contains(headerName)) {
                return value.length() > 4 ? 
                    value.substring(0, 4) + "****" : "****";
            }
            return value;
        }
    }
    
    /**
     * Rate limiting configuration for API protection
     */
    @Data
    public static class RateLimitingConfig {
        @NotNull
        private Boolean enabled = true;
        
        @Min(1)
        @Max(10000)
        private Integer defaultLimit = 100;
        
        @NotNull
        private Duration defaultWindow = Duration.ofMinutes(1);
        
        @NotNull
        private RateLimitStrategy strategy = RateLimitStrategy.SLIDING_WINDOW;
        
        @NotNull
        private Map<String, EndpointLimit> endpointLimits = new HashMap<>();
        
        @NotNull
        private Map<String, Integer> userLimits = new HashMap<>();
        
        @NotNull
        private Boolean useRedis = true;
        
        @NotNull
        private Boolean includeHeaders = true;
        
        @NotNull
        private Set<String> rateLimitHeaders = new HashSet<>(Arrays.asList(
            "X-RateLimit-Limit",
            "X-RateLimit-Remaining",
            "X-RateLimit-Reset"
        ));
        
        public enum RateLimitStrategy {
            FIXED_WINDOW,
            SLIDING_WINDOW,
            TOKEN_BUCKET,
            LEAKY_BUCKET
        }
        
        @Data
        public static class EndpointLimit {
            @Min(1)
            @Max(10000)
            private Integer limit;
            
            @NotNull
            private Duration window;
            
            private Set<String> methods = new HashSet<>();
            
            private Map<String, Integer> userOverrides = new HashMap<>();
        }
        
        /**
         * Get rate limit for endpoint
         */
        public EndpointLimit getLimitForEndpoint(String endpoint, String method) {
            return endpointLimits.entrySet().stream()
                .filter(entry -> endpoint.startsWith(entry.getKey()))
                .filter(entry -> entry.getValue().getMethods().isEmpty() || 
                               entry.getValue().getMethods().contains(method))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        }
    }
    
    /**
     * CSRF protection configuration
     */
    @Data
    public static class CsrfConfig {
        @NotNull
        private Boolean enabled = true;
        
        @NotBlank
        private String tokenHeader = "X-CSRF-TOKEN";
        
        @NotBlank
        private String tokenParameter = "_csrf";
        
        @NotBlank
        private String cookieName = "XSRF-TOKEN";
        
        @NotNull
        private Boolean cookieHttpOnly = false;
        
        @NotNull
        private Boolean cookieSecure = true;
        
        @NotBlank
        private String cookiePath = "/";
        
        @NotNull
        private Set<String> excludedPaths = new HashSet<>(Arrays.asList(
            "/api/**", "/internal/**"
        ));
        
        @NotNull
        private Set<String> excludedMethods = new HashSet<>(Arrays.asList(
            "GET", "HEAD", "OPTIONS"
        ));
        
        /**
         * Check if CSRF protection should be applied
         */
        public boolean shouldApplyCsrf(String path, String method) {
            if (!enabled) return false;
            
            if (excludedMethods.contains(method.toUpperCase())) return false;
            
            return excludedPaths.stream()
                .noneMatch(pattern -> pathMatches(path, pattern));
        }
        
        private boolean pathMatches(String path, String pattern) {
            String regex = pattern.replace("**", ".*").replace("*", "[^/]*");
            return Pattern.compile(regex).matcher(path).matches();
        }
    }
    
    /**
     * Session management configuration
     */
    @Data
    public static class SessionConfig {
        @NotNull
        private Boolean enabled = true;
        
        @Min(60)
        @Max(86400)
        private Integer timeoutSeconds = 1800; // 30 minutes
        
        @NotNull
        private Boolean concurrentSessionsAllowed = false;
        
        @Min(1)
        @Max(10)
        private Integer maxConcurrentSessions = 1;
        
        @NotNull
        private SessionCreationPolicy creationPolicy = SessionCreationPolicy.IF_REQUIRED;
        
        @NotNull
        private Boolean enableSessionFixationProtection = true;
        
        @NotNull
        private SessionFixationStrategy fixationStrategy = SessionFixationStrategy.MIGRATE_SESSION;
        
        @NotNull
        private Boolean invalidateOnLogout = true;
        
        @NotNull
        private Boolean trackActiveSessions = true;
        
        @NotBlank
        private String sessionCookieName = "JSESSIONID";
        
        @NotNull
        private Boolean sessionCookieSecure = true;
        
        @NotNull
        private Boolean sessionCookieHttpOnly = true;
        
        @NotBlank
        private String sessionCookiePath = "/";
        
        public enum SessionCreationPolicy {
            ALWAYS,
            IF_REQUIRED,
            NEVER,
            STATELESS
        }
        
        public enum SessionFixationStrategy {
            NONE,
            NEW_SESSION,
            MIGRATE_SESSION,
            CHANGE_SESSION_ID
        }
        
        public Duration getTimeoutDuration() {
            return Duration.ofSeconds(timeoutSeconds);
        }
    }
    
    /**
     * Password policy configuration
     */
    @Data
    public static class PasswordPolicyConfig {
        @NotNull
        private Boolean enabled = true;
        
        @Min(8)
        @Max(128)
        private Integer minLength = 12;
        
        @Min(0)
        @Max(64)
        private Integer maxLength = 128;
        
        @NotNull
        private Boolean requireUppercase = true;
        
        @NotNull
        private Boolean requireLowercase = true;
        
        @NotNull
        private Boolean requireDigits = true;
        
        @NotNull
        private Boolean requireSpecialChars = true;
        
        @NotBlank
        private String specialChars = "!@#$%^&*()_+-=[]{}|;:,.<>?";
        
        @Min(0)
        @Max(10)
        private Integer minUppercase = 1;
        
        @Min(0)
        @Max(10)
        private Integer minLowercase = 1;
        
        @Min(0)
        @Max(10)
        private Integer minDigits = 1;
        
        @Min(0)
        @Max(10)
        private Integer minSpecialChars = 1;
        
        @NotNull
        private Boolean preventCommonPasswords = true;
        
        @NotNull
        private Boolean preventUserInfoInPassword = true;
        
        @Min(0)
        @Max(24)
        private Integer passwordHistorySize = 5;
        
        @Min(0)
        @Max(365)
        private Integer passwordExpiryDays = 90;
        
        @Min(0)
        @Max(30)
        private Integer passwordExpiryWarningDays = 14;
        
        @NotNull
        private Boolean enforcePasswordChange = true;
        
        /**
         * Validate password against policy
         */
        public List<String> validatePassword(String password, String username) {
            List<String> violations = new ArrayList<>();
            
            if (!enabled) return violations;
            
            if (password.length() < minLength) {
                violations.add("Password must be at least " + minLength + " characters");
            }
            
            if (password.length() > maxLength) {
                violations.add("Password must not exceed " + maxLength + " characters");
            }
            
            if (requireUppercase && !password.matches(".*[A-Z].*")) {
                violations.add("Password must contain at least " + minUppercase + " uppercase letter(s)");
            }
            
            if (requireLowercase && !password.matches(".*[a-z].*")) {
                violations.add("Password must contain at least " + minLowercase + " lowercase letter(s)");
            }
            
            if (requireDigits && !password.matches(".*\\d.*")) {
                violations.add("Password must contain at least " + minDigits + " digit(s)");
            }
            
            if (requireSpecialChars && !containsSpecialChar(password)) {
                violations.add("Password must contain at least " + minSpecialChars + " special character(s)");
            }
            
            if (preventUserInfoInPassword && username != null && 
                password.toLowerCase().contains(username.toLowerCase())) {
                violations.add("Password must not contain username");
            }
            
            return violations;
        }
        
        private boolean containsSpecialChar(String password) {
            return password.chars().anyMatch(ch -> specialChars.indexOf(ch) >= 0);
        }
    }
    
    /**
     * Threat protection configuration
     */
    @Data
    public static class ThreatProtectionConfig {
        @NotNull
        private Boolean enabled = true;
        
        @NotNull
        private SqlInjectionConfig sqlInjection = new SqlInjectionConfig();
        
        @NotNull
        private XssProtectionConfig xssProtection = new XssProtectionConfig();
        
        @NotNull
        private XxeProtectionConfig xxeProtection = new XxeProtectionConfig();
        
        @NotNull
        private PathTraversalConfig pathTraversal = new PathTraversalConfig();
        
        @Data
        public static class SqlInjectionConfig {
            @NotNull
            private Boolean enabled = true;
            
            @NotNull
            private Set<String> dangerousPatterns = new HashSet<>(Arrays.asList(
                "(?i).*([';]+|(--)+|(/\\*)+|(\\*/)+|(xp_)|(sp_)|(exec(\\s|\\()+)|(union(\\s|\\()+)|(insert(\\s|\\()+)|(select(\\s|\\()+)|(delete(\\s|\\()+)|(update(\\s|\\()+)|(drop(\\s|\\()+)|(create(\\s|\\()+)).*"
            ));
            
            @NotNull
            private Boolean blockOnDetection = true;
            
            @NotNull
            private Boolean logAttempts = true;
        }
        
        @Data
        public static class XssProtectionConfig {
            @NotNull
            private Boolean enabled = true;
            
            @NotNull
            private Boolean sanitizeInput = true;
            
            @NotNull
            private Boolean encodeOutput = true;
            
            @NotNull
            private Set<String> dangerousPatterns = new HashSet<>(Arrays.asList(
                "<script", "javascript:", "onerror=", "onload=", "eval(", "alert("
            ));
            
            @NotNull
            private Boolean useContentSecurityPolicy = true;
            
            @NotBlank
            private String contentSecurityPolicy = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'";
        }
        
        @Data
        public static class XxeProtectionConfig {
            @NotNull
            private Boolean enabled = true;
            
            @NotNull
            private Boolean disableExternalEntities = true;
            
            @NotNull
            private Boolean disableDtdProcessing = true;
            
            @NotNull
            private Boolean secureXmlProcessing = true;
        }
        
        @Data
        public static class PathTraversalConfig {
            @NotNull
            private Boolean enabled = true;
            
            @NotNull
            private Set<String> dangerousPatterns = new HashSet<>(Arrays.asList(
                "../", "..\\", "%2e%2e/", "%2e%2e\\"
            ));
            
            @NotNull
            private Boolean blockOnDetection = true;
            
            @NotNull
            private Boolean normalizeFilePaths = true;
        }
    }
    
    /**
     * Validate configuration on startup
     */
    @PostConstruct
    public void validateConfiguration() {
        log.info("Validating Security configuration...");
        
        // Ensure JWT secret is set
        if (jwt.getSecret() == null || jwt.getSecret().length() < 32) {
            jwt.generateSecretIfNeeded();
        }
        
        // Validate OAuth2 configuration
        if (oauth2.isEnabled() && !oauth2.getResourceServer().isConfigured()) {
            log.warn("OAuth2 is enabled but resource server is not properly configured");
        }
        
        // Validate encryption master key
        if (encryption.isEnabled() && (encryption.getMasterKey() == null || encryption.getMasterKey().length() < 32)) {
            log.error("Encryption is enabled but master key is not properly set");
            throw new IllegalArgumentException("Encryption master key must be at least 32 characters");
        }
        
        // Validate rate limiting
        if (rateLimiting.isEnabled() && rateLimiting.isUseRedis()) {
            log.info("Rate limiting will use Redis for distributed rate limiting");
        }
        
        // Validate session configuration
        if (session.getCreationPolicy() == SessionConfig.SessionCreationPolicy.STATELESS && 
            csrf.isEnabled()) {
            log.warn("CSRF protection is enabled with stateless sessions - this may cause issues");
        }
        
        log.info("Security configuration validation completed successfully");
        logConfigurationSummary();
    }
    
    private void logConfigurationSummary() {
        log.info("=== Security Configuration Summary ===");
        log.info("JWT: algorithm={}, expiration={}ms, refresh={}ms", 
            jwt.getAlgorithm(), jwt.getExpiration(), jwt.getRefreshExpiration());
        log.info("OAuth2: enabled={}, providers={}", 
            oauth2.isEnabled(), oauth2.getProviders().keySet());
        log.info("Encryption: enabled={}, algorithm={}, keySize={}", 
            encryption.isEnabled(), encryption.getAlgorithm(), encryption.getKeySize());
        log.info("Access Control: model={}, methodSecurity={}, ipWhitelist={}", 
            accessControl.getModel(), accessControl.getEnforceMethodSecurity(), 
            accessControl.getIpWhitelist().isEnabled());
        log.info("Audit: enabled={}, events={}, retention={}days", 
            audit.isEnabled(), audit.getLoggedEvents().size(), audit.getRetentionDays());
        log.info("Rate Limiting: enabled={}, default={}req/{}s", 
            rateLimiting.isEnabled(), rateLimiting.getDefaultLimit(), 
            rateLimiting.getDefaultWindow().getSeconds());
        log.info("CSRF: enabled={}, header={}", 
            csrf.isEnabled(), csrf.getTokenHeader());
        log.info("Session: policy={}, timeout={}s, concurrent={}", 
            session.getCreationPolicy(), session.getTimeoutSeconds(), 
            session.getConcurrentSessionsAllowed());
        log.info("Password Policy: minLength={}, expiry={}days", 
            passwordPolicy.getMinLength(), passwordPolicy.getPasswordExpiryDays());
        log.info("Threat Protection: sql={}, xss={}, xxe={}", 
            threatProtection.getSqlInjection().isEnabled(), 
            threatProtection.getXssProtection().isEnabled(), 
            threatProtection.getXxeProtection().isEnabled());
        log.info("=====================================");
    }
}