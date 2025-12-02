package com.waqiti.user.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * JPA Converter for Map to JSON String conversion
 * 
 * Handles conversion of Map<String, Object> to JSON for database storage
 */
@Converter
@Slf4j
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};
    
    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "{}";
        }
        
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Error converting map to JSON", e);
            return "{}";
        }
    }
    
    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            return objectMapper.readValue(dbData, typeRef);
        } catch (IOException e) {
            log.error("Error converting JSON to map", e);
            return new HashMap<>();
        }
    }
}