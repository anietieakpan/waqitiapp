package com.waqiti.voice.security.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter for encrypting String fields
 *
 * Automatically encrypts data before storing in database
 * and decrypts when reading from database.
 *
 * Apply to sensitive String fields:
 * @Convert(converter = EncryptedStringConverter.class)
 * @Column(name = "transcribed_text")
 * private String transcribedText;
 *
 * CRITICAL: This converter uses AES-256-GCM encryption
 * Compliance: GDPR Article 32, PCI-DSS Req 3.4
 */
@Slf4j
@Component
@Converter
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final AESEncryptionService encryptionService;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }

        try {
            return encryptionService.encrypt(attribute);
        } catch (Exception e) {
            log.error("Failed to encrypt field", e);
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        try {
            return encryptionService.decrypt(dbData);
        } catch (Exception e) {
            log.error("Failed to decrypt field", e);
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
