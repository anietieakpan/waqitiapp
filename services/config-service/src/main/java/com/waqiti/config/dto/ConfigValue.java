package com.waqiti.config.dto;

import com.waqiti.config.enums.ConfigType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * DTO for configuration value response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigValue {
    private String key;
    private String value;
    private String dataType;
    private Boolean sensitive;
    private Boolean encrypted;
    private String service;
    private String environment;
    private Instant lastModified;

    /**
     * Cache expiration timestamp
     */
    private Instant expiresAt;

    /**
     * Static factory method to create a ConfigValue
     *
     * @param key Configuration key
     * @param value Configuration value
     * @param type Configuration type
     * @return ConfigValue instance
     */
    public static ConfigValue of(String key, String value, ConfigType type) {
        return ConfigValue.builder()
                .key(key)
                .value(value)
                .dataType(type.name())
                .sensitive(false)
                .encrypted(false)
                .lastModified(Instant.now())
                .build();
    }

    /**
     * Static factory method with TTL support
     *
     * @param key Configuration key
     * @param value Configuration value
     * @param type Configuration type
     * @param ttl Time-to-live duration
     * @return ConfigValue instance with expiration
     */
    public static ConfigValue of(String key, String value, ConfigType type, Duration ttl) {
        return ConfigValue.builder()
                .key(key)
                .value(value)
                .dataType(type.name())
                .sensitive(false)
                .encrypted(false)
                .lastModified(Instant.now())
                .expiresAt(Instant.now().plus(ttl))
                .build();
    }

    /**
     * Static factory method with all properties
     *
     * @param key Configuration key
     * @param value Configuration value
     * @param type Configuration type
     * @param sensitive Whether value is sensitive
     * @param encrypted Whether value is encrypted
     * @return ConfigValue instance
     */
    public static ConfigValue of(String key, String value, ConfigType type, boolean sensitive, boolean encrypted) {
        return ConfigValue.builder()
                .key(key)
                .value(value)
                .dataType(type.name())
                .sensitive(sensitive)
                .encrypted(encrypted)
                .lastModified(Instant.now())
                .build();
    }

    /**
     * Create from string value (defaults to STRING type)
     *
     * @param key Configuration key
     * @param value Configuration value
     * @return ConfigValue instance
     */
    public static ConfigValue ofString(String key, String value) {
        return of(key, value, ConfigType.STRING);
    }

    /**
     * Create from integer value
     *
     * @param key Configuration key
     * @param value Integer value
     * @return ConfigValue instance
     */
    public static ConfigValue ofInt(String key, int value) {
        return of(key, String.valueOf(value), ConfigType.INTEGER);
    }

    /**
     * Create from long value
     *
     * @param key Configuration key
     * @param value Long value
     * @return ConfigValue instance
     */
    public static ConfigValue ofLong(String key, long value) {
        return of(key, String.valueOf(value), ConfigType.LONG);
    }

    /**
     * Create from boolean value
     *
     * @param key Configuration key
     * @param value Boolean value
     * @return ConfigValue instance
     */
    public static ConfigValue ofBoolean(String key, boolean value) {
        return of(key, String.valueOf(value), ConfigType.BOOLEAN);
    }

    /**
     * Create from double value
     *
     * @param key Configuration key
     * @param value Double value
     * @return ConfigValue instance
     */
    public static ConfigValue ofDouble(String key, double value) {
        return of(key, String.valueOf(value), ConfigType.DOUBLE);
    }

    /**
     * Create from BigDecimal value (for financial calculations)
     *
     * @param key Configuration key
     * @param value BigDecimal value
     * @return ConfigValue instance
     */
    public static ConfigValue ofDecimal(String key, java.math.BigDecimal value) {
        return of(key, value.toPlainString(), ConfigType.DECIMAL);
    }

    /**
     * Create from JSON value
     *
     * @param key Configuration key
     * @param jsonValue JSON string
     * @return ConfigValue instance
     */
    public static ConfigValue ofJson(String key, String jsonValue) {
        return of(key, jsonValue, ConfigType.JSON);
    }

    /**
     * Check if this configuration value has expired
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Get the value as typed object
     *
     * @param <T> Target type
     * @return Parsed value
     */
    @SuppressWarnings("unchecked")
    public <T> T getTypedValue() {
        if (value == null) {
            return null;
        }

        ConfigType type = ConfigType.fromString(dataType);

        return (T) switch (type) {
            case STRING, URL, EMAIL, DATE, DATETIME, DURATION, ENCRYPTED -> value;
            case INTEGER -> Integer.parseInt(value);
            case LONG -> Long.parseLong(value);
            case DOUBLE -> Double.parseDouble(value);
            case DECIMAL -> new java.math.BigDecimal(value);
            case BOOLEAN -> Boolean.parseBoolean(value);
            case JSON, LIST -> value; // Return as string, caller should parse
        };
    }

    /**
     * Get value as string (convenience method)
     *
     * @return String value
     */
    public String getValueAsString() {
        return value;
    }

    /**
     * Get value as integer
     *
     * @return Integer value
     * @throws NumberFormatException if value is not a valid integer
     */
    public Integer getValueAsInt() {
        return Integer.parseInt(value);
    }

    /**
     * Get value as long
     *
     * @return Long value
     * @throws NumberFormatException if value is not a valid long
     */
    public Long getValueAsLong() {
        return Long.parseLong(value);
    }

    /**
     * Get value as boolean
     *
     * @return Boolean value
     */
    public Boolean getValueAsBoolean() {
        return Boolean.parseBoolean(value);
    }

    /**
     * Get value as double
     *
     * @return Double value
     * @throws NumberFormatException if value is not a valid double
     */
    public Double getValueAsDouble() {
        return Double.parseDouble(value);
    }

    /**
     * Get value as BigDecimal
     *
     * @return BigDecimal value
     * @throws NumberFormatException if value is not a valid decimal
     */
    public java.math.BigDecimal getValueAsDecimal() {
        return new java.math.BigDecimal(value);
    }
}
