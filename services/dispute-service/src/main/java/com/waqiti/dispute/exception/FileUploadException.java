package com.waqiti.dispute.exception;

/**
 * File Upload Exception
 *
 * Thrown when file upload validation or processing fails
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
public class FileUploadException extends Exception {

    public FileUploadException(String message) {
        super(message);
    }

    public FileUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
