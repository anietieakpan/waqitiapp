package com.waqiti.common.exception;

import org.springframework.boot.ExitCodeGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Critical Security Exception - Thrown when security configuration or validation fails critically
 * This exception implements ExitCodeGenerator to ensure proper application shutdown
 * when critical security issues are detected
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class CriticalSecurityException extends RuntimeException implements ExitCodeGenerator {
    
    private static final long serialVersionUID = 1L;
    
    private final int exitCode;
    private final String securityContext;
    private final boolean requiresImmediateShutdown;
    
    /**
     * Create a critical security exception with default exit code
     */
    public CriticalSecurityException(String message) {
        super(message);
        this.exitCode = 1;
        this.securityContext = "GENERAL";
        this.requiresImmediateShutdown = true;
    }
    
    /**
     * Create a critical security exception with cause
     */
    public CriticalSecurityException(String message, Throwable cause) {
        super(message, cause);
        this.exitCode = 1;
        this.securityContext = extractSecurityContext(cause);
        this.requiresImmediateShutdown = true;
    }
    
    /**
     * Create a critical security exception with specific exit code
     */
    public CriticalSecurityException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
        this.securityContext = "GENERAL";
        this.requiresImmediateShutdown = true;
    }
    
    /**
     * Create a critical security exception with full context
     */
    public CriticalSecurityException(String message, Throwable cause, int exitCode, String securityContext) {
        super(message, cause);
        this.exitCode = exitCode;
        this.securityContext = securityContext;
        this.requiresImmediateShutdown = true;
    }
    
    @Override
    public int getExitCode() {
        return exitCode;
    }
    
    public String getSecurityContext() {
        return securityContext;
    }
    
    public boolean requiresImmediateShutdown() {
        return requiresImmediateShutdown;
    }
    
    /**
     * Extract security context from the cause exception
     */
    private String extractSecurityContext(Throwable cause) {
        if (cause == null) {
            return "UNKNOWN";
        }
        
        String className = cause.getClass().getSimpleName();
        if (className.contains("Vault")) {
            return "VAULT_CONFIGURATION";
        } else if (className.contains("Crypto") || className.contains("Encryption")) {
            return "ENCRYPTION_CONFIGURATION";
        } else if (className.contains("Auth") || className.contains("Authentication")) {
            return "AUTHENTICATION_CONFIGURATION";
        } else if (className.contains("SSL") || className.contains("TLS")) {
            return "SSL_CONFIGURATION";
        }
        
        return "CONFIGURATION_ERROR";
    }
    
    @Override
    public String toString() {
        return String.format("CriticalSecurityException[exitCode=%d, context=%s, requiresShutdown=%s]: %s",
                exitCode, securityContext, requiresImmediateShutdown, getMessage());
    }
}