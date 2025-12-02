package com.waqiti.common.exception;

/**
 * Exit codes for security-related application failures
 */
public enum SecurityExitCode {
    INSECURE_SECRETS(101, "Insecure secrets detected in configuration"),
    VAULT_CONNECTION_FAILED(102, "Failed to connect to Vault"),
    INVALID_ENCRYPTION_KEY(103, "Invalid encryption key configuration"),
    HSM_INITIALIZATION_FAILED(104, "HSM initialization failed"),
    CERTIFICATE_VALIDATION_FAILED(105, "Certificate validation failed"),
    COMPLIANCE_CHECK_FAILED(106, "Compliance check failed");
    
    private final int code;
    private final String description;
    
    SecurityExitCode(int code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
}