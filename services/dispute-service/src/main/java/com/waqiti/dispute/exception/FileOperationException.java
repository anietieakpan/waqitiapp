package com.waqiti.dispute.exception;

/**
 * Exception thrown when a file operation fails
 *
 * HTTP Status: 500 Internal Server Error
 *
 * @author Waqiti Dispute Team
 */
public class FileOperationException extends DisputeServiceException {

    private final String operation;
    private final String fileName;

    public FileOperationException(String operation, String fileName, String message) {
        super(String.format("File %s operation failed for '%s': %s", operation, fileName, message),
                "FILE_OPERATION_ERROR", 500);
        this.operation = operation;
        this.fileName = fileName;
    }

    public FileOperationException(String operation, String fileName, String message, Throwable cause) {
        super(String.format("File %s operation failed for '%s': %s", operation, fileName, message),
                cause, "FILE_OPERATION_ERROR", 500);
        this.operation = operation;
        this.fileName = fileName;
    }

    public String getOperation() {
        return operation;
    }

    public String getFileName() {
        return fileName;
    }
}
