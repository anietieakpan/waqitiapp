package com.waqiti.vault.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.AwsIamAuthentication;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.KubernetesAuthentication;
import org.springframework.vault.authentication.KubernetesAuthenticationOptions;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.config.AbstractVaultConfiguration;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.SslConfiguration;

import java.net.URI;

/**
 * HashiCorp Vault Configuration
 * 
 * Configures Vault client with appropriate authentication methods
 * and SSL settings for secure communication.
 */
@Configuration
public class VaultConfiguration extends AbstractVaultConfiguration {

    @Value("${vault.uri:https://vault.example.internal:8200}")
    private String vaultUri;

    @Value("${vault.authentication.method:kubernetes}")
    private String authenticationMethod;

    @Value("${vault.authentication.role:waqiti-app}")
    private String role;

    @Value("${vault.authentication.jwt-path:/var/run/secrets/kubernetes.io/serviceaccount/token}")
    private String jwtPath;

    @Value("${vault.ssl.trust-store:}")
    private String trustStore;

    @Value("${vault.ssl.trust-store-password:}")
    private String trustStorePassword;

    @Value("${vault.connection-timeout:5000}")
    private int connectionTimeout;

    @Value("${vault.read-timeout:15000}")
    private int readTimeout;

    @Override
    public VaultEndpoint vaultEndpoint() {
        try {
            return VaultEndpoint.from(URI.create(vaultUri));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Vault URI: " + vaultUri, e);
        }
    }

    @Override
    public ClientAuthentication clientAuthentication() {
        switch (authenticationMethod.toLowerCase()) {
            case "kubernetes":
                return kubernetesAuthentication();
            case "aws-iam":
                return awsIamAuthentication();
            default:
                throw new IllegalArgumentException("Unsupported authentication method: " + authenticationMethod);
        }
    }

    @Override
    public SslConfiguration sslConfiguration() {
        if (trustStore != null && !trustStore.isEmpty()) {
            return SslConfiguration.forTrustStore(trustStore, trustStorePassword.toCharArray());
        }
        return SslConfiguration.unconfigured();
    }

    private ClientAuthentication kubernetesAuthentication() {
        KubernetesAuthenticationOptions options = KubernetesAuthenticationOptions.builder()
                .role(role)
                .jwtSupplier(() -> {
                    try {
                        return java.nio.file.Files.readString(java.nio.file.Paths.get(jwtPath));
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to read Kubernetes service account token", e);
                    }
                })
                .path("kubernetes")
                .build();

        return new KubernetesAuthentication(options, restOperations());
    }

    private ClientAuthentication awsIamAuthentication() {
        return new AwsIamAuthentication(restOperations());
    }

    @Bean
    public VaultTemplate vaultTemplate() {
        VaultTemplate template = new VaultTemplate(vaultEndpoint(), clientAuthentication());
        template.getRestTemplate().setConnectTimeout(connectionTimeout);
        template.getRestTemplate().setReadTimeout(readTimeout);
        return template;
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> vaultMetricsCustomizer() {
        return registry -> registry.config()
                .commonTags("service", "vault-client")
                .commonTags("authentication", authenticationMethod);
    }
}