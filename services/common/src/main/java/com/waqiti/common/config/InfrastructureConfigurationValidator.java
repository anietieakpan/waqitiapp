package com.waqiti.common.config;

import com.waqiti.common.config.validation.ConfigurationValidator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * CRITICAL PRODUCTION: Infrastructure Configuration Validator
 *
 * <p>Validates core infrastructure configuration across ALL services:
 * <ul>
 *   <li>Database connections (PostgreSQL with SSL/TLS)</li>
 *   <li>Redis/Cache (with TLS in production)</li>
 *   <li>Kafka message broker (with SASL in production)</li>
 *   <li>Keycloak/OAuth2 authentication</li>
 * </ul>
 *
 * <p>Applied to: Transaction Service, User Service, Wallet Service,
 * Fraud Detection Service, KYC Service, Compliance Service, Banking Integration Service
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-23
 */
@Slf4j
@Configuration
public class InfrastructureConfigurationValidator extends ConfigurationValidator {

    // ==================== DATABASE CONFIGURATION ====================
    @Value("${spring.datasource.url:}")
    private String databaseUrl;

    @Value("${spring.datasource.username:}")
    private String databaseUsername;

    @Value("${spring.datasource.password:}")
    private String databasePassword;

    // ==================== REDIS CONFIGURATION ====================
    @Value("${spring.data.redis.host:}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.url:}")
    private String redisUrl;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    // ==================== KAFKA CONFIGURATION ====================
    @Value("${spring.kafka.bootstrap-servers:}")
    private String kafkaBootstrapServers;

    @Value("${spring.kafka.properties.security.protocol:}")
    private String kafkaSecurityProtocol;

    @Value("${spring.kafka.properties.sasl.mechanism:}")
    private String kafkaSaslMechanism;

    // ==================== KEYCLOAK/OAUTH2 CONFIGURATION ====================
    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri:}")
    private String keycloakIssuerUri;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id:}")
    private String keycloakClientId;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret:}")
    private String keycloakClientSecret;

    @Value("${keycloak.auth-server-url:}")
    private String keycloakAuthServerUrl;

    public InfrastructureConfigurationValidator(Environment environment) {
        super(environment);
    }

    /**
     * CRITICAL PRODUCTION VALIDATION
     * Validates all infrastructure configuration at startup
     */
    @PostConstruct
    public void validateInfrastructureConfiguration() {
        log.info("====================================================================");
        log.info("    INFRASTRUCTURE - CONFIGURATION VALIDATION");
        log.info("====================================================================");

        validateDatabase();
        validateRedis();
        validateKafka();
        validateKeycloak();

        // Execute validation and fail fast if errors
        super.validateConfiguration();

        if (validationErrors.isEmpty()) {
            log.info("✅ Infrastructure configuration validation PASSED");
        }

        log.info("====================================================================");
    }

    /**
     * Validate database configuration
     */
    private void validateDatabase() {
        log.info("Validating database configuration...");

        requireNonEmpty("spring.datasource.url", databaseUrl,
            "Database URL is REQUIRED");

        if (databaseUrl != null && !databaseUrl.trim().isEmpty()) {
            // Validate not localhost in production
            requireNotLocalhost("spring.datasource.url", databaseUrl,
                "Database MUST NOT be localhost in production");

            // Validate PostgreSQL SSL
            if (databaseUrl.contains("postgresql")) {
                if (isProduction() && !databaseUrl.contains("sslmode=require")) {
                    addError("PostgreSQL MUST use SSL in production. " +
                        "Add ?sslmode=require to JDBC URL: " + maskPassword(databaseUrl));
                    log.error("🚨 DATABASE SECURITY: PostgreSQL without SSL in production!");
                } else if (databaseUrl.contains("sslmode=require")) {
                    log.info("✅ PostgreSQL SSL enabled");
                }
            }

            // Validate MySQL SSL
            if (databaseUrl.contains("mysql")) {
                if (isProduction() && !databaseUrl.contains("useSSL=true")) {
                    log.warn("⚠️  MySQL SSL not explicitly enabled (add useSSL=true)");
                }
            }
        }

        // Validate credentials
        requireInProduction("spring.datasource.username", databaseUsername,
            "Database username is REQUIRED in production");

        requireInProduction("spring.datasource.password", databasePassword,
            "Database password is REQUIRED in production");

        if (isProduction() && databasePassword != null) {
            if (databasePassword.length() < 16) {
                log.warn("⚠️  Database password should be at least 16 characters in production");
            }
        }
    }

    /**
     * Validate Redis configuration
     */
    private void validateRedis() {
        log.info("Validating Redis configuration...");

        String effectiveRedisUrl = redisUrl != null && !redisUrl.trim().isEmpty()
            ? redisUrl
            : (redisHost != null && !redisHost.trim().isEmpty()
                ? "redis://" + redisHost + ":" + redisPort
                : null);

        if (effectiveRedisUrl != null) {
            // Validate not localhost in production
            requireNotLocalhost("spring.data.redis", effectiveRedisUrl,
                "Redis MUST NOT be localhost in production");

            // Validate TLS in production
            if (isProduction()) {
                if (!effectiveRedisUrl.startsWith("rediss://")) {
                    log.warn("⚠️  SECURITY WARNING: Redis not using TLS (rediss://) in production");
                    log.warn("⚠️  Current: {}://{}:{}", "redis", redisHost, redisPort);
                    log.warn("⚠️  Recommended: Use rediss:// for encrypted connections");
                } else {
                    log.info("✅ Redis TLS enabled");
                }

                // Validate password in production
                if (redisPassword == null || redisPassword.trim().isEmpty()) {
                    log.warn("⚠️  Redis password not configured in production");
                } else {
                    log.info("✅ Redis password configured");
                }
            }
        } else {
            log.info("ℹ️  Redis configuration not present (optional for some services)");
        }
    }

    /**
     * Validate Kafka configuration
     */
    private void validateKafka() {
        log.info("Validating Kafka configuration...");

        requireNonEmpty("spring.kafka.bootstrap-servers", kafkaBootstrapServers,
            "Kafka bootstrap servers are REQUIRED");

        if (kafkaBootstrapServers != null && !kafkaBootstrapServers.trim().isEmpty()) {
            // Validate not localhost in production
            requireNotLocalhost("spring.kafka.bootstrap-servers", kafkaBootstrapServers,
                "Kafka MUST NOT be localhost in production");

            // Validate security in production
            if (isProduction()) {
                if (kafkaSecurityProtocol == null || kafkaSecurityProtocol.trim().isEmpty()) {
                    log.warn("⚠️  Kafka security protocol not configured in production");
                    log.warn("⚠️  Recommended: Use SASL_SSL for encrypted connections");
                } else if ("SASL_SSL".equals(kafkaSecurityProtocol)) {
                    log.info("✅ Kafka SASL_SSL enabled");

                    if (kafkaSaslMechanism == null || kafkaSaslMechanism.trim().isEmpty()) {
                        log.warn("⚠️  Kafka SASL mechanism not configured");
                    } else {
                        log.info("✅ Kafka SASL mechanism: {}", kafkaSaslMechanism);
                    }
                } else if ("SSL".equals(kafkaSecurityProtocol)) {
                    log.info("✅ Kafka SSL enabled");
                } else if ("PLAINTEXT".equals(kafkaSecurityProtocol)) {
                    addError("Kafka MUST NOT use PLAINTEXT protocol in production");
                    log.error("🚨 KAFKA SECURITY: Using PLAINTEXT in production!");
                }
            }
        }
    }

    /**
     * Validate Keycloak/OAuth2 configuration
     */
    private void validateKeycloak() {
        log.info("Validating Keycloak/OAuth2 configuration...");

        // Check which Keycloak config is present
        String effectiveKeycloakUrl = keycloakAuthServerUrl != null && !keycloakAuthServerUrl.trim().isEmpty()
            ? keycloakAuthServerUrl
            : keycloakIssuerUri;

        if (effectiveKeycloakUrl != null && !effectiveKeycloakUrl.trim().isEmpty()) {
            // Validate HTTPS
            requireHttps("keycloak.auth-server-url", effectiveKeycloakUrl,
                "Keycloak MUST use HTTPS in production");

            // Validate not localhost
            requireNotLocalhost("keycloak.auth-server-url", effectiveKeycloakUrl,
                "Keycloak MUST NOT be localhost in production");

            // Validate client credentials
            requireInProduction("keycloak.client-id", keycloakClientId,
                "Keycloak client ID is REQUIRED in production");

            requireInProduction("keycloak.client-secret", keycloakClientSecret,
                "Keycloak client secret is REQUIRED in production");

            if (keycloakClientSecret != null && keycloakClientSecret.trim().isEmpty()) {
                log.warn("⚠️  Keycloak client secret is empty");
            } else if (keycloakClientSecret != null) {
                log.info("✅ Keycloak authentication configured");
            }
        } else {
            log.info("ℹ️  Keycloak configuration not present (optional for some services)");
        }
    }

    /**
     * Mask password in URLs for logging
     */
    private String maskPassword(String url) {
        if (url == null) return "NOT SET";
        return url.replaceAll("password=[^&]+", "password=*****")
                  .replaceAll("://[^@]+@", "://*****:*****@");
    }
}
