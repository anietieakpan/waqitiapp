package com.waqiti.nft.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Vault Integration for Secure Key Storage
 * 
 * Integrates with HashiCorp Vault for secure storage of:
 * - Encrypted private keys
 * - Initialization vectors (IVs)
 * - Master encryption keys
 * - Key metadata and audit information
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VaultKeyProvider {

    private final VaultTemplate vaultTemplate;
    
    @Value("${vault.blockchain.path:secret/blockchain}")
    private String blockchainSecretsPath;
    
    @Value("${vault.blockchain.master-key-path:secret/blockchain/master-key}")
    private String masterKeyPath;
    
    @Value("${vault.blockchain.key-version:latest}")
    private String keyVersion;
    
    @PostConstruct
    public void init() {
        try {
            // Verify Vault connectivity
            vaultTemplate.opsForSys().health();
            log.info("Successfully connected to Vault for blockchain key management");
            
            // Ensure master encryption key exists
            ensureMasterKeyExists();
            
        } catch (Exception e) {
            log.error("Failed to initialize Vault connection", e);
            throw new KeyManagementException("Cannot initialize Vault connection", e);
        }
    }
    
    /**
     * Retrieve encrypted private key from Vault
     */
    public String getEncryptedPrivateKey(String keyIdentifier) {
        try {
            String path = buildKeyPath(keyIdentifier);
            VaultResponse response = vaultTemplate.read(path);
            
            if (response == null || response.getData() == null) {
                log.error("No encrypted key found in Vault for: {}", keyIdentifier);
                throw new KeyManagementException("Encrypted key not found in Vault for: " + keyIdentifier);
            }
            
            String encryptedKey = (String) response.getData().get("encrypted_private_key");
            if (encryptedKey == null) {
                log.error("Encrypted private key is null in Vault for: {}", keyIdentifier);
                throw new KeyManagementException("Encrypted private key is null for: " + keyIdentifier);
            }
            
            return encryptedKey;
            
        } catch (KeyManagementException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve encrypted private key from Vault: {}", keyIdentifier, e);
            throw new KeyManagementException("Failed to retrieve key from Vault", e);
        }
    }
    
    /**
     * Retrieve initialization vector for key decryption
     */
    public String getIV(String keyIdentifier) {
        try {
            String path = buildKeyPath(keyIdentifier);
            VaultResponse response = vaultTemplate.read(path);
            
            if (response == null || response.getData() == null) {
                log.error("No IV found in Vault for: {}", keyIdentifier);
                throw new KeyManagementException("IV not found in Vault for: " + keyIdentifier);
            }
            
            String iv = (String) response.getData().get("iv");
            if (iv == null) {
                log.error("IV is null in Vault for: {}", keyIdentifier);
                throw new KeyManagementException("IV is null for: " + keyIdentifier);
            }
            
            return iv;
            
        } catch (KeyManagementException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve IV from Vault: {}", keyIdentifier, e);
            throw new KeyManagementException("Failed to retrieve IV from Vault", e);
        }
    }
    
    /**
     * Store encrypted private key in Vault
     */
    public void storeEncryptedPrivateKey(String keyIdentifier, String encryptedPrivateKey) {
        try {
            String path = buildKeyPath(keyIdentifier);
            
            // Retrieve existing data to preserve other fields
            VaultResponse existingResponse = vaultTemplate.read(path);
            Map<String, Object> data = existingResponse != null && existingResponse.getData() != null
                    ? new HashMap<>(existingResponse.getData())
                    : new HashMap<>();
            
            // Update encrypted key
            data.put("encrypted_private_key", encryptedPrivateKey);
            data.put("updated_at", System.currentTimeMillis());
            data.put("key_version", keyVersion);
            
            // Write to Vault
            vaultTemplate.write(path, data);
            
            log.info("Successfully stored encrypted private key in Vault for: {}", keyIdentifier);
            
        } catch (Exception e) {
            log.error("Failed to store encrypted private key in Vault: {}", keyIdentifier, e);
            throw new KeyManagementException("Failed to store key in Vault", e);
        }
    }
    
    /**
     * Store initialization vector in Vault
     */
    public void storeIV(String keyIdentifier, String iv) {
        try {
            String path = buildKeyPath(keyIdentifier);
            
            // Retrieve existing data
            VaultResponse existingResponse = vaultTemplate.read(path);
            Map<String, Object> data = existingResponse != null && existingResponse.getData() != null
                    ? new HashMap<>(existingResponse.getData())
                    : new HashMap<>();
            
            // Update IV
            data.put("iv", iv);
            data.put("updated_at", System.currentTimeMillis());
            
            // Write to Vault
            vaultTemplate.write(path, data);
            
            log.debug("Successfully stored IV in Vault for: {}", keyIdentifier);
            
        } catch (Exception e) {
            log.error("Failed to store IV in Vault: {}", keyIdentifier, e);
            throw new KeyManagementException("Failed to store IV in Vault", e);
        }
    }
    
    /**
     * Get master encryption key from Vault
     * This key is used to encrypt/decrypt private keys
     */
    public byte[] getMasterEncryptionKey() {
        try {
            VaultResponse response = vaultTemplate.read(masterKeyPath);
            
            if (response == null || response.getData() == null) {
                throw new KeyManagementException("Master encryption key not found in Vault");
            }
            
            String masterKeyBase64 = (String) response.getData().get("master_key");
            if (masterKeyBase64 == null) {
                throw new KeyManagementException("Master encryption key is null");
            }
            
            return java.util.Base64.getDecoder().decode(masterKeyBase64);
            
        } catch (Exception e) {
            log.error("Failed to retrieve master encryption key from Vault", e);
            throw new KeyManagementException("Failed to retrieve master key", e);
        }
    }
    
    /**
     * Revoke a key by marking it as revoked in Vault
     */
    public void revokeKey(String keyIdentifier) {
        try {
            String path = buildKeyPath(keyIdentifier);
            
            // Retrieve existing data
            VaultResponse existingResponse = vaultTemplate.read(path);
            Map<String, Object> data = existingResponse != null && existingResponse.getData() != null
                    ? new HashMap<>(existingResponse.getData())
                    : new HashMap<>();
            
            // Mark as revoked
            data.put("revoked", true);
            data.put("revoked_at", System.currentTimeMillis());
            data.put("encrypted_private_key", null); // Clear the key
            data.put("iv", null); // Clear the IV
            
            // Write to Vault
            vaultTemplate.write(path, data);
            
            log.warn("SECURITY: Revoked key in Vault for: {}", keyIdentifier);
            
        } catch (Exception e) {
            log.error("Failed to revoke key in Vault: {}", keyIdentifier, e);
            throw new KeyManagementException("Failed to revoke key in Vault", e);
        }
    }
    
    /**
     * Check if a key is revoked
     */
    public boolean isKeyRevoked(String keyIdentifier) {
        try {
            String path = buildKeyPath(keyIdentifier);
            VaultResponse response = vaultTemplate.read(path);
            
            if (response == null || response.getData() == null) {
                return false;
            }
            
            Boolean revoked = (Boolean) response.getData().get("revoked");
            return revoked != null && revoked;
            
        } catch (Exception e) {
            log.error("Failed to check revocation status in Vault: {}", keyIdentifier, e);
            return false;
        }
    }
    
    /**
     * Store key metadata
     */
    public void storeKeyMetadata(String keyIdentifier, Map<String, Object> metadata) {
        try {
            String path = buildKeyPath(keyIdentifier);
            
            // Retrieve existing data
            VaultResponse existingResponse = vaultTemplate.read(path);
            Map<String, Object> data = existingResponse != null && existingResponse.getData() != null
                    ? new HashMap<>(existingResponse.getData())
                    : new HashMap<>();
            
            // Add metadata
            data.put("metadata", metadata);
            data.put("metadata_updated_at", System.currentTimeMillis());
            
            // Write to Vault
            vaultTemplate.write(path, data);
            
            log.debug("Successfully stored metadata in Vault for: {}", keyIdentifier);
            
        } catch (Exception e) {
            log.error("Failed to store metadata in Vault: {}", keyIdentifier, e);
            throw new KeyManagementException("Failed to store metadata in Vault", e);
        }
    }
    
    /**
     * Retrieve key metadata
     */
    public Map<String, Object> getKeyMetadata(String keyIdentifier) {
        try {
            String path = buildKeyPath(keyIdentifier);
            VaultResponse response = vaultTemplate.read(path);
            
            if (response == null || response.getData() == null) {
                log.warn("No metadata found in Vault for: {}", keyIdentifier);
                return new HashMap<>();
            }
            
            Map<String, Object> metadata = (Map<String, Object>) response.getData().get("metadata");
            return metadata != null ? metadata : new HashMap<>();
            
        } catch (Exception e) {
            log.error("Failed to retrieve metadata from Vault: {}", keyIdentifier, e);
            throw new KeyManagementException("Failed to retrieve metadata from Vault", e);
        }
    }
    
    // Private helper methods
    
    private String buildKeyPath(String keyIdentifier) {
        return blockchainSecretsPath + "/" + keyIdentifier;
    }
    
    private void ensureMasterKeyExists() {
        try {
            VaultResponse response = vaultTemplate.read(masterKeyPath);
            
            if (response == null || response.getData() == null || !response.getData().containsKey("master_key")) {
                log.warn("Master encryption key not found in Vault. Creating new master key...");
                createMasterKey();
            } else {
                log.info("Master encryption key found in Vault");
            }
            
        } catch (Exception e) {
            log.warn("Could not verify master key existence, attempting to create: {}", e.getMessage());
            createMasterKey();
        }
    }
    
    private void createMasterKey() {
        try {
            // Generate a new 256-bit master key
            java.security.SecureRandom secureRandom = new java.security.SecureRandom();
            byte[] masterKey = new byte[32]; // 256 bits
            secureRandom.nextBytes(masterKey);
            
            String masterKeyBase64 = java.util.Base64.getEncoder().encodeToString(masterKey);
            
            // Store in Vault
            Map<String, Object> data = new HashMap<>();
            data.put("master_key", masterKeyBase64);
            data.put("created_at", System.currentTimeMillis());
            data.put("key_version", keyVersion);
            data.put("algorithm", "AES-256-GCM");
            
            vaultTemplate.write(masterKeyPath, data);
            
            // Clear master key from memory
            java.util.Arrays.fill(masterKey, (byte) 0);
            masterKeyBase64 = null;
            System.gc();
            
            log.info("Successfully created and stored new master encryption key in Vault");
            
        } catch (Exception e) {
            log.error("Failed to create master encryption key", e);
            throw new KeyManagementException("Failed to create master key", e);
        }
    }
}