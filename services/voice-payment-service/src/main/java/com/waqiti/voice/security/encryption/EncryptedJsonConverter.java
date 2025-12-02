package com.waqiti.voice.security.encryption;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * JPA AttributeConverter for encrypting JSONB (Map) fields
 *
 * Encrypts complex data structures (biometric features, voice signatures)
 * before storing in PostgreSQL JSONB columns.
 *
 * Apply to sensitive Map fields:
 * @Convert(converter = EncryptedJsonConverter.class)
 * @Column(name = "voice_signature", columnDefinition = "jsonb")
 * private Map<String, Object> voiceSignature;
 *
 * Process:
 * 1. Serialize Map to JSON string
 * 2. Encrypt JSON string with AES-256-GCM
 * 3. Store encrypted Base64 string in database
 *
 * CRITICAL for GDPR/BIPA compliance: Biometric data MUST be encrypted
 */
@Slf4j
@Component
@Converter
@RequiredArgsConstructor
public class EncryptedJsonConverter implements AttributeConverter<Map<String, Object>, String> {

    private final AESEncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }

        try {
            // 1. Serialize to JSON
            String json = objectMapper.writeValueAsString(attribute);

            // 2. Encrypt JSON string
            return encryptionService.encrypt(json);

        } catch (Exception e) {
            log.error("Failed to encrypt JSON field", e);
            throw new IllegalStateException("JSON encryption failed", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }

        try {
            // 1. Decrypt to JSON string
            String json = encryptionService.decrypt(dbData);

            // 2. Deserialize from JSON
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

        } catch (Exception e) {
            log.error("Failed to decrypt JSON field", e);
            throw new IllegalStateException("JSON decryption failed", e);
        }
    }
}
