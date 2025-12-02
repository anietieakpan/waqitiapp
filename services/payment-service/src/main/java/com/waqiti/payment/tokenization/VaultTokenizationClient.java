package com.waqiti.payment.tokenization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultTransitContext;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * HashiCorp Vault client for tokenization operations
 *
 * Integrates with Vault Transit Engine for:
 * - Encryption as a Service
 * - Key management and rotation
 * - Hardware Security Module (HSM) integration
 * - Audit logging
 *
 * @author Waqiti Security Team
 * @version 3.0.0
 * @since 2025-10-11
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VaultTokenizationClient {

    private final VaultTemplate vaultTemplate;

    @Value("${vault.token.path:secret/tokens}")
    private String tokenPath;

    @Value("${vault.transit.mount:transit}")
    private String transitMount;

    @Value("${vault.transit.key:payment-tokens}")
    private String transitKey;

    /**
     * Store encrypted token in Vault
     *
     * @param token Token identifier
     * @param encryptedData Encrypted sensitive data
     * @param metadata Additional metadata
     * @param ttl Time to live in seconds
     */
    public void storeToken(String token, String encryptedData, Map<String, String> metadata, long ttl) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("encrypted_data", encryptedData);
            data.put("created_at", System.currentTimeMillis());
            data.put("expires_at", System.currentTimeMillis() + (ttl * 1000));

            if (metadata != null) {
                data.putAll(metadata);
            }

            String path = tokenPath + "/" + token;
            vaultTemplate.write(path, data);

            log.debug("Token stored in Vault: path={}", path);

        } catch (Exception e) {
            log.error("Failed to store token in Vault: token={}, error={}",
                maskToken(token), e.getMessage(), e);
            throw new RuntimeException("Failed to store token in Vault", e);
        }
    }

    /**
     * Retrieve encrypted token from Vault
     *
     * @param token Token identifier
     * @return Encrypted sensitive data
     */
    public String retrieveToken(String token) {
        try {
            String path = tokenPath + "/" + token;
            VaultResponse response = vaultTemplate.read(path);

            if (response == null || response.getData() == null) {
                log.warn("Token not found in Vault: token={}", maskToken(token));
                return null;
            }

            // Check expiration
            Long expiresAt = (Long) response.getData().get("expires_at");
            if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
                log.warn("Token expired: token={}, expiresAt={}", maskToken(token), expiresAt);
                deleteToken(token);
                return null;
            }

            String encryptedData = (String) response.getData().get("encrypted_data");
            log.debug("Token retrieved from Vault: path={}", path);

            return encryptedData;

        } catch (Exception e) {
            log.error("Failed to retrieve token from Vault: token={}, error={}",
                maskToken(token), e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve token from Vault", e);
        }
    }

    /**
     * Get token metadata from Vault
     *
     * @param token Token identifier
     * @return Token metadata
     */
    public Map<String, String> getTokenMetadata(String token) {
        try {
            String path = tokenPath + "/" + token;
            VaultResponse response = vaultTemplate.read(path);

            if (response == null || response.getData() == null) {
                return new HashMap<>();
            }

            Map<String, String> metadata = new HashMap<>();
            response.getData().forEach((key, value) -> {
                if (!key.equals("encrypted_data") && value != null) {
                    metadata.put(key, value.toString());
                }
            });

            return metadata;

        } catch (Exception e) {
            log.error("Failed to get token metadata: token={}, error={}",
                maskToken(token), e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * Delete token from Vault
     *
     * @param token Token identifier
     */
    public void deleteToken(String token) {
        try {
            String path = tokenPath + "/" + token;
            vaultTemplate.delete(path);

            log.debug("Token deleted from Vault: path={}", path);

        } catch (Exception e) {
            log.error("Failed to delete token from Vault: token={}, error={}",
                maskToken(token), e.getMessage(), e);
            throw new RuntimeException("Failed to delete token from Vault", e);
        }
    }

    /**
     * Get encryption key from Vault Transit Engine
     *
     * @param keyName Transit key name
     * @return Secret key for encryption
     */
    public SecretKey getEncryptionKey(String keyName) {
        try {
            // For this implementation, we'll use Vault's datakey endpoint
            // which generates a data encryption key backed by the master key
            String path = transitMount + "/datakey/plaintext/" + keyName;

            VaultResponse response = vaultTemplate.write(path, null);

            if (response == null || response.getData() == null) {
                throw new RuntimeException("Failed to get encryption key from Vault");
            }

            String plaintext = (String) response.getData().get("plaintext");
            byte[] keyBytes = Base64.getDecoder().decode(plaintext);

            return new SecretKeySpec(keyBytes, 0, 32, "AES");

        } catch (Exception e) {
            log.error("Failed to get encryption key from Vault: keyName={}, error={}",
                keyName, e.getMessage(), e);
            throw new RuntimeException("Failed to get encryption key from Vault", e);
        }
    }

    /**
     * Rotate encryption keys in Vault Transit Engine
     *
     * @param keyName Transit key name
     */
    public void rotateKey(String keyName) {
        try {
            String path = transitMount + "/keys/" + keyName + "/rotate";
            vaultTemplate.write(path, null);

            log.info("Encryption key rotated in Vault: keyName={}", keyName);

        } catch (Exception e) {
            log.error("Failed to rotate encryption key: keyName={}, error={}",
                keyName, e.getMessage(), e);
            throw new RuntimeException("Failed to rotate encryption key", e);
        }
    }

    /**
     * Mask token for logging
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
}
