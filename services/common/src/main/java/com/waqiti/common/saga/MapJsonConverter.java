package com.waqiti.common.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.HashMap;
import java.util.Map;

/**
 * JPA converter for storing Map<String, Object> as JSON in database
 */
@Converter
@Slf4j
public class MapJsonConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> typeReference = 
        new TypeReference<Map<String, Object>>() {};

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
        if (dbData == null || dbData.trim().isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            return objectMapper.readValue(dbData, typeReference);
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON to map: " + dbData, e);
            return new HashMap<>();
        }
    }
}

/**
 * JPA converter for storing Map<String, StepState> as JSON in database
 */
@Converter
@Slf4j
class StepStatesJsonConverter implements AttributeConverter<Map<String, StepState>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<Map<String, StepState>> typeReference = 
        new TypeReference<Map<String, StepState>>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, StepState> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "{}";
        }
        
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Error converting step states to JSON", e);
            return "{}";
        }
    }

    @Override
    public Map<String, StepState> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            return objectMapper.readValue(dbData, typeReference);
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON to step states: " + dbData, e);
            return new HashMap<>();
        }
    }
}