package com.waqiti.common.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter for transparent field-level encryption of sensitive data.
 *
 * Usage:
 * <pre>
 * {@code
 * @Convert(converter = EncryptedStringConverter.class)
 * @Column(name = "account_number")
 * private String accountNumber;
 * }
 * </pre>
 *
 * Security Features:
 * - AES-256-GCM encryption
 * - AWS KMS for key management (or HashiCorp Vault)
 * - Automatic encryption on persist
 * - Automatic decryption on load
 * - Null-safe operations
 * - Comprehensive error logging
 *
 * Use Cases:
 * - ACH account numbers
 * - Routing numbers
 * - SSN/Tax IDs
 * - Device fingerprints
 * - IP addresses (GDPR)
 * - Any PII subject to compliance
 *
 * @author Waqiti Security Team
 * @version 2.0 - Production Ready
 * @since 2025-10-07
 */
@Converter
@Component
@Slf4j
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Autowired
    private EncryptionService encryptionService;

    /**
     * Encrypts the plaintext attribute before storing in database.
     *
     * @param attribute Plaintext string to encrypt
     * @return Base64-encoded encrypted ciphertext
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }

        try {
            String encrypted = encryptionService.encrypt(attribute);
            log.debug("Successfully encrypted field of length: {}", attribute.length());
            return encrypted;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to encrypt sensitive data - data security compromised", e);
            // In production, you might want to fail the transaction entirely
            throw new EncryptionException("Failed to encrypt sensitive data", e);
        }
    }

    /**
     * Decrypts the ciphertext from database before returning to application.
     *
     * @param dbData Base64-encoded encrypted ciphertext
     * @return Decrypted plaintext string
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }

        try {
            String decrypted = encryptionService.decrypt(dbData);
            log.debug("Successfully decrypted field");
            return decrypted;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to decrypt sensitive data - data may be corrupted", e);
            // In production, you might want to fail the read entirely
            throw new EncryptionException("Failed to decrypt sensitive data", e);
        }
    }
}
