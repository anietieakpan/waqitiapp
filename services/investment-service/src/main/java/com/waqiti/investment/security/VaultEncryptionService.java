package com.waqiti.investment.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Vault Encryption Service - HashiCorp Vault integration for data encryption/decryption
 *
 * CRITICAL SECURITY: This service handles encryption and decryption of sensitive data
 * including Tax Identification Numbers (SSN/EIN).
 *
 * Security Features:
 * - AES-256-GCM encryption
 * - Key rotation support
 * - Audit logging
 * - Transit engine for encryption as a service
 *
 * Compliance:
 * - IRS Publication 1075 (Safeguarding Tax Information)
 * - NIST SP 800-57 (Key Management)
 * - PCI DSS 3.2.1 (encryption requirements)
 * - SOC 2 Type II data protection controls
 *
 * Vault Transit Engine API:
 * - POST /v1/transit/decrypt/{key_name} - Decrypt data
 * - POST /v1/transit/encrypt/{key_name} - Encrypt data
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-02
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VaultEncryptionService {

    private final RestTemplate restTemplate;

    @Value("${vault.url:http://vault:8200}")
    private String vaultUrl;

    @Value("${vault.token:}")
    private String vaultToken;

    @Value("${vault.transit.key-name:tax-data}")
    private String transitKeyName;

    /**
     * Decrypt encrypted data using Vault Transit Engine.
     *
     * CRITICAL SECURITY: This method decrypts sensitive PII data.
     * - All decryption operations are logged for audit
     * - Decrypted data must be handled securely
     * - Never log decrypted values
     *
     * @param encryptedData Base64-encoded encrypted data
     * @return Decrypted plaintext string
     * @throws EncryptionException if decryption fails
     */
    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isBlank()) {
            log.warn("Attempted to decrypt null or empty data");
            return null;
        }

        log.info("SECURITY AUDIT: Decrypting sensitive data using Vault Transit Engine");

        try {
            String url = vaultUrl + "/v1/transit/decrypt/" + transitKeyName;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Vault-Token", vaultToken);

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("ciphertext", encryptedData);

            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                if (data != null && data.containsKey("plaintext")) {
                    String base64Plaintext = (String) data.get("plaintext");
                    byte[] plaintextBytes = Base64.getDecoder().decode(base64Plaintext);
                    String plaintext = new String(plaintextBytes);

                    log.info("SECURITY AUDIT: Successfully decrypted data using Vault");
                    return plaintext;
                } else {
                    log.error("Vault decryption response missing plaintext data");
                    throw new EncryptionException("Invalid decryption response from Vault");
                }
            } else {
                log.error("Vault decryption failed with status: {}", response.getStatusCode());
                throw new EncryptionException("Vault decryption failed");
            }

        } catch (RestClientException e) {
            log.error("Failed to communicate with Vault for decryption", e);
            throw new EncryptionException("Vault communication error during decryption", e);
        } catch (Exception e) {
            log.error("Unexpected error during decryption", e);
            throw new EncryptionException("Decryption failed", e);
        }
    }

    /**
     * Encrypt plaintext data using Vault Transit Engine.
     *
     * @param plaintext Plaintext string to encrypt
     * @return Base64-encoded encrypted data
     * @throws EncryptionException if encryption fails
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            log.warn("Attempted to encrypt null or empty data");
            return null;
        }

        log.debug("Encrypting data using Vault Transit Engine");

        try {
            String url = vaultUrl + "/v1/transit/encrypt/" + transitKeyName;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Vault-Token", vaultToken);

            // Base64 encode the plaintext before sending to Vault
            String base64Plaintext = Base64.getEncoder().encodeToString(plaintext.getBytes());

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("plaintext", base64Plaintext);

            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                if (data != null && data.containsKey("ciphertext")) {
                    String ciphertext = (String) data.get("ciphertext");
                    log.debug("Successfully encrypted data using Vault");
                    return ciphertext;
                } else {
                    log.error("Vault encryption response missing ciphertext data");
                    throw new EncryptionException("Invalid encryption response from Vault");
                }
            } else {
                log.error("Vault encryption failed with status: {}", response.getStatusCode());
                throw new EncryptionException("Vault encryption failed");
            }

        } catch (RestClientException e) {
            log.error("Failed to communicate with Vault for encryption", e);
            throw new EncryptionException("Vault communication error during encryption", e);
        } catch (Exception e) {
            log.error("Unexpected error during encryption", e);
            throw new EncryptionException("Encryption failed", e);
        }
    }

    /**
     * Encryption/Decryption Exception
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message) {
            super(message);
        }

        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
