package com.waqiti.billpayment.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when payment amount exceeds configured limits
 * Results in HTTP 422 Unprocessable Entity
 */
public class PaymentLimitExceededException extends BillPaymentException {

    public PaymentLimitExceededException(BigDecimal amount, BigDecimal limit, String limitType) {
        super("PAYMENT_LIMIT_EXCEEDED",
              String.format("Payment amount %s exceeds %s limit of %s", amount, limitType, limit),
              amount, limit, limitType);
    }

    public PaymentLimitExceededException(String message) {
        super("PAYMENT_LIMIT_EXCEEDED", message);
    }
}
