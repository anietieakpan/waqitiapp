package com.waqiti.billpayment.exception;

import java.util.UUID;

/**
 * Exception thrown when auto-pay configuration is not found
 * Results in HTTP 404 Not Found
 */
public class AutoPayConfigNotFoundException extends BillPaymentException {

    public AutoPayConfigNotFoundException(UUID configId) {
        super("AUTOPAY_CONFIG_NOT_FOUND",
              "Auto-pay configuration not found with ID: " + configId,
              configId);
    }

    public AutoPayConfigNotFoundException(UUID configId, String userId) {
        super("AUTOPAY_CONFIG_NOT_FOUND",
              String.format("Auto-pay configuration not found with ID: %s for user: %s", configId, userId),
              configId, userId);
    }

    public AutoPayConfigNotFoundException(String message) {
        super("AUTOPAY_CONFIG_NOT_FOUND", message);
    }
}
