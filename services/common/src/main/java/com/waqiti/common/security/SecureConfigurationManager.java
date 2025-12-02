package com.waqiti.common.security;

import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "vault.enabled", havingValue = "true", matchIfMissing = false)
public class SecureConfigurationManager {

    private final VaultTemplate vaultTemplate;
    private final Map<String, String> secretCache = new ConcurrentHashMap<>();
    
    @Value("${spring.application.name}")
    private String applicationName;
    
    @Value("${vault.secrets.path:secret/data/}")
    private String secretsPath;
    
    @Value("${vault.encryption.enabled:true}")
    private boolean encryptionEnabled;
    
    private SecretKey encryptionKey;
    private static final String ENCRYPTION_ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;

    @PostConstruct
    public void initialize() {
        if (encryptionEnabled) {
            initializeEncryptionKey();
        }
        log.info("SecureConfigurationManager initialized for application: {}", applicationName);
    }

    @Timed("vault.secret.read")
    public String getSecret(String secretName) {
        try {
            String cacheKey = applicationName + "/" + secretName;
            
            // Check cache first
            String cachedSecret = secretCache.get(cacheKey);
            if (cachedSecret != null) {
                return cachedSecret;
            }

            // Read from Vault
            String secretPath = secretsPath + applicationName;
            VaultResponse response = vaultTemplate.read(secretPath);
            
            if (response != null && response.getData() != null) {
                Object secretValue = response.getData().get(secretName);
                if (secretValue != null) {
                    String secret = secretValue.toString();
                    secretCache.put(cacheKey, secret);
                    return secret;
                }
            }
            
            log.warn("Secret not found in Vault: {}/{}", secretPath, secretName);
            return null;
            
        } catch (Exception e) {
            log.error("Error reading secret from Vault: {}", secretName, e);
            return null;
        }
    }

    @Timed("vault.secret.write")
    public void storeSecret(String secretName, String secretValue) {
        try {
            String secretPath = secretsPath + applicationName;
            
            // Read existing secrets
            VaultResponse existingResponse = vaultTemplate.read(secretPath);
            Map<String, Object> secretData = new ConcurrentHashMap<>();
            
            if (existingResponse != null && existingResponse.getData() != null) {
                secretData.putAll(existingResponse.getData());
            }
            
            // Add new secret
            secretData.put(secretName, secretValue);
            
            // Write back to Vault
            vaultTemplate.write(secretPath, secretData);
            
            // Update cache
            String cacheKey = applicationName + "/" + secretName;
            secretCache.put(cacheKey, secretValue);
            
            log.info("Secret stored successfully: {}", secretName);
            
        } catch (Exception e) {
            log.error("Error storing secret in Vault: {}", secretName, e);
            throw new SecurityException("Failed to store secret", e);
        }
    }

    public String getJwtSecret() {
        String secret = getSecret("jwt-secret");
        if (secret == null || secret.isEmpty()) {
            // Generate new JWT secret if not found
            secret = generateSecureJwtSecret();
            storeSecret("jwt-secret", secret);
        }
        return secret;
    }

    public String getDatabasePassword() {
        return getSecret("database-password");
    }

    public String getServiceSecret(String serviceName) {
        return getSecret("service-secret-" + serviceName);
    }

    @Timed("encryption.encrypt")
    public String encryptSensitiveData(String plaintext) {
        if (!encryptionEnabled || plaintext == null) {
            return plaintext;
        }
        
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);
            byte[] encryptedData = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV + encrypted data
            byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedData, 0, encryptedWithIv, GCM_IV_LENGTH, encryptedData.length);
            
            return Base64.getEncoder().encodeToString(encryptedWithIv);
            
        } catch (Exception e) {
            log.error("Error encrypting sensitive data", e);
            throw new SecurityException("Encryption failed", e);
        }
    }

    @Timed("encryption.decrypt")
    public String decryptSensitiveData(String encryptedData) {
        if (!encryptionEnabled || encryptedData == null) {
            return encryptedData;
        }
        
        try {
            byte[] decodedData = Base64.getDecoder().decode(encryptedData);
            
            // Extract IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(decodedData, 0, iv, 0, GCM_IV_LENGTH);
            
            // Extract encrypted data
            byte[] encrypted = new byte[decodedData.length - GCM_IV_LENGTH];
            System.arraycopy(decodedData, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, parameterSpec);
            
            byte[] decryptedData = cipher.doFinal(encrypted);
            return new String(decryptedData, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Error decrypting sensitive data", e);
            throw new SecurityException("Decryption failed", e);
        }
    }

    public void clearSecretCache() {
        secretCache.clear();
        log.info("Secret cache cleared");
    }

    private void initializeEncryptionKey() {
        try {
            String encryptionKeyBase64 = getSecret("encryption-key");
            
            if (encryptionKeyBase64 == null) {
                // Generate new encryption key
                KeyGenerator keyGenerator = KeyGenerator.getInstance(ENCRYPTION_ALGORITHM);
                keyGenerator.init(256);
                SecretKey newKey = keyGenerator.generateKey();
                encryptionKeyBase64 = Base64.getEncoder().encodeToString(newKey.getEncoded());
                storeSecret("encryption-key", encryptionKeyBase64);
                encryptionKey = newKey;
            } else {
                byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
                encryptionKey = new SecretKeySpec(keyBytes, ENCRYPTION_ALGORITHM);
            }
            
            log.info("Encryption key initialized successfully");
            
        } catch (Exception e) {
            log.error("Error initializing encryption key", e);
            throw new SecurityException("Failed to initialize encryption", e);
        }
    }

    private String generateSecureJwtSecret() {
        SecureRandom random = new SecureRandom();
        byte[] secretBytes = new byte[64]; // 512 bits
        random.nextBytes(secretBytes);
        return Base64.getEncoder().encodeToString(secretBytes);
    }
}