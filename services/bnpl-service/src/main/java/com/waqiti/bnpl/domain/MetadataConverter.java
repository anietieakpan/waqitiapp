package com.waqiti.bnpl.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashMap;
import java.util.Map;

/**
 * JPA converter for storing metadata as JSON
 */
@Converter
public class MetadataConverter implements AttributeConverter<MetadataMap, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(MetadataMap metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error converting metadata to JSON", e);
        }
    }

    @Override
    public MetadataMap convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new MetadataMap();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(dbData, Map.class);
            return new MetadataMap(map);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error converting JSON to metadata", e);
        }
    }
}

/**
 * Custom map wrapper for metadata
 */
class MetadataMap extends HashMap<String, Object> {
    public MetadataMap() {
        super();
    }
    
    public MetadataMap(Map<String, Object> map) {
        super(map);
    }
}