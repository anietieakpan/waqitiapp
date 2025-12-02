package com.waqiti.billingorchestrator.exception;

/**
 * Exception thrown when an operation is invalid for the current billing cycle state
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
public class InvalidBillingCycleStateException extends BillingOrchestratorException {

    public InvalidBillingCycleStateException(String message) {
        super(message, "INVALID_BILLING_CYCLE_STATE");
    }

    public InvalidBillingCycleStateException(String currentState, String operation) {
        super(String.format("Cannot perform '%s' operation in '%s' state", operation, currentState),
              "INVALID_BILLING_CYCLE_STATE");
    }
}
