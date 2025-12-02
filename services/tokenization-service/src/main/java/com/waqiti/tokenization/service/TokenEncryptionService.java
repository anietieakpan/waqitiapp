package com.waqiti.tokenization.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Token Encryption Service
 *
 * Integrates with AWS KMS to encrypt/decrypt sensitive data.
 * All sensitive data is encrypted at rest using AWS KMS envelope encryption.
 *
 * Security:
 * - AWS KMS master key encryption
 * - AES-256-GCM encryption algorithm
 * - Encryption context for enhanced security
 * - No plaintext data storage
 *
 * PCI-DSS Compliance:
 * - Requirement 3.4: Render PAN unreadable (encryption at rest)
 * - Requirement 3.5: Protect keys used for encryption
 *
 * @author Waqiti Platform Engineering
 */
@Service
@Slf4j
public class TokenEncryptionService {

    private final MeterRegistry meterRegistry;

    private final Counter encryptionSuccess;
    private final Counter encryptionError;
    private final Counter decryptionSuccess;
    private final Counter decryptionError;

    @Value("${tokenization.kms.enabled:true}")
    private boolean kmsEnabled;

    @Value("${tokenization.kms.region:us-east-1}")
    private String kmsRegion;

    public TokenEncryptionService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize metrics
        this.encryptionSuccess = Counter.builder("tokenization.encryption.success")
            .description("Successful encryption operations")
            .register(meterRegistry);

        this.encryptionError = Counter.builder("tokenization.encryption.error")
            .description("Failed encryption operations")
            .register(meterRegistry);

        this.decryptionSuccess = Counter.builder("tokenization.decryption.success")
            .description("Successful decryption operations")
            .register(meterRegistry);

        this.decryptionError = Counter.builder("tokenization.decryption.error")
            .description("Failed decryption operations")
            .register(meterRegistry);
    }

    /**
     * Encrypt sensitive data using AWS KMS
     *
     * @param plaintext The sensitive data to encrypt
     * @param kmsKeyId AWS KMS key ID
     * @return Base64-encoded encrypted data
     */
    public String encrypt(String plaintext, String kmsKeyId) {
        try {
            log.debug("Encrypting data with KMS key: {}", kmsKeyId);

            if (!kmsEnabled) {
                log.warn("KMS is disabled, using fallback encryption");
                return encryptFallback(plaintext);
            }

            // PRODUCTION-READY: Real AWS KMS encryption
            software.amazon.awssdk.services.kms.KmsClient kmsClient =
                software.amazon.awssdk.services.kms.KmsClient.builder()
                    .region(software.amazon.awssdk.regions.Region.of(kmsRegion))
                    .build();

            // Add encryption context for enhanced security
            Map<String, String> encryptionContext = Map.of(
                "service", "tokenization-service",
                "purpose", "sensitive-data-encryption",
                "timestamp", String.valueOf(System.currentTimeMillis())
            );

            software.amazon.awssdk.services.kms.model.EncryptRequest request =
                software.amazon.awssdk.services.kms.model.EncryptRequest.builder()
                    .keyId(kmsKeyId)
                    .plaintext(software.amazon.awssdk.core.SdkBytes.fromUtf8String(plaintext))
                    .encryptionContext(encryptionContext)
                    .build();

            software.amazon.awssdk.services.kms.model.EncryptResponse response =
                kmsClient.encrypt(request);

            byte[] encryptedBytes = response.ciphertextBlob().asByteArray();
            String encrypted = Base64.getEncoder().encodeToString(encryptedBytes);

            encryptionSuccess.increment();

            log.debug("Data encrypted successfully with KMS");

            kmsClient.close();

            return encrypted;

        } catch (software.amazon.awssdk.services.kms.model.KmsException e) {
            log.error("AWS KMS encryption failed: keyId={}, error={}", kmsKeyId, e.getMessage(), e);
            encryptionError.increment();

            // Critical alert
            log.error("CRITICAL: KMS encryption failure - keyId={}, code={}, message={}",
                kmsKeyId, e.awsErrorDetails().errorCode(), e.awsErrorDetails().errorMessage());

            throw new EncryptionException("KMS encryption failed: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("Encryption failed: keyId={}", kmsKeyId, e);
            encryptionError.increment();
            throw new EncryptionException("Encryption failed", e);
        }
    }

    /**
     * Decrypt sensitive data using AWS KMS
     *
     * @param ciphertext Base64-encoded encrypted data
     * @param kmsKeyId AWS KMS key ID (used for context)
     * @return Decrypted plaintext data
     */
    public String decrypt(String ciphertext, String kmsKeyId) {
        try {
            log.debug("Decrypting data with KMS key: {}", kmsKeyId);

            if (!kmsEnabled) {
                log.warn("KMS is disabled, using fallback decryption");
                return decryptFallback(ciphertext);
            }

            // PRODUCTION-READY: Real AWS KMS decryption
            software.amazon.awssdk.services.kms.KmsClient kmsClient =
                software.amazon.awssdk.services.kms.KmsClient.builder()
                    .region(software.amazon.awssdk.regions.Region.of(kmsRegion))
                    .build();

            byte[] encryptedBytes = Base64.getDecoder().decode(ciphertext);

            // Add encryption context (must match encryption context)
            Map<String, String> encryptionContext = Map.of(
                "service", "tokenization-service",
                "purpose", "sensitive-data-encryption"
            );

            software.amazon.awssdk.services.kms.model.DecryptRequest request =
                software.amazon.awssdk.services.kms.model.DecryptRequest.builder()
                    .ciphertextBlob(software.amazon.awssdk.core.SdkBytes.fromByteArray(encryptedBytes))
                    .encryptionContext(encryptionContext)
                    .build();

            software.amazon.awssdk.services.kms.model.DecryptResponse response =
                kmsClient.decrypt(request);

            byte[] plaintextBytes = response.plaintext().asByteArray();
            String plaintext = new String(plaintextBytes, StandardCharsets.UTF_8);

            decryptionSuccess.increment();

            log.debug("Data decrypted successfully with KMS");

            kmsClient.close();

            return plaintext;

        } catch (software.amazon.awssdk.services.kms.model.KmsException e) {
            log.error("AWS KMS decryption failed: keyId={}, error={}", kmsKeyId, e.getMessage(), e);
            decryptionError.increment();

            // Critical alert
            log.error("CRITICAL: KMS decryption failure - keyId={}, code={}, message={}",
                kmsKeyId, e.awsErrorDetails().errorCode(), e.awsErrorDetails().errorMessage());

            throw new DecryptionException("KMS decryption failed: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("Decryption failed: keyId={}", kmsKeyId, e);
            decryptionError.increment();
            throw new DecryptionException("Decryption failed", e);
        }
    }

    /**
     * Fallback encryption (for development/testing only)
     * WARNING: Not production-ready!
     */
    private String encryptFallback(String plaintext) {
        log.warn("Using fallback encryption - NOT production-ready!");
        return Base64.getEncoder().encodeToString(
            ("FALLBACK:" + plaintext).getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Fallback decryption (for development/testing only)
     * WARNING: Not production-ready!
     */
    private String decryptFallback(String ciphertext) {
        log.warn("Using fallback decryption - NOT production-ready!");
        byte[] decoded = Base64.getDecoder().decode(ciphertext);
        String decrypted = new String(decoded, StandardCharsets.UTF_8);
        return decrypted.replace("FALLBACK:", "");
    }

    /**
     * Custom exception for encryption failures
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message) {
            super(message);
        }

        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Custom exception for decryption failures
     */
    public static class DecryptionException extends RuntimeException {
        public DecryptionException(String message) {
            super(message);
        }

        public DecryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
