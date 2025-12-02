package com.waqiti.common.encryption;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Specialized JPA converter for financial data fields
 * Implements banking-grade encryption with compliance logging
 */
@Component
@Converter
@Slf4j
@RequiredArgsConstructor
public class EncryptedFinancialConverter implements AttributeConverter<String, String> {
    
    private final AdvancedEncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    
    private static final String FINANCIAL_PREFIX = "FIN:";
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        
        try {
            // Create financial-specific context
            DataContext context = DataContext.builder()
                .userId("SYSTEM")
                .tenantId("DEFAULT")
                .operation("FINANCIAL_STORAGE")
                .auditRequired(true)
                .complianceLevel("FINANCIAL_GRADE")
                .build();
            
            // Encrypt with financial classification
            EncryptedData encrypted = encryptFinancialField(attribute, context);
            
            // Serialize with financial prefix
            String encryptedJson = objectMapper.writeValueAsString(encrypted);
            String result = FINANCIAL_PREFIX + encryptedJson;
            
            // Compliance logging
            log.info("FINANCIAL_ENCRYPTION: field encrypted for secure storage");
            
            return result;
            
        } catch (Exception e) {
            log.error("COMPLIANCE CRITICAL: Failed to encrypt financial data", e);
            throw new EncryptionException("Financial data encryption failed - regulatory compliance at risk", e);
        }
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        
        if (!dbData.startsWith(FINANCIAL_PREFIX)) {
            log.warn("Financial field found without proper encryption - compliance issue detected");
            return dbData; // Legacy data handling
        }
        
        try {
            // Remove prefix and deserialize
            String encryptedJson = dbData.substring(FINANCIAL_PREFIX.length());
            EncryptedData encrypted = objectMapper.readValue(encryptedJson, EncryptedData.class);
            
            // Verify financial classification
            if (encrypted.getClassification() != AdvancedEncryptionService.DataClassification.FINANCIAL) {
                log.error("COMPLIANCE WARNING: Financial field has incorrect classification: {}", 
                    encrypted.getClassification());
            }
            
            // Create compliance context
            DataContext context = DataContext.builder()
                .userId("SYSTEM")
                .tenantId("DEFAULT")
                .operation("FINANCIAL_ACCESS")
                .auditRequired(true)
                .complianceLevel("FINANCIAL_GRADE")
                .build();
            
            // Decrypt with full audit trail
            String decryptedValue = encryptionService.decryptSensitiveData(encrypted, String.class, context);
            
            // Compliance logging
            log.info("FINANCIAL_ACCESS: financial data decrypted - keyVersion={}, classification={}", 
                encrypted.getKeyVersion(), encrypted.getClassification());
            
            return decryptedValue;
            
        } catch (Exception e) {
            log.error("COMPLIANCE CRITICAL: Failed to decrypt financial data", e);
            throw new EncryptionException("Financial data decryption failed - data integrity compromised", e);
        }
    }
    
    /**
     * Encrypt financial field with banking-grade security
     */
    private EncryptedData encryptFinancialField(String value, DataContext context) {
        // Financial data always gets maximum security classification
        return encryptionService.encryptSensitiveData("financial_field", value, context);
    }
}