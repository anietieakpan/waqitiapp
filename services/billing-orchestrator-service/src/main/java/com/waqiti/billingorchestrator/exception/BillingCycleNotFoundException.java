package com.waqiti.billingorchestrator.exception;

import java.util.UUID;

/**
 * Exception thrown when a billing cycle is not found
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
public class BillingCycleNotFoundException extends BillingOrchestratorException {

    public BillingCycleNotFoundException(UUID cycleId) {
        super("Billing cycle not found with ID: " + cycleId, "BILLING_CYCLE_NOT_FOUND");
    }

    public BillingCycleNotFoundException(String message) {
        super(message, "BILLING_CYCLE_NOT_FOUND");
    }
}
