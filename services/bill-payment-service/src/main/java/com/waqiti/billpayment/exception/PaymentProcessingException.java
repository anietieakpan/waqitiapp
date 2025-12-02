package com.waqiti.billpayment.exception;

/**
 * Exception thrown when payment processing fails
 * Generic exception for payment processing errors
 * Results in HTTP 500 Internal Server Error or 422 Unprocessable Entity
 */
public class PaymentProcessingException extends BillPaymentException {

    public PaymentProcessingException(String message) {
        super("PAYMENT_PROCESSING_ERROR", message);
    }

    public PaymentProcessingException(String message, Throwable cause) {
        super("PAYMENT_PROCESSING_ERROR", message, cause);
    }

    public PaymentProcessingException(String errorCode, String message) {
        super(errorCode, message);
    }

    public PaymentProcessingException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
