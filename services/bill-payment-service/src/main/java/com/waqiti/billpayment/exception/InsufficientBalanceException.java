package com.waqiti.billpayment.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when user has insufficient balance for bill payment
 * Results in HTTP 402 Payment Required or 409 Conflict
 */
public class InsufficientBalanceException extends BillPaymentException {

    public InsufficientBalanceException(BigDecimal required, BigDecimal available) {
        super("INSUFFICIENT_BALANCE",
              String.format("Insufficient balance. Required: %s, Available: %s", required, available),
              required, available);
    }

    public InsufficientBalanceException(String message) {
        super("INSUFFICIENT_BALANCE", message);
    }

    public InsufficientBalanceException(String message, Throwable cause) {
        super("INSUFFICIENT_BALANCE", message, cause);
    }
}
