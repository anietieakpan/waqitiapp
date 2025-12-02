package com.waqiti.billpayment.exception;

/**
 * Exception thrown when bill sharing/splitting operations fail
 * Results in HTTP 422 Unprocessable Entity
 */
public class BillSharingException extends BillPaymentException {

    public BillSharingException(String message) {
        super("BILL_SHARING_ERROR", message);
    }

    public BillSharingException(String message, Throwable cause) {
        super("BILL_SHARING_ERROR", message, cause);
    }

    public BillSharingException(String errorCode, String message) {
        super(errorCode, message);
    }
}
