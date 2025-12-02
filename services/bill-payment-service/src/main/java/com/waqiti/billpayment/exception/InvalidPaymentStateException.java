package com.waqiti.billpayment.exception;

/**
 * Exception thrown when an operation is attempted on a payment in an invalid state
 * For example: trying to cancel a completed payment
 * Results in HTTP 409 Conflict
 */
public class InvalidPaymentStateException extends BillPaymentException {

    public InvalidPaymentStateException(String currentState, String requiredState) {
        super("INVALID_PAYMENT_STATE",
              String.format("Payment is in state '%s' but operation requires state '%s'",
                          currentState, requiredState),
              currentState, requiredState);
    }

    public InvalidPaymentStateException(String message) {
        super("INVALID_PAYMENT_STATE", message);
    }

    public InvalidPaymentStateException(String message, Throwable cause) {
        super("INVALID_PAYMENT_STATE", message, cause);
    }
}
