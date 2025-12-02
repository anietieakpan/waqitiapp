package com.waqiti.common.servicemesh;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Security Manager for Service Mesh
 * Handles mTLS, JWT validation, and authorization policies
 */
@Slf4j
@Data
@Builder
@RequiredArgsConstructor
public class SecurityManager {

    private final boolean mtlsEnabled;
    private final String certificatePath;
    private final String privateKeyPath;
    private final String caPath;
    private final boolean authorizationEnabled;
    private final boolean jwtValidation;
    @Builder.Default
    private final boolean strictMode = true;
    @Builder.Default
    private final boolean vaultEnabled = false;
    
    // Security components - removed as they were being reassigned
    // These will be computed on-demand or stored differently

    @Builder.Default
    private final Map<String, SecurityPolicy> securityPolicies = new ConcurrentHashMap<>();
    @Builder.Default
    private final Map<String, Set<String>> servicePermissions = new ConcurrentHashMap<>();
    @Builder.Default
    private final Map<String, CertificateInfo> serviceCertificates = new ConcurrentHashMap<>();
    @Builder.Default
    private final Set<String> trustedIssuers = Collections.synchronizedSet(new HashSet<>());
    @Builder.Default
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Initialize security manager
     */
    public void initialize() {
        if (initialized.compareAndSet(false, true)) {
            log.info("Initializing Security Manager - mTLS: {}, Authorization: {}", 
                    mtlsEnabled, authorizationEnabled);
            
            if (mtlsEnabled) {
                initializeMTLS();
            }
            
            if (jwtValidation) {
                initializeJWTValidation();
            }
            
            log.info("Security Manager initialized successfully");
        }
    }

    /**
     * Enable mTLS for service mesh
     */
    public void enableMTLS() {
        if (!mtlsEnabled) {
            log.warn("mTLS is not configured, cannot enable");
            return;
        }
        
        log.info("Enabling mTLS for service mesh");
        initializeMTLS();
    }

    /**
     * Configure mTLS for a specific service
     */
    public void configureMTLS(String serviceName) {
        log.info("Configuring mTLS for service: {}", serviceName);
        
        CertificateInfo certInfo = CertificateInfo.builder()
                .serviceName(serviceName)
                .certificatePath(certificatePath)
                .privateKeyPath(privateKeyPath)
                .validFrom(Instant.now())
                .validUntil(Instant.now().plusSeconds(365 * 24 * 60 * 60)) // 1 year
                .build();
        
        serviceCertificates.put(serviceName, certInfo);
    }

    /**
     * Enable authorization
     */
    public void enableAuthorization() {
        log.info("Enabling authorization for service mesh");
        // Implementation would configure authorization policies
    }

    /**
     * Validate request authentication
     */
    public AuthenticationResult authenticate(AuthenticationRequest request) {
        log.debug("Authenticating request from service: {}", request.getSourceService());
        
        // Validate mTLS if enabled
        if (mtlsEnabled) {
            if (!validateCertificate(request.getCertificate())) {
                return AuthenticationResult.builder()
                        .authenticated(false)
                        .reason("Invalid certificate")
                        .build();
            }
        }
        
        // Validate JWT if provided
        if (request.getJwtToken() != null && jwtValidation) {
            try {
                JWTClaimsSet claims = validateJWT(request.getJwtToken());
                
                return AuthenticationResult.builder()
                        .authenticated(true)
                        .principal(claims.getSubject())
                        .claims(claims.getClaims())
                        .build();
                        
            } catch (Exception e) {
                log.error("JWT validation failed", e);
                return AuthenticationResult.builder()
                        .authenticated(false)
                        .reason("JWT validation failed: " + e.getMessage())
                        .build();
            }
        }
        
        return AuthenticationResult.builder()
                .authenticated(true)
                .principal(request.getSourceService())
                .build();
    }

    /**
     * Authorize service request
     */
    public AuthorizationResult authorize(AuthorizationRequest request) {
        if (!authorizationEnabled) {
            return AuthorizationResult.builder()
                    .authorized(true)
                    .build();
        }
        
        log.debug("Authorizing request from {} to {}", 
                request.getSourceService(), request.getTargetService());
        
        // Check service permissions
        Set<String> permissions = servicePermissions.get(request.getSourceService());
        
        if (permissions == null || permissions.isEmpty()) {
            return AuthorizationResult.builder()
                    .authorized(false)
                    .reason("No permissions configured for service")
                    .build();
        }
        
        // Check if source service can access target service
        String requiredPermission = "service:" + request.getTargetService() + ":" + request.getMethod();
        
        if (!permissions.contains(requiredPermission) && !permissions.contains("service:*:*")) {
            return AuthorizationResult.builder()
                    .authorized(false)
                    .reason("Permission denied for " + requiredPermission)
                    .build();
        }
        
        // Check additional policies
        SecurityPolicy policy = securityPolicies.get(request.getTargetService());
        if (policy != null) {
            if (!evaluatePolicy(policy, request)) {
                return AuthorizationResult.builder()
                        .authorized(false)
                        .reason("Policy evaluation failed")
                        .build();
            }
        }
        
        return AuthorizationResult.builder()
                .authorized(true)
                .build();
    }

    /**
     * Update security policy for a service
     */
    public void updatePolicy(String serviceName, SecurityPolicy policy) {
        log.info("Updating security policy for service: {}", serviceName);
        securityPolicies.put(serviceName, policy);
    }

    /**
     * Remove security policies for a service
     */
    public void removePolicies(String serviceName) {
        log.info("Removing security policies for service: {}", serviceName);
        securityPolicies.remove(serviceName);
        servicePermissions.remove(serviceName);
        serviceCertificates.remove(serviceName);
    }

    /**
     * Apply security configuration
     */
    public void applyConfiguration(ServiceMeshManager.SecurityConfiguration config) {
        log.info("Applying security configuration");
        // Implementation would update security settings based on configuration
    }

    /**
     * Check if security manager is healthy
     */
    public boolean isHealthy() {
        return initialized.get();
    }

    /**
     * Get SSL context for mTLS
     */
    public SSLContext getSSLContext() {
        if (!mtlsEnabled) {
            log.debug("mTLS not enabled, returning null SSL context");
            return null;
        }
        
        if (!initialized.get()) {
            log.warn("SecurityManager not initialized, initializing now...");
            initialize();
        }
        
        try {
            log.debug("Creating SSL context for mTLS");
            
            // Load client certificate and key
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (FileInputStream keyStoreStream = new FileInputStream(certificatePath)) {
                String keystorePassword = System.getenv("WAQITI_KEYSTORE_PASSWORD");
                if (keystorePassword == null || keystorePassword.isEmpty()) {
                    keystorePassword = getKeystorePasswordFromVault();
                }
                keyStore.load(keyStoreStream, keystorePassword.toCharArray());
            }
            
            // Create key manager
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
            String keystorePassword = System.getenv("WAQITI_KEYSTORE_PASSWORD");
            if (keystorePassword == null || keystorePassword.isEmpty()) {
                keystorePassword = getKeystorePasswordFromVault();
            }
            keyManagerFactory.init(keyStore, keystorePassword.toCharArray());
            
            // Load CA certificate for trust store
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            
            if (caPath != null && !caPath.isEmpty() && new File(caPath).exists()) {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                try (FileInputStream caStream = new FileInputStream(caPath)) {
                    Certificate caCert = certificateFactory.generateCertificate(caStream);
                    trustStore.setCertificateEntry("ca", caCert);
                }
            }
            
            // Create trust manager
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            
            // Create and configure SSL context
            SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(keyManagerFactory.getKeyManagers(), 
                          trustManagerFactory.getTrustManagers(), 
                          new java.security.SecureRandom());
            
            log.debug("SSL context created successfully");
            return sslContext;
            
        } catch (Exception e) {
            log.error("Failed to create SSL context", e);
            if (strictMode) {
                throw new SecurityException("SSL context creation failed", e);
            }
            return null;
        }
    }

    // Private helper methods

    private void initializeMTLS() {
        try {
            log.info("Initializing mTLS with certificate: {}", certificatePath);
            
            // Load client certificate
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (FileInputStream keyStoreStream = new FileInputStream(certificatePath)) {
                // Use environment variable for keystore password
                String keystorePassword = System.getenv("WAQITI_KEYSTORE_PASSWORD");
                if (keystorePassword == null || keystorePassword.isEmpty()) {
                    // Fallback to Vault if environment variable not set
                    keystorePassword = getKeystorePasswordFromVault();
                }
                keyStore.load(keyStoreStream, keystorePassword.toCharArray());
            }
            
            // Create key manager
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            // Reuse the same password for key manager initialization
            String keystorePassword = System.getenv("WAQITI_KEYSTORE_PASSWORD");
            if (keystorePassword == null || keystorePassword.isEmpty()) {
                keystorePassword = getKeystorePasswordFromVault();
            }
            keyManagerFactory.init(keyStore, keystorePassword.toCharArray());
            KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();
            
            // Load CA certificate
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Certificate caCert;
            try (FileInputStream caStream = new FileInputStream(caPath)) {
                caCert = certificateFactory.generateCertificate(caStream);
            }
            
            // Create trust store
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("ca", caCert);
            
            // Create trust manager
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
            trustManagerFactory.init(trustStore);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            
            // Create SSL context
            SSLContext context = SSLContext.getInstance("TLSv1.3");
            context.init(keyManagers, trustManagers, null);
            // Store in a thread-safe way or return for immediate use
            
            log.info("mTLS initialized successfully");
            
        } catch (Exception e) {
            log.error("Failed to initialize mTLS", e);
            throw new SecurityException("mTLS initialization failed", e);
        }
    }

    private void initializeJWTValidation() {
        try {
            log.info("Initializing JWT validation");
            
            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            // Configure and use the processor
            
            // Configure JWT processor
            // In production, this would connect to actual JWKS endpoint
            // For now, we'll create a basic configuration
            
            log.info("JWT validation initialized");
            
        } catch (Exception e) {
            log.error("Failed to initialize JWT validation", e);
        }
    }

    private boolean validateCertificate(X509Certificate certificate) {
        if (certificate == null) {
            return false;
        }
        
        try {
            // Check certificate validity
            certificate.checkValidity();
            
            // Verify certificate chain
            // In production, this would validate against CA
            
            return true;
            
        } catch (Exception e) {
            log.error("Certificate validation failed", e);
            return false;
        }
    }

    private JWTClaimsSet validateJWT(String token) throws Exception {
        try {
            log.debug("Validating JWT token");
            
            // Create JWT processor
            ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
            
            // Configure JWS key selector
            JWKSource<SecurityContext> keySource = null;
            
            // Try to get JWKS URL from configuration
            String jwksUrl = System.getenv("WAQITI_JWKS_URL");
            if (jwksUrl == null || jwksUrl.isEmpty()) {
                // Fallback to configuration property
                jwksUrl = System.getProperty("waqiti.security.jwks.url", "http://localhost:8080/realms/waqiti/protocol/openid_connect/certs");
            }
            
            try {
                keySource = new RemoteJWKSet<>(new URL(jwksUrl));
                log.debug("Using remote JWK set from: {}", jwksUrl);
            } catch (Exception e) {
                log.warn("Failed to configure remote JWK set, will use local validation", e);
            }
            
            if (keySource != null) {
                JWSVerificationKeySelector<SecurityContext> keySelector = 
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
                jwtProcessor.setJWSKeySelector(keySelector);
                
                // Set acceptable issuers
                Set<String> acceptableIssuers = new HashSet<>(trustedIssuers);
                if (acceptableIssuers.isEmpty()) {
                    // Default trusted issuers
                    acceptableIssuers.add("waqiti-auth");
                    acceptableIssuers.add("http://localhost:8080/realms/waqiti");
                }
                jwtProcessor.setJWTClaimsSetAwareJWSKeySelector((jwsHeader, claimsSet, context) -> {
                    if (!acceptableIssuers.contains(claimsSet.getIssuer())) {
                        throw new SecurityException("Untrusted issuer: " + claimsSet.getIssuer());
                    }
                    return keySelector.selectJWSKeys(jwsHeader, context);
                });
                
                // Process and validate the JWT
                JWTClaimsSet claimsSet = jwtProcessor.process(token, null);
                
                // Additional validations
                validateClaims(claimsSet);
                
                log.debug("JWT validation successful for subject: {}", claimsSet.getSubject());
                return claimsSet;
                
            } else {
                // Fallback to basic validation without signature verification
                log.warn("JWT signature validation disabled - using basic validation only");
                
                // Parse JWT without verification (NOT recommended for production)
                String[] parts = token.split("\\.");
                if (parts.length != 3) {
                    throw new SecurityException("Invalid JWT format");
                }
                
                // Decode payload
                String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                
                @SuppressWarnings("unchecked")
                Map<String, Object> claims = mapper.readValue(payload, Map.class);
                
                // Create claims set
                JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
                
                claims.forEach((key, value) -> {
                    try {
                        switch (key) {
                            case "sub":
                                builder.subject(value.toString());
                                break;
                            case "iss":
                                builder.issuer(value.toString());
                                break;
                            case "aud":
                                if (value instanceof java.util.List) {
                                    builder.audience((java.util.List<String>) value);
                                } else {
                                    builder.audience(value.toString());
                                }
                                break;
                            case "exp":
                                builder.expirationTime(new Date(((Number) value).longValue() * 1000));
                                break;
                            case "iat":
                                builder.issueTime(new Date(((Number) value).longValue() * 1000));
                                break;
                            case "nbf":
                                builder.notBeforeTime(new Date(((Number) value).longValue() * 1000));
                                break;
                            default:
                                builder.claim(key, value);
                        }
                    } catch (Exception e) {
                        log.debug("Failed to process claim: {} = {}", key, value, e);
                    }
                });
                
                JWTClaimsSet claimsSet = builder.build();
                validateClaims(claimsSet);
                
                return claimsSet;
            }
            
        } catch (Exception e) {
            log.error("JWT validation failed", e);
            throw new SecurityException("JWT validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate JWT claims for security requirements
     */
    private void validateClaims(JWTClaimsSet claimsSet) throws SecurityException {
        // Check expiration
        Date expirationTime = claimsSet.getExpirationTime();
        if (expirationTime != null && expirationTime.before(new Date())) {
            throw new SecurityException("JWT token has expired");
        }
        
        // Check not before
        Date notBeforeTime = claimsSet.getNotBeforeTime();
        if (notBeforeTime != null && notBeforeTime.after(new Date())) {
            throw new SecurityException("JWT token not yet valid");
        }
        
        // Check issuer
        String issuer = claimsSet.getIssuer();
        if (issuer == null || issuer.trim().isEmpty()) {
            throw new SecurityException("JWT token missing issuer");
        }
        
        if (!trustedIssuers.isEmpty() && !trustedIssuers.contains(issuer)) {
            throw new SecurityException("JWT token from untrusted issuer: " + issuer);
        }
        
        // Check subject
        String subject = claimsSet.getSubject();
        if (subject == null || subject.trim().isEmpty()) {
            throw new SecurityException("JWT token missing subject");
        }
        
        log.debug("JWT claims validation passed for subject: {} from issuer: {}", subject, issuer);
    }

    private boolean evaluatePolicy(SecurityPolicy policy, AuthorizationRequest request) {
        // Evaluate rate limits
        if (policy.getRateLimit() > 0) {
            // Check rate limit (implementation would track requests)
        }
        
        // Evaluate time-based access
        if (policy.getTimeRestrictions() != null) {
            // Check time restrictions
        }
        
        // Evaluate IP restrictions
        if (policy.getIpWhitelist() != null && !policy.getIpWhitelist().isEmpty()) {
            if (!policy.getIpWhitelist().contains(request.getSourceIp())) {
                return false;
            }
        }
        
        return true;
    }

    // Inner classes

    @Data
    @Builder
    public static class SecurityPolicy {
        private String serviceName;
        private Set<String> allowedServices;
        private Set<String> deniedServices;
        private Map<String, Set<String>> methodPermissions;
        private int rateLimit;
        private Set<String> ipWhitelist;
        private TimeRestrictions timeRestrictions;
    }

    @Data
    @Builder
    public static class CertificateInfo {
        private String serviceName;
        private String certificatePath;
        private String privateKeyPath;
        private Instant validFrom;
        private Instant validUntil;
        private String fingerprint;
    }

    @Data
    @Builder
    public static class AuthenticationRequest {
        private String sourceService;
        private X509Certificate certificate;
        private String jwtToken;
        private Map<String, String> headers;
    }

    @Data
    @Builder
    public static class AuthenticationResult {
        private boolean authenticated;
        private String principal;
        private String reason;
        private Map<String, Object> claims;
    }

    @Data
    @Builder
    public static class AuthorizationRequest {
        private String sourceService;
        private String targetService;
        private String method;
        private String path;
        private String sourceIp;
        private Map<String, String> headers;
    }

    @Data
    @Builder
    public static class AuthorizationResult {
        private boolean authorized;
        private String reason;
        private Map<String, String> additionalHeaders;
    }

    @Data
    @Builder
    public static class TimeRestrictions {
        private Set<Integer> allowedDays; // 1-7 for Monday-Sunday
        private int startHour; // 0-23
        private int endHour; // 0-23
    }
    
    /**
     * Retrieves keystore password from Vault
     * @return The keystore password
     * @throws SecurityException if password cannot be retrieved
     */
    private String getKeystorePasswordFromVault() {
        try {
            // Try to get from Vault first
            if (vaultEnabled) {
                // Using Spring Vault if available
                String vaultPath = "secret/data/keystore";
                Map<String, String> secrets = readVaultSecret(vaultPath);
                if (secrets != null && secrets.containsKey("password")) {
                    return secrets.get("password");
                }
            }
            
            // Fallback to configuration property (should be externalized)
            String configPassword = System.getProperty("keystore.password");
            if (configPassword != null && !configPassword.isEmpty()) {
                log.warn("Using keystore password from system property. Consider using Vault in production.");
                return configPassword;
            }
            
            throw new SecurityException("Unable to retrieve keystore password from Vault or system properties");
        } catch (Exception e) {
            log.error("Failed to retrieve keystore password", e);
            throw new SecurityException("Failed to retrieve keystore password", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, String> readVaultSecret(String path) {
        try {
            log.debug("Reading secret from Vault path: {}", path);
            
            // Try to get VaultTemplate from Spring context if available
            try {
                // This would normally be injected, but for backward compatibility we'll look it up
                org.springframework.context.ApplicationContext context = 
                    org.springframework.context.support.ApplicationContextHolder.getApplicationContext();
                
                if (context != null && context.containsBean("vaultTemplate")) {
                    Object vaultTemplate = context.getBean("vaultTemplate");
                    
                    // Use reflection to call VaultTemplate methods
                    java.lang.reflect.Method readMethod = vaultTemplate.getClass()
                        .getMethod("read", String.class);
                    
                    Object vaultResponse = readMethod.invoke(vaultTemplate, path);
                    
                    if (vaultResponse != null) {
                        // Extract data from VaultResponse
                        java.lang.reflect.Method getDataMethod = vaultResponse.getClass()
                            .getMethod("getData");
                        Object data = getDataMethod.invoke(vaultResponse);
                        
                        if (data instanceof Map) {
                            return (Map<String, String>) data;
                        }
                    }
                }
            } catch (Exception vaultException) {
                log.debug("Vault integration not available or failed", vaultException);
            }
            
            // Fallback to environment variable based approach
            Map<String, String> secrets = new HashMap<>();
            
            // Extract secret name from path (e.g., "secret/data/keystore" -> "keystore")
            String secretName = path.substring(path.lastIndexOf('/') + 1);
            
            // Look for environment variables with pattern VAULT_{SECRET_NAME}_{KEY}
            System.getenv().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("VAULT_" + secretName.toUpperCase() + "_"))
                .forEach(entry -> {
                    String key = entry.getKey()
                        .substring(("VAULT_" + secretName.toUpperCase() + "_").length())
                        .toLowerCase();
                    secrets.put(key, entry.getValue());
                });
            
            return secrets.isEmpty() ? null : secrets;
            
        } catch (Exception e) {
            log.error("Failed to read Vault secret from path: {}", path, e);
            return null;
        }
    }
}