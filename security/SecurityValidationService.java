package com.waqiti.security.validation;

import com.waqiti.common.exception.SecurityValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyStore;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Security Validation Service
 * 
 * Performs comprehensive security configuration validation at startup
 * to ensure all critical security controls are properly configured
 * and no default/hardcoded values are in use.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityValidationService {

    private final Environment environment;
    
    // Critical security properties that must be validated
    private static final List<String> CRITICAL_PROPERTIES = Arrays.asList(
        "spring.security.oauth2.client.registration.keycloak.client-secret",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri",
        "spring.datasource.password",
        "spring.redis.password",
        "spring.kafka.security.protocol",
        "vault.token",
        "encryption.master.key"
    );

    // Patterns that indicate insecure defaults
    private static final List<Pattern> INSECURE_PATTERNS = Arrays.asList(
        Pattern.compile(".*(-secret|-default|password123|admin|test|demo|VAULT_SECRET_REQUIRED).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^(changeme|password|secret|12345).*"),
        Pattern.compile(".*_PLACEHOLDER.*"),
        Pattern.compile(".*TODO.*")
    );

    // Environment validation
    private static final Set<String> VALID_ENVIRONMENTS = Set.of("development", "staging", "production");
    
    @Value("${security.validation.enabled:true}")
    private boolean validationEnabled;
    
    @Value("${security.validation.fail-fast:true}")
    private boolean failFast;
    
    @Value("${spring.profiles.active:}")
    private String activeProfile;

    /**
     * Validate security configuration at application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateSecurityConfiguration() {
        if (!validationEnabled) {
            log.warn("SECURITY WARNING: Security validation is disabled. This should never happen in production!");
            return;
        }

        log.info("Starting comprehensive security configuration validation...");
        
        List<SecurityIssue> issues = new ArrayList<>();
        
        // Perform all validations
        issues.addAll(validateCriticalProperties());
        issues.addAll(validateEnvironment());
        issues.addAll(validateEncryption());
        issues.addAll(validateOAuth2Configuration());
        issues.addAll(validateDatabaseSecurity());
        issues.addAll(validateKafkaSecurity());
        issues.addAll(validateRedisSecurity());
        issues.addAll(validateVaultIntegration());
        issues.addAll(validateTLSConfiguration());
        issues.addAll(validateCORSConfiguration());
        issues.addAll(validateRateLimiting());
        issues.addAll(validateCSRFProtection());
        
        // Report results
        reportValidationResults(issues);
    }

    /**
     * Validate critical security properties
     */
    private List<SecurityIssue> validateCriticalProperties() {
        List<SecurityIssue> issues = new ArrayList<>();
        
        for (String property : CRITICAL_PROPERTIES) {
            String value = environment.getProperty(property);
            
            if (value == null || value.trim().isEmpty()) {
                issues.add(new SecurityIssue(
                    Severity.CRITICAL,
                    "Missing critical security property: " + property,
                    "Property must be configured with a secure value"
                ));
                continue;
            }
            
            // Check for insecure patterns
            for (Pattern pattern : INSECURE_PATTERNS) {
                if (pattern.matcher(value).matches()) {
                    issues.add(new SecurityIssue(
                        Severity.CRITICAL,
                        "Insecure default value detected for: " + property,
                        "Value matches insecure pattern: " + pattern.pattern()
                    ));
                }
            }
        }
        
        return issues;
    }

    /**
     * Validate environment configuration
     */
    private List<SecurityIssue> validateEnvironment() {
        List<SecurityIssue> issues = new ArrayList<>();
        
        // Validate active profile
        if (activeProfile == null || activeProfile.trim().isEmpty()) {
            issues.add(new SecurityIssue(
                Severity.HIGH,
                "No active Spring profile configured",
                "Spring profile must be explicitly set"
            ));
        } else if (!VALID_ENVIRONMENTS.contains(activeProfile)) {
            issues.add(new SecurityIssue(
                Severity.HIGH,
                "Unknown environment profile: " + activeProfile,
                "Profile must be one of: " + VALID_ENVIRONMENTS
            ));
        }
        
        // Production-specific checks
        if ("production".equals(activeProfile)) {
            // Ensure debug is disabled
            if (Boolean.TRUE.equals(environment.getProperty("debug", Boolean.class))) {
                issues.add(new SecurityIssue(
                    Severity.HIGH,
                    "Debug mode enabled in production",
                    "Debug must be disabled in production environment"
                ));
            }
            
            // Ensure actuator endpoints are secured
            String actuatorExposure = environment.getProperty("management.endpoints.web.exposure.include");
            if ("*".equals(actuatorExposure)) {
                issues.add(new SecurityIssue(
                    Severity.HIGH,
                    "All actuator endpoints exposed in production",
                    "Limit actuator endpoint exposure in production"
                ));
            }
        }
        
        return issues;
    }

    /**
     * Validate encryption configuration
     */
    private List<SecurityIssue> validateEncryption() {
        List<SecurityIssue> issues = new ArrayList<>();
        
        // Check master encryption key
        String masterKey = environment.getProperty("encryption.master.key");
        if (masterKey != null) {
            // Validate key strength
            if (masterKey.length() < 32) {
                issues.add(new SecurityIssue(
                    Severity.CRITICAL,
                    "Weak encryption key detected",
                    "Encryption key must be at least 256 bits (32 characters)"
                ));
            }
            
            // Test encryption capability
            try {
                testEncryption(masterKey);
            } catch (Exception e) {
                issues.add(new SecurityIssue(
                    Severity.CRITICAL,
                    "Encryption validation failed",
                    "Unable to perform encryption with configured key: " + e.getMessage()
                ));
            }
        }
        
        // Check for encryption algorithm
        String algorithm = environment.getProperty("encryption.algorithm", "AES");
        if (!"AES".equals(algorithm) && !"RSA".equals(algorithm)) {
            issues.add(new SecurityIssue(
                Severity.HIGH,
                "Weak encryption algorithm: " + algorithm,
                "Use AES or RSA for encryption"
            ));
        }
        
        return issues;
    }

    /**
     * Validate OAuth2/OIDC configuration
     */
    private List<SecurityIssue> validateOAuth2Configuration() {
        List<SecurityIssue> issues = new ArrayList<>();
        
        // Check issuer URI
        String issuerUri = environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri");
        if (issuerUri != null && issuerUri.startsWith("http://") && "production".equals(activeProfile)) {
            issues.add(new SecurityIssue(
                Severity.CRITICAL,
                "OAuth2 issuer using HTTP in production",
                "OAuth2 issuer must use HTTPS in production"
            ));
        }
        
        // Check client credentials
        String clientSecret = environment.getProperty("spring.security.oauth2.client.registration.keycloak.client-secret");
        if (clientSecret != null && clientSecret.contains("secret")) {
            issues.add(new SecurityIssue(
                Severity.HIGH,
                "Default client secret detected",
                "Client secret appears to be a default value"
            ));
        }
        
        // Check token validation
        String jwkSetUri = environment.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri");
        if (jwkSetUri == null || jwkSetUri.trim().isEmpty()) {
            issues.add(new SecurityIssue(
                Severity.HIGH,
                "JWK Set URI not configured",
                "JWK Set URI required for token validation"
            ));
        }
        
        return issues;
    }

    /**
     * Validate database security configuration
     */
    private List<SecurityIssue> validateDatabaseSecurity() {
        List<SecurityIssue> issues = new ArrayList<>();
        
        // Check database password
        String dbPassword = environment.getProperty("spring.datasource.password");
        if (dbPassword != null && (dbPassword.length() < 12 || dbPassword.equals("password"))) {
            issues.add(new SecurityIssue(
                Severity.CRITICAL,
                "Weak database password",
                "Database password must be strong and at least 12 characters"
            ));
        }
        
        // Check SSL configuration
        String sslMode = environment.getProperty("spring.datasource.hikari.data-source-properties.sslmode");
        if (!"require".equals(sslMode) && !"verify-full".equals(sslMode) && "production".equals(activeProfile)) {
            issues.add(new SecurityIssue(
                Severity.HIGH,
                "Database SSL not properly configured",
                "SSL mode should be 'require' or 'verify-full' in production"
            ));
        }
        
        // Check connection pool security
        Integer maxPoolSize = environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class);
        if (maxPoolSize != null && maxPoolSize > 50) {
            issues.add(new SecurityIssue(
                Severity.MEDIUM,
                "Database connection pool too large: " + maxPoolSize,
                "Consider reducing pool size to prevent resource exhaustion"
            ));
        }
        
        return issues;
    }

    /**
     * Validate Kafka security configuration
     */
    private List<SecurityIssue> validateKafkaSecurity() {
        List<SecurityIssue> issues = new ArrayList<>();
        
        String securityProtocol = environment.getProperty("spring.kafka.security.protocol");
        
        if ("production".equals(activeProfile)) {
            if (!"SSL".equals(securityProtocol) && !"SASL_SSL".equals(securityProtocol)) {
                issues.add(new SecurityIssue(
                    Severity.HIGH,
                    "Kafka not using SSL in production",
                    "Kafka security protocol must be SSL or SASL_SSL in production"
                ));
            }
        }
        
        // Check SASL configuration if enabled
        if (securityProtocol != null && securityProtocol.contains("SASL")) {
            String saslMechanism = environment.getProperty("spring.kafka.properties.sasl.mechanism");
            if ("PLAIN".equals(saslMechanism) && "production".equals(activeProfile)) {
                issues.add(new SecurityIssue(
                    Severity.MEDIUM,
                    "Kafka using PLAIN SASL mechanism",
                    "Consider using SCRAM-SHA-512 for better security"
                ));
            }
        }
        
        return issues;
    }

    /**
     * Validate Redis security configuration
     */
    private List<SecurityIssue> validateRedisSecurity() {
        List<SecurityIssue> issues = new ArrayList<>();
        
        String redisPassword = environment.getProperty("spring.redis.password");
        
        if ("production".equals(activeProfile)) {
            if (redisPassword == null || redisPassword.trim().isEmpty()) {
                issues.add(new SecurityIssue(
                    Severity.HIGH,
                    "Redis password not configured",
                    "Redis must have password authentication in production"
                ));
            }
            
            // Check SSL
            Boolean sslEnabled = environment.getProperty("spring.redis.ssl", Boolean.class);
            if (!Boolean.TRUE.equals(sslEnabled)) {
                issues.add(new SecurityIssue(
                    Severity.MEDIUM,
                    "Redis SSL not enabled",
                    "Consider enabling SSL for Redis in production"
                ));
            }
        }
        
        return issues;
    }

    /**
     * Validate Vault integration
     */
    private List<SecurityIssue> validateVaultIntegration() {
        List<SecurityIssue> issues = new ArrayList<>();
        
        Boolean vaultEnabled = environment.getProperty("vault.enabled", Boolean.class);
        
        if (Boolean.TRUE.equals(vaultEnabled)) {
            String vaultToken = environment.getProperty("vault.token");
            String vaultUri = environment.getProperty("vault.uri");
            
            if (vaultToken != null && vaultToken.contains("root")) {
                issues.add(new SecurityIssue(
                    Severity.CRITICAL,
                    "Vault root token in use",
                    "Never use root token in application configuration"
                ));
            }
            
            if (vaultUri != null && vaultUri.startsWith("http://") && "production".equals(activeProfile)) {
                issues.add(new SecurityIssue(
                    Severity.HIGH,
                    "Vault using HTTP in production",
                    "Vault must use HTTPS in production"
                ));
            }
            
            // Check AppRole configuration
            String roleId = environment.getProperty("vault.app-role.role-id");
            String secretId = environment.getProperty("vault.app-role.secret-id");
            
            if (roleId == null || secretId == null) {
                issues.add(new SecurityIssue(
                    Severity.MEDIUM,
                    "Vault AppRole not configured",
                    "Consider using AppRole authentication instead of tokens"
                ));
            }
        } else if ("production".equals(activeProfile)) {
            issues.add(new SecurityIssue(
                Severity.HIGH,
                "Vault not enabled in production",
                "Vault should be enabled for secrets management in production"
            ));
        }
        
        return issues;
    }

    /**
     * Validate TLS configuration
     */
    private List<SecurityIssue> validateTLSConfiguration() {
        List<SecurityIssue> issues = new ArrayList<>();
        
        if ("production".equals(activeProfile)) {
            String keyStore = environment.getProperty("server.ssl.key-store");
            
            if (keyStore == null || keyStore.trim().isEmpty()) {
                issues.add(new SecurityIssue(
                    Severity.HIGH,
                    "TLS keystore not configured",
                    "TLS must be configured in production"
                ));
            }
            
            // Check TLS protocols
            String[] protocols = environment.getProperty("server.ssl.enabled-protocols", String[].class);
            if (protocols != null) {
                for (String protocol : protocols) {
                    if (protocol.contains("TLSv1.0") || protocol.contains("TLSv1.1")) {
                        issues.add(new SecurityIssue(
                            Severity.HIGH,
                            "Weak TLS protocol enabled: " + protocol,
                            "Only TLS 1.2 and 1.3 should be enabled"
                        ));
                    }
                }
            }
        }
        
        return issues;
    }

    /**
     * Validate CORS configuration
     */
    private List<SecurityIssue> validateCORSConfiguration() {
        List<SecurityIssue> issues = new ArrayList<>();
        
        String[] allowedOrigins = environment.getProperty("cors.allowed-origins", String[].class);
        
        if (allowedOrigins != null) {
            for (String origin : allowedOrigins) {
                if ("*".equals(origin) && "production".equals(activeProfile)) {
                    issues.add(new SecurityIssue(
                        Severity.HIGH,
                        "Wildcard CORS origin in production",
                        "Specific origins must be configured in production"
                    ));
                }
                
                if (origin.startsWith("http://") && "production".equals(activeProfile)) {
                    issues.add(new SecurityIssue(
                        Severity.MEDIUM,
                        "HTTP origin in CORS: " + origin,
                        "Consider using HTTPS origins only in production"
                    ));
                }
            }
        }
        
        return issues;
    }

    /**
     * Validate rate limiting configuration
     */
    private List<SecurityIssue> validateRateLimiting() {
        List<SecurityIssue> issues = new ArrayList<>();
        
        Boolean rateLimitEnabled = environment.getProperty("rate-limiting.enabled", Boolean.class);
        
        if (!Boolean.TRUE.equals(rateLimitEnabled) && "production".equals(activeProfile)) {
            issues.add(new SecurityIssue(
                Severity.HIGH,
                "Rate limiting not enabled",
                "Rate limiting must be enabled in production"
            ));
        }
        
        Integer globalLimit = environment.getProperty("rate-limiting.global.limit", Integer.class);
        if (globalLimit != null && globalLimit > 10000) {
            issues.add(new SecurityIssue(
                Severity.MEDIUM,
                "Rate limit too high: " + globalLimit,
                "Consider lowering global rate limit"
            ));
        }
        
        return issues;
    }

    /**
     * Validate CSRF protection configuration
     */
    private List<SecurityIssue> validateCSRFProtection() {
        List<SecurityIssue> issues = new ArrayList<>();
        
        Boolean csrfEnabled = environment.getProperty("security.csrf.enabled", Boolean.class);
        
        if (Boolean.FALSE.equals(csrfEnabled) && "production".equals(activeProfile)) {
            issues.add(new SecurityIssue(
                Severity.HIGH,
                "CSRF protection disabled",
                "CSRF protection should be enabled in production"
            ));
        }
        
        return issues;
    }

    /**
     * Test encryption capability
     */
    private void testEncryption(String key) throws Exception {
        byte[] keyBytes = key.getBytes();
        SecretKey secretKey = new SecretKeySpec(keyBytes, 0, 32, "AES");
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        
        // Test encryption
        byte[] encrypted = cipher.doFinal("test".getBytes());
        if (encrypted == null || encrypted.length == 0) {
            throw new SecurityValidationException("Encryption test failed");
        }
    }

    /**
     * Report validation results
     */
    private void reportValidationResults(List<SecurityIssue> issues) {
        if (issues.isEmpty()) {
            log.info("✅ Security configuration validation completed successfully. No issues found.");
            return;
        }
        
        // Group issues by severity
        Map<Severity, List<SecurityIssue>> groupedIssues = issues.stream()
            .collect(java.util.stream.Collectors.groupingBy(SecurityIssue::getSeverity));
        
        // Log issues
        log.error("🔴 Security configuration validation found {} issues", issues.size());
        
        groupedIssues.forEach((severity, severityIssues) -> {
            log.error("  {} {} issues:", severityIssues.size(), severity);
            severityIssues.forEach(issue -> {
                log.error("    - {}", issue.getDescription());
                log.error("      Recommendation: {}", issue.getRecommendation());
            });
        });
        
        // Fail fast if critical issues found
        if (failFast && groupedIssues.containsKey(Severity.CRITICAL)) {
            throw new SecurityValidationException(
                "Critical security configuration issues detected. Application startup aborted.");
        }
    }

    /**
     * Security issue representation
     */
    private static class SecurityIssue {
        private final Severity severity;
        private final String description;
        private final String recommendation;

        public SecurityIssue(Severity severity, String description, String recommendation) {
            this.severity = severity;
            this.description = description;
            this.recommendation = recommendation;
        }

        public Severity getSeverity() { return severity; }
        public String getDescription() { return description; }
        public String getRecommendation() { return recommendation; }
    }

    /**
     * Issue severity levels
     */
    private enum Severity {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW
    }
}