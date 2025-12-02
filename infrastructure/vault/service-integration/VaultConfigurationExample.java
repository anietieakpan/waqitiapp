package com.waqiti.common.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import jakarta.annotation.PostConstruct;

/**
 * Vault Configuration Example
 *
 * This class demonstrates how to integrate HashiCorp Vault into a service.
 *
 * USAGE:
 * 1. Copy this class to your service's config package
 * 2. Uncomment the @Configuration annotation
 * 3. Update the VaultPaths class with your service-specific paths
 * 4. Inject VaultSecretService wherever you need to access secrets
 *
 * NOTE: Most common secrets (JWT, encryption keys) are already available
 * through the common SecretService. Only use this for service-specific secrets.
 */
// @Configuration  // Uncomment to enable
@Slf4j
@Data
public class VaultConfigurationExample {

    @Value("${spring.cloud.vault.host:vault.vault-system.svc.cluster.local}")
    private String vaultHost;

    @Value("${spring.cloud.vault.port:8200}")
    private int vaultPort;

    @Value("${spring.cloud.vault.scheme:https}")
    private String vaultScheme;

    @Value("${VAULT_TOKEN}")
    private String vaultToken;

    @PostConstruct
    public void init() {
        log.info("Vault Configuration initialized - Host: {}:{}", vaultHost, vaultPort);
    }

    /**
     * Configure VaultTemplate for programmatic secret access
     */
    @Bean
    public VaultTemplate vaultTemplate() {
        VaultEndpoint endpoint = VaultEndpoint.create(vaultHost, vaultPort);
        endpoint.setScheme(vaultScheme);

        TokenAuthentication authentication = new TokenAuthentication(vaultToken);

        VaultTemplate template = new VaultTemplate(endpoint, authentication);

        log.info("VaultTemplate configured successfully");
        return template;
    }

    /**
     * Service for accessing Vault secrets programmatically
     */
    @Bean
    public VaultSecretService vaultSecretService(VaultTemplate vaultTemplate) {
        return new VaultSecretService(vaultTemplate);
    }

    /**
     * Service-specific Vault paths
     * Update these paths based on your service's needs
     */
    @Configuration
    @ConfigurationProperties(prefix = "vault.paths")
    @Data
    public static class VaultPaths {
        private String jwtSecret = "secret/data/waqiti/jwt-secret";
        private String jwtRefreshSecret = "secret/data/waqiti/jwt-refresh-secret";
        private String encryptionKey = "secret/data/waqiti/encryption/field-encryption-key";
        private String database = "secret/data/waqiti/${spring.application.name}/database";
        private String redis = "secret/data/waqiti/${spring.application.name}/redis";
        private String kafka = "secret/data/waqiti/${spring.application.name}/kafka";
        private String externalApis = "secret/data/waqiti/${spring.application.name}/external-apis";
    }

    /**
     * Service for accessing Vault secrets
     */
    public static class VaultSecretService {

        private final VaultTemplate vaultTemplate;

        public VaultSecretService(VaultTemplate vaultTemplate) {
            this.vaultTemplate = vaultTemplate;
        }

        /**
         * Read a secret from Vault
         *
         * @param path Vault path (e.g., "secret/data/waqiti/payment-service/stripe")
         * @param key Secret key within the path (e.g., "api_key")
         * @return Secret value
         */
        public String readSecret(String path, String key) {
            try {
                VaultResponse response = vaultTemplate.read(path);

                if (response == null || response.getData() == null) {
                    throw new VaultSecretNotFoundException("Secret not found at path: " + path);
                }

                // KV v2 stores data under "data" key
                @SuppressWarnings("unchecked")
                var data = (java.util.Map<String, Object>) response.getData().get("data");

                if (data == null || !data.containsKey(key)) {
                    throw new VaultSecretNotFoundException("Key '" + key + "' not found in secret at path: " + path);
                }

                return data.get(key).toString();
            } catch (Exception e) {
                log.error("Failed to read secret from Vault - Path: {}, Key: {}", path, key, e);
                throw new VaultSecretException("Failed to read secret from Vault", e);
            }
        }

        /**
         * Read all secrets at a path
         *
         * @param path Vault path
         * @return Map of all secrets at the path
         */
        @SuppressWarnings("unchecked")
        public java.util.Map<String, Object> readAllSecrets(String path) {
            try {
                VaultResponse response = vaultTemplate.read(path);

                if (response == null || response.getData() == null) {
                    throw new VaultSecretNotFoundException("No secrets found at path: " + path);
                }

                // KV v2 stores data under "data" key
                return (java.util.Map<String, Object>) response.getData().get("data");
            } catch (Exception e) {
                log.error("Failed to read secrets from Vault - Path: {}", path, e);
                throw new VaultSecretException("Failed to read secrets from Vault", e);
            }
        }

        /**
         * Write a secret to Vault (for testing/initialization only)
         *
         * WARNING: Production secrets should be written via vault-setup.sh or manually
         */
        public void writeSecret(String path, String key, String value) {
            try {
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put(key, value);

                java.util.Map<String, Object> body = new java.util.HashMap<>();
                body.put("data", data);

                vaultTemplate.write(path, body);
                log.info("Secret written to Vault - Path: {}, Key: {}", path, key);
            } catch (Exception e) {
                log.error("Failed to write secret to Vault - Path: {}, Key: {}", path, key, e);
                throw new VaultSecretException("Failed to write secret to Vault", e);
            }
        }

        /**
         * Check if Vault is accessible
         */
        public boolean isVaultAccessible() {
            try {
                vaultTemplate.opsForSys().health();
                return true;
            } catch (Exception e) {
                log.error("Vault health check failed", e);
                return false;
            }
        }
    }

    /**
     * Exception thrown when a secret is not found
     */
    public static class VaultSecretNotFoundException extends RuntimeException {
        public VaultSecretNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when Vault operations fail
     */
    public static class VaultSecretException extends RuntimeException {
        public VaultSecretException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
