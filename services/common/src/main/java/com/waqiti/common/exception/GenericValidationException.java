package com.waqiti.common.exception;

import java.util.List;

/**
 * Generic validation exception for cases where a specific validation exception subclass is not needed.
 * Use specific ValidationException subclasses (InvalidAddressException, etc.) when available.
 *
 * @author Waqiti Engineering Team
 * @since 2.0.0
 */
public class GenericValidationException extends ValidationException {

    /**
     * Constructor with message and errors
     */
    public GenericValidationException(String message, List<String> errors) {
        super(message, errors);
    }

    /**
     * Constructor with message, field, and errors
     */
    public GenericValidationException(String message, String field, List<String> errors) {
        super(message, field, errors);
    }

    /**
     * Constructor with single error message
     */
    public GenericValidationException(String message) {
        super(message);
    }

    /**
     * Constructor with message and cause
     */
    public GenericValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructor with field and single error
     */
    public GenericValidationException(String field, String message) {
        super(field, message);
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.VALIDATION_FAILED;
    }
}
