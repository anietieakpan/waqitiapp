package com.waqiti.common.config;

/**
 * Exception thrown when critical security configuration issues are detected.
 * This exception prevents application startup when insecure configurations are found.
 */
public class CriticalSecurityConfigurationException extends RuntimeException {
    
    private final SecurityExitCode exitCode;
    
    public CriticalSecurityConfigurationException(String message, SecurityExitCode exitCode) {
        super(message);
        this.exitCode = exitCode;
    }
    
    public CriticalSecurityConfigurationException(String message, SecurityExitCode exitCode, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
    }
    
    public SecurityExitCode getExitCode() {
        return exitCode;
    }
}