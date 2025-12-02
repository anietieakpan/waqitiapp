package com.waqiti.saga.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for serializing/deserializing saga execution context
 *
 * CRITICAL: Properly serializes context to/from JSON for database storage
 *
 * Features:
 * - BigDecimal serialization without precision loss
 * - LocalDateTime support (ISO-8601 format)
 * - Type-safe deserialization
 * - Null handling
 *
 * Usage:
 * <pre>
 * // Serialize
 * Map<String, Object> context = new HashMap<>();
 * context.put("amount", new BigDecimal("100.0000"));
 * String json = contextSerializer.serialize(context);
 *
 * // Deserialize
 * Map<String, Object> restored = contextSerializer.deserialize(json);
 * BigDecimal amount = contextSerializer.getTypedValue(restored, "amount", BigDecimal.class);
 * </pre>
 */
@Component
public class ContextSerializer {

    private static final Logger logger = LoggerFactory.getLogger(ContextSerializer.class);

    private final ObjectMapper objectMapper;

    public ContextSerializer() {
        this.objectMapper = new ObjectMapper();
        // Register JavaTimeModule for LocalDateTime support
        this.objectMapper.registerModule(new JavaTimeModule());
        // Configure BigDecimal serialization
        this.objectMapper.configOverride(BigDecimal.class)
            .setFormat(com.fasterxml.jackson.annotation.JsonFormat.Value.forShape(
                com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING
            ));
    }

    /**
     * Serialize context map to JSON string
     *
     * @param context Context map (can contain BigDecimal, LocalDateTime, etc.)
     * @return JSON string representation
     * @throws RuntimeException if serialization fails
     */
    public String serialize(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return "{}";
        }

        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize context", e);
            throw new RuntimeException("Context serialization failed", e);
        }
    }

    /**
     * Deserialize JSON string to context map
     *
     * @param json JSON string
     * @return Context map
     * @throws RuntimeException if deserialization fails
     */
    public Map<String, Object> deserialize(String json) {
        if (json == null || json.trim().isEmpty() || "{}".equals(json.trim())) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize context: {}", json, e);
            throw new RuntimeException("Context deserialization failed", e);
        }
    }

    /**
     * Get typed value from context
     *
     * Type-safe extraction with automatic conversion
     *
     * @param context Context map
     * @param key Key to extract
     * @param type Expected type
     * @return Typed value or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getTypedValue(Map<String, Object> context, String key, Class<T> type) {
        if (context == null || !context.containsKey(key)) {
            return null;
        }

        Object value = context.get(key);
        if (value == null) {
            return null;
        }

        // Direct type match
        if (type.isInstance(value)) {
            return (T) value;
        }

        // BigDecimal conversion
        if (type == BigDecimal.class) {
            if (value instanceof String) {
                return (T) new BigDecimal((String) value);
            } else if (value instanceof Number) {
                return (T) BigDecimal.valueOf(((Number) value).doubleValue());
            }
        }

        // String conversion
        if (type == String.class) {
            return (T) value.toString();
        }

        // Try ObjectMapper conversion as fallback
        try {
            return objectMapper.convertValue(value, type);
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to convert context value {} to type {}", key, type, e);
            return null;
        }
    }

    /**
     * Set typed value in context with validation
     *
     * @param context Context map
     * @param key Key to set
     * @param value Value to set
     */
    public void setTypedValue(Map<String, Object> context, String key, Object value) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }

        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        // Validate BigDecimal precision
        if (value instanceof BigDecimal) {
            BigDecimal decimal = (BigDecimal) value;
            if (decimal.scale() > 4) {
                logger.warn("BigDecimal {} has scale > 4, rounding to 4 decimal places", key);
                value = decimal.setScale(4, java.math.RoundingMode.HALF_UP);
            }
        }

        // Prevent storing Float/Double for money
        if (value instanceof Float || value instanceof Double) {
            logger.error("CRITICAL: Attempted to store Float/Double in context for key: {}. Convert to BigDecimal!", key);
            throw new IllegalArgumentException(
                "Float/Double not allowed in context. Use BigDecimal for financial amounts. Key: " + key
            );
        }

        context.put(key, value);
    }
}
