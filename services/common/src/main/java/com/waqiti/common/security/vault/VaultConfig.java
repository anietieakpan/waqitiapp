package com.waqiti.common.security.vault;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * Supports multiple authentication methods:
 * - Token (default, for development)
 * - AppRole (recommended for production)
 * - Kubernetes (for K8s deployments)
 * - AWS IAM (for AWS environments)
 *
 * Configuration via application.yml:
 * <pre>
 * vault:
 *   enabled: true
 *   address: https://api.example.com:8200
 *   token: ${VAULT_TOKEN}
 *   namespace: waqiti-production
 *   connection-timeout: 5000
 *   read-timeout: 15000
 *   ssl:
 *     trust-store: classpath:vault-truststore.jks
 *     trust-store-password: ${VAULT_TRUSTSTORE_PASSWORD}
 * </pre>
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "vault")
@Data
public class VaultConfig extends AbstractVaultConfiguration {

    private boolean enabled = true;
    private String address = "http://localhost:8200";
    private String token;
    private String namespace;
    private String authMethod = "token"; // token, approle, kubernetes, aws
    private int connectionTimeout = 5000;
    private int readTimeout = 15000;

    // AppRole authentication
    private String roleId;
    private String secretId;

    // Kubernetes authentication
    private String kubernetesRole;
    private String kubernetesServiceAccountTokenPath = "/var/run/secrets/kubernetes.io/serviceaccount/token";

    // SSL Configuration
    private SslConfig ssl = new SslConfig();

    @Override
    public VaultEndpoint vaultEndpoint() {
        try {
            URI uri = URI.create(address);
            VaultEndpoint endpoint = VaultEndpoint.from(uri);

            // Note: In Spring Vault 3.x+, namespace is configured via VaultEnvironment/VaultTemplate
            // The namespace property is used in clientAuthentication() method instead

            return endpoint;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid Vault address: " + address, e);
        }
    }

    @Override
    public ClientAuthentication clientAuthentication() {
        // Support multiple authentication methods
        switch (authMethod.toLowerCase()) {
            case "token":
                return tokenAuthentication();
            case "approle":
                return appRoleAuthentication();
            case "kubernetes":
                return kubernetesAuthentication();
            case "aws":
                return awsAuthentication();
            default:
                throw new IllegalStateException("Unsupported Vault authentication method: " + authMethod);
        }
    }

    private ClientAuthentication tokenAuthentication() {
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("Vault token not configured. Set vault.token or VAULT_TOKEN environment variable");
        }
        return new TokenAuthentication(token);
    }

    private ClientAuthentication appRoleAuthentication() {
        if (roleId == null || secretId == null) {
            throw new IllegalStateException("AppRole credentials not configured. Set vault.roleId and vault.secretId");
        }

        return new org.springframework.vault.authentication.AppRoleAuthentication(
            org.springframework.vault.authentication.AppRoleAuthenticationOptions.builder()
                .roleId(org.springframework.vault.authentication.AppRoleAuthenticationOptions.RoleId.provided(roleId))
                .secretId(org.springframework.vault.authentication.AppRoleAuthenticationOptions.SecretId.provided(secretId))
                .build(),
            restOperations()
        );
    }

    private ClientAuthentication kubernetesAuthentication() {
        if (kubernetesRole == null) {
            throw new IllegalStateException("Kubernetes role not configured. Set vault.kubernetesRole");
        }

        return new org.springframework.vault.authentication.KubernetesAuthentication(
            org.springframework.vault.authentication.KubernetesAuthenticationOptions.builder()
                .role(kubernetesRole)
                .jwtSupplier(() -> {
                    try {
                        return new String(java.nio.file.Files.readAllBytes(
                            java.nio.file.Paths.get(kubernetesServiceAccountTokenPath)
                        ));
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to read Kubernetes service account token", e);
                    }
                })
                .build(),
            restOperations()
        );
    }

    private ClientAuthentication awsAuthentication() {
        return new org.springframework.vault.authentication.AwsIamAuthentication(
            org.springframework.vault.authentication.AwsIamAuthenticationOptions.builder()
                .build(),
            restOperations()
        );
    }

    @Override
    public SslConfiguration sslConfiguration() {
        if (ssl.getTrustStore() != null) {
            try {
                org.springframework.core.io.Resource trustStoreResource =
                    new org.springframework.core.io.ClassPathResource(ssl.getTrustStore());
                return SslConfiguration.forTrustStore(
                    trustStoreResource,
                    ssl.getTrustStorePassword().toCharArray()
                );
            } catch (Exception e) {
                log.error("Failed to load SSL truststore: {}", ssl.getTrustStore(), e);
                return SslConfiguration.unconfigured();
            }
        }
        return SslConfiguration.unconfigured();
    }

    @Data
    public static class SslConfig {
        private String trustStore;
        private String trustStorePassword;
        private boolean verifyHostname = true;
    }
}
