package com.waqiti.audit.exception;

/**
 * Exception thrown when audit log encryption operations fail
 */
public class AuditEncryptionException extends RuntimeException {

    public AuditEncryptionException(String message) {
        super(message);
    }

    public AuditEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuditEncryptionException(Throwable cause) {
        super(cause);
    }
}
