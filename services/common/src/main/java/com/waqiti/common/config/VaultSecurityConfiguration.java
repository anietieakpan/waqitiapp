package com.waqiti.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.config.AbstractVaultConfiguration;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.net.URI;

/**
 * Vault Security Configuration for Centralized Secrets Management
 *
 * This configuration replaces ALL hardcoded credentials with secure Vault-based retrieval.
 *
 * Features:
 * - AppRole authentication for service-to-service security
 * - Dynamic secret retrieval with automatic rotation support
 * - Encrypted secret storage
 * - Audit logging of all secret access
 *
 * Security Benefits:
 * - Eliminates hardcoded credentials (CRITICAL-3 vulnerability)
 * - Centralized secret management
 * - Automatic credential rotation
 * - Audit trail for compliance (PCI-DSS, SOC 2)
 *
 * Usage:
 * {@code
 * @Autowired
 * private VaultSecretService vaultSecretService;
 *
 * String apiKey = vaultSecretService.getSecret("stripe/api-key");
 * }
 *
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2025-10-11
 */
@Configuration
@ConfigurationProperties(prefix = "spring.cloud.vault")
public class VaultSecurityConfiguration extends AbstractVaultConfiguration {

    private String uri;
    private String roleId;
    private String secretId;
    private String namespace;

    @Override
    public VaultEndpoint vaultEndpoint() {
        try {
            VaultEndpoint endpoint = VaultEndpoint.from(URI.create(uri));

            // Set namespace for multi-tenant Vault installations
            // made this update 13-oct-25 - aniix
            // if (namespace != null && !namespace.isEmpty()) {
            //     endpoint.setNamespace(namespace);
            // }

            return endpoint;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure Vault endpoint: " + uri, e);
        }
    }

    @Override
    public ClientAuthentication clientAuthentication() {
        AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
                .roleId(AppRoleAuthenticationOptions.RoleId.provided(roleId))
                .secretId(AppRoleAuthenticationOptions.SecretId.provided(secretId))
                .build();

        return new AppRoleAuthentication(options, restOperations());
    }

    @Bean
    public VaultTemplate vaultTemplate() {
        return new VaultTemplate(vaultEndpoint(), clientAuthentication());
    }

    @Bean
    public VaultSecretService vaultSecretService(VaultTemplate vaultTemplate) {
        return new VaultSecretService(vaultTemplate);
    }

    // Getters and setters for configuration properties
    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
