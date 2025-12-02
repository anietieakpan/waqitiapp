package com.waqiti.common.config;

import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.VaultException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HashiCorp Vault configuration for secure secret management
 */
@Slf4j
@Configuration
public class VaultConfig {
    
    @Value("${vault.uri:http://localhost:8200}")
    private String vaultUri;
    
    @Value("${vault.token:${VAULT_TOKEN:}}")
    private String vaultToken;
    
    @Value("${vault.app-role.role-id:${VAULT_ROLE_ID:}}")
    private String roleId;
    
    @Value("${vault.app-role.secret-id:${VAULT_SECRET_ID:}}")
    private String secretId;
    
    @Value("${vault.namespace:}")
    private String namespace;
    
    @Value("${vault.engine-version:2}")
    private Integer engineVersion;
    
    @Value("${vault.authentication:TOKEN}")
    private String authMethod;
    
    @Bean
    public Vault createVaultClient() throws VaultException {
        io.github.jopenlibs.vault.VaultConfig config = new io.github.jopenlibs.vault.VaultConfig()
            .address(vaultUri)
            .engineVersion(engineVersion);
        
        // Set namespace if provided
        if (!namespace.isEmpty()) {
            config.nameSpace(namespace);
        }
        
        // Configure authentication
        switch (authMethod.toUpperCase()) {
            case "TOKEN":
                if (vaultToken.isEmpty()) {
                    throw new VaultException("Vault token not provided");
                }
                config.token(vaultToken);
                break;
                
            case "APPROLE":
                if (roleId.isEmpty() || secretId.isEmpty()) {
                    throw new VaultException("AppRole credentials not provided");
                }
                // AppRole authentication will be handled separately
                Vault tempVault = new Vault(config);
                String token = authenticateWithAppRole(tempVault);
                config.token(token);
                break;
                
            default:
                throw new VaultException("Unsupported authentication method: " + authMethod);
        }
        
        Vault vault = new Vault(config);
        
        // Verify connection
        vault.logical().read("sys/health");
        log.info("Successfully connected to Vault at {}", vaultUri);
        
        return vault;
    }
    
    private String authenticateWithAppRole(Vault vault) throws VaultException {
        var authResponse = vault.auth().loginByAppRole(roleId, secretId);
        return authResponse.getAuthClientToken();
    }
    
    /**
     * Create a scoped Vault client for a specific service
     */
    public Vault createServiceVaultClient(String serviceName) throws VaultException {
        Vault mainVault = createVaultClient();
        
        // Create a policy-scoped token for the service
        var tokenRequest = new io.github.jopenlibs.vault.api.Auth.TokenRequest()
            .polices(java.util.List.of(serviceName + "-policy"))
            .ttl("24h")
            .renewable(true);
        
        var tokenResponse = mainVault.auth().createToken(tokenRequest);

        io.github.jopenlibs.vault.VaultConfig serviceConfig = new io.github.jopenlibs.vault.VaultConfig()
            .address(vaultUri)
            .token(tokenResponse.getAuthClientToken())
            .engineVersion(engineVersion);

        return new Vault(serviceConfig);
    }
}