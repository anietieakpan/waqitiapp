package com.waqiti.payment.fraud.exception;

/**
 * Exception thrown when fraud detection service encounters an error
 *
 * This exception indicates a failure in the fraud detection pipeline
 * that prevents proper risk assessment.
 *
 * Security Policy: Fail closed - block transaction on service error
 *
 * @author Waqiti Fraud Detection Team
 * @version 2.0.0
 */
public class FraudServiceException extends RuntimeException {

    public FraudServiceException(String message) {
        super(message);
    }

    public FraudServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
