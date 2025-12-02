package com.waqiti.wallet.exception;

/**
 * Exception thrown when wallet operations fail due to system errors,
 * locking issues, or other operational problems
 */
public class WalletOperationException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;

    public WalletOperationException(String message) {
        super(message);
        this.errorCode = "WALLET_OPERATION_ERROR";
        this.userMessage = "A wallet operation failed. Please try again later.";
    }

    public WalletOperationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "WALLET_OPERATION_ERROR";
        this.userMessage = "A wallet operation failed. Please try again later.";
    }

    public WalletOperationException(String errorCode, String message, String userMessage) {
        super(message);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }

    public WalletOperationException(String errorCode, String message, String userMessage, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }
}