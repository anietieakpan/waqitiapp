package com.waqiti.config.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.config.AbstractVaultConfiguration;
import org.springframework.vault.core.VaultTemplate;

@Configuration
@ConfigurationProperties(prefix = "vault")
public class VaultConfig extends AbstractVaultConfiguration {

    private String host = "localhost";
    private int port = 8200;
    private String scheme = "http";
    private String token;
    
    @Override
    public VaultEndpoint vaultEndpoint() {
        VaultEndpoint endpoint = new VaultEndpoint();
        endpoint.setHost(this.host);
        endpoint.setPort(this.port);
        endpoint.setScheme(this.scheme);
        return endpoint;
    }

    @Override
    public TokenAuthentication clientAuthentication() {
        return new TokenAuthentication(this.token);
    }

    @Bean
    public VaultTemplate vaultTemplate() {
        return new VaultTemplate(vaultEndpoint(), clientAuthentication());
    }

    // Getters and Setters
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}