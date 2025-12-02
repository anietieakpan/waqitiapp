package com.waqiti.config.enums;

/**
 * Configuration value types supported by the configuration service
 * Used for type validation and conversion
 */
public enum ConfigType {
    /**
     * String value - any text
     */
    STRING,

    /**
     * Integer value - whole numbers (-2147483648 to 2147483647)
     */
    INTEGER,

    /**
     * Long value - large whole numbers
     */
    LONG,

    /**
     * Double value - decimal numbers (WARNING: Use DECIMAL for money!)
     */
    DOUBLE,

    /**
     * Decimal value - high-precision decimal (recommended for financial calculations)
     */
    DECIMAL,

    /**
     * Boolean value - true/false
     */
    BOOLEAN,

    /**
     * JSON object or array - stored as string, validated as valid JSON
     */
    JSON,

    /**
     * List of values - comma-separated string list
     */
    LIST,

    /**
     * URL - validated as valid URL format
     */
    URL,

    /**
     * Email - validated as valid email format
     */
    EMAIL,

    /**
     * Date - ISO 8601 format (YYYY-MM-DD)
     */
    DATE,

    /**
     * DateTime - ISO 8601 format with time (YYYY-MM-DDTHH:mm:ss.sssZ)
     */
    DATETIME,

    /**
     * Duration - ISO 8601 duration format (PT1H30M)
     */
    DURATION,

    /**
     * Encrypted value - stored encrypted, decrypted on retrieval
     */
    ENCRYPTED;

    /**
     * Check if this type requires validation
     */
    public boolean requiresValidation() {
        return this == URL || this == EMAIL || this == JSON || this == DATE || this == DATETIME || this == DURATION;
    }

    /**
     * Check if this type represents a numeric value
     */
    public boolean isNumeric() {
        return this == INTEGER || this == LONG || this == DOUBLE || this == DECIMAL;
    }

    /**
     * Check if this type should use high-precision arithmetic
     */
    public boolean isHighPrecision() {
        return this == DECIMAL;
    }

    /**
     * Get the Java class that represents this type
     */
    public Class<?> getJavaType() {
        return switch (this) {
            case STRING, URL, EMAIL, DATE, DATETIME, DURATION, ENCRYPTED -> String.class;
            case INTEGER -> Integer.class;
            case LONG -> Long.class;
            case DOUBLE -> Double.class;
            case DECIMAL -> java.math.BigDecimal.class;
            case BOOLEAN -> Boolean.class;
            case JSON, LIST -> String.class; // Stored as string
        };
    }

    /**
     * Parse from string value (case-insensitive)
     */
    public static ConfigType fromString(String value) {
        if (value == null || value.isBlank()) {
            return STRING; // Default to STRING
        }

        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return STRING; // Default to STRING for unknown types
        }
    }
}
