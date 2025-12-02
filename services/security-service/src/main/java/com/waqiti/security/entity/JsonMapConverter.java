package com.waqiti.security.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * JPA converter for storing Map<String, Object> as JSON in database.
 * Handles serialization and deserialization of complex metadata.
 */
@Converter
@Slf4j
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Error converting map to JSON: {}", e.getMessage());
            throw new IllegalArgumentException("Unable to convert map to JSON", e);
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            return objectMapper.readValue(dbData, Map.class);
        } catch (IOException e) {
            log.error("Error converting JSON to map: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}