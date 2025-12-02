package com.waqiti.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class VaultSecretService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${vault.url:http://localhost:8200}")
    private String vaultUrl;
    
    @Value("${vault.token:}")
    private String vaultToken;
    
    @Value("${vault.namespace:waqiti}")
    private String vaultNamespace;
    
    private String currentVaultToken;
    private final Map<String, SecretCacheEntry> secretCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private SecretKey encryptionKey;

    @PostConstruct
    public void initialize() {
        initializeEncryption();
        authenticateWithVault();
        startTokenRenewalScheduler();
        startSecretRefreshScheduler();
    }

    private void initializeEncryption() {
        try {
            // Generate or load encryption key for local secret caching
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            this.encryptionKey = keyGen.generateKey();
            log.info("Encryption key initialized for secret caching");
        } catch (Exception e) {
            log.error("Failed to initialize encryption", e);
            throw new RuntimeException("Failed to initialize secret encryption", e);
        }
    }

    private void authenticateWithVault() {
        try {
            if (vaultToken != null && !vaultToken.isEmpty()) {
                this.currentVaultToken = vaultToken;
                log.info("Using provided Vault token");
                return;
            }

            // Use Kubernetes auth method in production
            String jwtToken = System.getenv("VAULT_JWT_TOKEN");
            if (jwtToken != null) {
                authenticateWithKubernetes(jwtToken);
                return;
            }

            // Fallback to AppRole authentication
            String roleId = System.getenv("VAULT_ROLE_ID");
            String secretId = System.getenv("VAULT_SECRET_ID");
            if (roleId != null && secretId != null) {
                authenticateWithAppRole(roleId, secretId);
                return;
            }

            log.warn("No Vault authentication method available, using development mode");
            this.currentVaultToken = "dev-token";
            
        } catch (Exception e) {
            log.error("Failed to authenticate with Vault", e);
            throw new RuntimeException("Vault authentication failed", e);
        }
    }

    private void authenticateWithKubernetes(String jwtToken) {
        try {
            String url = vaultUrl + "/v1/auth/kubernetes/login";
            
            Map<String, Object> request = Map.of(
                "role", "waqiti-service",
                "jwt", jwtToken
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            ResponseEntity<Map> response = restTemplate.postForEntity(
                url, new HttpEntity<>(request, headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> auth = (Map<String, Object>) response.getBody().get("auth");
                this.currentVaultToken = (String) auth.get("client_token");
                log.info("Successfully authenticated with Vault using Kubernetes auth");
            }
        } catch (Exception e) {
            log.error("Kubernetes authentication failed", e);
            throw e;
        }
    }

    private void authenticateWithAppRole(String roleId, String secretId) {
        try {
            String url = vaultUrl + "/v1/auth/approle/login";
            
            Map<String, String> request = Map.of(
                "role_id", roleId,
                "secret_id", secretId
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            ResponseEntity<Map> response = restTemplate.postForEntity(
                url, new HttpEntity<>(request, headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> auth = (Map<String, Object>) response.getBody().get("auth");
                this.currentVaultToken = (String) auth.get("client_token");
                log.info("Successfully authenticated with Vault using AppRole");
            }
        } catch (Exception e) {
            log.error("AppRole authentication failed", e);
            throw e;
        }
    }

    @Cacheable(value = "vault-secrets", key = "#secretPath")
    public String getSecret(String secretPath) {
        return getSecretWithFallback(secretPath, null);
    }

    public String getSecretWithFallback(String secretPath, String fallbackValue) {
        try {
            // Check cache first
            SecretCacheEntry cached = secretCache.get(secretPath);
            if (cached != null && !cached.isExpired()) {
                return decrypt(cached.getEncryptedValue());
            }

            // Fetch from Vault
            String url = vaultUrl + "/v1/" + vaultNamespace + "/data/" + secretPath;
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Vault-Token", currentVaultToken);
            headers.set("Content-Type", "application/json");

            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                if (data != null) {
                    Map<String, Object> secretData = (Map<String, Object>) data.get("data");
                    if (secretData != null && !secretData.isEmpty()) {
                        // Return the first value or the "value" key
                        Object secretValue = secretData.get("value");
                        if (secretValue == null) {
                            secretValue = secretData.values().iterator().next();
                        }
                        
                        String secret = secretValue.toString();
                        
                        // Cache the encrypted secret
                        cacheSecret(secretPath, secret);
                        
                        return secret;
                    }
                }
            }
            
            log.warn("Secret not found in Vault: {}, using fallback", secretPath);
            return fallbackValue;
            
        } catch (Exception e) {
            log.error("Failed to retrieve secret: {}", secretPath, e);
            
            // Try to return cached value even if expired
            SecretCacheEntry cached = secretCache.get(secretPath);
            if (cached != null) {
                log.warn("Using expired cached secret for: {}", secretPath);
                return decrypt(cached.getEncryptedValue());
            }
            
            return fallbackValue;
        }
    }

    public void storeSecret(String secretPath, String secretValue) {
        try {
            String url = vaultUrl + "/v1/" + vaultNamespace + "/data/" + secretPath;
            
            Map<String, Object> data = Map.of("data", Map.of("value", secretValue));
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Vault-Token", currentVaultToken);
            headers.set("Content-Type", "application/json");

            ResponseEntity<Void> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(data, headers), Void.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully stored secret: {}", secretPath);
                // Update cache
                cacheSecret(secretPath, secretValue);
            }
            
        } catch (Exception e) {
            log.error("Failed to store secret: {}", secretPath, e);
            throw new RuntimeException("Failed to store secret in Vault", e);
        }
    }

    private void cacheSecret(String secretPath, String secretValue) {
        try {
            String encryptedValue = encrypt(secretValue);
            long expiryTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1); // 1 hour TTL
            
            secretCache.put(secretPath, new SecretCacheEntry(encryptedValue, expiryTime));
        } catch (Exception e) {
            log.error("Failed to cache secret: {}", secretPath, e);
        }
    }

    private String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);
            
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and ciphertext
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            
            return Base64.getEncoder().encodeToString(combined);
            
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    private String decrypt(String encryptedData) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedData);
            
            byte[] iv = new byte[12];
            byte[] ciphertext = new byte[combined.length - 12];
            
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, ciphertext, 0, ciphertext.length);
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, parameterSpec);
            
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private void startTokenRenewalScheduler() {
        scheduler.scheduleAtFixedRate(this::renewVaultToken, 30, 30, TimeUnit.MINUTES);
    }

    private void startSecretRefreshScheduler() {
        scheduler.scheduleAtFixedRate(this::refreshExpiredSecrets, 60, 60, TimeUnit.MINUTES);
    }

    private void renewVaultToken() {
        try {
            String url = vaultUrl + "/v1/auth/token/renew-self";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Vault-Token", currentVaultToken);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                url, new HttpEntity<>(headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Vault token renewed successfully");
            }
        } catch (Exception e) {
            log.error("Failed to renew Vault token", e);
            // Re-authenticate if renewal fails
            authenticateWithVault();
        }
    }

    private void refreshExpiredSecrets() {
        secretCache.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                log.debug("Removing expired secret from cache: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    private static class SecretCacheEntry {
        private final String encryptedValue;
        private final long expiryTime;

        public SecretCacheEntry(String encryptedValue, long expiryTime) {
            this.encryptedValue = encryptedValue;
            this.expiryTime = expiryTime;
        }

        public String getEncryptedValue() {
            return encryptedValue;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }
}