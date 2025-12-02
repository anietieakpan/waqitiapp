package com.waqiti.account.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.account.exception.SerializationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Helper component for JSON serialization and deserialization with proper error handling.
 *
 * CRITICAL FIX P0-4: Previously silently swallowed exceptions returning null.
 * Now properly logs errors and throws SerializationException for explicit handling.
 *
 * Key improvements:
 * - Explicit exception throwing instead of silent null returns
 * - Comprehensive error logging with context
 * - Optional<T> methods for safe nullable handling
 * - Separate exception types for better error categorization
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Slf4j
@Component
public class AccountMapperHelper {

    private final ObjectMapper objectMapper;

    public AccountMapperHelper() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    /**
     * Converts an object to JSON string.
     *
     * CRITICAL FIX P0-4: Previously returned null on error (data corruption risk).
     * Now throws SerializationException with detailed context for proper error handling.
     *
     * @param obj the object to serialize
     * @return JSON string representation
     * @throws SerializationException if serialization fails
     */
    public String toJson(Object obj) {
        if (obj == null) {
            log.debug("toJson called with null object, returning null");
            return null;
        }

        try {
            String json = objectMapper.writeValueAsString(obj);
            log.trace("Successfully serialized {} to JSON ({} bytes)",
                obj.getClass().getSimpleName(), json.length());
            return json;

        } catch (JsonProcessingException e) {
            String className = obj.getClass().getName();
            log.error("CRITICAL: JSON serialization failed for type: {} - Error: {} - {}",
                className, e.getClass().getSimpleName(), e.getMessage());

            throw new SerializationException(
                "Failed to serialize object to JSON: " + className,
                className,
                "SERIALIZATION",
                e
            );
        }
    }

    /**
     * Safe version of toJson that returns Optional instead of throwing exception.
     * Useful for non-critical serialization where failure can be handled gracefully.
     *
     * @param obj the object to serialize
     * @return Optional containing JSON string, or empty if serialization fails
     */
    public Optional<String> toJsonSafe(Object obj) {
        try {
            return Optional.ofNullable(toJson(obj));
        } catch (SerializationException e) {
            log.warn("Safe JSON serialization failed for {}: {}",
                obj != null ? obj.getClass().getSimpleName() : "null",
                e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Converts JSON string to object of specified type.
     *
     * CRITICAL FIX P0-4: Previously returned null on error (silent data loss).
     * Now throws SerializationException with detailed context for proper error handling.
     *
     * @param json the JSON string to deserialize
     * @param clazz the target class type
     * @param <T> the type parameter
     * @return deserialized object
     * @throws SerializationException if deserialization fails
     */
    public <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) {
            log.debug("fromJson called with empty JSON, returning null");
            return null;
        }

        try {
            T result = objectMapper.readValue(json, clazz);
            log.trace("Successfully deserialized JSON ({} bytes) to {}",
                json.length(), clazz.getSimpleName());
            return result;

        } catch (JsonProcessingException e) {
            String className = clazz.getName();
            log.error("CRITICAL: JSON deserialization failed for type: {} - Error: {} - {} - JSON: {}",
                className, e.getClass().getSimpleName(), e.getMessage(),
                json.length() > 200 ? json.substring(0, 200) + "..." : json);

            throw new SerializationException(
                "Failed to deserialize JSON to type: " + className,
                className,
                "DESERIALIZATION",
                e
            );
        }
    }

    /**
     * Safe version of fromJson that returns Optional instead of throwing exception.
     * Useful for non-critical deserialization where failure can be handled gracefully.
     *
     * @param json the JSON string to deserialize
     * @param clazz the target class type
     * @param <T> the type parameter
     * @return Optional containing deserialized object, or empty if deserialization fails
     */
    public <T> Optional<T> fromJsonSafe(String json, Class<T> clazz) {
        try {
            return Optional.ofNullable(fromJson(json, clazz));
        } catch (SerializationException e) {
            log.warn("Safe JSON deserialization failed for {}: {}",
                clazz.getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Provides access to the underlying ObjectMapper for advanced use cases.
     * Use with caution - prefer the wrapper methods for consistent error handling.
     *
     * @return the configured ObjectMapper instance
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
