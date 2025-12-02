package com.waqiti.billpayment.exception;

/**
 * Exception thrown when wallet service operations fail
 * Results in HTTP 502 Bad Gateway or 503 Service Unavailable
 */
public class WalletServiceException extends BillPaymentException {

    public WalletServiceException(String message) {
        super("WALLET_SERVICE_ERROR", message);
    }

    public WalletServiceException(String message, Throwable cause) {
        super("WALLET_SERVICE_ERROR", message, cause);
    }

    public WalletServiceException(String operation, String errorMessage) {
        super("WALLET_SERVICE_ERROR",
              String.format("Wallet service %s operation failed: %s", operation, errorMessage),
              operation, errorMessage);
    }
}
