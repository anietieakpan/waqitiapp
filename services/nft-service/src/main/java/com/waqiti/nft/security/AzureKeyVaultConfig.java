package com.waqiti.nft.security;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.KeyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

/**
 * Production-ready Azure Key Vault configuration
 * Provides HSM-backed key management for blockchain private keys
 *
 * Security Features:
 * - FIPS 140-2 Level 3 compliance via Azure Managed HSM
 * - Managed Identity authentication (no secrets in code)
 * - Automatic key rotation support
 * - Comprehensive audit logging
 * - Multi-region redundancy
 *
 * @author Waqiti Security Team
 * @version 2.0 - Production Ready
 */
@Slf4j
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "waqiti.security.azure-keyvault")
public class AzureKeyVaultConfig {

    /**
     * Azure Key Vault URL
     * Example: https://waqiti-prod-keyvault.vault.azure.net/
     */
    @NotBlank(message = "Azure Key Vault URL must be configured")
    private String vaultUrl;

    /**
     * Key Encryption Key (KEK) name used for wrapping blockchain keys
     * This key should be HSM-backed RSA 4096-bit key
     */
    @NotBlank(message = "KEK name must be configured")
    private String kekName = "blockchain-kek";

    /**
     * Enable HSM-backed operations (Managed HSM vs Standard Key Vault)
     * true = Managed HSM (FIPS 140-2 Level 3)
     * false = Standard Key Vault (FIPS 140-2 Level 2)
     */
    @NotNull
    private Boolean enableHsm = true;

    /**
     * Connection timeout for Key Vault operations
     */
    private Duration connectionTimeout = Duration.ofSeconds(10);

    /**
     * Read timeout for Key Vault operations
     */
    private Duration readTimeout = Duration.ofSeconds(30);

    /**
     * Retry policy configuration
     */
    private Integer maxRetries = 3;
    private Duration retryDelay = Duration.ofSeconds(2);

    /**
     * Enable automatic key rotation
     */
    private Boolean enableKeyRotation = true;

    /**
     * Key rotation interval (days)
     */
    private Integer keyRotationIntervalDays = 90;

    /**
     * Enable distributed tracing for Key Vault operations
     */
    private Boolean enableTracing = true;

    /**
     * Enable metrics collection
     */
    private Boolean enableMetrics = true;

    /**
     * Backup vault URL for disaster recovery
     */
    private String backupVaultUrl;

    /**
     * Environment tag (dev, staging, production)
     */
    private String environment = "production";

    /**
     * Creates a production-ready KeyClient with managed identity authentication
     */
    @Bean
    public KeyClient azureKeyClient() {
        log.info("Initializing Azure Key Vault client: vaultUrl={}, kekName={}, hsm={}",
            vaultUrl, kekName, enableHsm);

        return new KeyClientBuilder()
            .vaultUrl(vaultUrl)
            .credential(new DefaultAzureCredentialBuilder()
                .build()) // Uses managed identity in Azure or Azure CLI locally
            .buildClient();
    }

    /**
     * Creates a CryptographyClient for wrap/unwrap operations
     * This client is scoped to the specific KEK
     */
    @Bean
    public CryptographyClient azureCryptographyClient(KeyClient keyClient) {
        log.info("Initializing Azure Cryptography client for KEK: {}", kekName);

        // Get the key identifier for the KEK
        var key = keyClient.getKey(kekName);

        return new CryptographyClientBuilder()
            .credential(new DefaultAzureCredentialBuilder().build())
            .keyIdentifier(key.getId())
            .buildClient();
    }

    /**
     * Validates the Azure Key Vault configuration at startup
     */
    @Bean
    public AzureKeyVaultHealthCheck azureKeyVaultHealthCheck(KeyClient keyClient) {
        return new AzureKeyVaultHealthCheck(keyClient, this);
    }
}
