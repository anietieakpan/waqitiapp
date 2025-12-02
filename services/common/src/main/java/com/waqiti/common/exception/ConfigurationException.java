package com.waqiti.common.exception;

/**
 * Exception thrown when application configuration is invalid or missing
 * Used for handling configuration-related startup failures
 */
public class ConfigurationException extends WaqitiException {
    
    private final String configKey;
    private final String configSource;
    private final String expectedType;
    private final String actualValue;
    
    public ConfigurationException(String message) {
        super(ErrorCode.CONFIG_ERROR, message);
        this.configKey = null;
        this.configSource = null;
        this.expectedType = null;
        this.actualValue = null;
    }
    
    public ConfigurationException(String message, Throwable cause) {
        super(ErrorCode.CONFIG_ERROR, message, cause);
        this.configKey = null;
        this.configSource = null;
        this.expectedType = null;
        this.actualValue = null;
    }
    
    public ConfigurationException(String message, String configKey, String configSource) {
        super(ErrorCode.CONFIG_ERROR, message);
        this.configKey = configKey;
        this.configSource = configSource;
        this.expectedType = null;
        this.actualValue = null;
    }
    
    public ConfigurationException(String message, String configKey, String configSource, 
                                String expectedType, String actualValue) {
        super(ErrorCode.CONFIG_ERROR, message);
        this.configKey = configKey;
        this.configSource = configSource;
        this.expectedType = expectedType;
        this.actualValue = actualValue;
    }
    
    public ConfigurationException(String message, Throwable cause, String configKey, 
                                String configSource, String expectedType, String actualValue) {
        super(ErrorCode.CONFIG_ERROR, message, cause);
        this.configKey = configKey;
        this.configSource = configSource;
        this.expectedType = expectedType;
        this.actualValue = actualValue;
    }
    
    public String getConfigKey() {
        return configKey;
    }
    
    public String getConfigSource() {
        return configSource;
    }
    
    public String getExpectedType() {
        return expectedType;
    }
    
    public String getActualValue() {
        return actualValue;
    }
    
    public String getConfigContext() {
        StringBuilder context = new StringBuilder();
        if (configKey != null) context.append("key=").append(configKey);
        if (configSource != null) context.append(", source=").append(configSource);
        if (expectedType != null) context.append(", expected=").append(expectedType);
        if (actualValue != null) context.append(", actual=").append(actualValue);
        return context.toString();
    }
    
    @Override
    public String getMessage() {
        String baseMessage = super.getMessage();
        String context = getConfigContext();
        if (context.isEmpty()) {
            return baseMessage;
        }
        return baseMessage + " [" + context + "]";
    }
    
    // Static factory methods for common scenarios
    public static ConfigurationException missingRequired(String configKey, String configSource) {
        return new ConfigurationException(
            "Required configuration property is missing", 
            configKey, configSource
        );
    }
    
    public static ConfigurationException invalidValue(String configKey, String configSource, 
                                                    String expectedType, String actualValue) {
        return new ConfigurationException(
            "Configuration property has invalid value", 
            configKey, configSource, expectedType, actualValue
        );
    }
    
    public static ConfigurationException invalidFormat(String configKey, String configSource, 
                                                     String expectedType, String actualValue, Throwable cause) {
        return new ConfigurationException(
            "Configuration property has invalid format", 
            cause, configKey, configSource, expectedType, actualValue
        );
    }
    
    public static ConfigurationException conflictingValues(String configKey1, String configKey2, String configSource) {
        return new ConfigurationException(
            "Conflicting configuration values detected: " + configKey1 + " conflicts with " + configKey2, 
            configKey1, configSource
        );
    }
    
    public static ConfigurationException sourceNotFound(String configSource) {
        return new ConfigurationException(
            "Configuration source not found or inaccessible", 
            null, configSource
        );
    }
}