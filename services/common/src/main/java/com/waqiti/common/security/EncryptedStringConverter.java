package com.waqiti.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter for automatic field encryption/decryption.
 * This converter is applied to entity fields marked with @FieldEncryption.
 */
@Converter
@Component
@RequiredArgsConstructor
@Slf4j
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final SecureConfigurationManager secureConfigurationManager;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        
        try {
            String encrypted = secureConfigurationManager.encryptSensitiveData(attribute);
            log.trace("Field encrypted for database storage");
            return encrypted;
        } catch (Exception e) {
            log.error("Failed to encrypt field for database storage", e);
            throw new SecurityException("Field encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        
        try {
            String decrypted = secureConfigurationManager.decryptSensitiveData(dbData);
            log.trace("Field decrypted from database");
            return decrypted;
        } catch (Exception e) {
            log.error("Failed to decrypt field from database", e);
            throw new SecurityException("Field decryption failed", e);
        }
    }
}