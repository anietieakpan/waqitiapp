package com.waqiti.billpayment.exception;

import java.util.UUID;

/**
 * Exception thrown when a biller is not found
 * Results in HTTP 404 Not Found
 */
public class BillerNotFoundException extends BillPaymentException {

    public BillerNotFoundException(UUID billerId) {
        super("BILLER_NOT_FOUND", "Biller not found with ID: " + billerId, billerId);
    }

    public BillerNotFoundException(String billerId) {
        super("BILLER_NOT_FOUND", "Biller not found with ID: " + billerId, billerId);
    }

    public BillerNotFoundException(String message, Object... args) {
        super("BILLER_NOT_FOUND", message, args);
    }
}
