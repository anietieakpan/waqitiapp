package com.waqiti.common.encryption;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA Attribute Converter for automatic encryption/decryption of sensitive fields
 * 
 * Usage: @Convert(converter = EncryptedFieldConverter.class)
 */
@Component
@Converter
@Slf4j
public class EncryptedFieldConverter implements AttributeConverter<String, String> {
    
    private final AdvancedEncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    
    public EncryptedFieldConverter(
            AdvancedEncryptionService encryptionService,
            ObjectMapper objectMapper) {
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
    }
    
    private static final String ENCRYPTED_PREFIX = "ENC:";
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        
        try {
            // Get field name from stack trace (for classification)
            String fieldName = getFieldNameFromStackTrace();
            
            // Create system context for JPA operations
            DataContext context = DataContext.systemContext();
            
            // Encrypt the attribute
            EncryptedData encrypted = encryptionService.encryptSensitiveData(fieldName, attribute, context);
            
            // Serialize encrypted data to JSON and prefix
            String encryptedJson = objectMapper.writeValueAsString(encrypted);
            return ENCRYPTED_PREFIX + encryptedJson;
            
        } catch (Exception e) {
            log.error("Failed to encrypt field during database write", e);
            // In production, you might want to fail the transaction
            throw new EncryptionException("Database encryption failed", e);
        }
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        
        // Check if data is encrypted
        if (!dbData.startsWith(ENCRYPTED_PREFIX)) {
            // Data is not encrypted (legacy data or plain text)
            return dbData;
        }
        
        try {
            // Remove prefix and deserialize
            String encryptedJson = dbData.substring(ENCRYPTED_PREFIX.length());
            EncryptedData encrypted = objectMapper.readValue(encryptedJson, EncryptedData.class);
            
            // Create system context for JPA operations
            DataContext context = DataContext.systemContext();
            
            // Decrypt the data
            return encryptionService.decryptSensitiveData(encrypted, String.class, context);
            
        } catch (Exception e) {
            log.error("Failed to decrypt field during database read", e);
            // In production, you might want to return the encrypted data or fail
            throw new EncryptionException("Database decryption failed", e);
        }
    }
    
    /**
     * Extract field name from stack trace for data classification
     * This is a simplified approach - in production you'd use better metadata
     */
    private String getFieldNameFromStackTrace() {
        try {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            
            for (StackTraceElement element : stackTrace) {
                String methodName = element.getMethodName();
                
                // Look for setter methods
                if (methodName.startsWith("set") && methodName.length() > 3) {
                    return methodName.substring(3).toLowerCase();
                }
                
                // Look for getter methods
                if (methodName.startsWith("get") && methodName.length() > 3) {
                    return methodName.substring(3).toLowerCase();
                }
            }
            
        } catch (Exception e) {
            log.debug("Could not extract field name from stack trace", e);
        }
        
        return "unknown_field";
    }
}