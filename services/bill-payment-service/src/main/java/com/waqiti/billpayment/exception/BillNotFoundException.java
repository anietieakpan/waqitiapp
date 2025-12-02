package com.waqiti.billpayment.exception;

import java.util.UUID;

/**
 * Exception thrown when a bill is not found
 * Results in HTTP 404 Not Found
 */
public class BillNotFoundException extends BillPaymentException {

    public BillNotFoundException(UUID billId) {
        super("BILL_NOT_FOUND", "Bill not found with ID: " + billId, billId);
    }

    public BillNotFoundException(String message) {
        super("BILL_NOT_FOUND", message);
    }

    public BillNotFoundException(UUID billId, String userId) {
        super("BILL_NOT_FOUND",
              String.format("Bill not found with ID: %s for user: %s", billId, userId),
              billId, userId);
    }
}
