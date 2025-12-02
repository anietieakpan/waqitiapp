package com.waqiti.common.audit;

/**
 * Exception thrown when audit operations fail
 */
public class AuditException extends RuntimeException {
    
    public AuditException(String message) {
        super(message);
    }
    
    public AuditException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public AuditException(Throwable cause) {
        super(cause);
    }
}