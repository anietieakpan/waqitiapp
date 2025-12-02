package com.waqiti.payment.exception;

/**
 * ACH Processing Exception
 *
 * Thrown when ACH batch or transaction processing fails
 *
 * @author Waqiti Engineering
 */
public class ACHProcessingException extends RuntimeException {

    private String batchNumber;
    private String errorCode;

    public ACHProcessingException(String message) {
        super(message);
    }

    public ACHProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public ACHProcessingException(String message, String batchNumber) {
        super(message);
        this.batchNumber = batchNumber;
    }

    public ACHProcessingException(String message, String batchNumber, String errorCode) {
        super(message);
        this.batchNumber = batchNumber;
        this.errorCode = errorCode;
    }

    public ACHProcessingException(String message, String batchNumber, Throwable cause) {
        super(message, cause);
        this.batchNumber = batchNumber;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
