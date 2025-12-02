package com.waqiti.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Secure secret management using HashiCorp Vault
 * Replaces hardcoded secrets with dynamic secret retrieval
 */
@Component
@Slf4j
public class VaultSecretManager {
    
    private final VaultTemplate vaultTemplate;
    private final Map<String, Object> secretCache = new ConcurrentHashMap<>();
    
    @Value("${vault.kv.path:waqiti}")
    private String vaultPath;
    
    @Value("${vault.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${vault.cache.ttl:300000}") // 5 minutes
    private long cacheTtl;
    
    public VaultSecretManager(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }
    
    @PostConstruct
    public void init() {
        log.info("Initializing Vault Secret Manager with path: {}", vaultPath);
        
        // Test connection to Vault
        try {
            vaultTemplate.opsForSys().health();
            log.info("Successfully connected to Vault");
        } catch (Exception e) {
            log.error("Failed to connect to Vault: {}", e.getMessage());
            throw new RuntimeException("Vault connection failed", e);
        }
    }
    
    /**
     * Get database secrets
     */
    public DatabaseSecrets getDatabaseSecrets() {
        Map<String, Object> secrets = getSecrets("database");
        
        return DatabaseSecrets.builder()
                .host((String) secrets.get("host"))
                .port(Integer.parseInt(secrets.get("port").toString()))
                .name((String) secrets.get("name"))
                .username((String) secrets.get("username"))
                .password((String) secrets.get("password"))
                .build();
    }
    
    /**
     * Get Redis secrets
     */
    public RedisSecrets getRedisSecrets() {
        Map<String, Object> secrets = getSecrets("redis");
        
        return RedisSecrets.builder()
                .host((String) secrets.get("host"))
                .port(Integer.parseInt(secrets.get("port").toString()))
                .password((String) secrets.get("password"))
                .build();
    }
    
    /**
     * Get JWT secrets
     */
    public JwtSecrets getJwtSecrets() {
        Map<String, Object> secrets = getSecrets("jwt");
        
        return JwtSecrets.builder()
                .secret((String) secrets.get("secret"))
                .expirationMs(Long.parseLong(secrets.get("expiration_ms").toString()))
                .refreshExpirationMs(Long.parseLong(secrets.get("refresh_expiration_ms").toString()))
                .build();
    }
    
    /**
     * Get encryption secrets
     */
    public EncryptionSecrets getEncryptionSecrets() {
        Map<String, Object> secrets = getSecrets("encryption");
        
        return EncryptionSecrets.builder()
                .aesKey((String) secrets.get("aes_key"))
                .rsaPublicKeyPath((String) secrets.get("rsa_public_key_path"))
                .rsaPrivateKeyPath((String) secrets.get("rsa_private_key_path"))
                .build();
    }
    
    /**
     * Get external service secrets
     */
    public ExternalServiceSecrets getExternalServiceSecrets(String serviceName) {
        Map<String, Object> secrets = getSecrets("external-services/" + serviceName);
        
        return ExternalServiceSecrets.builder()
                .serviceUrl((String) secrets.get("service_url"))
                .apiKey((String) secrets.get("api_key"))
                .build();
    }
    
    /**
     * Get payment gateway secrets
     */
    public PaymentGatewaySecrets getPaymentGatewaySecrets() {
        Map<String, Object> secrets = getSecrets("payment-gateway");
        
        return PaymentGatewaySecrets.builder()
                .merchantId((String) secrets.get("merchant_id"))
                .apiKey((String) secrets.get("api_key"))
                .secret((String) secrets.get("secret"))
                .build();
    }
    
    /**
     * Get KYC provider secrets
     */
    public KycProviderSecrets getKycProviderSecrets(String providerName) {
        Map<String, Object> secrets = getSecrets("kyc/" + providerName);
        
        return KycProviderSecrets.builder()
                .apiToken((String) secrets.get("api_token"))
                .apiSecret((String) secrets.get("api_secret"))
                .webhookSecret((String) secrets.get("webhook_secret"))
                .build();
    }
    
    /**
     * Get payment provider secrets
     */
    public PaymentProviderSecrets getPaymentProviderSecrets(String providerName) {
        Map<String, Object> secrets = getSecrets("payment-providers/" + providerName);
        
        return PaymentProviderSecrets.builder()
                .apiKey((String) secrets.get("api_key"))
                .apiSecret((String) secrets.get("api_secret"))
                .clientId((String) secrets.get("client_id"))
                .clientSecret((String) secrets.get("client_secret"))
                .accessToken((String) secrets.get("access_token"))
                .webhookSecret((String) secrets.get("webhook_secret"))
                .build();
    }
    
    /**
     * Generic method to get secrets from Vault
     */
    private Map<String, Object> getSecrets(String path) {
        String cacheKey = vaultPath + "/" + path;
        
        // Check cache first
        if (cacheEnabled && secretCache.containsKey(cacheKey)) {
            CachedSecret cached = (CachedSecret) secretCache.get(cacheKey);
            if (System.currentTimeMillis() - cached.getTimestamp() < cacheTtl) {
                log.debug("Retrieved cached secrets for path: {}", path);
                return cached.getSecrets();
            }
        }
        
        try {
            String fullPath = vaultPath + "/data/" + path;
            VaultResponse response = vaultTemplate.read(fullPath);
            
            if (response == null || response.getData() == null) {
                throw new RuntimeException("No secrets found at path: " + path);
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getData().get("data");
            
            if (data == null || data.isEmpty()) {
                throw new RuntimeException("Empty secrets at path: " + path);
            }
            
            // Cache the secrets
            if (cacheEnabled) {
                secretCache.put(cacheKey, new CachedSecret(data, System.currentTimeMillis()));
            }
            
            log.debug("Retrieved secrets from Vault path: {}", path);
            return data;
            
        } catch (Exception e) {
            log.error("Failed to retrieve secrets from path: {}", path, e);
            throw new RuntimeException("Failed to retrieve secrets from Vault", e);
        }
    }
    
    /**
     * Clear secret cache
     */
    public void clearCache() {
        secretCache.clear();
        log.info("Secret cache cleared");
    }
    
    /**
     * Check if Vault is healthy and accessible
     */
    public boolean isVaultHealthy() {
        try {
            vaultTemplate.opsForSys().health();
            return true;
        } catch (Exception e) {
            log.warn("Vault health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Cache entry for secrets
     */
    private static class CachedSecret {
        private final Map<String, Object> secrets;
        private final long timestamp;
        
        public CachedSecret(Map<String, Object> secrets, long timestamp) {
            this.secrets = secrets;
            this.timestamp = timestamp;
        }
        
        public Map<String, Object> getSecrets() {
            return secrets;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
    
    // Secret classes
    public static class DatabaseSecrets {
        private String host;
        private int port;
        private String name;
        private String username;
        private String password;
        
        public static DatabaseSecretsBuilder builder() {
            return new DatabaseSecretsBuilder();
        }
        
        public static class DatabaseSecretsBuilder {
            private String host;
            private int port;
            private String name;
            private String username;
            private String password;
            
            public DatabaseSecretsBuilder host(String host) {
                this.host = host;
                return this;
            }
            
            public DatabaseSecretsBuilder port(int port) {
                this.port = port;
                return this;
            }
            
            public DatabaseSecretsBuilder name(String name) {
                this.name = name;
                return this;
            }
            
            public DatabaseSecretsBuilder username(String username) {
                this.username = username;
                return this;
            }
            
            public DatabaseSecretsBuilder password(String password) {
                this.password = password;
                return this;
            }
            
            public DatabaseSecrets build() {
                DatabaseSecrets secrets = new DatabaseSecrets();
                secrets.host = this.host;
                secrets.port = this.port;
                secrets.name = this.name;
                secrets.username = this.username;
                secrets.password = this.password;
                return secrets;
            }
        }
        
        // Getters
        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getName() { return name; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
    }
    
    public static class RedisSecrets {
        private String host;
        private int port;
        private String password;
        
        public static RedisSecretsBuilder builder() {
            return new RedisSecretsBuilder();
        }
        
        public static class RedisSecretsBuilder {
            private String host;
            private int port;
            private String password;
            
            public RedisSecretsBuilder host(String host) {
                this.host = host;
                return this;
            }
            
            public RedisSecretsBuilder port(int port) {
                this.port = port;
                return this;
            }
            
            public RedisSecretsBuilder password(String password) {
                this.password = password;
                return this;
            }
            
            public RedisSecrets build() {
                RedisSecrets secrets = new RedisSecrets();
                secrets.host = this.host;
                secrets.port = this.port;
                secrets.password = this.password;
                return secrets;
            }
        }
        
        // Getters
        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getPassword() { return password; }
    }
    
    // Additional secret classes (abbreviated for brevity)
    public static class JwtSecrets {
        private String secret;
        private long expirationMs;
        private long refreshExpirationMs;
        
        public static JwtSecretsBuilder builder() { return new JwtSecretsBuilder(); }
        
        public static class JwtSecretsBuilder {
            private String secret;
            private long expirationMs;
            private long refreshExpirationMs;
            
            public JwtSecretsBuilder secret(String secret) { this.secret = secret; return this; }
            public JwtSecretsBuilder expirationMs(long expirationMs) { this.expirationMs = expirationMs; return this; }
            public JwtSecretsBuilder refreshExpirationMs(long refreshExpirationMs) { this.refreshExpirationMs = refreshExpirationMs; return this; }
            
            public JwtSecrets build() {
                JwtSecrets secrets = new JwtSecrets();
                secrets.secret = this.secret;
                secrets.expirationMs = this.expirationMs;
                secrets.refreshExpirationMs = this.refreshExpirationMs;
                return secrets;
            }
        }
        
        public String getSecret() { return secret; }
        public long getExpirationMs() { return expirationMs; }
        public long getRefreshExpirationMs() { return refreshExpirationMs; }
    }
    
    public static class EncryptionSecrets {
        private String aesKey;
        private String rsaPublicKeyPath;
        private String rsaPrivateKeyPath;
        
        public static EncryptionSecretsBuilder builder() { return new EncryptionSecretsBuilder(); }
        
        public static class EncryptionSecretsBuilder {
            private String aesKey;
            private String rsaPublicKeyPath;
            private String rsaPrivateKeyPath;
            
            public EncryptionSecretsBuilder aesKey(String aesKey) { this.aesKey = aesKey; return this; }
            public EncryptionSecretsBuilder rsaPublicKeyPath(String rsaPublicKeyPath) { this.rsaPublicKeyPath = rsaPublicKeyPath; return this; }
            public EncryptionSecretsBuilder rsaPrivateKeyPath(String rsaPrivateKeyPath) { this.rsaPrivateKeyPath = rsaPrivateKeyPath; return this; }
            
            public EncryptionSecrets build() {
                EncryptionSecrets secrets = new EncryptionSecrets();
                secrets.aesKey = this.aesKey;
                secrets.rsaPublicKeyPath = this.rsaPublicKeyPath;
                secrets.rsaPrivateKeyPath = this.rsaPrivateKeyPath;
                return secrets;
            }
        }
        
        public String getAesKey() { return aesKey; }
        public String getRsaPublicKeyPath() { return rsaPublicKeyPath; }
        public String getRsaPrivateKeyPath() { return rsaPrivateKeyPath; }
    }
    
    public static class ExternalServiceSecrets {
        private String serviceUrl;
        private String apiKey;
        
        public static ExternalServiceSecretsBuilder builder() { return new ExternalServiceSecretsBuilder(); }
        
        public static class ExternalServiceSecretsBuilder {
            private String serviceUrl;
            private String apiKey;
            
            public ExternalServiceSecretsBuilder serviceUrl(String serviceUrl) { this.serviceUrl = serviceUrl; return this; }
            public ExternalServiceSecretsBuilder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
            
            public ExternalServiceSecrets build() {
                ExternalServiceSecrets secrets = new ExternalServiceSecrets();
                secrets.serviceUrl = this.serviceUrl;
                secrets.apiKey = this.apiKey;
                return secrets;
            }
        }
        
        public String getServiceUrl() { return serviceUrl; }
        public String getApiKey() { return apiKey; }
    }
    
    public static class PaymentGatewaySecrets {
        private String merchantId;
        private String apiKey;
        private String secret;
        
        public static PaymentGatewaySecretsBuilder builder() { return new PaymentGatewaySecretsBuilder(); }
        
        public static class PaymentGatewaySecretsBuilder {
            private String merchantId;
            private String apiKey;
            private String secret;
            
            public PaymentGatewaySecretsBuilder merchantId(String merchantId) { this.merchantId = merchantId; return this; }
            public PaymentGatewaySecretsBuilder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
            public PaymentGatewaySecretsBuilder secret(String secret) { this.secret = secret; return this; }
            
            public PaymentGatewaySecrets build() {
                PaymentGatewaySecrets secrets = new PaymentGatewaySecrets();
                secrets.merchantId = this.merchantId;
                secrets.apiKey = this.apiKey;
                secrets.secret = this.secret;
                return secrets;
            }
        }
        
        public String getMerchantId() { return merchantId; }
        public String getApiKey() { return apiKey; }
        public String getSecret() { return secret; }
    }
    
    public static class KycProviderSecrets {
        private String apiToken;
        private String apiSecret;
        private String webhookSecret;
        
        public static KycProviderSecretsBuilder builder() { return new KycProviderSecretsBuilder(); }
        
        public static class KycProviderSecretsBuilder {
            private String apiToken;
            private String apiSecret;
            private String webhookSecret;
            
            public KycProviderSecretsBuilder apiToken(String apiToken) { this.apiToken = apiToken; return this; }
            public KycProviderSecretsBuilder apiSecret(String apiSecret) { this.apiSecret = apiSecret; return this; }
            public KycProviderSecretsBuilder webhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; return this; }
            
            public KycProviderSecrets build() {
                KycProviderSecrets secrets = new KycProviderSecrets();
                secrets.apiToken = this.apiToken;
                secrets.apiSecret = this.apiSecret;
                secrets.webhookSecret = this.webhookSecret;
                return secrets;
            }
        }
        
        public String getApiToken() { return apiToken; }
        public String getApiSecret() { return apiSecret; }
        public String getWebhookSecret() { return webhookSecret; }
    }
    
    public static class PaymentProviderSecrets {
        private String apiKey;
        private String apiSecret;
        private String clientId;
        private String clientSecret;
        private String accessToken;
        private String webhookSecret;
        
        public static PaymentProviderSecretsBuilder builder() { return new PaymentProviderSecretsBuilder(); }
        
        public static class PaymentProviderSecretsBuilder {
            private String apiKey;
            private String apiSecret;
            private String clientId;
            private String clientSecret;
            private String accessToken;
            private String webhookSecret;
            
            public PaymentProviderSecretsBuilder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
            public PaymentProviderSecretsBuilder apiSecret(String apiSecret) { this.apiSecret = apiSecret; return this; }
            public PaymentProviderSecretsBuilder clientId(String clientId) { this.clientId = clientId; return this; }
            public PaymentProviderSecretsBuilder clientSecret(String clientSecret) { this.clientSecret = clientSecret; return this; }
            public PaymentProviderSecretsBuilder accessToken(String accessToken) { this.accessToken = accessToken; return this; }
            public PaymentProviderSecretsBuilder webhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; return this; }
            
            public PaymentProviderSecrets build() {
                PaymentProviderSecrets secrets = new PaymentProviderSecrets();
                secrets.apiKey = this.apiKey;
                secrets.apiSecret = this.apiSecret;
                secrets.clientId = this.clientId;
                secrets.clientSecret = this.clientSecret;
                secrets.accessToken = this.accessToken;
                secrets.webhookSecret = this.webhookSecret;
                return secrets;
            }
        }
        
        public String getApiKey() { return apiKey; }
        public String getApiSecret() { return apiSecret; }
        public String getClientId() { return clientId; }
        public String getClientSecret() { return clientSecret; }
        public String getAccessToken() { return accessToken; }
        public String getWebhookSecret() { return webhookSecret; }
    }
}