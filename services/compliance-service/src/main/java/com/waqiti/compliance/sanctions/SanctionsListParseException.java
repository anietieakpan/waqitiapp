package com.waqiti.compliance.sanctions;

/**
 * Exception thrown when sanctions list parsing fails
 *
 * @author Waqiti Engineering Team
 * @since 2025-11-19
 */
public class SanctionsListParseException extends Exception {

    public SanctionsListParseException(String message) {
        super(message);
    }

    public SanctionsListParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
