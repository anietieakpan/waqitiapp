package com.waqiti.tokenization.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.config.AbstractVaultConfiguration;
import org.springframework.vault.core.VaultTemplate;

import java.net.URI;

/**
 * HashiCorp Vault Configuration
 *
 * Configures Vault client for card tokenization using Transit Engine
 *
 * Required Vault Setup:
 * 1. Enable Transit Engine: vault secrets enable transit
 * 2. Create encryption key: vault write -f transit/keys/card-tokenization
 * 3. Create AppRole: vault auth enable approle
 * 4. Create policy with transit access
 * 5. Bind policy to AppRole
 *
 * Environment Variables Required:
 * - VAULT_URI: Vault server URI (e.g., https://vault.example.com:8200)
 * - VAULT_ROLE_ID: AppRole role ID
 * - VAULT_SECRET_ID: AppRole secret ID
 *
 * @author Waqiti Security Team
 */
@Configuration
public class VaultConfiguration extends AbstractVaultConfiguration {

    @Value("${vault.uri}")
    private String vaultUri;

    @Value("${vault.app-role.role-id}")
    private String roleId;

    @Value("${vault.app-role.secret-id}")
    private String secretId;

    @Value("${vault.app-role.path:approle}")
    private String appRolePath;

    @Override
    public VaultEndpoint vaultEndpoint() {
        return VaultEndpoint.from(URI.create(vaultUri));
    }

    @Override
    public ClientAuthentication clientAuthentication() {
        AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
                .path(appRolePath)
                .roleId(AppRoleAuthenticationOptions.RoleId.provided(roleId))
                .secretId(AppRoleAuthenticationOptions.SecretId.provided(secretId))
                .build();

        return new AppRoleAuthentication(options, restOperations());
    }

    @Bean
    public VaultTemplate vaultTemplate() {
        return new VaultTemplate(vaultEndpoint(), clientAuthentication());
    }
}
