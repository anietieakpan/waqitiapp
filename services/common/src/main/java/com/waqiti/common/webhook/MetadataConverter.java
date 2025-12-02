package com.waqiti.common.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * JPA converter for webhook metadata
 */
@Slf4j
@Converter
public class MetadataConverter implements AttributeConverter<Map<String, Object>, String> {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> TYPE_REFERENCE = new TypeReference<Map<String, Object>>() {};
    
    @Override
    public String convertToDatabaseColumn(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert metadata to JSON", e);
            return null;
        }
    }
    
    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            return OBJECT_MAPPER.readValue(dbData, TYPE_REFERENCE);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert JSON to metadata", e);
            return new HashMap<>();
        }
    }
}