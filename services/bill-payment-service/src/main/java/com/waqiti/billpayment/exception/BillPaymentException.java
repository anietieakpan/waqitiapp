package com.waqiti.billpayment.exception;

/**
 * Base exception for all bill payment service exceptions
 * Provides common functionality for exception handling across the service
 */
public class BillPaymentException extends RuntimeException {

    private final String errorCode;
    private final Object[] args;

    public BillPaymentException(String message) {
        super(message);
        this.errorCode = "BILL_PAYMENT_ERROR";
        this.args = null;
    }

    public BillPaymentException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "BILL_PAYMENT_ERROR";
        this.args = null;
    }

    public BillPaymentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
    }

    public BillPaymentException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = null;
    }

    public BillPaymentException(String errorCode, String message, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object[] getArgs() {
        return args;
    }
}
