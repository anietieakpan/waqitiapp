package com.waqiti.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import jakarta.annotation.PostConstruct;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Secure application configuration using Vault-managed secrets
 * Replaces hardcoded configuration with dynamic secret retrieval
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "waqiti.security.vault.enabled", havingValue = "true", matchIfMissing = true)
public class SecureApplicationConfiguration {

    private final VaultSecretManager vaultSecretManager;

    @Value("${spring.application.name:waqiti-service}")
    private String applicationName;

    @PostConstruct
    public void initializeSecureConfiguration() {
        log.info("Initializing secure configuration for service: {}", applicationName);
        
        // Verify Vault connectivity at startup
        if (!vaultSecretManager.isVaultHealthy()) {
            throw new RuntimeException("Vault is not accessible - cannot start service securely");
        }
        
        log.info("Secure configuration initialized successfully");
    }

    /**
     * Secure database configuration using Vault secrets
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public SecureDataSourceProperties secureDataSourceProperties() {
        VaultSecretManager.DatabaseSecrets dbSecrets = vaultSecretManager.getDatabaseSecrets();
        
        SecureDataSourceProperties properties = new SecureDataSourceProperties();
        properties.setUrl(String.format("jdbc:postgresql://%s:%d/%s", 
            dbSecrets.getHost(), dbSecrets.getPort(), dbSecrets.getName()));
        properties.setUsername(dbSecrets.getUsername());
        properties.setPassword(dbSecrets.getPassword());
        properties.setDriverClassName("org.postgresql.Driver");
        
        log.info("Database configuration loaded from Vault for host: {}", dbSecrets.getHost());
        return properties;
    }

    /**
     * Secure Redis configuration using Vault secrets
     */
    @Bean
    @Primary
    public SecureRedisProperties secureRedisProperties() {
        VaultSecretManager.RedisSecrets redisSecrets = vaultSecretManager.getRedisSecrets();
        
        SecureRedisProperties properties = new SecureRedisProperties();
        properties.setHost(redisSecrets.getHost());
        properties.setPort(redisSecrets.getPort());
        properties.setPassword(redisSecrets.getPassword());
        properties.setTimeout(5000);
        properties.setSsl(true); // Always use SSL in production
        
        log.info("Redis configuration loaded from Vault for host: {}", redisSecrets.getHost());
        return properties;
    }

    /**
     * Secure JWT configuration using Vault secrets
     */
    @Bean
    @Primary
    public SecureJwtProperties secureJwtProperties() {
        VaultSecretManager.JwtSecrets jwtSecrets = vaultSecretManager.getJwtSecrets();
        
        SecureJwtProperties properties = new SecureJwtProperties();
        properties.setSecret(jwtSecrets.getSecret());
        properties.setExpirationMs(jwtSecrets.getExpirationMs());
        properties.setRefreshExpirationMs(jwtSecrets.getRefreshExpirationMs());
        
        log.info("JWT configuration loaded from Vault");
        return properties;
    }

    /**
     * JWT Decoder using secure secrets
     */
    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        VaultSecretManager.JwtSecrets jwtSecrets = vaultSecretManager.getJwtSecrets();
        
        SecretKeySpec secretKey = new SecretKeySpec(
            jwtSecrets.getSecret().getBytes(StandardCharsets.UTF_8), 
            "HmacSHA256"
        );
        
        return NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
            .build();
    }

    /**
     * Secure encryption configuration using Vault secrets
     */
    @Bean
    @Primary
    public SecureEncryptionProperties secureEncryptionProperties() {
        VaultSecretManager.EncryptionSecrets encryptionSecrets = vaultSecretManager.getEncryptionSecrets();
        
        SecureEncryptionProperties properties = new SecureEncryptionProperties();
        properties.setAesKey(encryptionSecrets.getAesKey());
        properties.setRsaPublicKeyPath(encryptionSecrets.getRsaPublicKeyPath());
        properties.setRsaPrivateKeyPath(encryptionSecrets.getRsaPrivateKeyPath());
        
        log.info("Encryption configuration loaded from Vault");
        return properties;
    }

    /**
     * Secure payment gateway configuration using Vault secrets
     */
    @Bean
    @Primary
    public SecurePaymentGatewayProperties securePaymentGatewayProperties() {
        VaultSecretManager.PaymentGatewaySecrets paymentSecrets = vaultSecretManager.getPaymentGatewaySecrets();
        
        SecurePaymentGatewayProperties properties = new SecurePaymentGatewayProperties();
        properties.setMerchantId(paymentSecrets.getMerchantId());
        properties.setApiKey(paymentSecrets.getApiKey());
        properties.setSecret(paymentSecrets.getSecret());
        
        log.info("Payment gateway configuration loaded from Vault");
        return properties;
    }

    /**
     * Get external service configuration securely
     */
    public SecureExternalServiceProperties getExternalServiceProperties(String serviceName) {
        VaultSecretManager.ExternalServiceSecrets serviceSecrets = 
            vaultSecretManager.getExternalServiceSecrets(serviceName);
        
        SecureExternalServiceProperties properties = new SecureExternalServiceProperties();
        properties.setServiceUrl(serviceSecrets.getServiceUrl());
        properties.setApiKey(serviceSecrets.getApiKey());
        
        log.info("External service configuration loaded from Vault for: {}", serviceName);
        return properties;
    }

    /**
     * Get KYC provider configuration securely
     */
    public SecureKycProviderProperties getKycProviderProperties(String providerName) {
        VaultSecretManager.KycProviderSecrets kycSecrets = 
            vaultSecretManager.getKycProviderSecrets(providerName);
        
        SecureKycProviderProperties properties = new SecureKycProviderProperties();
        properties.setApiToken(kycSecrets.getApiToken());
        properties.setApiSecret(kycSecrets.getApiSecret());
        properties.setWebhookSecret(kycSecrets.getWebhookSecret());
        
        log.info("KYC provider configuration loaded from Vault for: {}", providerName);
        return properties;
    }

    /**
     * Get payment provider configuration securely
     */
    public SecurePaymentProviderProperties getPaymentProviderProperties(String providerName) {
        VaultSecretManager.PaymentProviderSecrets providerSecrets = 
            vaultSecretManager.getPaymentProviderSecrets(providerName);
        
        SecurePaymentProviderProperties properties = new SecurePaymentProviderProperties();
        properties.setApiKey(providerSecrets.getApiKey());
        properties.setApiSecret(providerSecrets.getApiSecret());
        properties.setClientId(providerSecrets.getClientId());
        properties.setClientSecret(providerSecrets.getClientSecret());
        properties.setAccessToken(providerSecrets.getAccessToken());
        properties.setWebhookSecret(providerSecrets.getWebhookSecret());
        
        log.info("Payment provider configuration loaded from Vault for: {}", providerName);
        return properties;
    }

    // Configuration property classes
    public static class SecureDataSourceProperties {
        private String url;
        private String username;
        private String password;
        private String driverClassName;

        // Getters and setters
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getDriverClassName() { return driverClassName; }
        public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
    }

    public static class SecureRedisProperties {
        private String host;
        private int port;
        private String password;
        private int timeout;
        private boolean ssl;

        // Getters and setters
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }
        public boolean isSsl() { return ssl; }
        public void setSsl(boolean ssl) { this.ssl = ssl; }
    }

    public static class SecureJwtProperties {
        private String secret;
        private long expirationMs;
        private long refreshExpirationMs;

        // Getters and setters
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getExpirationMs() { return expirationMs; }
        public void setExpirationMs(long expirationMs) { this.expirationMs = expirationMs; }
        public long getRefreshExpirationMs() { return refreshExpirationMs; }
        public void setRefreshExpirationMs(long refreshExpirationMs) { this.refreshExpirationMs = refreshExpirationMs; }
    }

    public static class SecureEncryptionProperties {
        private String aesKey;
        private String rsaPublicKeyPath;
        private String rsaPrivateKeyPath;

        // Getters and setters
        public String getAesKey() { return aesKey; }
        public void setAesKey(String aesKey) { this.aesKey = aesKey; }
        public String getRsaPublicKeyPath() { return rsaPublicKeyPath; }
        public void setRsaPublicKeyPath(String rsaPublicKeyPath) { this.rsaPublicKeyPath = rsaPublicKeyPath; }
        public String getRsaPrivateKeyPath() { return rsaPrivateKeyPath; }
        public void setRsaPrivateKeyPath(String rsaPrivateKeyPath) { this.rsaPrivateKeyPath = rsaPrivateKeyPath; }
    }

    public static class SecurePaymentGatewayProperties {
        private String merchantId;
        private String apiKey;
        private String secret;

        // Getters and setters
        public String getMerchantId() { return merchantId; }
        public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
    }

    public static class SecureExternalServiceProperties {
        private String serviceUrl;
        private String apiKey;

        // Getters and setters
        public String getServiceUrl() { return serviceUrl; }
        public void setServiceUrl(String serviceUrl) { this.serviceUrl = serviceUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    public static class SecureKycProviderProperties {
        private String apiToken;
        private String apiSecret;
        private String webhookSecret;

        // Getters and setters
        public String getApiToken() { return apiToken; }
        public void setApiToken(String apiToken) { this.apiToken = apiToken; }
        public String getApiSecret() { return apiSecret; }
        public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }
        public String getWebhookSecret() { return webhookSecret; }
        public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    }

    public static class SecurePaymentProviderProperties {
        private String apiKey;
        private String apiSecret;
        private String clientId;
        private String clientSecret;
        private String accessToken;
        private String webhookSecret;

        // Getters and setters
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getApiSecret() { return apiSecret; }
        public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getWebhookSecret() { return webhookSecret; }
        public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    }
}