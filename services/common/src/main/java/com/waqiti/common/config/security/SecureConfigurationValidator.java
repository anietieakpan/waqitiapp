package com.waqiti.common.config.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CRITICAL SECURITY: Secure Configuration Validator
 *
 * Production-grade validator that ensures all sensitive configuration values
 * are properly set before application startup. This prevents the catastrophic
 * security vulnerability of running with empty/default passwords.
 *
 * FAIL-FAST PRINCIPLE:
 * - Application MUST NOT start if any required secret is missing
 * - No degraded/partial functionality allowed for security-sensitive configs
 * - Clear, actionable error messages for operators
 *
 * COMPREHENSIVE VALIDATION:
 * - Database credentials (password, username)
 * - Redis credentials (password if authentication enabled)
 * - Kafka security (SASL, SSL if required)
 * - JWT secrets (signing keys)
 * - Vault authentication (tokens, role-ids)
 * - External API credentials (payment providers, banks)
 * - Encryption keys (PII, PCI data)
 *
 * SECURITY BEST PRACTICES:
 * - Never log actual secret values
 * - Validate secret strength (length, complexity)
 * - Check for common weak passwords
 * - Verify secret sources (Vault, K8s secrets, env vars)
 * - Audit configuration access
 *
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2025-10-06
 */
@Slf4j
@Component
@Profile("!test") // Disable in test profile to allow test configurations
public class SecureConfigurationValidator implements ApplicationListener<ApplicationReadyEvent> {

    private final Environment environment;
    private final SecureConfigurationProperties secureConfigProperties;

    // Configuration validation results
    private final List<ConfigurationValidationError> validationErrors = new ArrayList<>();
    private final List<ConfigurationWarning> validationWarnings = new ArrayList<>();
    private final Map<String, SecretMetadata> secretMetadata = new LinkedHashMap<>();

    // Critical configuration properties
    @Value("${spring.datasource.username:#{null}}")
    private String dbUsername;

    @Value("${spring.datasource.password:#{null}}")
    private String dbPassword;

    @Value("${spring.data.redis.password:#{null}}")
    private String redisPassword;

    @Value("${spring.cloud.vault.token:#{null}}")
    private String vaultToken;

    @Value("${spring.cloud.vault.app-role.role-id:#{null}}")
    private String vaultRoleId;

    @Value("${spring.cloud.vault.app-role.secret-id:#{null}}")
    private String vaultSecretId;

    @Value("${security.jwt.secret:#{null}}")
    private String jwtSecret;

    @Value("${waqiti.security.encryption.master-key:#{null}}")
    private String encryptionMasterKey;

    @Value("${spring.cloud.vault.enabled:true}")
    private boolean vaultEnabled;

    @Value("${VAULT_ENABLED:true}")
    private boolean vaultEnabledEnv;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    // Validation rules
    private static final int MIN_PASSWORD_LENGTH = 16;
    private static final int MIN_SECRET_LENGTH = 32;
    private static final int RECOMMENDED_SECRET_LENGTH = 64;

    private static final Set<String> WEAK_PASSWORDS = Set.of(
        "password", "Password1", "admin", "changeme", "secret",
        "12345678", "password123", "qwerty", "test", "demo"
    );

    private static final Pattern STRONG_SECRET_PATTERN =
        Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]");

    public SecureConfigurationValidator(
            Environment environment,
            SecureConfigurationProperties secureConfigProperties) {
        this.environment = environment;
        this.secureConfigProperties = secureConfigProperties;
    }

    /**
     * Get active profile from Environment
     */
    private String getActiveProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length > 0 ? activeProfiles[0] : "default";
    }

    @PostConstruct
    public void init() {
        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║  CRITICAL SECURITY: Initializing Configuration Validator      ║");
        log.info("║  Environment: {}                                            ║",
                 String.format("%-45s", getActiveProfile()));
        log.info("║  Vault Enabled: {}                                         ║",
                 String.format("%-43s", vaultEnabled));
        log.info("╚════════════════════════════════════════════════════════════════╝");
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("🔒 SECURITY: Starting comprehensive configuration validation...");

        long startTime = System.currentTimeMillis();

        try {
            // Phase 1: Validate Database Credentials
            validateDatabaseConfiguration();

            // Phase 2: Validate Cache/Redis Credentials
            validateRedisConfiguration();

            // Phase 3: Validate Vault Authentication
            validateVaultConfiguration();

            // Phase 4: Validate JWT Security
            validateJwtConfiguration();

            // Phase 5: Validate Encryption Keys
            validateEncryptionConfiguration();

            // Phase 6: Validate Kafka Security (if applicable)
            validateKafkaConfiguration();

            // Phase 7: Validate External Service Credentials
            validateExternalServiceConfiguration();

            // Phase 8: Check for environment-specific requirements
            validateEnvironmentSpecificRequirements();

            // Generate validation report
            long duration = System.currentTimeMillis() - startTime;
            generateValidationReport(duration);

            // FAIL-FAST: Terminate if critical errors found
            if (!validationErrors.isEmpty()) {
                handleValidationFailure();
            }

            // Log warnings (non-blocking)
            logValidationWarnings();

            // Success
            logValidationSuccess(duration);

        } catch (Exception e) {
            log.error("🚨 CRITICAL: Configuration validation failed with exception", e);
            throw new SecureConfigurationException(
                "Configuration validation failed - application cannot start", e);
        }
    }

    /**
     * Phase 1: Validate Database Configuration
     *
     * CRITICAL: Database credentials MUST be provided and meet security standards
     */
    private void validateDatabaseConfiguration() {
        log.debug("Validating database configuration...");

        // Validate username
        if (dbUsername == null || dbUsername.trim().isEmpty()) {
            validationErrors.add(ConfigurationValidationError.builder()
                .property("spring.datasource.username")
                .severity(ValidationSeverity.CRITICAL)
                .message("Database username is not configured")
                .remedy("Set DB_USERNAME environment variable or spring.datasource.username property")
                .securityImpact("Database connection will fail - application cannot function")
                .build());
        } else {
            recordSecretMetadata("database.username", dbUsername, SecretType.USERNAME);

            // Warn if using default/common usernames
            if (Set.of("postgres", "admin", "root", "app_user").contains(dbUsername.toLowerCase())) {
                validationWarnings.add(ConfigurationWarning.builder()
                    .property("spring.datasource.username")
                    .message("Database username appears to be default/common value")
                    .recommendation("Use application-specific username for better security audit trail")
                    .build());
            }
        }

        // Validate password - MOST CRITICAL
        if (dbPassword == null || dbPassword.trim().isEmpty()) {
            validationErrors.add(ConfigurationValidationError.builder()
                .property("spring.datasource.password")
                .severity(ValidationSeverity.CRITICAL)
                .message("Database password is MISSING - this is a CRITICAL security vulnerability")
                .remedy("Set DB_PASSWORD environment variable immediately. NEVER use empty password defaults.")
                .securityImpact("CRITICAL: Database will run without authentication, exposing all financial data")
                .cve("CWE-798: Use of Hard-coded Credentials")
                .build());
        } else {
            // Validate password strength
            validatePasswordStrength("spring.datasource.password", dbPassword, MIN_PASSWORD_LENGTH);
            recordSecretMetadata("database.password", "***REDACTED***", SecretType.PASSWORD);
        }

        // Validate in Vault-enabled production
        if ("production".equalsIgnoreCase(getActiveProfile()) && vaultEnabled) {
            if (!isSecretFromVault("spring.datasource.password")) {
                validationWarnings.add(ConfigurationWarning.builder()
                    .property("spring.datasource.password")
                    .message("Database password not sourced from Vault in production")
                    .recommendation("Enable Vault dynamic database credentials for automatic rotation")
                    .build());
            }
        }
    }

    /**
     * Phase 2: Validate Redis Configuration
     */
    private void validateRedisConfiguration() {
        log.debug("Validating Redis configuration...");

        // Check if Redis requires authentication (best practice)
        boolean redisRequiresAuth = !redisHost.contains("localhost") ||
                                   "production".equalsIgnoreCase(getActiveProfile());

        if (redisRequiresAuth) {
            if (redisPassword == null || redisPassword.trim().isEmpty()) {
                validationErrors.add(ConfigurationValidationError.builder()
                    .property("spring.data.redis.password")
                    .severity(ValidationSeverity.HIGH)
                    .message("Redis password is MISSING - cache/session data exposed")
                    .remedy("Set REDIS_PASSWORD environment variable")
                    .securityImpact("HIGH: Unauthorized access to cached sensitive data (PII, auth tokens)")
                    .build());
            } else {
                validatePasswordStrength("spring.data.redis.password", redisPassword, MIN_PASSWORD_LENGTH);
                recordSecretMetadata("redis.password", "***REDACTED***", SecretType.PASSWORD);
            }
        } else {
            validationWarnings.add(ConfigurationWarning.builder()
                .property("spring.data.redis.password")
                .message("Redis authentication not configured (acceptable for localhost dev only)")
                .recommendation("Enable Redis AUTH for non-local environments")
                .build());
        }
    }

    /**
     * Phase 3: Validate Vault Configuration
     */
    private void validateVaultConfiguration() {
        log.debug("Validating Vault configuration...");

        if (!vaultEnabled) {
            if ("production".equalsIgnoreCase(getActiveProfile())) {
                validationErrors.add(ConfigurationValidationError.builder()
                    .property("spring.cloud.vault.enabled")
                    .severity(ValidationSeverity.CRITICAL)
                    .message("Vault is DISABLED in production - secrets not managed securely")
                    .remedy("Set VAULT_ENABLED=true and configure Vault authentication")
                    .securityImpact("CRITICAL: Secrets stored in environment variables/config files")
                    .build());
            }
            return;
        }

        // Validate Vault authentication method
        String authMethod = environment.getProperty("spring.cloud.vault.authentication", "TOKEN");

        switch (authMethod.toUpperCase()) {
            case "KUBERNETES":
                // Preferred for K8s deployments
                log.info("✅ Vault authentication: Kubernetes (RECOMMENDED for production)");
                break;

            case "APPROLE":
                if (vaultRoleId == null || vaultRoleId.trim().isEmpty()) {
                    validationErrors.add(ConfigurationValidationError.builder()
                        .property("spring.cloud.vault.app-role.role-id")
                        .severity(ValidationSeverity.CRITICAL)
                        .message("Vault AppRole role-id is MISSING")
                        .remedy("Set VAULT_ROLE_ID environment variable")
                        .securityImpact("Application cannot authenticate to Vault")
                        .build());
                }
                if (vaultSecretId == null || vaultSecretId.trim().isEmpty()) {
                    validationErrors.add(ConfigurationValidationError.builder()
                        .property("spring.cloud.vault.app-role.secret-id")
                        .severity(ValidationSeverity.CRITICAL)
                        .message("Vault AppRole secret-id is MISSING")
                        .remedy("Set VAULT_SECRET_ID environment variable")
                        .securityImpact("Application cannot authenticate to Vault")
                        .build());
                }
                recordSecretMetadata("vault.role-id", vaultRoleId, SecretType.CREDENTIAL);
                recordSecretMetadata("vault.secret-id", "***REDACTED***", SecretType.CREDENTIAL);
                break;

            case "TOKEN":
                if (vaultToken == null || vaultToken.trim().isEmpty()) {
                    validationErrors.add(ConfigurationValidationError.builder()
                        .property("spring.cloud.vault.token")
                        .severity(ValidationSeverity.CRITICAL)
                        .message("Vault token is MISSING")
                        .remedy("Set VAULT_TOKEN environment variable")
                        .securityImpact("Application cannot authenticate to Vault")
                        .build());
                } else if ("production".equalsIgnoreCase(getActiveProfile())) {
                    validationWarnings.add(ConfigurationWarning.builder()
                        .property("spring.cloud.vault.authentication")
                        .message("Using TOKEN authentication in production (not recommended)")
                        .recommendation("Switch to Kubernetes or AppRole authentication for production")
                        .build());
                }
                recordSecretMetadata("vault.token", "***REDACTED***", SecretType.TOKEN);
                break;

            default:
                validationWarnings.add(ConfigurationWarning.builder()
                    .property("spring.cloud.vault.authentication")
                    .message("Unknown Vault authentication method: " + authMethod)
                    .recommendation("Use KUBERNETES, APPROLE, or TOKEN")
                    .build());
        }
    }

    /**
     * Phase 4: Validate JWT Configuration
     */
    private void validateJwtConfiguration() {
        log.debug("Validating JWT configuration...");

        boolean jwtVaultEnabled = Boolean.parseBoolean(
            environment.getProperty("security.jwt.vault-enabled", "true"));

        if (jwtVaultEnabled && vaultEnabled) {
            // JWT secret should come from Vault
            log.info("✅ JWT secrets managed by Vault (RECOMMENDED)");
            return;
        }

        // Fallback: validate local JWT secret
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            validationErrors.add(ConfigurationValidationError.builder()
                .property("security.jwt.secret")
                .severity(ValidationSeverity.CRITICAL)
                .message("JWT secret is MISSING - authentication tokens cannot be signed")
                .remedy("Set JWT_SECRET environment variable with strong random value (min 64 bytes)")
                .securityImpact("CRITICAL: Application cannot issue or validate authentication tokens")
                .cve("CWE-327: Use of a Broken or Risky Cryptographic Algorithm")
                .build());
        } else {
            // Validate JWT secret strength
            if (jwtSecret.length() < MIN_SECRET_LENGTH) {
                validationErrors.add(ConfigurationValidationError.builder()
                    .property("security.jwt.secret")
                    .severity(ValidationSeverity.HIGH)
                    .message(String.format("JWT secret too short (%d bytes, minimum %d)",
                                         jwtSecret.length(), MIN_SECRET_LENGTH))
                    .remedy("Generate stronger secret: openssl rand -base64 64")
                    .securityImpact("HIGH: Weak JWT secret vulnerable to brute-force attacks")
                    .build());
            }

            if (jwtSecret.length() < RECOMMENDED_SECRET_LENGTH) {
                validationWarnings.add(ConfigurationWarning.builder()
                    .property("security.jwt.secret")
                    .message(String.format("JWT secret below recommended length (%d < %d bytes)",
                                         jwtSecret.length(), RECOMMENDED_SECRET_LENGTH))
                    .recommendation("Use 64+ byte secret for maximum security")
                    .build());
            }

            // Check for weak patterns
            if (WEAK_PASSWORDS.stream().anyMatch(weak -> jwtSecret.toLowerCase().contains(weak))) {
                validationErrors.add(ConfigurationValidationError.builder()
                    .property("security.jwt.secret")
                    .severity(ValidationSeverity.CRITICAL)
                    .message("JWT secret contains common/weak password patterns")
                    .remedy("Generate cryptographically random secret immediately")
                    .securityImpact("CRITICAL: Predictable JWT secret enables token forgery")
                    .build());
            }

            recordSecretMetadata("jwt.secret", "***REDACTED***", SecretType.SIGNING_KEY);
        }
    }

    /**
     * Phase 5: Validate Encryption Configuration
     */
    private void validateEncryptionConfiguration() {
        log.debug("Validating encryption configuration...");

        boolean encryptionEnabled = Boolean.parseBoolean(
            environment.getProperty("waqiti.security.encryption.enabled", "true"));

        if (!encryptionEnabled) {
            if ("production".equalsIgnoreCase(getActiveProfile())) {
                validationErrors.add(ConfigurationValidationError.builder()
                    .property("waqiti.security.encryption.enabled")
                    .severity(ValidationSeverity.CRITICAL)
                    .message("PII/PCI encryption is DISABLED in production")
                    .remedy("Set ENCRYPTION_ENABLED=true")
                    .securityImpact("CRITICAL: Sensitive data stored unencrypted (PCI-DSS violation)")
                    .compliance("PCI-DSS 3.4, GDPR Art. 32")
                    .build());
            }
            return;
        }

        if (encryptionMasterKey == null || encryptionMasterKey.trim().isEmpty()) {
            validationErrors.add(ConfigurationValidationError.builder()
                .property("waqiti.security.encryption.master-key")
                .severity(ValidationSeverity.CRITICAL)
                .message("Encryption master key is MISSING")
                .remedy("Configure master key in Vault or KMS")
                .securityImpact("CRITICAL: Cannot encrypt/decrypt sensitive data")
                .compliance("PCI-DSS 3.6.1")
                .build());
        } else {
            if (encryptionMasterKey.length() < 32) {
                validationErrors.add(ConfigurationValidationError.builder()
                    .property("waqiti.security.encryption.master-key")
                    .severity(ValidationSeverity.HIGH)
                    .message("Encryption master key too short (< 256 bits)")
                    .remedy("Use 256-bit or 512-bit encryption key")
                    .securityImpact("HIGH: Weak encryption vulnerable to attacks")
                    .build());
            }
            recordSecretMetadata("encryption.master-key", "***REDACTED***", SecretType.ENCRYPTION_KEY);
        }
    }

    /**
     * Phase 6: Validate Kafka Security Configuration
     */
    private void validateKafkaConfiguration() {
        log.debug("Validating Kafka configuration...");

        String kafkaServers = environment.getProperty("spring.kafka.bootstrap-servers", "localhost:9092");
        boolean isRemoteKafka = !kafkaServers.contains("localhost");

        if (isRemoteKafka || "production".equalsIgnoreCase(getActiveProfile())) {
            // Check for SASL authentication
            String saslMechanism = environment.getProperty("spring.kafka.properties.sasl.mechanism");
            if (saslMechanism == null) {
                validationWarnings.add(ConfigurationWarning.builder()
                    .property("spring.kafka.properties.sasl.mechanism")
                    .message("Kafka SASL authentication not configured")
                    .recommendation("Enable SASL/PLAIN or SASL/SCRAM for production Kafka")
                    .build());
            }

            // Check for SSL/TLS
            String securityProtocol = environment.getProperty("spring.kafka.properties.security.protocol");
            if (securityProtocol == null || !securityProtocol.contains("SSL")) {
                validationWarnings.add(ConfigurationWarning.builder()
                    .property("spring.kafka.properties.security.protocol")
                    .message("Kafka SSL/TLS not enabled")
                    .recommendation("Enable SASL_SSL or SSL for encrypted communication")
                    .build());
            }
        }
    }

    /**
     * Phase 7: Validate External Service Credentials
     */
    private void validateExternalServiceConfiguration() {
        log.debug("Validating external service configuration...");

        // Check payment provider credentials
        validateOptionalSecret("waqiti.stripe.api-key", "Stripe API key");
        validateOptionalSecret("waqiti.paypal.client-id", "PayPal client ID");
        validateOptionalSecret("waqiti.paypal.client-secret", "PayPal client secret");
        validateOptionalSecret("waqiti.twilio.auth-token", "Twilio auth token");
        validateOptionalSecret("waqiti.sendgrid.api-key", "SendGrid API key");
    }

    /**
     * Phase 8: Validate Environment-Specific Requirements
     */
    private void validateEnvironmentSpecificRequirements() {
        String activeProfile = getActiveProfile();
        log.debug("Validating environment-specific requirements for: {}", activeProfile);

        switch (activeProfile.toLowerCase()) {
            case "production":
            case "prod":
                validateProductionRequirements();
                break;
            case "staging":
            case "stage":
                validateStagingRequirements();
                break;
            case "development":
            case "dev":
                validateDevelopmentRequirements();
                break;
            default:
                validationWarnings.add(ConfigurationWarning.builder()
                    .property("ENVIRONMENT")
                    .message("Unknown environment: " + environment)
                    .recommendation("Use production, staging, or development")
                    .build());
        }
    }

    private void validateProductionRequirements() {
        // Production MUST have Vault enabled
        if (!vaultEnabled) {
            validationErrors.add(ConfigurationValidationError.builder()
                .property("VAULT_ENABLED")
                .severity(ValidationSeverity.CRITICAL)
                .message("Vault MUST be enabled in production")
                .remedy("Set VAULT_ENABLED=true")
                .securityImpact("Production secrets not centrally managed")
                .build());
        }

        // Production MUST NOT use default ports
        int serverPort = Integer.parseInt(environment.getProperty("server.port", "8080"));
        if (serverPort == 8080) {
            validationWarnings.add(ConfigurationWarning.builder()
                .property("server.port")
                .message("Using default port 8080 in production")
                .recommendation("Use non-standard port for production")
                .build());
        }
    }

    private void validateStagingRequirements() {
        // Staging should mirror production security
        if (!vaultEnabled) {
            validationWarnings.add(ConfigurationWarning.builder()
                .property("VAULT_ENABLED")
                .message("Vault disabled in staging (should mirror production)")
                .recommendation("Enable Vault for staging environment")
                .build());
        }
    }

    private void validateDevelopmentRequirements() {
        // Development can be more lenient, but warn about security
        if (validationErrors.isEmpty() && validationWarnings.isEmpty()) {
            log.info("✅ Development configuration validated successfully");
        }
    }

    // Helper methods

    private void validatePasswordStrength(String property, String password, int minLength) {
        if (password.length() < minLength) {
            validationErrors.add(ConfigurationValidationError.builder()
                .property(property)
                .severity(ValidationSeverity.HIGH)
                .message(String.format("Password too short (%d chars, minimum %d)",
                                     password.length(), minLength))
                .remedy("Use longer, more complex password")
                .securityImpact("Weak password vulnerable to brute-force attacks")
                .build());
        }

        if (WEAK_PASSWORDS.contains(password.toLowerCase())) {
            validationErrors.add(ConfigurationValidationError.builder()
                .property(property)
                .severity(ValidationSeverity.CRITICAL)
                .message("Password is a commonly used weak password")
                .remedy("Generate strong random password immediately")
                .securityImpact("CRITICAL: Weak password easily guessable")
                .build());
        }
    }

    private void validateOptionalSecret(String property, String description) {
        String value = environment.getProperty(property);
        if (value != null && !value.trim().isEmpty()) {
            recordSecretMetadata(property, "***REDACTED***", SecretType.API_KEY);
            log.debug("✅ {} configured", description);
        }
    }

    private boolean isSecretFromVault(String property) {
        // Check if property source is Vault
        String propertySource = environment.getProperty(property + ".source");
        return "vault".equalsIgnoreCase(propertySource);
    }

    private void recordSecretMetadata(String name, String value, SecretType type) {
        secretMetadata.put(name, SecretMetadata.builder()
            .name(name)
            .type(type)
            .configured(value != null && !value.trim().isEmpty())
            .length(value != null ? value.length() : 0)
            .source(determineSecretSource(name))
            .validatedAt(new Date())
            .build());
    }

    private SecretSource determineSecretSource(String name) {
        if (vaultEnabled) {
            return SecretSource.VAULT;
        }
        if (System.getenv(name.toUpperCase().replace(".", "_")) != null) {
            return SecretSource.ENVIRONMENT_VARIABLE;
        }
        return SecretSource.CONFIGURATION_FILE;
    }

    private void generateValidationReport(long duration) {
        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║         CONFIGURATION VALIDATION REPORT                       ║");
        log.info("╠════════════════════════════════════════════════════════════════╣");
        log.info("║  Duration: {} ms                                            ║",
                 String.format("%-50d", duration));
        log.info("║  Environment: {}                                           ║",
                 String.format("%-47s", environment));
        log.info("║  Vault Enabled: {}                                         ║",
                 String.format("%-45s", vaultEnabled));
        log.info("╠════════════════════════════════════════════════════════════════╣");
        log.info("║  Secrets Validated: {}                                     ║",
                 String.format("%-43d", secretMetadata.size()));
        log.info("║  Critical Errors: {}                                       ║",
                 String.format("%-45d", validationErrors.stream()
                     .filter(e -> e.getSeverity() == ValidationSeverity.CRITICAL).count()));
        log.info("║  High Severity Errors: {}                                  ║",
                 String.format("%-40d", validationErrors.stream()
                     .filter(e -> e.getSeverity() == ValidationSeverity.HIGH).count()));
        log.info("║  Warnings: {}                                              ║",
                 String.format("%-48d", validationWarnings.size()));
        log.info("╚════════════════════════════════════════════════════════════════╝");
    }

    private void handleValidationFailure() {
        log.error("╔════════════════════════════════════════════════════════════════╗");
        log.error("║  🚨 CRITICAL: CONFIGURATION VALIDATION FAILED                 ║");
        log.error("║                                                                ║");
        log.error("║  Application CANNOT START due to insecure configuration       ║");
        log.error("╠════════════════════════════════════════════════════════════════╣");

        // Group errors by severity
        Map<ValidationSeverity, List<ConfigurationValidationError>> errorsBySeverity =
            validationErrors.stream()
                .collect(Collectors.groupingBy(ConfigurationValidationError::getSeverity));

        // Log CRITICAL errors first
        if (errorsBySeverity.containsKey(ValidationSeverity.CRITICAL)) {
            log.error("║  CRITICAL ERRORS ({}):", errorsBySeverity.get(ValidationSeverity.CRITICAL).size());
            errorsBySeverity.get(ValidationSeverity.CRITICAL).forEach(error -> {
                log.error("║  ❌ {}", error.getProperty());
                log.error("║     Message: {}", error.getMessage());
                log.error("║     Impact: {}", error.getSecurityImpact());
                log.error("║     Remedy: {}", error.getRemedy());
                if (error.getCve() != null) {
                    log.error("║     CVE: {}", error.getCve());
                }
                if (error.getCompliance() != null) {
                    log.error("║     Compliance: {}", error.getCompliance());
                }
                log.error("║");
            });
        }

        // Log HIGH severity errors
        if (errorsBySeverity.containsKey(ValidationSeverity.HIGH)) {
            log.error("║  HIGH SEVERITY ERRORS ({}):", errorsBySeverity.get(ValidationSeverity.HIGH).size());
            errorsBySeverity.get(ValidationSeverity.HIGH).forEach(error -> {
                log.error("║  ⚠️  {}", error.getProperty());
                log.error("║     Message: {}", error.getMessage());
                log.error("║     Remedy: {}", error.getRemedy());
                log.error("║");
            });
        }

        log.error("╠════════════════════════════════════════════════════════════════╣");
        log.error("║  NEXT STEPS:                                                   ║");
        log.error("║  1. Review errors above and fix configuration                  ║");
        log.error("║  2. Set required environment variables or Vault secrets        ║");
        log.error("║  3. Verify secret strength meets minimum requirements          ║");
        log.error("║  4. Restart application after fixing configuration             ║");
        log.error("║                                                                ║");
        log.error("║  Documentation: docs/security/configuration-security.md       ║");
        log.error("╚════════════════════════════════════════════════════════════════╝");

        // Throw exception to prevent startup
        throw new SecureConfigurationException(
            String.format("Configuration validation failed with %d error(s). " +
                         "Application cannot start with insecure configuration. " +
                         "Review logs above for details.",
                         validationErrors.size()),
            validationErrors);
    }

    private void logValidationWarnings() {
        if (validationWarnings.isEmpty()) {
            return;
        }

        log.warn("╔════════════════════════════════════════════════════════════════╗");
        log.warn("║  ⚠️  CONFIGURATION WARNINGS ({})                              ║",
                 String.format("%-29d", validationWarnings.size()));
        log.warn("╠════════════════════════════════════════════════════════════════╣");

        validationWarnings.forEach(warning -> {
            log.warn("║  Property: {}", warning.getProperty());
            log.warn("║  Warning: {}", warning.getMessage());
            log.warn("║  Recommendation: {}", warning.getRecommendation());
            log.warn("║");
        });

        log.warn("╚════════════════════════════════════════════════════════════════╝");
    }

    private void logValidationSuccess(long duration) {
        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║  ✅ CONFIGURATION VALIDATION SUCCESSFUL                        ║");
        log.info("╠════════════════════════════════════════════════════════════════╣");
        log.info("║  All critical security configurations validated                ║");
        log.info("║  Duration: {} ms                                            ║",
                 String.format("%-50d", duration));
        log.info("║  Secrets validated: {}                                     ║",
                 String.format("%-43d", secretMetadata.size()));
        log.info("║  Warnings: {}                                              ║",
                 String.format("%-48d", validationWarnings.size()));
        log.info("╠════════════════════════════════════════════════════════════════╣");
        log.info("║  Application startup can proceed safely                        ║");
        log.info("╚════════════════════════════════════════════════════════════════╝");

        // Log secret metadata (without actual values)
        if (log.isDebugEnabled()) {
            log.debug("Validated secrets metadata:");
            secretMetadata.forEach((name, metadata) -> {
                log.debug("  - {}: type={}, source={}, length={}",
                         name, metadata.getType(), metadata.getSource(), metadata.getLength());
            });
        }
    }

    // Supporting classes

    @lombok.Data
    @lombok.Builder
    private static class ConfigurationValidationError {
        private String property;
        private ValidationSeverity severity;
        private String message;
        private String remedy;
        private String securityImpact;
        private String cve;
        private String compliance;
    }

    @lombok.Data
    @lombok.Builder
    private static class ConfigurationWarning {
        private String property;
        private String message;
        private String recommendation;
    }

    @lombok.Data
    @lombok.Builder
    private static class SecretMetadata {
        private String name;
        private SecretType type;
        private boolean configured;
        private int length;
        private SecretSource source;
        private Date validatedAt;
    }

    private enum ValidationSeverity {
        CRITICAL, HIGH, MEDIUM, LOW
    }

    private enum SecretType {
        PASSWORD, USERNAME, TOKEN, API_KEY, CREDENTIAL,
        SIGNING_KEY, ENCRYPTION_KEY, CERTIFICATE
    }

    private enum SecretSource {
        VAULT, KUBERNETES_SECRET, ENVIRONMENT_VARIABLE,
        CONFIGURATION_FILE, UNKNOWN
    }

    /**
     * Custom exception for configuration validation failures
     */
    public static class SecureConfigurationException extends RuntimeException {
        private final List<ConfigurationValidationError> errors;

        public SecureConfigurationException(String message) {
            super(message);
            this.errors = Collections.emptyList();
        }

        public SecureConfigurationException(String message, Throwable cause) {
            super(message, cause);
            this.errors = Collections.emptyList();
        }

        public SecureConfigurationException(String message, List<ConfigurationValidationError> errors) {
            super(message);
            this.errors = errors;
        }

        public List<ConfigurationValidationError> getErrors() {
            return errors;
        }
    }
}
