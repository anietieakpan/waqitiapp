package com.waqiti.common.config;

/**
 * Exit codes for security-related application failures.
 */
public enum SecurityExitCode {
    INSECURE_SECRETS(100, "Insecure secrets detected in configuration"),
    VAULT_CONNECTION_FAILED(101, "Failed to connect to Vault"),
    HSM_INITIALIZATION_FAILED(102, "HSM initialization failed"),
    CERTIFICATE_VALIDATION_FAILED(103, "Certificate validation failed"),
    ENCRYPTION_KEY_MISSING(104, "Required encryption keys are missing"),
    SECURITY_POLICY_VIOLATION(105, "Security policy violation detected");
    
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