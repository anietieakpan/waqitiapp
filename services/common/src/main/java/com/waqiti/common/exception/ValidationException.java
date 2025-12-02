package com.waqiti.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Exception thrown when validation fails with multiple errors
 *
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Getter
@ResponseStatus(HttpStatus.BAD_REQUEST)
public abstract class ValidationException extends RuntimeException {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private final List<String> errors;
    private final String field;
    
    /**
     * Constructor with message and errors
     */
    public ValidationException(String message, List<String> errors) {
        super(message);
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
        this.field = null;
    }
    
    /**
     * Constructor with message, field, and errors
     */
    public ValidationException(String message, String field, List<String> errors) {
        super(message);
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
        this.field = field;
    }
    
    /**
     * Constructor with single error message
     */
    public ValidationException(String message) {
        super(message);
        this.errors = List.of(message);
        this.field = null;
    }
    
    /**
     * Constructor with message and cause
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        this.errors = List.of(message);
        this.field = null;
    }
    
    /**
     * Constructor with field and single error
     */
    public ValidationException(String field, String message) {
        super(String.format("Validation failed for field '%s': %s", field, message));
        this.field = field;
        this.errors = List.of(message);
    }
    
    /**
     * Get formatted error message
     */
    public String getFormattedMessage() {
        if (errors.isEmpty()) {
            return getMessage();
        }
        
        StringBuilder sb = new StringBuilder(getMessage());
        sb.append(": ");
        
        if (errors.size() == 1) {
            sb.append(errors.get(0));
        } else {
            sb.append("\n");
            for (int i = 0; i < errors.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(errors.get(i));
                if (i < errors.size() - 1) {
                    sb.append("\n");
                }
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Check if there are multiple errors
     */
    public boolean hasMultipleErrors() {
        return errors.size() > 1;
    }
    
    /**
     * Get error count
     */
    public int getErrorCount() {
        return errors.size();
    }

    /**
     * Get the error code for this validation exception
     * Each subclass must provide a specific error code for proper error handling
     *
     * @return the error code representing this validation failure
     */
    public abstract ErrorCode getErrorCode();
}