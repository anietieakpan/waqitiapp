package com.waqiti.billingorchestrator.exception;

/**
 * Base exception for all Billing Orchestrator exceptions
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
public class BillingOrchestratorException extends RuntimeException {

    private final String errorCode;

    public BillingOrchestratorException(String message) {
        super(message);
        this.errorCode = "BILLING_ERROR";
    }

    public BillingOrchestratorException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public BillingOrchestratorException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "BILLING_ERROR";
    }

    public BillingOrchestratorException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
