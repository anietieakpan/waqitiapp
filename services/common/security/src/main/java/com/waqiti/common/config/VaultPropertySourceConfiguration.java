package com.waqiti.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.vault.annotation.VaultPropertySource;
import org.springframework.vault.annotation.VaultPropertySources;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.config.AbstractVaultConfiguration;
import org.springframework.vault.core.VaultTemplate;

import java.net.URI;

/**
 * Spring Cloud Vault configuration that automatically loads secrets
 * from HashiCorp Vault into Spring properties
 */
@Slf4j
@Configuration
@Profile("!local")
@VaultPropertySources({
    @VaultPropertySource(value = "secret/${spring.application.name}"),
    @VaultPropertySource(value = "secret/application"),
    @VaultPropertySource(value = "database/${spring.application.name}"),
    @VaultPropertySource(value = "jwt/${spring.application.name}")
})
public class VaultPropertySourceConfiguration extends AbstractVaultConfiguration {
    
    @Value("${vault.uri:http://vault:8200}")
    private String vaultUri;
    
    @Value("${vault.token:}")
    private String vaultToken;
    
    @Value("${vault.app-role.role-id:}")
    private String roleId;
    
    @Value("${vault.app-role.secret-id:}")
    private String secretId;
    
    @Value("${vault.authentication:TOKEN}")
    private String authMethod;
    
    @Value("${spring.application.name}")
    private String appName;
    
    @Override
    public VaultEndpoint vaultEndpoint() {
        log.info("Configuring Vault endpoint: {}", vaultUri);
        return VaultEndpoint.from(URI.create(vaultUri));
    }
    
    @Override
    public ClientAuthentication clientAuthentication() {
        log.info("Configuring Vault authentication method: {} for service: {}", authMethod, appName);
        
        switch (authMethod.toUpperCase()) {
            case "TOKEN":
                if (vaultToken.isEmpty()) {
                    throw new IllegalStateException("Vault token not provided");
                }
                return new TokenAuthentication(vaultToken);
                
            case "APPROLE":
                if (roleId.isEmpty() || secretId.isEmpty()) {
                    throw new IllegalStateException("AppRole credentials not provided");
                }
                
                AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
                    .roleId(AppRoleAuthenticationOptions.RoleId.provided(roleId))
                    .secretId(AppRoleAuthenticationOptions.SecretId.provided(secretId))
                    .build();
                    
                return new AppRoleAuthentication(options, restOperations());
                
            case "KUBERNETES":
                // For Kubernetes deployments
                String role = appName + "-role";
                return new KubernetesAuthentication(role, restOperations());
                
            default:
                throw new IllegalStateException("Unsupported authentication method: " + authMethod);
        }
    }
    
    @Override
    public VaultTemplate vaultTemplate() {
        VaultTemplate template = super.vaultTemplate();
        
        // Configure session management
        template.setSessionManager(sessionManager());
        
        log.info("VaultTemplate configured for service: {}", appName);
        return template;
    }
}