package com.waqiti.common.encryption;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Specialized JPA converter for PII (Personally Identifiable Information) fields
 * Uses high-security encryption with additional protections
 */
@Component
@Converter
@Slf4j
@RequiredArgsConstructor
public class EncryptedPIIConverter implements AttributeConverter<String, String> {
    
    private final AdvancedEncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    
    private static final String PII_PREFIX = "PII:";
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        
        try {
            // Create PII-specific context
            DataContext context = DataContext.builder()
                .userId("SYSTEM")
                .tenantId("DEFAULT")
                .operation("PII_STORAGE")
                .auditRequired(true)
                .complianceLevel("PII_PROTECTION")
                .build();
            
            // Force PII classification
            EncryptedData encrypted = encryptPIIField(attribute, context);
            
            // Serialize and store with PII prefix
            String encryptedJson = objectMapper.writeValueAsString(encrypted);
            return PII_PREFIX + encryptedJson;
            
        } catch (Exception e) {
            log.error("SECURITY CRITICAL: Failed to encrypt PII field", e);
            throw new EncryptionException("PII encryption failed - data protection compromised", e);
        }
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        
        if (!dbData.startsWith(PII_PREFIX)) {
            log.warn("PII field found without proper encryption prefix - possible security issue");
            return dbData; // Legacy data handling
        }
        
        try {
            // Remove prefix and deserialize
            String encryptedJson = dbData.substring(PII_PREFIX.length());
            EncryptedData encrypted = objectMapper.readValue(encryptedJson, EncryptedData.class);
            
            // Verify PII classification
            if (encrypted.getClassification() != AdvancedEncryptionService.DataClassification.PII) {
                log.error("SECURITY WARNING: PII field has incorrect classification: {}", 
                    encrypted.getClassification());
            }
            
            // Create audit context for decryption
            DataContext context = DataContext.builder()
                .userId("SYSTEM")
                .tenantId("DEFAULT")
                .operation("PII_ACCESS")
                .auditRequired(true)
                .complianceLevel("PII_PROTECTION")
                .build();
            
            // Decrypt with audit trail
            String decryptedValue = encryptionService.decryptSensitiveData(encrypted, String.class, context);
            
            // Log PII access for compliance
            log.info("PII_ACCESS: field decrypted for system operation");
            
            return decryptedValue;
            
        } catch (Exception e) {
            log.error("SECURITY CRITICAL: Failed to decrypt PII field", e);
            throw new EncryptionException("PII decryption failed - data may be corrupted", e);
        }
    }
    
    /**
     * Encrypt PII field with enhanced security
     */
    private EncryptedData encryptPIIField(String value, DataContext context) {
        return encryptionService.encryptSensitiveData("pii_field", value, context);
    }
}