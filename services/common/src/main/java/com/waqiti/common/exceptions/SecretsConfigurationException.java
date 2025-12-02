package com.waqiti.common.exceptions;

/**
 * Exception thrown when there's an issue with secrets configuration
 */
public class SecretsConfigurationException extends RuntimeException {
    
    public SecretsConfigurationException(String message) {
        super(message);
    }
    
    public SecretsConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}