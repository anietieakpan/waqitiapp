package com.waqiti.billingorchestrator.exception;

import java.util.UUID;

/**
 * Exception thrown when a subscription is not found
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
public class SubscriptionNotFoundException extends BillingOrchestratorException {

    public SubscriptionNotFoundException(UUID subscriptionId) {
        super("Subscription not found with ID: " + subscriptionId, "SUBSCRIPTION_NOT_FOUND");
    }

    public SubscriptionNotFoundException(String message) {
        super(message, "SUBSCRIPTION_NOT_FOUND");
    }
}
