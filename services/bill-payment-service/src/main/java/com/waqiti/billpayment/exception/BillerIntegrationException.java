package com.waqiti.billpayment.exception;

/**
 * Exception thrown when biller integration/API calls fail
 * Results in HTTP 502 Bad Gateway or 503 Service Unavailable
 */
public class BillerIntegrationException extends BillPaymentException {

    public BillerIntegrationException(String billerName, String message) {
        super("BILLER_INTEGRATION_ERROR",
              String.format("Biller integration failed for %s: %s", billerName, message),
              billerName, message);
    }

    public BillerIntegrationException(String billerName, String message, Throwable cause) {
        super("BILLER_INTEGRATION_ERROR",
              String.format("Biller integration failed for %s: %s", billerName, message),
              cause);
    }

    public BillerIntegrationException(String message) {
        super("BILLER_INTEGRATION_ERROR", message);
    }

    public BillerIntegrationException(String message, Throwable cause) {
        super("BILLER_INTEGRATION_ERROR", message, cause);
    }
}
