package com.waqiti.payment.fraud.exception;

/**
 * Exception thrown when fraud detection check times out
 *
 * This is a critical exception indicating that the fraud detection
 * system could not complete its analysis within the required SLA.
 *
 * Security Policy: Fail closed - block transaction on timeout
 *
 * @author Waqiti Fraud Detection Team
 * @version 2.0.0
 */
public class FraudCheckTimeoutException extends RuntimeException {

    public FraudCheckTimeoutException(String message) {
        super(message);
    }

    public FraudCheckTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
