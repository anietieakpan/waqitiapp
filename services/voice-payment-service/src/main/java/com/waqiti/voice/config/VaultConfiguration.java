package com.waqiti.voice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.config.AbstractVaultConfiguration;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.SslConfiguration;

import java.net.URI;

/**
 * HashiCorp Vault Configuration
 *
 * CRITICAL SECURITY: Centralized secret management for:
 * - AES encryption keys (voice biometric data)
 * - Database credentials (PostgreSQL)
 * - Redis credentials
 * - Kafka credentials
 * - External API keys (Google Cloud, AWS)
 * - Service-to-service tokens (Keycloak client secrets)
 *
 * Architecture:
 * - Vault KV Secrets Engine v2 (versioned secrets)
 * - Token-based authentication (dev/test)
 * - AppRole authentication (production)
 * - TLS/SSL enabled for production
 *
 * Secret Paths:
 * - voice-payment-service/encryption/aes-key
 * - voice-payment-service/database/credentials
 * - voice-payment-service/redis/credentials
 * - voice-payment-service/kafka/credentials
 * - voice-payment-service/google-cloud/api-key
 *
 * Compliance:
 * - PCI-DSS Requirement 3.5 (Protect keys used to secure cardholder data)
 * - GDPR Article 32 (Security of processing)
 * - SOC 2 (Access control and encryption key management)
 */
@Slf4j
@Configuration
@Profile("!test")  // Disable Vault for unit tests
public class VaultConfiguration extends AbstractVaultConfiguration {

    @Value("${vault.uri:http://localhost:8200}")
    private String vaultUri;

    @Value("${vault.token:#{null}}")
    private String vaultToken;

    @Value("${vault.namespace:}")
    private String vaultNamespace;

    @Value("${vault.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${vault.ssl.trust-store:}")
    private String trustStore;

    @Value("${vault.ssl.trust-store-password:}")
    private String trustStorePassword;

    @Value("${vault.connection-timeout:5000}")
    private int connectionTimeout;

    @Value("${vault.read-timeout:15000}")
    private int readTimeout;

    /**
     * Configure Vault endpoint
     */
    @Override
    public VaultEndpoint vaultEndpoint() {
        try {
            URI uri = URI.create(vaultUri);
            VaultEndpoint endpoint = VaultEndpoint.from(uri);

            log.info("Configuring Vault endpoint: {}", vaultUri);

            return endpoint;

        } catch (Exception e) {
            log.error("Failed to configure Vault endpoint", e);
            throw new VaultConfigurationException("Invalid Vault URI: " + vaultUri, e);
        }
    }

    /**
     * Configure client authentication
     *
     * Uses Token authentication for development/staging
     * Production should use AppRole authentication
     */
    @Override
    public ClientAuthentication clientAuthentication() {
        if (vaultToken == null || vaultToken.isBlank()) {
            log.error("Vault token not configured");
            throw new VaultConfigurationException(
                    "Vault token is required. Set vault.token property or VAULT_TOKEN environment variable"
            );
        }

        log.info("Using Token authentication for Vault");
        return new TokenAuthentication(vaultToken);
    }

    /**
     * Configure SSL (for production)
     */
    @Override
    public SslConfiguration sslConfiguration() {
        if (!sslEnabled) {
            log.warn("Vault SSL/TLS is DISABLED (not recommended for production)");
            return SslConfiguration.unconfigured();
        }

        try {
            SslConfiguration.KeyStoreConfiguration keyStoreConfig = null;

            if (trustStore != null && !trustStore.isBlank()) {
                keyStoreConfig = SslConfiguration.KeyStoreConfiguration.of(
                        org.springframework.core.io.FileSystemResource.class.cast(
                                new org.springframework.core.io.FileSystemResource(trustStore)
                        ),
                        trustStorePassword.toCharArray()
                );
            }

            log.info("Vault SSL/TLS enabled with trust store: {}", trustStore);
            return SslConfiguration.forTrustStore(keyStoreConfig);

        } catch (Exception e) {
            log.error("Failed to configure Vault SSL", e);
            throw new VaultConfigurationException("SSL configuration failed", e);
        }
    }

    /**
     * Create VaultTemplate bean
     */
    @Bean
    public VaultTemplate vaultTemplate() {
        try {
            VaultTemplate template = new VaultTemplate(vaultEndpoint(), clientAuthentication());

            // Test connection
            boolean healthy = testVaultConnection(template);
            if (!healthy) {
                log.error("Vault health check failed");
                throw new VaultConfigurationException("Cannot connect to Vault");
            }

            log.info("VaultTemplate configured successfully");
            return template;

        } catch (Exception e) {
            log.error("Failed to create VaultTemplate", e);
            throw new VaultConfigurationException("VaultTemplate initialization failed", e);
        }
    }

    /**
     * Test Vault connection and authentication
     */
    private boolean testVaultConnection(VaultTemplate template) {
        try {
            // Try to read from sys/health endpoint
            org.springframework.vault.support.VaultHealth health = template.opsForSys().health();

            boolean isHealthy = health.isInitialized() && !health.isSealed();

            if (isHealthy) {
                log.info("Vault health check PASSED: initialized={}, sealed={}",
                        health.isInitialized(), health.isSealed());
            } else {
                log.error("Vault health check FAILED: initialized={}, sealed={}",
                        health.isInitialized(), health.isSealed());
            }

            return isHealthy;

        } catch (Exception e) {
            log.error("Vault health check failed with exception", e);
            return false;
        }
    }

    /**
     * Vault configuration exception
     */
    public static class VaultConfigurationException extends RuntimeException {
        public VaultConfigurationException(String message) {
            super(message);
        }

        public VaultConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
