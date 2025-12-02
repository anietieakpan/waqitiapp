package com.waqiti.common.config.validation;

/**
 * Exception thrown when application configuration validation fails.
 *
 * <p>This is a fatal exception that prevents application startup when
 * critical configuration properties are missing or invalid.
 *
 * <p>Thrown by {@link ConfigurationValidator} during startup validation.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-23
 */
public class ConfigurationValidationException extends RuntimeException {

    public ConfigurationValidationException(String message) {
        super(message);
    }

    public ConfigurationValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
