package com.waqiti.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.config.AbstractVaultConfiguration;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponseSupport;

import java.net.URI;
import java.util.Arrays;

/**
 * HashiCorp Vault configuration for centralized secret management
 * Provides VaultTemplate bean that was referenced in EncryptionService
 *
 * ✅ CRITICAL PRODUCTION FIX: Added HTTPS enforcement for production
 */
@Slf4j
@Configuration
public class VaultConfiguration extends AbstractVaultConfiguration {

    private final Environment environment;

    public VaultConfiguration(Environment environment) {
        this.environment = environment;
    }

    // ✅ FIXED: Removed default value - must be explicitly configured
    @Value("${vault.uri}")
    private String vaultUri;

    // ✅ FIXED: Removed default empty string - must be explicitly configured
    @Value("${vault.token}")
    private String vaultToken;
    
    @Value("${vault.app-role.role-id:}")
    private String roleId;
    
    @Value("${vault.app-role.secret-id:}")
    private String secretId;
    
    @Value("${vault.authentication:TOKEN}")
    private AuthenticationMethod authMethod;
    
    @Value("${vault.connection-timeout:5000}")
    private int connectionTimeout;
    
    @Value("${vault.read-timeout:15000}")
    private int readTimeout;
    
    @Value("${vault.namespace:}")
    private String namespace;

    /**
     * ✅ CRITICAL PRODUCTION FIX: Validate configuration at startup
     * Enforces HTTPS in production and validates required fields
     */
    @PostConstruct
    public void validateConfiguration() {
        log.info("Validating Vault configuration...");

        // Validate URI is set
        if (vaultUri == null || vaultUri.trim().isEmpty()) {
            throw new VaultConfigurationException(
                "Vault URI must be configured via vault.uri property");
        }

        // Get active profiles
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isProduction = Arrays.stream(activeProfiles)
            .anyMatch(profile -> profile.equalsIgnoreCase("production") ||
                                profile.equalsIgnoreCase("prod"));

        // ✅ CRITICAL: Enforce HTTPS in production
        if (isProduction) {
            if (!vaultUri.startsWith("https://")) {
                throw new VaultConfigurationException(
                    "SECURITY VIOLATION: Vault URI must use HTTPS in production. " +
                    "Got: " + vaultUri.substring(0, Math.min(20, vaultUri.length())) + "...");
            }
            log.info("✅ Vault HTTPS validation passed for production");
        } else {
            // Allow HTTP in dev/test, but warn
            if (!vaultUri.startsWith("http://") && !vaultUri.startsWith("https://")) {
                throw new VaultConfigurationException(
                    "Vault URI must start with http:// or https://");
            }

            if (vaultUri.startsWith("http://")) {
                log.warn("⚠️  WARNING: Using HTTP for Vault in non-production environment");
                log.warn("⚠️  This is acceptable for dev/test but NEVER use in production");
            }
        }

        // Validate authentication credentials
        if (authMethod == AuthenticationMethod.TOKEN) {
            if (vaultToken == null || vaultToken.trim().isEmpty()) {
                throw new VaultConfigurationException(
                    "Vault token must be configured via vault.token property");
            }
        } else if (authMethod == AuthenticationMethod.APP_ROLE) {
            if (roleId == null || roleId.trim().isEmpty() ||
                secretId == null || secretId.trim().isEmpty()) {
                throw new VaultConfigurationException(
                    "AppRole authentication requires vault.app-role.role-id and " +
                    "vault.app-role.secret-id properties");
            }
        }

        log.info("✅ Vault configuration validated successfully");
        log.info("   URI: {}", vaultUri);
        log.info("   Auth Method: {}", authMethod);
        log.info("   Production Mode: {}", isProduction);
    }

    /**
     * Configure Vault endpoint
     */
    @Override
    public VaultEndpoint vaultEndpoint() {
        try {
            VaultEndpoint endpoint = VaultEndpoint.from(new URI(vaultUri));
            
            // Note: Namespace configuration is handled via VaultNamespaceInterceptor
            // or environment variables in newer Vault versions
            
            log.info("Configured Vault endpoint: {}", vaultUri);
            return endpoint;
            
        } catch (Exception e) {
            log.error("Failed to configure Vault endpoint", e);
            throw new VaultConfigurationException("Invalid Vault URI: " + vaultUri, e);
        }
    }
    
    /**
     * Configure Vault authentication
     */
    @Override
    public ClientAuthentication clientAuthentication() {
        log.info("Configuring Vault authentication method: {}", authMethod);
        
        switch (authMethod) {
            case APP_ROLE:
                return configureAppRoleAuth();
                
            case TOKEN:
                return configureTokenAuth();
                
            default:
                throw new VaultConfigurationException("Unsupported authentication method: " + authMethod);
        }
    }
    
    /**
     * Create VaultTemplate bean
     */
    @Bean
    @Override
    public VaultTemplate vaultTemplate() {
        VaultTemplate template = new VaultTemplate(
            vaultEndpoint(),
            clientAuthentication()
        );
        
        // Verify connection
        try {
            template.opsForSys().health();
            log.info("Successfully connected to Vault");
        } catch (Exception e) {
            log.warn("Vault health check failed - operations may fail", e);
        }
        
        return template;
    }
    
    /**
     * Create VaultOperations bean (alias for VaultTemplate)
     */
    @Bean
    public VaultOperations vaultOperations(VaultTemplate vaultTemplate) {
        return vaultTemplate;
    }
    
    /**
     * Configure AppRole authentication
     */
    private ClientAuthentication configureAppRoleAuth() {
        if (roleId == null || roleId.isEmpty() || secretId == null || secretId.isEmpty()) {
            throw new VaultConfigurationException("AppRole authentication requires role-id and secret-id");
        }
        
        AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
            .roleId(AppRoleAuthenticationOptions.RoleId.provided(roleId))
            .secretId(AppRoleAuthenticationOptions.SecretId.provided(secretId))
            .build();
        
        return new AppRoleAuthentication(options, restOperations());
    }
    
    /**
     * Configure Token authentication
     */
    private ClientAuthentication configureTokenAuth() {
        if (vaultToken == null || vaultToken.isEmpty()) {
            throw new VaultConfigurationException("Token authentication requires a valid token");
        }
        
        return new TokenAuthentication(vaultToken);
    }
    
    /**
     * Authentication methods enum
     */
    public enum AuthenticationMethod {
        TOKEN,
        APP_ROLE
    }
    
    /**
     * Custom exception for Vault configuration errors
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