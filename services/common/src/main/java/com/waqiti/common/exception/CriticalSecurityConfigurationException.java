package com.waqiti.common.exception;

import com.waqiti.common.config.SecurityExitCode;

/**
 * Exception thrown when critical security configuration issues are detected
 * This prevents the application from starting with insecure configurations
 */
public class CriticalSecurityConfigurationException extends RuntimeException {
    
    private final SecurityExitCode exitCode;
    
    public CriticalSecurityConfigurationException(String message, SecurityExitCode exitCode) {
        super(message);
        this.exitCode = exitCode;
    }
    
    public CriticalSecurityConfigurationException(String message, Throwable cause, SecurityExitCode exitCode) {
        super(message, cause);
        this.exitCode = exitCode;
    }
    
    public SecurityExitCode getExitCode() {
        return exitCode;
    }
}