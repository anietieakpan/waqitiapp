package com.waqiti.common.config;

import com.waqiti.common.security.secrets.SecretProvider;
import com.waqiti.common.security.secrets.providers.VaultSecretProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.vault.annotation.VaultPropertySource;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.KubernetesAuthentication;
import org.springframework.vault.authentication.KubernetesAuthenticationOptions;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.config.AbstractVaultConfiguration;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultHealth;
import org.springframework.vault.support.VaultToken;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@Configuration
@ConditionalOnProperty(value = "vault.enabled", havingValue = "true", matchIfMissing = true)
@VaultPropertySource(value = "secret/waqiti/${spring.application.name}", 
                    renewal = VaultPropertySource.Renewal.RENEW,
                    ignoreSecretNotFound = false)
public class VaultConfig extends AbstractVaultConfiguration {

    @Value("${vault.uri:http://localhost:8200}")
    private String vaultUri;
    
    @Value("${vault.token:}")
    private String vaultToken;
    
    @Value("${vault.authentication:TOKEN}")
    private String authMethod;
    
    @Value("${vault.kubernetes.role:}")
    private String kubernetesRole;
    
    @Value("${vault.kubernetes.service-account-token-file:/var/run/secrets/kubernetes.io/serviceaccount/token}")
    private String serviceAccountTokenFile;
    
    @Value("${spring.application.name}")
    private String applicationName;
    
    @Value("${vault.kv.backend:secret}")
    private String kvBackend;
    
    @Value("${vault.kv.default-context:waqiti}")
    private String defaultContext;
    
    @Override
    public VaultEndpoint vaultEndpoint() {
        VaultEndpoint endpoint = VaultEndpoint.from(URI.create(vaultUri));
        log.info("Configured Vault endpoint: {}", vaultUri);
        return endpoint;
    }
    
    @Override
    public ClientAuthentication clientAuthentication() {
        log.info("Configuring Vault authentication method: {}", authMethod);
        
        switch (authMethod.toUpperCase()) {
            case "KUBERNETES":
                return kubernetesAuthentication();
            case "TOKEN":
                if (vaultToken == null || vaultToken.isEmpty()) {
                    throw new IllegalStateException("Vault token is required for TOKEN authentication");
                }
                return new TokenAuthentication(vaultToken);
            default:
                log.warn("Unknown authentication method: {}, falling back to token auth", authMethod);
                return new TokenAuthentication(vaultToken);
        }
    }
    
    private ClientAuthentication kubernetesAuthentication() {
        try {
            String jwt = new String(Files.readAllBytes(Paths.get(serviceAccountTokenFile)));
            String role = kubernetesRole.isEmpty() ? "waqiti-" + applicationName : kubernetesRole;
            
            KubernetesAuthenticationOptions options = KubernetesAuthenticationOptions.builder()
                .role(role)
                .jwtSupplier(() -> jwt)
                .build();
                
            log.info("Configured Kubernetes authentication for role: {}", role);
            return new KubernetesAuthentication(options, restOperations());
        } catch (Exception e) {
            log.error("Failed to configure Kubernetes authentication", e);
            throw new RuntimeException("Failed to configure Kubernetes authentication", e);
        }
    }
    
    @Bean
    @Primary
    public SecretProvider vaultSecretProvider(VaultTemplate vaultTemplate) {
        String basePath = String.format("%s/%s/%s", kvBackend, defaultContext, applicationName);
        log.info("Creating VaultSecretProvider with base path: {}", basePath);
        return new VaultSecretProvider(vaultTemplate, basePath);
    }
    
    @Bean
    public VaultTemplate vaultTemplate() {
        VaultTemplate template = new VaultTemplate(vaultEndpoint(), clientAuthentication());
        
        // Configure session management for token renewal
        if (authMethod.equalsIgnoreCase("TOKEN")) {
            template.afterPropertiesSet();
            
            // Log token information
            try {
                // In newer Spring Vault versions, token management is handled internally
                log.info("Vault template configured with token authentication");
            } catch (Exception e) {
                log.warn("Could not retrieve token information", e);
            }
        }
        
        return template;
    }
    
    /**
     * Health indicator for Vault connectivity
     */
    @Bean
    public HealthIndicator vaultHealthIndicator(VaultTemplate vaultTemplate) {
        return new VaultHealthIndicator(vaultTemplate);
    }
    
    /**
     * Custom Vault health indicator
     */
    public static class VaultHealthIndicator implements HealthIndicator {
        
        private final VaultTemplate vaultTemplate;
        
        public VaultHealthIndicator(VaultTemplate vaultTemplate) {
            this.vaultTemplate = vaultTemplate;
        }
        
        @Override
        public Health health() {
            try {
                VaultHealth vaultHealth = vaultTemplate.opsForSys().health();
                
                if (vaultHealth.isInitialized() && !vaultHealth.isSealed()) {
                    return Health.up()
                        .withDetail("version", vaultHealth.getVersion())
                        .withDetail("standby", vaultHealth.isStandby())
                        .withDetail("performanceStandby", vaultHealth.isPerformanceStandby())
                        .build();
                } else {
                    return Health.down()
                        .withDetail("initialized", vaultHealth.isInitialized())
                        .withDetail("sealed", vaultHealth.isSealed())
                        .build();
                }
            } catch (Exception e) {
                return Health.down()
                    .withException(e)
                    .build();
            }
        }
    }
    
    /**
     * Configuration properties for Vault
     */
    @Bean
    @ConditionalOnProperty("vault.config.order")
    public VaultConfigurationProperties vaultConfigurationProperties() {
        return new VaultConfigurationProperties();
    }
    
    /**
     * Vault configuration properties holder
     */
    public static class VaultConfigurationProperties {
        
        @Value("${vault.config.order:0}")
        private int order;
        
        @Value("${vault.config.lifecycle.enabled:true}")
        private boolean lifecycleEnabled;
        
        @Value("${vault.config.lifecycle.min-renewal:10s}")
        private String minRenewal;
        
        @Value("${vault.config.lifecycle.expiry-threshold:1m}")
        private String expiryThreshold;
        
        @Value("${vault.config.lifecycle.lease-endpoints:true}")
        private boolean leaseEndpoints;
        
        // Getters
        public int getOrder() { return order; }
        public boolean isLifecycleEnabled() { return lifecycleEnabled; }
        public String getMinRenewal() { return minRenewal; }
        public String getExpiryThreshold() { return expiryThreshold; }
        public boolean isLeaseEndpoints() { return leaseEndpoints; }
    }
}