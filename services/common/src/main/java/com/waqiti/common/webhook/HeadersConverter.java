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
 * JPA converter for webhook headers
 */
@Slf4j
@Converter
public class HeadersConverter implements AttributeConverter<Map<String, String>, String> {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> TYPE_REFERENCE = new TypeReference<Map<String, String>>() {};
    
    @Override
    public String convertToDatabaseColumn(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.writeValueAsString(headers);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert headers to JSON", e);
            return null;
        }
    }
    
    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            return OBJECT_MAPPER.readValue(dbData, TYPE_REFERENCE);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert JSON to headers", e);
            return new HashMap<>();
        }
    }
}